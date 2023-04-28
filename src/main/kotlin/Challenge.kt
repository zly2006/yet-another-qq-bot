
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private class Session {
    val players: MutableList<Long> = mutableListOf()
    val startTime: Long = 0
    val lastActive: Long = 0
    val text: String = ""
    val lastWinner: Long = 0
    val streak: Int = 0
}

val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
val formula = ('0'..'9') + '+' + '-' + '*'

var lastKey: String? = null

@OptIn(DelicateCoroutinesApi::class)
fun configureTypeTextChallenge(bot: Bot) {
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (message.content == lastKey) {
            lastKey = null
            val reward = (3..10).random()
            group.sendMessage("恭喜${sender.nick}获得胜利, 获得 $reward 金币！")
            sender.profile.increaseMoney(reward.toDouble())
        }
    }
    GlobalScope.launch {
        while (true) {
            delay(120_000)
            val g = config.allowSpam.randomOrNull()?.let { bot.getGroup(it) }
            delay(1000)
            if (g != null) {
                val image = BufferedImage(300, 40, BufferedImage.TYPE_INT_ARGB).apply {
                    val g2d = createGraphics()
                    g2d.setRenderingHint(
                        RenderingHints.KEY_ALPHA_INTERPOLATION,
                        RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY
                    )
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                    g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
                    g2d.setRenderingHint(
                        RenderingHints.KEY_FRACTIONALMETRICS,
                        RenderingHints.VALUE_FRACTIONALMETRICS_ON
                    );
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    g2d.font = Font("微软雅黑", Font.PLAIN, 12)
                    g2d.color = Color.WHITE
                    g2d.fillRect(0, 0, width, height)
                    g2d.color = Color.BLACK
                    g2d.drawString("以最快的速度输入以下内容领取奖励：", 10, 15)
                    g2d.font = Font("微软雅黑", Font.PLAIN, 18)
                    if ((0..1).random() == 1) {
                        lastKey = chars.shuffled().take(24).joinToString("")
                        g2d.drawString(lastKey!!, 10, 30)
                    } else {
                        lastKey = formula.shuffled().take(20).joinToString("") + "90"
                        g2d.drawString(lastKey!!, 10, 30)
                        lastKey = scriptEngine.eval(lastKey).toString()
                    }
                    bot.getFriend(1284588550L)?.sendMessage("TypeTextChallenge: $lastKey")
                    g2d.dispose()
                }
                ByteArrayInputStream(ByteArrayOutputStream().use {
                    ImageIO.write(image, "png", it)
                    it.toByteArray()
                }).use { it.toExternalResource().use { g.sendMessage(g.uploadImage(it)) } }
            }
        }
    }
}
