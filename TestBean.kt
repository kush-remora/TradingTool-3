import java.beans.Introspector
import com.tradingtool.core.strategy.csvbacktest.BacktestTradeReview

fun main() {
    val info = Introspector.getBeanInfo(BacktestTradeReview::class.java)
    for (pd in info.propertyDescriptors) {
        println(pd.name + " -> " + pd.readMethod?.name)
    }
}
