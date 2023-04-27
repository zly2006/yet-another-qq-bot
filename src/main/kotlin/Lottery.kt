
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import user.takeMoney
import java.util.*
import kotlin.random.Random

fun configureLottery(bot: Bot) {
    helpMessages.add("#抽签 - 今日运势")
    helpMessages.add("#抽奖 - 抽奖")
    helpMessages.add("#抽奖规则 - 抽奖规则")
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (group.enabled) {
            when (message.content) {
                "#抽签" -> {
                    val result = when (val r = (0..100).random(Random(
                        (System.currentTimeMillis() + TimeZone.getDefault().rawOffset) / 1000 / 60 / 60 / 24
                    ).apply {
                        nextBytes(100) // make it looks like randomly
                    }
                    )) {
                        in 0..10 -> "大凶"
                        in 11..30 -> "凶"
                        in 31..60 -> "吉"
                        in 61..90 -> "大吉"
                        else -> "超大吉"
                    }
                    group.sendMessage(result)
                }
                "#抽奖" -> {
                    if (!sender.profile.takeMoney(10.0)) {
                        group.sendMessage("金币不足")
                        return@subscribeAlways
                    }
                    val n = (0..100).random()
                    when (n) {
                        in 0..1 -> {
                            sender.profile.money += 100
                            group.sendMessage("恭喜你抽中了一等奖，获得100金币")
                        }
                        in 2..21 -> {
                            sender.profile.money += 30
                            group.sendMessage("恭喜你抽中了二等奖，获得30金币")
                        }
                        in 22..56 -> {
                            sender.profile.money += 10
                            group.sendMessage("恭喜你抽中了三等奖，获得10金币")
                        }
                        else -> {
                            group.sendMessage("很遗憾，你没有抽中奖")
                        }
                    }
                }
                "#抽奖规则" -> {
                    group.sendMessage("""
                        每次花费10金币
                        概率：
                        2% 一等奖 100金币
                        20% 二等奖 30金币
                        35% 三等奖 10金币
                        43% 未中奖
                    """.trimIndent())
                }
            }
        }
    }
}