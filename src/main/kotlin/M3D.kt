import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.decodeBase64
import java.net.URL
import java.nio.charset.Charset

val types = setOf("head", "bust", "full", "face", "front", "frontfull")

fun configureM3D(bot: Bot) {
    helpMessages.add("#m3d <类型> <游戏ID> - 生成MC3D图片，类型列表: head bust full face front frontfull")
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (group.enabled) {
            val split = it.message.content.split("\\s".toRegex())
            if (split.size == 3) {
                if (split[0].startsWith("#m3d")) {
                    if (!types.contains(split[1])) {
                        it.group.sendMessage(it.message.quote().plus(PlainText("不支持的类型，请再试一次叭")))
                        return@subscribeAlways
                    }
                    val uuidStr = try {
                        getUUIDStr(split[2]) ?: error("")
                    } catch (_: Exception) {
                        it.group.sendMessage(it.message.quote().plus(PlainText(" 不能获取角色的UUID，请再试一次叭")))
                        return@subscribeAlways
                    }
                    try {
                        val connection = URL("https://visage.surgeplay.com/${split[1]}/512/$uuidStr").openConnection()
                        connection.connect()
                        connection.getInputStream().use { input ->
                            input.toExternalResource().use { resource ->
                                val image = it.group.uploadImage(resource)
                                it.group.sendMessage(it.message.quote().plus(image))
                            }
                        }
                    } catch (_: Exception) {
                        it.group.sendMessage(it.message.quote().plus(PlainText("失！败！辣！")))
                    }
                }
            }
        }
    }
}


private fun getUUIDStr(name: String): String? {
    val response = OkHttpClient().newCall(
        Request.Builder()
            .url("https://api.mojang.com/users/profiles/minecraft/$name")
            .build()
    ).execute()
    val string = response.body?.string() ?: return null
    return Json.decodeFromString<JsonObject>(string)["id"]?.jsonPrimitive?.content
}

private fun getSkinInfo(uuid: String): Pair<String, String?>? {
    val response = OkHttpClient().newCall(
        Request.Builder()
            .url("https://sessionserver.mojang.com/session/minecraft/profile/$uuid")
            .build()
    ).execute()
    val string = response.body?.string() ?: return null

    val optional = Json.decodeFromString<JsonObject>(string)["properties"]?.jsonArray?.find {
        it.jsonObject["name"]?.jsonPrimitive?.content.equals("textures")
    } ?: return null

    val textures = optional.jsonObject["value"]?.jsonPrimitive?.content ?: return null

    val encode = textures.decodeBase64()?.string(Charset.forName("UTF8"))
    if (encode.isNullOrEmpty()) {
        return null
    }
    val element = Json.decodeFromString<JsonObject>(encode)["textures"]?.jsonObject?.get("SKIN")?.jsonObject
    val url = element?.get("url")?.jsonPrimitive?.content ?: return null
    val model = element["metadata"]?.jsonObject?.get("model")?.jsonPrimitive?.content

    return Pair(url, model)
}