
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.net.URL

fun configureFace(bot: Bot) {
    bot.eventChannel.subscribeAlways<FriendMessageEvent> {
        if (message.content.startsWith("#face ")) {
            val id = message.content.substringAfter("#face ").toLongOrNull()
            if (id == null) {
                friend.sendMessage("无法解析ID")
                return@subscribeAlways
            }
            val member = bot.groups.firstOrNull { it.contains(id) }?.get(id)
            suspend fun send(url: String) {
                val connection = URL(url).openConnection()
                connection.connect()
                connection.getInputStream().use {
                    it.toExternalResource().use {
                        friend.sendMessage(friend.uploadImage(it))
                    }
                }
            }
            if (member != null) {
                send(member.avatarUrl)
            }
            else {
                bot.getStranger(id)?.let { it1 -> send(it1.avatarUrl) }
                    ?: friend.sendMessage("无法找到用户")
            }
        }
    }
}