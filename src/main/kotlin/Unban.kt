
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.UserMessageEvent
import net.mamoe.mirai.message.data.content
import user.takeMoney
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

fun configureUnban(bot: Bot) {
    bot.eventChannel.subscribeAlways<UserMessageEvent> {
        if (message.content == "#小黑屋") {
            subject.sendMessage("你的封禁持续到：${
                SimpleDateFormat().format(Date(subject.profile.banUntil))
            }，还剩${
                max(sender.profile.banUntil - System.currentTimeMillis(), 0) / 1000
            }")
        }
        if (message.content == "#保释") {
            val time = max(sender.profile.banUntil - System.currentTimeMillis(), 0) / 1000
            val amount = time * 2 / 100.0
            if (!sender.profile.takeMoney(amount)) {
                subject.sendMessage("你的金币不足！如果想要保释，请支付 $amount 金币")
                return@subscribeAlways
            }
            sender.profile.banUntil = 0
            subject.sendMessage("你已经成功保释！支付了 $amount 金币")
        }
    }
}