
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.content
import user.takeMoney
import kotlin.math.max

private val moneyRegex = Regex("\\d+(\\.\\d{1,2})?")
private val numberRegex = Regex("\\d+")

private fun configureCheckIn(bot: Bot) {
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (group.enabled) {
            if (message.content == "#签到") {
                val date = System.currentTimeMillis() / 1000 / 60 / 60 / 24
                if (sender.profile.lastCheckInDate >= date) {
                    group.sendMessage("今天已经签到过了")
                    return@subscribeAlways
                } else {
                    if (sender.profile.lastCheckInDate == date - 1) {
                        sender.profile.keepCheckInDuration++
                    } else {
                        sender.profile.keepCheckInDuration = 1
                    }
                    val randomMax = max(sender.profile.keepCheckInDuration * 5, 10).toInt()
                    val money = 100 + (0..randomMax).random()
                    sender.profile.money += money
                    group.sendMessage("签到成功，获得 $money 金币，连续签到 ${sender.profile.keepCheckInDuration} 天")
                }
            }
        }
    }
}

fun resolveTarget(message: MessageChain, bot: Bot) = message.filterIsInstance<At>()
    .firstOrNull { it.target != bot.id }?.target
    ?: numberRegex.matchAt(message.content.substringAfter("qq").trimStart(), 0)
        ?.value?.toLong()

fun resolveAmount(message: MessageChain) = moneyRegex.findAll(message.content.substringBefore("金币"))
    .last().value.toDouble()


private fun configureTransform(bot: Bot) {
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (group.enabled) {
            if (message.content.startsWith("#转账")) {
                val target = resolveTarget(message, bot)
                val amount = (resolveAmount(message) * 100).toInt() / 100.0
                if (target == null) {
                    group.sendMessage("无法识别目标")
                    return@subscribeAlways
                }
                if (sender.profile.takeMoney(amount)) {
                    profiles[target].money += amount
                    group.sendMessage("成功转账 $amount 金币给 " + At(target))
                }
                else {
                    group.sendMessage("余额不足")
                }
            }
        }
    }
}

fun configureMoney(bot: Bot) {
    configureCheckIn(bot)
}