import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Contact.Companion.uploadImage
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import java.io.File

fun configureFufu(bot: Bot) {
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (group.enabled) {
            when (message.content) {
                "fufu", "capoo" -> {
                    val file = File(message.content).listFiles()?.randomOrNull()
                    if (file == null) {
                        group.sendMessage("没有${message.content}了")
                    } else {
                        group.sendMessage(group.uploadImage(file))
                    }
                }
            }
        }
    }
}
