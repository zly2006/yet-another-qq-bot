import net.mamoe.mirai.contact.Group

val Group.enabled: Boolean
    get() = config.enabledGroup.contains(id)