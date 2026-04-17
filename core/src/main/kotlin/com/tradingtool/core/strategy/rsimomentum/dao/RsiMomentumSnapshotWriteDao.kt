package com.tradingtool.core.strategy.rsimomentum.dao

import com.tradingtool.core.constants.DatabaseConstants.Tables
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.time.LocalDate
import java.time.OffsetDateTime

interface RsiMomentumSnapshotWriteDao {

    @SqlUpdate(
        """
        INSERT INTO public.${Tables.RSI_MOMENTUM_SNAPSHOT_DAILY}
            (profile_id, as_of_date, run_at, snapshot_json, updated_at)
        VALUES
            (:profileId, :asOfDate, :runAt, :snapshotJson::jsonb, NOW())
        ON CONFLICT (profile_id, as_of_date)
        DO UPDATE SET
            run_at        = EXCLUDED.run_at,
            snapshot_json = EXCLUDED.snapshot_json,
            updated_at    = NOW()
        """
    )
    fun upsert(
        @Bind("profileId") profileId: String,
        @Bind("asOfDate") asOfDate: LocalDate,
        @Bind("runAt") runAt: OffsetDateTime,
        @Bind("snapshotJson") snapshotJson: String,
    )

    @SqlUpdate(
        """
        DELETE FROM public.${Tables.RSI_MOMENTUM_SNAPSHOT_DAILY}
        """
    )
    fun deleteAll(): Int
}
