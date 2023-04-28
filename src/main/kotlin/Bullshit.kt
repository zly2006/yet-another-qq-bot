
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import java.io.File
import kotlin.random.Random

private val numberRegex = Regex("[\\d,，]+")

@Serializable
private data class Data(
    val famous: List<String>,
    val bosh: List<String>,
    val after: List<String>,
    val before: List<String>,
)

private val data = JSON.decodeFromString<Data>(File("bullshit.data.json").readText())

fun configureBullshit(bot: Bot) {
    helpMessages.add("用狗屁不通文章生成器写一篇 [100] 字 <主题> 的文章")
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (shouldRespond) {
            if (message.content.contains("狗屁不通")) {
                var i = 0
                fun nextBosh(): String {
                    val b = data.bosh[i]
                    i = (i + 1) % data.bosh.size
                    return b
                }
                val title = message.content.substringAfter("写一篇")
                        .substringAfter("关于")
                        .substringAfter("字")
                        .substringBefore("的文章")
                val length = numberRegex.find(message.content.substringBefore('字'))
                    ?.value?.toIntOrNull() ?: 1000
                if (title.isEmpty()) {
                    group.sendMessage("无法识别标题")
                } else {
                    val sb = StringBuilder("    ")
                    while (sb.length < length) {
                        val b = Random.nextInt(0, 100)
                        if (b < 5) {
                            sb.append("\n    ")
                        }
                        else if (b < 20) {
                            sb.append(
                                data.famous.random()
                                    .replace("a", data.before.random())
                                    .replace("b", data.after.random())
                            )
                        }
                        else {
                            sb.append(nextBosh())
                        }
                    }
                    group.sendMessage("x\n$sb".replace("x", title))
                }
            }
        }
    }
}