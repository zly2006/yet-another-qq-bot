import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import user.UserProfile
import java.net.URL

fun configureMarry(bot: Bot) {
    helpMessages.add("#marry - 随机结婚")
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (group.enabled) {
            when (message.content) {
                "#marry" -> {
                    doMarry(it)
                }
            }
        }
    }
}

private suspend fun doMarry(event: GroupMessageEvent) {
    var data = event.sender.profile.marry[event.group.id]

    if (data == null || !data.valid()) {
        // 随机抽取一个幸运用户
        val aimless = event.group.members.stream()
            .filter { it.id != event.sender.id }
            .filter {
                val marryData = it.profile.marry[it.group.id]
                return@filter marryData == null || !marryData.valid()
            }
            .toList()
        if (aimless.isEmpty()) {
            event.group.sendMessage(buildMessageChain {
                +At(event.sender)
                +PlainText(" 没可marry目标了")
            })
            return
        }
        val random = aimless.random()
        data = UserProfile.MarryData(
            System.currentTimeMillis(), random.id, random.nick, random.avatarUrl, true
        )
        event.sender.profile.marry[event.group.id] = data
        random.profile.marry[event.group.id] = UserProfile.MarryData(
            System.currentTimeMillis(),
            event.sender.id,
            event.sender.nick,
            event.sender.avatarUrl,
            false
        )
    }

    if (data.applicant) {
        val connection = URL(data.targetAvatarUrl).openConnection()
        connection.connect()
        connection.getInputStream().use {
            it.toExternalResource().use {
                val image = event.group.uploadImage(it)
                event.group.sendMessage(buildMessageChain {
                    +At(event.sender.id)
                    +PlainText(" 今天你的群老婆是")
                    +image
                    +PlainText("${data.targetName}(${data.target})哒")
                })
            }
        }
    } else {
        val connection = URL(data.targetAvatarUrl).openConnection()
        connection.connect()
        connection.getInputStream().use {
            it.toExternalResource().use {
                val image = event.group.uploadImage(it)
                event.group.sendMessage(buildMessageChain {
                    +At(event.sender.id)
                    +PlainText(" 今天你被了, 群老公是")
                    +image
                    +PlainText("${data.targetName}(${data.target})哒")
                })
            }
        }
    }
}
