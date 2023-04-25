import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content

fun configureGroupManage(bot: Bot) {
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (sender.id in config.admins) {
            when (message.content) {
                "!enable" -> {
                    config.enabledGroup.add(group.id)
                    group.sendMessage("已启用")
                }
                "!disable" -> {
                    config.enabledGroup.remove(group.id)
                    group.sendMessage("已禁用")
                }
                "!test" -> {
                    config.testGroup.add(group.id)
                    group.sendMessage("开启为测试群组")
                }
            }
        }
    }
}
