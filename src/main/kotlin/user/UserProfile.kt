package user

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.*

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
    @Transient
    var marry: MutableMap<Long, MarryData> = mutableMapOf()
) {
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

    class MarryData(
        // 登记时间
        val time: Long,
        // 登记对象的ID
        val target: Long,
        // 登记对象的昵称
        val targetName: String,
        // 登记对象头像的
        val targetAvatarUrl: String,
        // 是否是申请人（娶和被取）
        val applicant: Boolean
    ){
        fun valid(): Boolean{
            val instance = Calendar.getInstance()
            instance.set(Calendar.HOUR, 0)
            instance.set(Calendar.SECOND, 0)
            instance.set(Calendar.MINUTE, 0)
            return instance.timeInMillis <= time
        }
    }
}

fun UserProfile.takeMoney(money: Double): Boolean {
    if (this.money >= money) {
        this.money -= money
        return true
    }
    return false
}
