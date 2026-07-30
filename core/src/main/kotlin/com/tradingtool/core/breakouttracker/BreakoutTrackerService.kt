package com.tradingtool.core.breakouttracker

import com.google.inject.Inject
import com.tradingtool.core.database.JdbiHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.sql.ResultSet
import java.time.LocalDate

data class BreakoutTrackerEntry(
    val id: Long,
    val instrumentToken: Long,
    val symbol: String,
    val companyName: String,
    val breakoutDate: LocalDate,
    val breakoutPrice: Double,
    val notes: String,
)

data class SaveBreakoutTrackerEntryRequest(
    val instrumentToken: Long,
    val symbol: String,
    val companyName: String,
    val breakoutDate: LocalDate,
    val breakoutPrice: Double,
    val notes: String,
)

@RegisterRowMapper(BreakoutTrackerEntryMapper::class)
interface BreakoutTrackerReadDao {
    @SqlQuery("""
        SELECT id, instrument_token, symbol, company_name, breakout_date, breakout_price, notes
        FROM public.breakout_tracker_entries
        ORDER BY breakout_date DESC, symbol ASC
    """)
    fun list(): List<BreakoutTrackerEntry>
}

@RegisterRowMapper(BreakoutTrackerEntryMapper::class)
interface BreakoutTrackerWriteDao {
    @SqlQuery("""
        INSERT INTO public.breakout_tracker_entries (
            instrument_token, symbol, company_name, breakout_date, breakout_price, notes, updated_at
        ) VALUES (
            :instrumentToken, :symbol, :companyName, :breakoutDate, :breakoutPrice, :notes, NOW()
        )
        ON CONFLICT (instrument_token) DO UPDATE SET
            symbol = EXCLUDED.symbol,
            company_name = EXCLUDED.company_name,
            breakout_date = EXCLUDED.breakout_date,
            breakout_price = EXCLUDED.breakout_price,
            notes = EXCLUDED.notes,
            updated_at = NOW()
        RETURNING id, instrument_token, symbol, company_name, breakout_date, breakout_price, notes
    """)
    fun save(@BindBean request: SaveBreakoutTrackerEntryRequest): BreakoutTrackerEntry

    @SqlUpdate("DELETE FROM public.breakout_tracker_entries WHERE id = :id")
    fun delete(@Bind("id") id: Long): Int
}

class BreakoutTrackerEntryMapper : RowMapper<BreakoutTrackerEntry> {
    override fun map(resultSet: ResultSet, context: StatementContext): BreakoutTrackerEntry = BreakoutTrackerEntry(
        id = resultSet.getLong("id"),
        instrumentToken = resultSet.getLong("instrument_token"),
        symbol = resultSet.getString("symbol"),
        companyName = resultSet.getString("company_name"),
        breakoutDate = resultSet.getDate("breakout_date").toLocalDate(),
        breakoutPrice = resultSet.getDouble("breakout_price"),
        notes = resultSet.getString("notes"),
    )
}

class BreakoutTrackerService @Inject constructor(
    private val entries: JdbiHandler<BreakoutTrackerReadDao, BreakoutTrackerWriteDao>,
) {
    suspend fun list(): List<BreakoutTrackerEntry> = withContext(Dispatchers.IO) { entries.read { it.list() } }

    suspend fun save(request: SaveBreakoutTrackerEntryRequest): BreakoutTrackerEntry =
        withContext(Dispatchers.IO) { entries.write { it.save(request) } }

    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) { entries.write { it.delete(id) > 0 } }
}
