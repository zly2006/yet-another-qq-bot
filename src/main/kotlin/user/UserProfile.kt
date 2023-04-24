package user

import kotlinx.serialization.Serializable

@Serializable
class UserProfile(
    var id: Long,
    var lastAppearedGroup: Long = 0,
    var lastAppearedTime: Long = 0,
) {
}
