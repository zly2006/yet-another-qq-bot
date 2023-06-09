
import annotation.Security
import kotlinx.serialization.Serializable
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.content
import user.Items
import user.UserProfile
import user.takeMoney
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.*
import kotlin.math.max


private val moneyRegex = Regex("\\d+(\\.\\d{1,2})?")
private val numberRegex = Regex("\\d+")

fun date(timestamp: Long) = (timestamp + TimeZone.getDefault().rawOffset) / 1000 / 60 / 60 / 24
val date
    get() = date(System.currentTimeMillis())

/**
 * Item name of rewards.
 * @see [user.Items.itemRegistry]
 */
val checkInRewards = buildList {
    addAll(("GENERATIVE" + "PRETRAINED" + "TRANSFORMER").map { it.toString() })
}

private fun configureCheckIn(bot: Bot) {
    helpMessages.add("#签到 - 一天一次，连续签到可以获得更多金币")
    helpMessages.add("#签到信息 - 查看签到信息")
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (shouldRespond) {
            if (message.content == "#签到") {
                val profile = sender.profile
                if (profile.lastCheckInDate >= date) {
                    group.sendMessage("今天已经签到过了")
                    return@subscribeAlways
                } else {
                    if (profile.lastCheckInDate == date - 1) {
                        profile.keepCheckInDuration++
                    } else {
                        profile.keepCheckInDuration = 1
                    }
                    profile.lastCheckInDate = date
                    val randomMax = 100 + max(profile.keepCheckInDuration * 5, 10).toInt()
                    val money = (100..randomMax).random().plus(
                        if (profile.vipDateUntil > date) 90 // vip
                        else 0
                    )
                    profile.increaseMoney(money.toDouble())
                    group.sendMessage("签到成功，获得 $money 金币，连续签到 ${sender.profile.keepCheckInDuration} 天")
                    val item = checkInRewards.random()
                    Items.itemRegistry[item]?.let {
                        group.sendMessage("此次签到，你获得了一个 ${it.rarity} 级的 ${it.name}")
                        sender.profile.addItem(it.name)
                    }
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

fun resolveAmount(message: MessageChain) = moneyRegex.findAll(message.filterIsInstance<PlainText>()
    .joinToString { it.content }.substringBefore("金币")).first().value.toDouble()

private fun configureTransfer(bot: Bot) {
    helpMessages.add("#转账 @某人 <金币> - 转账给指定用户")
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (shouldRespond) {
            val content = message.content.trim()
            if (content.startsWith("#转账")) {
                val target = resolveTarget(message, bot)
                val amount = (resolveAmount(message) * 100).toInt() / 100.0
                if (target == null) {
                    group.sendMessage("无法识别目标")
                    return@subscribeAlways
                }
                if (sender.profile.takeMoney(amount)) {
                    @Security
                    if (amount > sender.profile.money * 2) {
                        sender.profile.records.add(
                            UserProfile.Record(
                                System.currentTimeMillis(),
                                "security-alert",
                                "转账给 ${profile(target).guz(bot)} $amount 金币，疑似小号",
                                "transfer-money-to-$target"
                            )
                        )
                        // if in 3 days, transfer more than 2 days of > 2/3 money to the same target, then ban
                        val threeDaysAgo = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000
                        val all = sender.profile.records.filter { it.time > threeDaysAgo && it.type == "security-alert" && it.source.startsWith("transfer-money") }
                        val max = all.groupBy { it.source }.maxOf { it.value.size }
                        if (all.size >= 5 || max >= 2) {
                            sender.profile.records.add(
                                UserProfile.Record(
                                    System.currentTimeMillis(),
                                    "security-ban",
                                    "转账给 ${profile(target).guz(bot)} $amount 金币，疑似小号，已被封禁并扣除300金币",
                                    "transfer-money-to-$target"
                                )
                            )
                            sender.profile.money -= 300
                            sender.profile.banUntil = System.currentTimeMillis() + 4 * 24 * 60 * 60 * 1000
                            group.sendMessage("你的账号已被封禁4天，并扣除300金币，如需申诉请联系管理员并提供以下信息：transfer-money-to-$target, security-ban")
                            return@subscribeAlways
                        }
                    }
                    @Security
                    if (amount > sender.profile.money * 5 && sender.profile.keepCheckInDuration < 3) {
                        sender.profile.records.add(
                            UserProfile.Record(
                                System.currentTimeMillis(),
                                "security-ban",
                                "转账给 ${profile(target).guz(bot)} $amount 金币，疑似小号，已被封禁并扣除300金币",
                                "transfer-money-to-$target"
                            )
                        )
                        sender.profile.money -= 300
                        sender.profile.banUntil = System.currentTimeMillis() + 4 * 24 * 60 * 60 * 1000
                        group.sendMessage("你的账号已被封禁4天，并扣除300金币，如需申诉请联系管理员并提供以下信息：transfer-money-to-$target, security-ban")
                        return@subscribeAlways
                    }

                    // security check ok
                    profile(target).increaseMoney(amount)
                    group.sendMessage(PlainText("成功转账 $amount 金币给 ") + At(target))
                    profile(target).records.add(
                        UserProfile.Record(
                        System.currentTimeMillis(),
                        "transfer-money-record",
                        "收到 ${sender.profile.guz(bot)} 转账 $amount 金币",
                        "transfer-money-from-${sender.id}"
                    ))
                    sender.profile.records.add(
                        UserProfile.Record(
                        System.currentTimeMillis(),
                        "transfer-money-record",
                        "转账给 ${profile(target).guz(bot)} $amount 金币",
                        "transfer-money-to-$target"
                    ))
                }
                else {
                    group.sendMessage("余额不足")
                }
            }
        }
    }
}

private fun configureBasic(bot: Bot) {
    helpMessages.add("#我的余额 - 查看自己的余额")
    helpMessages.add("#财富榜 - 或者\"#财负榜\"看倒数前十")
    val regex = Regex("^#财([富负])榜$")
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (shouldRespond) {
            val content = message.content.trim()
            if (content == "#我的余额") {
                group.sendMessage("你的余额为 ${sender.profile.money} 金币")
            }
            val match = regex.find(content)
            if (match != null) {
                val sorted = profiles.values.sortedByDescending { it.money }
                val top = sorted.let {
                    if (match.groups[1]!!.value == "负") it.reversed()
                    else it
                }.take(10)
                val rank = sorted.indexOf(sender.profile) + 1
                group.sendMessage("你的排名为 $rank")
                @Suppress("NAME_SHADOWING")
                group.sendMessage("$content:\n" + top.mapIndexed { index, it ->
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
    val description: String,
    val stock: Int,
    val sender: Long,
    val group: Long,
    val expiresOn: Long,
    val sell: List<String>,
    val buy: List<String>,
)

var shop = loadJson("shop.json") { mutableListOf<ShopItem>() }

fun configureShop(bot: Bot) {
    helpMessages.add("#商店 <页面> - 查看商店")
    helpMessages.add("#购买 <商品序号> - 购买指定商品")
    helpMessages.add("#查看商品 <商品序号> - 查看指定商品")
    helpMessages.add("#我的物品 - 查看自己的库存")
    helpMessages.add("#物品详情 - 根据名称查看物品详情")
    bot.shouldRespondChannel.subscribeAlways<GroupMessageEvent> {
        val content = message.content.trim()
        @Suppress("NAME_SHADOWING")
        if (content.startsWith("#商店")) {
            val page = content.substringAfter("#商店").trimStart().toIntOrNull()
                ?.minus(1) ?: 0

            val list = shop.drop(page * 5).take(5).mapIndexed { index, it ->
                "第${index + 1 + page * 5}件 ${it.name} - ${it.description}"
            }

            group.sendMessage("商店：\n" + list.joinToString("\n") +
                    "\n第${page + 1}页，共${shop.size / 5 + 1}页")
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
            if (sender.profile.hasAll(item.buy) &&
                // Notice: side effect here
                sender.profile.takeMoney(item.price)) {
                sender.profile.addItems(item.sell)
                sender.profile.removeItems(item.buy)
                group.sendMessage("购买成功")
            }
            else {
                group.sendMessage("余额不足或没有足够的物品")
            }
        }
        if (content.startsWith("#查看商品")) {
            val id = numberRegex.matchAt(content.substringAfter("查看商品").trimStart(), 0)
                ?.value?.toInt()?.minus(1)
            val item = shop.getOrNull(id ?: -1)
            if (item == null) {
                group.sendMessage("无法识别商品")
                return@subscribeAlways
            }
            group.sendMessage(buildString {
                append("商品信息：\n")
                append("名称：${item.name}\n")
                append("价格：${item.price}金币\n")
                append("描述：${item.description}\n")
                if (item.stock == -1) {
                    append("库存：无限\n")
                }
                else {
                    append("库存：${item.stock}\n")
                }
                if (item.expiresOn >= 0) {
                    append("过期时间：${SimpleDateFormat().format(Date(item.expiresOn))}\n")
                }
                if (item.sender != 0L) {
                    append("卖家：${item.sender.guz(bot)}\n")
                }
                if (item.group != 0L) {
                    append("限制群：${item.group.guz(bot)} (仅限在此群交易)\n")
                }
                append("出售物品：${item.sell.joinToString(", ")}\n")
                append("收购物品：${item.buy.joinToString(", ")}\n")
            })
        }
        if (content == "#我的物品") {
            group.sendMessage("你的物品：\n" + sender.profile.items.entries.joinToString("\n") {
                "${it.value} 个 ${it.key}"
            })
        }
        if (content.startsWith("#物品详情")) {
            val name = content.substringAfter("物品详情").trim()
            val item = Items.itemRegistry[name]
            if (item == null) {
                group.sendMessage("无法识别物品")
                return@subscribeAlways
            }
            group.sendMessage(buildString {
                append("物品信息：\n")
                append("名称：${item.name}\n")
                append("描述：${item.description}\n")
                append("稀有度：${item.rarity}\n")
            })
        }
    }
}

private fun configureManage(bot: Bot) {
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (sender.id in config.admins && group.enabled) {
            if (message.content.startsWith('!')) {
                val parts = message.content.drop(1).split(' ')
                when (parts[0]) {
                    "vip" -> when (parts[1]) {
                        "apply" -> {
                            val profile = profile(parts[2].toLong())
                            val time = parts[3].toLong()
                            if (profile.vipDateUntil >= date) {
                                profile.vipDateUntil += time
                            }
                            else {
                                profile.vipDateUntil = date + time
                            }
                            if (profile.lastCheckInDate == date) {
                                profile.sendMessageWithAt(PlainText("您已签到，自动补发vip奖励"), bot)
                                profile.increaseMoney(90.0)
                            }
                            val instant = Instant.ofEpochSecond(0)
                                .plusSeconds(24 * 3600 * profile.lastCheckInDate)
                                .minusMillis(TimeZone.getDefault().rawOffset.toLong())
                                .atZone(ZoneId.systemDefault())
                            group.sendMessage("VIP资格持续到${instant}")
                        }
                    }
                    "money" -> when (parts[1]) {
                        "add" -> {
                            val profile = profile(parts[2].toLong())
                            val amount = parts[3].toDouble()
                            profile.increaseMoney(amount)
                            group.sendMessage("OK")
                        }
                    }
                }
            }
        }
    }
}

private fun configureVip(bot: Bot) {
    helpMessages.add("#vip")
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (group.enabled) {
            if (message.content == "#vip") {
                val profile = sender.profile
                if (profile.vipDateUntil == 0L) {
                    group.sendMessage("您还没有购买过VIP")
                    return@subscribeAlways
                }
                val instant = Instant.ofEpochSecond(0)
                    .plusSeconds(24 * 3600 * profile.vipDateUntil)
                    .minusMillis(TimeZone.getDefault().rawOffset.toLong())
                    .atZone(ZoneId.systemDefault())
                group.sendMessage("VIP资格持续到${instant}")
            }
        }
    }
}

fun configureMoney(bot: Bot) {
    configureTransfer(bot)
    configureCheckIn(bot)
    configureBasic(bot)
    configureManage(bot)
    configureVip(bot)
}