import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.contact.nameCardOrNick
import user.UserProfile

val Group.enabled: Boolean
    get() = config.enabledGroup.contains(id)

val User.profile: UserProfile
    get() = profile(id)

val Member.guz: String
    get() = "$nameCardOrNick($id)"

fun UserProfile.asMember(bot: Bot): Member? {
    return bot.getGroup(lastAppearedGroup)?.get(id)
}

fun UserProfile.addItem(type: String) {
    items[type] = items.getOrDefault(type, 0) + 1
}

fun profile(id: Long): UserProfile {
    return profiles[id] ?: UserProfile(id).apply { profiles[id] = this }
}

suspend fun <T> T.loggingError(action: suspend T.() -> Unit) {
    try {
        action()
    }
    catch (e: Exception) {
        e.printStackTrace()
    }
}
