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
                    if (group.id !in config.testGroup) {
                        config.testGroup.add(group.id)
                        group.sendMessage("开启为测试群组")
                    }
                    else {
                        config.testGroup.remove(group.id)
                        group.sendMessage("不再启用测试功能")
                    }
                }
                "!spam" -> {
                    if (group.id !in config.allowSpam) {
                        config.allowSpam.add(group.id)
                        group.sendMessage("已启用可能被认为垃圾信息的功能")
                    }
                    else {
                        config.allowSpam.remove(group.id)
                        group.sendMessage("已禁用可能被认为垃圾信息的功能")
                    }
                }
            }
            saveJson("config.json", config)
        }
    }
}
