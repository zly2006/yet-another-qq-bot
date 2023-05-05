
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.*

val httpClient = OkHttpClient()

fun configureTodayInHistory(bot: Bot) {
    bot.shouldRespondChannel.subscribeAlways<GroupMessageEvent> {
        if (message.content == "#today") {
            val month = Calendar.getInstance().get(Calendar.MONTH) + 1
            val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            val url = "https://zh.wikipedia.org/wiki/Wikipedia:历史上的今天/${month}月${day}日"
            val content = httpClient.newCall(Request.Builder().url(url)
                .header("accept-language", "zh-CN,zh;q=0.9")
                .build()).execute().body!!.string()
            val document = Jsoup.parse(content)
            val mediaWikiOutput = document.selectFirst("div.mw-parser-output")!!
            group.sendMessage(mediaWikiOutput.children().asSequence().filter { it?.tagName() != "table" }
                .joinToString("\n") {
                    it.text()
                })
        }
    }
}