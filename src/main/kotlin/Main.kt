
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.auth.BotAuthorization
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.DirectoryLogger
import net.mamoe.mirai.utils.MiraiLogger
import user.UserProfile
import java.io.File
import javax.script.ScriptEngineManager

val JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

@Serializable
class Config(
    val accounts: MutableList<Account> = mutableListOf(),
    val admins: MutableList<Long> = mutableListOf(1284588550L),
    val testGroup: MutableList<Long> = mutableListOf(),
    val enabledGroup: MutableList<Long> = mutableListOf(),
    val openaiApiKey: String = "",
) {
    @Serializable
    class Account(
        val account: Long,
        val password: String,
        val log2Console: Boolean = true,
    )
}

var config = Config()

private val configureFuns = mutableListOf<(Bot) -> Unit>(
    ::configureFufu,
    ::configureBullshit,
    ::configureMoney,
    ::configureGroupManage,
    ::configureLottery
)

fun configure(fun_: (Bot) -> Unit) {
    configureFuns.add(fun_)
}

var profiles = mutableMapOf<Long, UserProfile>()

val scriptEngine = ScriptEngineManager().getEngineByName("js")
val helpMessages = mutableListOf<String>()

fun newBot(account: Long, password: String, log2Console: Boolean = true): Bot {
    return BotFactory.newBot(
        qq = account,
        //password = password,
        authorization = BotAuthorization.byQRCode()
    ) {
        cacheDir = File("cache/$account")
        protocol = BotConfiguration.MiraiProtocol.ANDROID_WATCH
        fileBasedDeviceInfo("cache/$account/device.json")
        botLoggerSupplier = {
            if (log2Console) {
                val logger1 = MiraiLogger.Factory.create(Bot::class, "Bot ${it.id}")
                val logger2 = DirectoryLogger("Bot ${it.id}", File("logs/${it.id}"))
                object : MiraiLogger {
                    override val identity: String? = logger1.identity
                    override val isEnabled: Boolean = true

                    override fun debug(message: String?) {
                        logger1.debug(message)
                        logger2.debug(message)
                    }

                    override fun debug(message: String?, e: Throwable?) {
                        logger1.debug(message, e)
                        logger2.debug(message, e)
                    }

                    override fun error(message: String?) {
                        logger1.error(message)
                        logger2.error(message)
                    }

                    override fun error(message: String?, e: Throwable?) {
                        logger1.error(message, e)
                        logger2.error(message, e)
                    }

                    override fun info(message: String?) {
                        logger1.info(message)
                        logger2.info(message)
                    }

                    override fun info(message: String?, e: Throwable?) {
                        logger1.info(message, e)
                        logger2.info(message, e)
                    }

                    override fun verbose(message: String?) {
                        logger1.verbose(message)
                        logger2.verbose(message)
                    }

                    override fun verbose(message: String?, e: Throwable?) {
                        logger1.verbose(message, e)
                        logger2.verbose(message, e)
                    }

                    override fun warning(message: String?) {
                        logger1.warning(message)
                        logger2.warning(message)
                    }

                    override fun warning(message: String?, e: Throwable?) {
                        logger1.warning(message, e)
                        logger2.warning(message, e)
                    }
                }
            }
            else {
                DirectoryLogger("Bot ${it.id}", File("logs/${it.id}"))
            }
        }
    }
}

inline fun <reified T> loadJson(filename: String, default: () -> T): T {
    if (!File("data").exists()) {
        File("data").mkdir()
    }
    val file = File("data", filename)
    return if (file.exists()) {
        JSON.decodeFromString(file.readText())
    } else {
        val r = default()
        file.writeText(JSON.encodeToString(r))
        r
    }
}

inline fun <reified T> saveJson(file: String, data: T) {
    if (!File("data").exists()) {
        File("data").mkdir()
    }
    File("data", file).writeText(JSON.encodeToString(data))
}

@OptIn(DelicateCoroutinesApi::class)
suspend fun main() {
    config = loadJson("config.json") { Config() }
    profiles = loadJson("profiles.json") { mutableMapOf() }
    config.accounts.forEach {
        val bot = newBot(it.account, it.password, it.log2Console)
        try {
            bot.login()
            configureFuns.forEach { it(bot) }
            bot.eventChannel.subscribeAlways<GroupMessageEvent> {
                if (group.enabled) {
                    if (message.content.startsWith("#")) {
                        sender.profile.lastAppearedTime = System.currentTimeMillis()
                        sender.profile.lastAppearedGroup = group.id
                    }
                    if (message.content == "#help") {
                        group.sendMessage(helpMessages.joinToString("\n"))
                    }
                }
            }

            val ai = ChatGPT(bot, initPrompt = File("data/prompt.txt").readText(), blameInappropriateSpeech = true)
            ai.startMonitor(1000 * 300)
            ai.configureChatGPT(bot)
            ai.configureManageAi(bot)
        } catch (e: Exception) {
            bot.logger.error(e)
        }
    }
    GlobalScope.launch {
        while (true) {
            Thread.sleep(300 * 1000)
            saveJson("profiles.json", profiles)
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        saveJson("profiles.json", profiles)
    })
    awaitCancellation()
}
