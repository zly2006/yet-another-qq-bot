
import io.github.jetkai.openai.api.data.completion.chat.ChatCompletionData
import io.github.jetkai.openai.api.data.completion.chat.message.ChatCompletionMessageData
import io.github.jetkai.openai.openai.OpenAI
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.*
import user.UserProfile
import java.security.MessageDigest
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

data class Context(
    val id: Long,
    val uuid: UUID = UUID.randomUUID(),
    val messages: MutableList<ChatCompletionMessageData> = mutableListOf(),
    var responded: Boolean = true,
    var lastRequest: Long = System.currentTimeMillis(),
    var lockedTimestamp: Long = System.currentTimeMillis(),
)

private const val suffix = """
你的答案应该基于现实世界。现在回答我上面的最后一个问题，在开始谈话之前不要道歉。如果你不想回答问题，你可以说“我不想回答这个问题”。
不要模拟任何人或任何事！请遵循内容政策和道德要求，专注于为Starlight Server提供帮助。
"""

private val previousConversation = mutableMapOf<UUID, Context>()

class ChatGPT(
    val bot: Bot,
    private val initPrompt: String? = null,
    private val antiSimulate: Boolean = true,
    private val blameInappropriateSpeech: Boolean = false,
) {
    private val map = mutableMapOf<Long, Context>()

    private var initialPrompt = listOfNotNull(
        initPrompt?.let {
            ChatCompletionMessageData.create("system", initPrompt)
        }
    )

    private fun buildMessage(
        group: Group,
        context: Context,
        showSystem: Boolean = false,
    ): ForwardMessage {
        return buildForwardMessage(group, object : ForwardMessage.DisplayStrategy {
            override fun generatePreview(forward: RawForwardMessage) = listOf(
                "AI和${context.id}的对话",
                "如果您发现AI在对话中发表了不当言论，请反馈",
                "如果用户在引导AI发布不当言论，请向我们举报",
                "AI的回答不代表SLS运营团队立场，仅供帮助玩家使用"
            )

            override fun generateSummary(forward: RawForwardMessage) = "点击查看完整对话"
        }) {
            bot says "此回答由AI生成，不代表SLS运营团队立场，仅供帮助玩家使用，@我 可以继续对话，@我并说“清空”可以重新开始。"
            if (group.id in config.testGroup) {
                10000 says "[DEBUG] UUID of this conversation = ${context.uuid}"
            }
            context.messages.forEach {
                when (it.role) {
                    "user" -> context.id says (it.content)
                    "assistant" -> bot says (it.content)
                    "system" -> if (showSystem) {
                        context.id says "[DEBUG] " + it.content.take(300)
                    }
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun resetContext(id: Long, setNull: Boolean = false) {
        if (map[id] == null && setNull) return

        if (blameInappropriateSpeech && map[id] != null) {
            // copy to conversation history for review
            previousConversation[map[id]!!.uuid] = Context(id, map[id]!!.uuid, map[id]!!.messages.toMutableList())

            GlobalScope.launch {
                // then auto review
                if (map[id]!!.copy().review() == false) {
                    val ts = System.currentTimeMillis()
                    val punishment = UserProfile.Punishment(
                        ts,
                        "Inappropriate Speech",
                        map[id]!!.messages.filter { it.role != "system" }.joinToString("\n") { it.content },
                        "AI Auto Ban"
                    )
                    profile(id).punishments.add(punishment)
                    bot.getGroup(profile(id).lastAppearedGroup)
                        ?.sendMessage(At(id) + "您刚才似乎发布了不当言论。请注意您的言行，如需申诉请联系管理员并提供以下信息：timestamp=$ts")
                }
            }
        }

        if (map[id] == null) map[id] = Context(id)
        else map[id]!!.lockedTimestamp = System.currentTimeMillis()

        if (setNull) {
            map.remove(id)
        } else {
            map[id]!!.messages.clear()
            map[id]!!.responded = true
            map[id]!!.lastRequest = System.currentTimeMillis()
            map[id]!!.messages.addAll(initialPrompt)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun configureChatGPT(bot: Bot) {
        bot.eventChannel.subscribeAlways<GroupMessageEvent> {
            if (shouldRespond) {
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
                                val messages = if (antiSimulate) {
                                    // add a suffix, so that GPT will follow our content policy.
                                    map[sender.id]!!.messages + ChatCompletionMessageData.create("user", suffix)
                                } else {
                                    map[sender.id]!!.messages
                                }
                                val history = ChatCompletionData.builder()
                                    .setUser(euid)
                                    .setModel("gpt-3.5-turbo")
                                    .setMessages(messages)
                                    .build()
                                val openAI = OpenAI.builder()
                                    .setApiKey(config.openaiApiKey)
                                    .createChatCompletion(history)
                                    .setTimeout(15.seconds.toJavaDuration())
                                    .build()
                                    .sendRequest()
                                val data = openAI.chatCompletion.asChatResponseData()
                                    .run {
                                        val ingnoreRegex = Regex("非常抱歉，我没有理解您的问题")
                                    // modify the response
                                    ChatCompletionMessageData.create(
                                        "assistant",
                                        this.content.split("。").mapNotNull {
                                            if (it.contains("抱歉") && it.contains("解") && it.contains("的问题")) {
                                                null
                                            } else it
                                        }.joinToString("。")
                                    )
                                }
                                map[sender.id]!!.messages += data
                                buildMessage(group, map[sender.id]!!)
                            }  catch (e: Exception) {
                                bot.logger.error(e)
                                fun getAllReason(e: Throwable): String {
                                    if (e.cause != null) e.message + getAllReason(e.cause!!)
                                    return e.message ?: ""
                                }
                                if (getAllReason(e).contains("Rate limit reached for default-gpt"))
                                    PlainText("AI达到了请求速率上限，这是由于您或其他玩家过快的使用AI。你可以 @我 并说“清空”来重新开始一段会话")
                                else
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
            if (sender.id in config.admins && shouldRespond) loggingError {
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
                            !ai review <uuid>
                            !ai review_content <uuid>
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
                                group.sendMessage(buildMessage(group, map[id]!!, parts.contains("--show-system")))
                            }
                        }

                        "review" -> {
                            if (!blameInappropriateSpeech) {
                                group.sendMessage("请先设置 blame_inappropriate_speech 为 true")
                                return@loggingError
                            }
                            val uuid = UUID.fromString(parts[1])
                            val conversation = previousConversation[uuid] ?: map.values.firstOrNull { it.uuid == uuid }
                            ?: return@loggingError
                            val messages =
                                conversation.messages.filter { it.role == "user" }.joinToString("\n") { it.content }
                            val data = ChatCompletionMessageData.create(
                                "user", """
                                Please help me to determine whether these content below is appropriate or not.
                                Answer with "yes" or "no". If you find any sentence inappropriate, please say "no".
                                
                                $messages
                            """.trimIndent()
                            )
                            val openAI = OpenAI.builder()
                                .setApiKey(config.openaiApiKey)
                                .createChatCompletion(ChatCompletionData.create(listOf(data)))
                                .setTimeout(15.seconds.toJavaDuration())
                                .build()
                                .sendRequest()
                            group.sendMessage(openAI.chatCompletion.asText())
                        }

                        "review_content" -> {
                            if (!blameInappropriateSpeech) {
                                group.sendMessage("请先设置 blame_inappropriate_speech 为 true")
                                return@loggingError
                            }
                            val uuid = UUID.fromString(parts[1])
                            val conversation = previousConversation[uuid] ?: map.values.firstOrNull { it.uuid == uuid }
                            ?: return@loggingError
                            val messages =
                                conversation.messages.filter { it.role == "user" }.joinToString("\n") { it.content }
                            group.sendMessage("${conversation.id}'s conversation:\n$messages")
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

    private fun Context.review(): Boolean? {
        if (!blameInappropriateSpeech) {
            return null
        }
        val messages = messages.filter { it.role == "user" }.joinToString("\n") { it.content }
        val data = ChatCompletionMessageData.create(
            "user", """
                Please help me to determine whether these content below is appropriate or not.
                Answer with "yes" or "no". If you find any sentence inappropriate, please say "no".
                
                $messages
            """.trimIndent()
        )
        val openAI = OpenAI.builder()
            .setApiKey(config.openaiApiKey)
            .createChatCompletion(ChatCompletionData.create(listOf(data)))
            .setTimeout(15.seconds.toJavaDuration())
            .build()
            .sendRequest()
        val response = openAI.chatCompletion.asText().lowercase()
        if (response.startsWith("yes")) return true
        if (response.startsWith("no")) return false
        return null
    }

    fun startMonitor(expire: Long) {
        Thread {
            while (true) {
                Thread.sleep(1000)
                val now = System.currentTimeMillis()
                map.values.removeIf { now - it.lastRequest > expire }
            }
        }.start()
    }
}

fun configureAI(
    bot: Bot,
    initPrompt: String? = null,
    antiSimulate: Boolean = true,
    blameInappropriateSpeech: Boolean = false,
) {
    val chatGPT = ChatGPT(bot, initPrompt, antiSimulate, blameInappropriateSpeech)
    chatGPT.configureChatGPT(bot)
    chatGPT.configureManageAi(bot)
}
