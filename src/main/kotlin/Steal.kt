import kotlinx.serialization.Serializable
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.at
import net.mamoe.mirai.message.data.content
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

@Serializable
class StealRecord(
    var thief: Long = 0L, var victim: Long = 0L, var price: Double = 0.0, var time: Int = 0
)

var stealRecords: MutableList<StealRecord> = loadJson("steals.json") { mutableListOf() }

fun normal(x: Double, mu: Double, sigma: Double): Double {

    return 1 / (sigma * sqrt(2 * 3.14159265359)) * exp(-(x - mu).pow(2) / (2 * sigma.pow(2)))
}

fun configureSteal(bot: Bot) {
    helpMessages.add("#偷金币 <玩家QQ号或@>")
    helpMessages.add("#举报小偷 <玩家QQ号或@>")

    saveActions.add { saveJson("steals.json", stealRecords) }
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (group.enabled) {
            if (message.content.startsWith("#偷金币")) {
                when (val target =
                    message.content.substringAfter("#偷金币 ").toLongOrNull() ?: resolveTarget(message, bot)) {
                    null -> {
                        group.sendMessage("无法识别目标用户")
                        return@subscribeAlways
                    }

                    sender.id -> {
                        group.sendMessage("无法偷取自己的金币")
                        return@subscribeAlways
                    }

                    else -> {
                        for (i in stealRecords) {
                            if (i.thief == sender.id && (i.time / 60 / 60 / 24) == (time / 60 / 60 / 24)) {
                                group.sendMessage("你今天已经偷过金币了")
                                return@subscribeAlways
                            }
                        }
                        val coins: MutableList<Int> = mutableListOf()
                        for (i in 5 until 151) {
                            val p = normal(i.toDouble(), 75.0, 40.0)
                            val n = round(p * 5000)

                            for (j in 0 until n.toInt()) {
                                coins.add(i)
                            }
                        }
                        var coinsGet = coins.random().toDouble()  // 采用正态分布，μ=75，σ=40，取值为5-150
                        val targetMoney = profile(target).money
                        if (targetMoney < coinsGet) {
                            coinsGet = targetMoney
                        }
                        profile(target).money -= coinsGet
                        profile(target).sendMessageWithAt(
                            PlainText("你被 ${sender.guz} 偷走了 $coinsGet 个金币！"), bot
                        )
                        sender.profile.increaseMoney(coinsGet)
                        group.sendMessage(sender.at() + "你偷走了 $coinsGet 个金币！")


                        stealRecords.add(
                            StealRecord(sender.id, target, coinsGet, time)
                        )

                        return@subscribeAlways
                    }
                }
            }
            if (message.content.startsWith("#举报小偷")) {
                when (val target =
                    message.content.substringAfter("举报小偷").toLongOrNull() ?: resolveTarget(message, bot)) {
                    null -> {
                        group.sendMessage("无法找到指定用户")
                    }

                    sender.id -> {
                        group.sendMessage("不能举报自己")
                    }

                    else -> {
                        var isStolen = false
                        for (i in stealRecords) {
                            if (i.victim == sender.id && time - i.time <= (48 * 60 * 60) && i.thief == target) {
                                isStolen = true
                                val money = i.price
                                sender.profile.increaseMoney(money * 1.15)
                                profile(target).money -= (money * 1.20)
                                group.sendMessage(sender.at() + "你已追回 $money 金币，额外奖励15%")
                                profile(target).sendMessageWithAt(
                                    PlainText(
                                        "你偷取的 $money 金币已被追回，额外罚款20%"
                                    ), bot
                                )
                                i.time = 0  // 作废
                            }
                        }
                        if (!isStolen) group.sendMessage(sender.at() + "该用户在48小时内没有偷窃您的金币")
                    }
                }
            }
        }
    }
}
