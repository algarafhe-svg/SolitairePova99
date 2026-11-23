package com.solitaire.pova

/** suit: 0=♠ 1=♥ 2=♦ 3=♣ ; rank: 1..13 ; rank 0 = غير معروفة بعد */
data class Card(val rank: Int, val suit: Int) {
    val red get() = suit == 1 || suit == 2
    val unknown get() = rank == 0
    override fun toString() = if (rank == 0) "??" else "A23456789TJQK"[rank - 1].toString() + "SHDC"[suit]

    companion object { val UNKNOWN = Card(0, 0) }
}

sealed class Obs {
    object Empty : Obs()
    object Back : Obs()
    data class Face(val card: Card?) : Obs()
}

class Snap(val tab: Array<Obs>, val waste: Obs, val stock: Obs, val found: Array<Obs>)

sealed class Move {
    data class ToFound(val fromWaste: Boolean, val col: Int) : Move()
    data class Tab(val from: Int, val idx: Int, val to: Int) : Move()
    data class WasteTab(val to: Int) : Move()
    object Draw : Move()
}

class State {
    val down = IntArray(7)
    val up = Array(7) { ArrayList<Card>() }
    val found = arrayOfNulls<Card>(4)
    var waste: Card? = null
    var stock = true
    var wildWaste = false
    var wildStock = false

    val won get() = found.all { it?.rank == 13 }

    fun copy(): State {
        val s = State()
        for (i in 0 until 7) { s.down[i] = down[i]; s.up[i].addAll(up[i]) }
        for (i in 0 until 4) s.found[i] = found[i]
        s.waste = waste; s.stock = stock; s.wildWaste = wildWaste; s.wildStock = wildStock
        return s
    }

    fun foundRank(suit: Int): Int = found.firstOrNull { it != null && it.suit == suit }?.rank ?: 0

    /** رقم خانة الأساس المناسبة للورقة أو -1 */
    fun slotFor(c: Card): Int {
        if (c.rank == 1) return found.indexOfFirst { it == null }
        return found.indexOfFirst { it != null && it.suit == c.suit && it.rank == c.rank - 1 }
    }

    fun key(): String {
        val sb = StringBuilder()
        for (i in 0 until 7) {
            sb.append(down[i]).append(':')
            for (c in up[i]) sb.append(c).append(',')
            sb.append('|')
        }
        for (f in found) sb.append(f?.toString() ?: "-").append('/')
        sb.append(waste?.toString() ?: "-").append(if (stock) 'S' else 'x')
        return sb.toString()
    }
}
