
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Contact.Companion.uploadImage
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content

fun configureFufu(bot: Bot) {
    helpMessages.add("fufu/capoo - 随机发送fufu或capoo表情包")
    val size = mapOf(
        "fufu" to 277,
        "capoo" to 873
    )
    bot.shouldRespondChannel.subscribeAlways<GroupMessageEvent> {
        val key = message.content.trim()
        if (key == "fufu" || key == "capoo") {
            Config::class.java.getResourceAsStream("/$key/${(0 until size[key]!!).random()}").use {
                it?.let { group.sendMessage(group.uploadImage(it)) }
                    ?: group.sendMessage("没有${message.content}了")
            }
        }
    }
}
