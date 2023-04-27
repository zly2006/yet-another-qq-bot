import com.sksamuel.scrimage.ImmutableImage

private class Session {
    val players: MutableList<Long> = mutableListOf()
    val startTime: Long = 0
    val lastActive: Long = 0
    val text: String = ""
    val lastWinner: Long = 0
    val streak: Int = 0
}

fun configureTypeTextChallenge() {
    ImmutableImage.create(400, 300).apply {
        
    }
}
