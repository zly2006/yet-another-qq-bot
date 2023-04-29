
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.buildForwardMessage
import net.mamoe.mirai.message.data.content
import user.UserProfile
import user.takeMoney
import java.util.*
import kotlin.random.Random

private fun GroupMessageEvent.doLottery(): Message {
    val n = (0..10000).random()
    return when (n) {
        in 0 until 2 -> {
            sender.profile.money += 3000
            sender.profile.records.add(
                UserProfile.Record(
                    System.currentTimeMillis(),
                    "lottery",
                    "特等奖，3000金币",
                    "主动抽奖"
                )
            )
            PlainText("恭喜你抽中了特等奖，获得3000金币")
        }

        2 -> {
            sender.profile.addItem(checkInRewards.random())
            sender.profile.records.add(
                UserProfile.Record(
                    System.currentTimeMillis(),
                    "lottery",
                    "特等奖，随机字母碎片",
                    "主动抽奖"
                )
            )
            PlainText("恭喜你抽中了特等奖，获得随机字母碎片")
        }

        in 3 until 200 -> {
            sender.profile.money += 100
            PlainText("恭喜你抽中了一等奖，获得100金币")
        }

        in 200 until 1200 -> {
            sender.profile.money += 40
            PlainText("恭喜你抽中了二等奖，获得40金币")
        }

        in 1200 until 4000 -> {
            sender.profile.money += 10
            PlainText("恭喜你抽中了三等奖，获得10金币")
        }

        else -> {
            PlainText("很遗憾，你没有抽中奖")
        }
    }
}

fun configureLottery(bot: Bot) {
    helpMessages.add("#抽签 - 今日运势")
    helpMessages.add("#抽奖 / #十连 / #百连 - 抽奖")
    helpMessages.add("#抽奖规则 - 抽奖规则")
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (shouldRespond) {
            when (message.content) {
                "#十连" -> {
                    if (!sender.profile.takeMoney(100.0)) {
                        group.sendMessage("金币不足")
                        return@subscribeAlways
                    }
                    group.sendMessage(buildForwardMessage {
                        repeat(10) {
                            bot says doLottery()
                        }
                    })
                }
                "#百连" -> {
                    if (!sender.profile.takeMoney(1000.0)) {
                        group.sendMessage("金币不足")
                        return@subscribeAlways
                    }
                    group.sendMessage(buildForwardMessage {
                        repeat(100) {
                            bot says doLottery()
                        }
                    })
                }
                "#抽签" -> {
                    val r = (0..100).random(Random(
                        (System.currentTimeMillis() + TimeZone.getDefault().rawOffset) / 1000 / 60 / 60 / 24
                                + sender.id
                    ).apply {
                        nextBytes(100) // make it looks like randomly
                    })
                    println(r)
                    val result = when (r) {
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
                    group.sendMessage(doLottery())
                }
                "#抽奖规则" -> {
                    group.sendMessage("""
                                每次花费10金币
                                概率：
                                0.02% 特等奖 3000金币
                                0.01% 特等奖 随机字母碎片
                                1.98% 一等奖 100金币
                                10% 二等奖 40金币
                                28% 三等奖 10金币
                                60% 未中奖
                            """.trimIndent())
                }
            }
        }
    }
}