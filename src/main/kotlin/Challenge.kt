
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random

val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')

var lastKey: String? = null
var lastWinner: Long? = null

suspend fun newChallenge(g: Group): ByteArray {
    val image = BufferedImage(600, 150, BufferedImage.TYPE_INT_ARGB).apply {
        val g2d = createGraphics()
        g2d.font = Font("微软雅黑", Font.PLAIN, 36)
        g2d.color = Color.WHITE
        g2d.fillRect(0, 0, width, height)
        g2d.color = Color.BLACK
        g2d.drawString("以最快的速度输入以下内容领取奖励：", 30, 45)
        if ((0..1).random() == 1) {
            lastKey = chars.shuffled().take(12).joinToString("")
            // draw chars with random rotation
            lastKey!!.forEachIndexed { index, c ->
                val x = index * 36 + 30
                val y = 90
                val angle = Random.nextDouble() - 0.5
                g2d.rotate(angle, x.toDouble(), y.toDouble())
                g2d.font = Font("Monaco", Font.PLAIN, (45..60).random())
                g2d.drawString(c.toString(), x, y)
                g2d.rotate(-angle, x.toDouble(), y.toDouble())
            }
        } else {
            val numbers = ('1'..'9')
            val operators = listOf('+', '-', '*')
            lastKey = buildString {
                repeat(5) {
                    append(numbers.random())
                    append(numbers.random())
                    append(numbers.random())
                    append(operators.random())
                }
            }.dropLast(1)
            g2d.drawString(lastKey!!, 30, 90)
            lastKey = (scriptEngine.eval(lastKey) as Number).toLong().toString()
        }
        // then draw some lines on it
        g2d.color = Color.BLACK
        repeat(3) {
            g2d.color = listOf(Color.RED, Color.CYAN, Color.ORANGE).random()
            g2d.stroke = BasicStroke(5f)
            g2d.drawLine(0, (60..100).random(), 600, (60..100).random())
        }
        g.bot.getFriend(1284588550L)?.sendMessage("TypeTextChallenge: $lastKey")
        g2d.dispose()
    }
    val ba = ByteArrayOutputStream().use {
        ImageIO.write(image, "png", it)
        it.toByteArray()
    }
    ByteArrayInputStream(ba).use {
        it.toExternalResource().use { g.sendMessage(g.uploadImage(it)) }
    }
    return ba
}

@OptIn(DelicateCoroutinesApi::class)
fun configureTypeTextChallenge(bot: Bot) {
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (lastKey != null && shouldRespond) {
            if (message.content == lastKey) {
                lastKey = null
                val reward = (3..10).random() +
                        if (lastWinner == sender.id) {
                            group.sendMessage("连续答对，额外赠送5金币！")
                            5
                        } else 0
                lastWinner = sender.id
                group.sendMessage("恭喜${sender.nick}获得胜利, 获得 $reward 金币！")
                sender.profile.increaseMoney(reward.toDouble())
            } else if (message.content.length == lastKey?.length) {
                val similarCharacters = listOf(
                    "Cc", "Il1", "O0", "Ss", "Uu", "Vv", "Ww", "Xx", "Zz"
                )
                var differenceCount = 0

                for (i in 0 until message.content.length) {
                    val userInput = message.content[i]
                    val answer = lastKey!![i]
                    if (userInput != answer) {
                        if (similarCharacters.any { it.contains(userInput) && it.contains(answer) }) {
                            differenceCount++
                        } else {
                            return@subscribeAlways
                        }
                    }
                }
                if (differenceCount == 1) {
                    val reward = (1..8).random()
                    lastWinner = sender.id
                    group.sendMessage(
                        "${sender.nick}的答案与正确答案相近(相差1个相似字符), " +
                                "获得 $reward 金币！"
                    )
                    lastKey = null
                }
            }
        }
        if (sender.id in config.admins && message.content == "!test new challenge") {
            newChallenge(group)
        }
    }
    GlobalScope.launch {
        while (true) {
            for (i in 0 until 40) {
                delay(30_000)
                if (lastKey == null)
                    break
            }
            val g = config.allowSpam.randomOrNull()?.let { bot.getGroup(it) }
            delay(1000)
            if (g != null)
                newChallenge(g)
        }
    }
}
