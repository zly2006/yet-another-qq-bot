package user

import kotlinx.serialization.Serializable
import net.mamoe.mirai.Bot
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageContent

@Serializable
class UserProfile(
    var id: Long,
    var lastAppearedGroup: Long = 0,
    var lastAppearedTime: Long = 0,
    var lastCheckInDate: Long = 0,
    var keepCheckInDuration: Long = 0,
    var money: Double = 0.0,
    val items: MutableMap<String, Int> = mutableMapOf(),
    val punishments: MutableList<Punishment> = mutableListOf(),
) {
    fun increaseMoney(amount: Double): Boolean {
        val i = amount.times(100).toInt()
        if (i <= 0) return false
        money = money.times(100).plus(i).div(100)
        return true
    }

    suspend fun sendMessageWithAt(plainText: MessageContent, bot: Bot) {
        bot.getGroup(lastAppearedGroup)?.sendMessage(At(id) + plainText)
    }

    @Serializable
    class Punishment(
        val time: Long,
        val reason: String,
        val detail: String,
        val source: String,
        val duration: Long = -1,
        val cancelTime: Long = 0,
        val cancelReason: String = "",
        val cancelSource: String = "",
    )
}

fun UserProfile.takeMoney(money: Double): Boolean {
    if (this.money >= money) {
        this.money -= money
        return true
    }
    return false
}
