
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.at
import net.mamoe.mirai.message.data.content
import user.UserProfile
import kotlin.math.*


fun normalRandom(mean: Double, stdDev: Double): Double {
    val u1 = Math.random()
    val u2 = Math.random()
    val z = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    return mean + z * stdDev
}

fun configureSteal(bot: Bot) {
    helpMessages.add("#偷金币 @某人")
    helpMessages.add("#举报小偷 @某人")
    bot.shouldRespondChannel.subscribeAlways<GroupMessageEvent> {
        if (message.content.startsWith("#偷金币")) {
            val profile = sender.profile
            if (date(profile.stealMoneyRecords.lastOrNull()?.time ?: 0) >= date) {
                group.sendMessage("你今天已经偷过金币了！")
                return@subscribeAlways
            }
            val target = resolveTarget(message, bot)?.let { profile(it) } ?: run {
                group.sendMessage("无法找到目标")
                return@subscribeAlways
            }
            val amount = min(normalRandom(75.0, 40.0), target.money)
            if (amount <= 0.01) {
                group.sendMessage("他已经身无分文了，你真的忍心抢吗？")
                return@subscribeAlways
            }
            val finalAmount = amount.times(100).roundToInt() / 100.0
            target.money -= finalAmount
            profile.increaseMoney(finalAmount)
            profile.stealMoneyRecords.add(UserProfile.StealMoneyRecord(
                System.currentTimeMillis(),
                target.id,
                finalAmount
            ))
            target.sendMessageWithAt(PlainText("你被 ${sender.guz} 偷走了 $finalAmount 个金币！"), bot)
            group.sendMessage(sender.at() + "你偷走了 $finalAmount 个金币！")
        }
        if (message.content.startsWith("#举报小偷")) {
            val target = resolveTarget(message, bot)?.let { profile(it) } ?: run {
                group.sendMessage("无法找到小偷")
                return@subscribeAlways
            }
            val record = target.stealMoneyRecords.lastOrNull { it.target == sender.id &&
                    System.currentTimeMillis() - it.time < 48 * 3600_000 && !it.caught }
            if (record == null) {
                group.sendMessage("无法找到TA在48小时偷你金币的记录！")
                return@subscribeAlways
            }
            record.caught = true
            sender.profile.increaseMoney(record.amount * 1.15)
            group.sendMessage(sender.at() + "你已追回 ${record.amount} 金币，额外奖励15%")
            target.money -= (record.amount * 1.2)
            target.sendMessageWithAt(PlainText("你从 ${sender.guz} 偷取的 ${record.amount} 金币已被追回，有期徒刑2小时，另处罚款20%"), bot)
            target.banUntil = System.currentTimeMillis() + 7200_000
        }
    }
}