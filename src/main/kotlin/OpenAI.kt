
import io.github.jetkai.openai.api.data.completion.chat.ChatCompletionData
import io.github.jetkai.openai.api.data.completion.chat.message.ChatCompletionMessageData
import io.github.jetkai.openai.openai.OpenAI
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.PlainText
import java.io.File

class Context(
    val id: Long,
    val messages: MutableList<ChatCompletionMessageData>,
    var responded: Boolean = true,
    var lastRequest: Long = 0,
    var lockedTimestamp: Long = 0,
)

private val map = mutableMapOf<Long, Context>()

val initialPrompt = ChatCompletionMessageData.create("system", File("data/prompt.txt").readText())

@OptIn(DelicateCoroutinesApi::class)
fun configureChatGPT(bot: Bot) {
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (group.enabled) {
            if (message.filterIsInstance<At>().firstOrNull()?.target == bot.id) {
                val content = message.filterIsInstance<PlainText>().joinToString()
                if (map[sender.id] == null) {
                    map[sender.id] = Context(sender.id, mutableListOf(initialPrompt))
                }
                if (map[sender.id]!!.lockedTimestamp + 1000 * 300 > System.currentTimeMillis()) {
                    group.sendMessage("休息一下再来提问吧！")
                    return@subscribeAlways
                }
                map[sender.id]!!.lastRequest = System.currentTimeMillis()
                if (!map[sender.id]!!.responded) {
                    group.sendMessage("请等待AI回复")
                    return@subscribeAlways
                }
                if (map[sender.id]!!.messages.size > 8) {
                    group.sendMessage("聊天消息太长了，会话已清空。请休息 5 分钟再来提问吧！")
                    if (sender.id !in config.admins) {
                        map[sender.id]!!.lockedTimestamp = System.currentTimeMillis()
                    }
                    map[sender.id]!!.messages.clear()
                    return@subscribeAlways
                }
                map[sender.id]!!.responded = false
                GlobalScope.launch {
                    try {
                        map[sender.id]!!.messages += ChatCompletionMessageData.create("user", content)
                        val history = ChatCompletionData.create("gpt-3.5-turbo", map[sender.id]!!.messages)
                        val openAI = OpenAI.builder()
                            .setApiKey(config.openaiApiKey)
                            .createChatCompletion(history)
                            .build()
                            .sendRequest()
                        map[sender.id]!!.messages += openAI.chatCompletion.asChatResponseDataList()
                        group.sendMessage(message.quote() + At(sender.id) + openAI.chatCompletion.asText())
                        map[sender.id]!!.responded = true
                    } catch (e: Exception) {
                        group.sendMessage("AI出错了，联系管理员获取帮助")
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
