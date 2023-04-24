import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import user.UserProfile

val Group.enabled: Boolean
    get() = config.enabledGroup.contains(id)

val User.profile: UserProfile
    get() = profiles[id]
