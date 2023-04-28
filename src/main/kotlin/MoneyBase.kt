
import kotlinx.serialization.Serializable
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.content
import user.takeMoney
import java.util.*
import kotlin.math.max

private val moneyRegex = Regex("\\d+(\\.\\d{1,2})?")
private val numberRegex = Regex("\\d+")

private fun configureCheckIn(bot: Bot) {
    helpMessages.add("#签到 - 一天一次，连续签到可以获得更多金币")
    helpMessages.add("#签到信息 - 查看签到信息")
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (group.enabled) {
            if (message.content == "#签到") {
                val date = (System.currentTimeMillis() + TimeZone.getDefault().rawOffset) / 1000 / 60 / 60 / 24
                if (sender.profile.lastCheckInDate >= date) {
                    group.sendMessage("今天已经签到过了")
                    return@subscribeAlways
                } else {
                    if (sender.profile.lastCheckInDate == date - 1) {
                        sender.profile.keepCheckInDuration++
                    } else {
                        sender.profile.keepCheckInDuration = 1
                    }
                    sender.profile.lastCheckInDate = date
                    val randomMax = 100 + max(sender.profile.keepCheckInDuration * 5, 10).toInt()
                    val money = (100..randomMax).random()
                    sender.profile.money += money
                    group.sendMessage("签到成功，获得 $money 金币，连续签到 ${sender.profile.keepCheckInDuration} 天")
                }
            }
            if (message.content == "#签到信息") {
                group.sendMessage("你已经连续签到 ${sender.profile.keepCheckInDuration} 天，接下来随机签到可以随机获得 100 至 ${100 + max(sender.profile.keepCheckInDuration * 5, 10)} 金币")
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
    helpMessages.add("#转账 @某人 <金币> - 转账给指定用户")
    helpMessages.add("#我的余额 - 查看自己的余额")
    helpMessages.add("#财富榜 - 查看财富榜")
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (group.enabled) {
            val content = message.content.trim()
            if (content.startsWith("#转账")) {
                val target = resolveTarget(message, bot)
                val amount = (resolveAmount(message) * 100).toInt() / 100.0
                if (target == null) {
                    group.sendMessage("无法识别目标")
                    return@subscribeAlways
                }
                if (sender.profile.takeMoney(amount)) {
                    profile(target).money += amount
                    group.sendMessage(PlainText("成功转账 $amount 金币给 ") + At(target))
                }
                else {
                    group.sendMessage("余额不足")
                }
            }
            if (content == "#我的余额") {
                group.sendMessage("你的余额为 ${sender.profile.money} 金币")
            }
            if (content == "#财富榜") {
                val sorted = profiles.values.sortedByDescending { it.money }
                val top = sorted.take(10)
                val rank = sorted.indexOf(sender.profile) + 1
                group.sendMessage("你的排名为 $rank")
                @Suppress("NAME_SHADOWING")
                group.sendMessage("财富榜：\n" + top.mapIndexed { index, it ->
                    "第${index + 1}名 ${it.guz(bot)} ${it.money}金币"
                }.joinToString("\n"))
            }
        }
    }
}

@Serializable
class ShopItem(
    val name: String,
    val price: Double,
    val number: Int,
    val sender: Long,
    val group: Long,
    val expiresOn: Long,
)

var shop = mutableListOf<ShopItem>()

private fun configureShop(bot: Bot) {
    helpMessages.add("#商店 - 查看商店")
    helpMessages.add("#购买 <商品序号> - 购买指定商品")
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (group.enabled) {
            val content = message.content.trim()
            @Suppress("NAME_SHADOWING")
            if (content == "#商店") {
                group.sendMessage("商店：\n" + shop.mapIndexed { index, it ->
                    "第${index + 1}件 ${it.name} ${it.price}金币"
                }.joinToString("\n"))
            }
            if (content.startsWith("#购买")) {
                val index = numberRegex.matchAt(content.substringAfter("购买").trimStart(), 0)
                    ?.value?.toInt()?.minus(1)
                if (index == null) {
                    group.sendMessage("无法识别商品")
                    return@subscribeAlways
                }
                val item = shop.getOrNull(index)
                if (item == null) {
                    group.sendMessage("无法识别商品")
                    return@subscribeAlways
                }
                if (sender.profile.takeMoney(item.price)) {
                    sender.profile.addItem(item.name)
                    group.sendMessage("成功购买 ${item.name}")
                }
                else {
                    group.sendMessage("余额不足")
                }
            }
            if (content == "#我的物品") {
                group.sendMessage("你的物品：\n" + sender.profile.items.entries.joinToString("\n") {
                    "${it.value} 个 ${it.key}"
                })
            }
        }
    }
}

fun configureMoney(bot: Bot) {
    configureCheckIn(bot)
    configureTransform(bot)
}