
import kotlinx.serialization.decodeFromString
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import java.io.File
import kotlin.random.Random

private val numberRegex = Regex("[\\d,，]+")

private data class Data(
    val famous: List<String>,
    val bosh: List<String>,
    val after: List<String>,
    val before: List<String>,
)

private val data = JSON.decodeFromString<Data>(File("bullshit.data.json").readText())

fun configureBullshit(bot: Bot) {
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (group.enabled) {
            if (message.content.contains("狗屁不通")) {
                val title =
                    message.content.substringAfter("写一篇")
                        .substringAfter("关于")
                        .substringBefore("书")
                        .substringBefore("信")
                        .substringBefore("的文章")
                val length = numberRegex.find(message.content.substringBefore('字'))
                    ?.value?.toIntOrNull() ?: 1000
                if (title.isEmpty()) {
                    group.sendMessage("无法识别标题")
                } else {
                    val sb = StringBuilder()
                    while (sb.length < length) {
                        val b = Random.nextInt(0, 100)
                        if (b < 5) {
                            sb.append("。\n    ")
                        }
                        else if (b < 20) {
                            sb.append(
                                data.famous.random()
                                    .replace("a", data.before.random())
                                    .replace("b", data.after.random())
                            )
                        }
                        else {
                            sb.append(data.bosh.random())
                        }
                    }
                    group.sendMessage("x\n$sb".replace("x", title))
                }
            }
        }
    }
}