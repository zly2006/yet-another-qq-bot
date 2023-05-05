import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import kotlin.math.roundToInt

class RedBucket(
    val id: Long,
    val sender: Long,
    val group: Long,
    val message: String,
    val total: Double,
    val time: Long,
    val size: Int,
    val amounts: List<Double> = run {
        val all = total.times(100).roundToInt()
        val list = mutableListOf<Double>()
        var sum = 0
        for (i in 1 until size) {
            val amount = (all - sum).times(2).div(size - i).div(100.0)
            list.add(amount)
            sum += amount.times(100).roundToInt()
        }
        listOf()
    },
    val receivers: MutableList<Long> = mutableListOf(),
)

fun configureRedBucket(bot: Bot) {
    bot.shouldRespondChannel.subscribeAlways<GroupMessageEvent> {

    }
}
