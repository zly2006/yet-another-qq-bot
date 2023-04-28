
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.at
import net.mamoe.mirai.message.data.content
import user.takeMoney

val bets: MutableMap<Long, Double> = loadJson("bets.json") { mutableMapOf() }

private val trueFalseList = listOf(true, false)

fun configureBet(bot: Bot) {
    helpMessages.add("#下注 <金额> - 下注")
    helpMessages.add("#下注规则 - 下注规则")
    helpMessages.add("#收回赌注")
    helpMessages.add("#参与对赌 <玩家QQ 或 at> - 参与对赌")
    helpMessages.add("#筹码列表")
    saveActions.add {
        saveJson("bets.json", bets)
    }
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (shouldRespond) {
            if (message.content == "#筹码列表") {
                val list = bets.toList().sortedBy { (_, value) -> value }.reversed()
                group.sendMessage(
                    "筹码列表：\n" + list.joinToString("\n") {
                        "来自 ${profile(it.first).guz(bot)} 的 ${it.second} 金币"
                    }
                )
            }
            if (message.content == "#收回赌注") {
                if (sender.id in bets.keys) {
                    val amount = bets[sender.id]!!
                    sender.profile.increaseMoney(amount)
                    group.sendMessage("已收回 $amount 金币")
                    bets.remove(sender.id)
                } else {
                    group.sendMessage("你没有下注")
                }
            }
            if (message.content.startsWith("#下注")) {
                val amount = resolveAmount(message)
                if (amount < 0.01) {
                    group.sendMessage("下注金额不能小于 0.01")
                    return@subscribeAlways
                }
                if (bets.contains(sender.id)) {
                    group.sendMessage("你已经下注了，请#收回赌注")
                    return@subscribeAlways
                }
                if (sender.profile.money < amount) {
                    group.sendMessage("你没有足够的金币")
                } else {
                    if (sender.profile.takeMoney(amount)) {
                        bets[sender.id] = amount
                        group.sendMessage("已下注 $amount 金币")
                    } else {
                        group.sendMessage("你没有足够的金币")
                    }
                }
            }
            if (message.content.startsWith("#参与对赌")) {
                val target = message.content.substringAfter("#参与对赌 ").toLongOrNull() ?: resolveTarget(message, bot)
                if (target == null || target == sender.id || target !in bets.keys) {
                    group.sendMessage("无法识别对赌者")
                    return@subscribeAlways
                }
                val amount = bets[target]!!
                if (sender.profile.takeMoney(amount)) {
                    if (trueFalseList.random()) {
                        sender.profile.increaseMoney(amount * 2)
                        group.sendMessage(sender.at() + "你赢了 $amount 金币")
                        profile(target).sendMessageWithAt(PlainText("你在对赌输了 $amount 金币，对赌者是 ${sender.guz}"), bot)
                    } else {
                        profile(target).increaseMoney(amount * 2)
                        group.sendMessage(sender.at() + "你输了 $amount 金币")
                        profile(target).sendMessageWithAt(PlainText("你在对赌赢了 $amount 金币，对赌者是 ${sender.guz}"), bot)
                    }
                    bets.remove(target)
                } else {
                    group.sendMessage("你没有足够的金币")
                }
            }
        }
    }
}