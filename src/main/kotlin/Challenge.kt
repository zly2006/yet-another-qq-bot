
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random

val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')

var lastKey: String? = null

suspend fun newChallenge(g: Group) {
    val image = BufferedImage(300, 40, BufferedImage.TYPE_INT_ARGB).apply {
        val g2d = createGraphics()
        g2d.font = Font("微软雅黑", Font.PLAIN, 12)
        g2d.color = Color.WHITE
        g2d.fillRect(0, 0, width, height)
        g2d.color = Color.BLACK
        g2d.drawString("以最快的速度输入以下内容领取奖励：", 10, 15)
        g2d.font = Font("Monaco", Font.PLAIN, 18)
        if ((0..1).random() == 1) {
            lastKey = chars.shuffled().take(24).joinToString("")
            // draw chars with random rotation
            lastKey!!.forEachIndexed { index, c ->
                val x = index * 12 + 10
                val y = 30
                val angle = Random.nextDouble() - 0.5
                g2d.rotate(angle, x.toDouble(), y.toDouble())
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
            g2d.drawString(lastKey!!, 10, 30)
            lastKey = (scriptEngine.eval(lastKey) as Number).toLong().toString()
        }
        // then draw some lines on it
        g2d.color = Color.BLACK
        repeat(3) {
            g2d.drawLine(0, (20..35).random(), 300, (20..35).random())
        }
        g.bot.getFriend(1284588550L)?.sendMessage("TypeTextChallenge: $lastKey")
        g2d.dispose()
    }
    ByteArrayInputStream(ByteArrayOutputStream().use {
        ImageIO.write(image, "png", it)
        it.toByteArray()
    }).use { it.toExternalResource().use { g.sendMessage(g.uploadImage(it)) } }
}

@OptIn(DelicateCoroutinesApi::class)
fun configureTypeTextChallenge(bot: Bot) {
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (message.content == lastKey) {
            lastKey = null
            val reward = (3..10).random()
            group.sendMessage("恭喜${sender.nick}获得胜利, 获得 $reward 金币！")
            sender.profile.increaseMoney(reward.toDouble())
        }
        if (sender.id in config.admins && message.content == "!test new challenge") {
            newChallenge(group)
        }
    }
    GlobalScope.launch {
        while (true) {
            delay(120_000)
            val g = config.allowSpam.randomOrNull()?.let { bot.getGroup(it) }
            delay(1000)
            if (g != null)
                newChallenge(g)
        }
    }
}
