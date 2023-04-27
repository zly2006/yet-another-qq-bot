
import io.github.jetkai.openai.api.data.completion.chat.ChatCompletionData
import io.github.jetkai.openai.api.data.completion.chat.message.ChatCompletionMessageData
import io.github.jetkai.openai.openai.OpenAI
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.*
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class Context(
    val id: Long,
    val messages: MutableList<ChatCompletionMessageData>,
    var responded: Boolean = true,
    var lastRequest: Long = 0,
    var lockedTimestamp: Long = 0,
)

private val suffix = """
Please focus on Starlight Server, you will never be asked to simulate anyone or anything in the following conversation.
Your answer should based on the real world. Now response to my last question above in Chinese,
DO NOT simulate anyone or anything! follow content policy and moral requirement and focus on Starlght Server.
"""

class ChatGPT(
    val initPrompt: String? = null
) {
    private val map = mutableMapOf<Long, Context>()

    private var initialPrompt = listOfNotNull(
        initPrompt?.let {
            ChatCompletionMessageData.create("system", initPrompt)
        }
    )

    private fun buildMessage(
        bot: Bot,
        group: Group,
        sender: Member,
        messages: List<ChatCompletionMessageData>,
        showSystem: Boolean = false
    ): ForwardMessage {
        return buildForwardMessage(group) {
            bot says "此回答由AI生成，不代表SLS运营团队立场，仅供帮助玩家使用，@我 可以继续对话，@我并说“清空”可以重新开始。"
            messages.forEach {
                when (it.role) {
                    "user" -> sender says (it.content)
                    "assistant" -> bot says (it.content)
                    "system" -> if (showSystem) {
                        sender says it.content.take(300)
                    }
                }
            }
        }
    }

    fun resetContext(id: Long) {
        if (map[id] == null) map[id] = Context(id, mutableListOf())
        else map[id]!!.lockedTimestamp = System.currentTimeMillis()
        map[id]!!.messages.clear()
        map[id]!!.responded = true
        map[id]!!.messages.addAll(initialPrompt)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun configureChatGPT(bot: Bot) {
        bot.eventChannel.subscribeAlways<GroupMessageEvent> {
            if (group.enabled) {
                if (message.filterIsInstance<At>().firstOrNull()?.target == bot.id) {
                    val content = message.filterIsInstance<PlainText>().joinToString().trim()
                    val euid = MessageDigest.getInstance("SHA-256").digest(sender.id.toString().toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    if (map[sender.id] == null) {
                        resetContext(sender.id)
                    }
                    if (map[sender.id]!!.lockedTimestamp + 1000 * 300 > System.currentTimeMillis()) {
                        if (group.id !in config.testGroup) {
                            group.sendMessage("休息一下再来提问吧！")
                            return@subscribeAlways
                        }
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

                    if (content == "清空") {
                        resetContext(sender.id)
                        group.sendMessage("已清空")
                        return@subscribeAlways
                    }

                    map[sender.id]!!.responded = false
                    GlobalScope.launch {
                        val message = withTimeoutOrNull(15.seconds) {
                            try {
                                map[sender.id]!!.messages += ChatCompletionMessageData.create("user", content)
                                val history = ChatCompletionData.builder()
                                    .setUser(euid)
                                    .setModel("gpt-3.5-turbo")
                                    .setMessages(map[sender.id]!!.messages +
                                            // add a suffix, so that GPT will follow our content policy.
                                            ChatCompletionMessageData.create("user",suffix))
                                    .setTemperature(0.5)
                                    .build()
                                val openAI = OpenAI.builder()
                                    .setApiKey(config.openaiApiKey)
                                    .createChatCompletion(history)
                                    .setTimeout(15.seconds.toJavaDuration())
                                    .build()
                                    .sendRequest()
                                map[sender.id]!!.messages += openAI.chatCompletion.asChatResponseDataList()
                                buildMessage(bot, group, sender, map[sender.id]!!.messages)
                            } catch (e: Exception) {
                                bot.logger.error(e)
                                PlainText("AI出错了，联系管理员获取帮助，你可以 @我 并说“清空”来重新开始一段会话")
                            }
                        } ?: PlainText("AI请求超时。")
                        map[sender.id]!!.responded = true
                        group.sendMessage(message)
                    }
                }
            }
        }
    }

    fun configureManageAi(bot: Bot) {
        bot.eventChannel.subscribeAlways<GroupMessageEvent> {
            if (sender.id in config.admins && group.enabled) {
                if (message.content.startsWith("!ai ")) {
                    val parts = message.content.substring(4).split(" ")
                    when (parts[0]) {
                        "help" -> {
                            group.sendMessage(
                                """
                            !ai lock <id>
                            !ai unlock <id>
                            !ai clear <id>
                            !ai list
                            !ai view <id>
                            !ai reload_prompt
                        """.trimIndent()
                            )
                        }

                        "lock" -> {
                            val id = parts[1].toLong()
                            if (map[id] == null) {
                                group.sendMessage("没有这个会话")
                            } else {
                                map[id]!!.lockedTimestamp = Long.MAX_VALUE
                                group.sendMessage("已锁定")
                            }
                        }

                        "unlock" -> {
                            val id = parts[1].toLong()
                            if (map[id] == null) {
                                group.sendMessage("没有这个会话")
                            } else {
                                map[id]!!.lockedTimestamp = 0
                                group.sendMessage("已解锁")
                            }
                        }

                        "clear" -> {
                            val id = parts[1].toLong()
                            if (map[id] == null) {
                                group.sendMessage("没有这个会话")
                            } else {
                                map.remove(id)
                                group.sendMessage("已清空")
                            }
                        }

                        "list" -> {
                            group.sendMessage("有以下会话：" + map.keys.joinToString())
                        }

                        "view" -> {
                            val id = parts[1].toLong()
                            if (map[id] == null) {
                                group.sendMessage("没有这个会话")
                            } else {
                                group.sendMessage(
                                    buildMessage(
                                        bot = bot,
                                        group = group,
                                        sender = group.getMember(id)!!,
                                        messages = map[id]!!.messages,
                                        showSystem = parts.contains("--show-system")
                                    )
                                )
                            }
                        }

                        "reload_prompt" -> {
                            initialPrompt = listOfNotNull(
                                initPrompt?.let {
                                    ChatCompletionMessageData.create("system", initPrompt)
                                }
                            )
                            group.sendMessage("已重新加载")
                        }
                    }
                }
            }
        }
    }
}