package user

import kotlinx.serialization.Serializable

@Serializable
class UserProfile(
    var id: Long,
    var lastAppearedGroup: Long = 0,
    var lastAppearedTime: Long = 0,
    var lastCheckInDate: Long = 0,
    var keepCheckInDuration: Long = 0,
    var money: Double = 0.0
) {
}

fun UserProfile.takeMoney(money: Double): Boolean {
    if (this.money >= money) {
        this.money -= money
        return true
    }
    return false
}
