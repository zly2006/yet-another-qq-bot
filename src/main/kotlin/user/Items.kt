package user

import kotlinx.serialization.Serializable
import loadJson

@Serializable
class Item(
    val name: String,
    val description: String,
    val rarity: Rarity,
) {
    enum class Rarity {
        COMMON {
            override fun toString() = "普通(0)"
        },
        UNCOMMON {
            override fun toString() = "不凡(1)"
        },
        RARE {
            override fun toString() = "稀有(2)"
        },
        EPIC {
            override fun toString() = "史诗(3)"
        },
        LEGENDARY {
            override fun toString() = "传说(4)"
        },
        MYTHIC {
            override fun toString() = "神话(5)"
        },
        SPECIAL {
            override fun toString() = "特殊(100)"
        },
    }
}

object Items {
    init {

    }
    val itemRegistry = loadJson("items.json") { mutableMapOf<String, Item>().apply {
        put("test", Item("测试物品", "这是一个测试物品", Item.Rarity.COMMON))
        ('A'..'Z').map { Item(it.toString(), "字母碎片 $it", Item.Rarity.COMMON) }.forEach { put(it.name, it) }
    } }
}

