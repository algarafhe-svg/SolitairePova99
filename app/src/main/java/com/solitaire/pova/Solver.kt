package com.solitaire.pova

/** حلّال جشع سريع (ميكرو ثانية): يقلب الأوراق المقلوبة أولاً ثم الأساسات الآمنة. */
object Solver {
    class Scored(val move: Move, val score: Int)

    fun canPlace(s: State, c: Card, b: Int): Boolean {
        if (s.up[b].isEmpty()) return s.down[b] == 0 && c.rank == 13
        val t = s.up[b].last()
        return !t.unknown && t.rank == c.rank + 1 && t.red != c.red
    }

    private fun safe(s: State, c: Card): Boolean {
        if (c.rank <= 2) return true
        val opp = if (c.red) intArrayOf(0, 3) else intArrayOf(1, 2)
        return opp.all { s.foundRank(it) >= c.rank - 1 }
    }

    private fun kingWaiting(s: State): Boolean =
        s.waste?.rank == 13 || (0 until 7).any { s.down[it] > 0 && s.up[it].firstOrNull()?.rank == 13 }

    fun moves(s: State): List<Scored> {
        val out = ArrayList<Scored>()

        for (a in 0 until 7) {
            val c = s.up[a].lastOrNull() ?: continue
            if (c.unknown || s.slotFor(c) < 0) continue
            val flips = s.up[a].size == 1 && s.down[a] > 0
            val sc = if (flips) 90 + s.down[a] else if (safe(s, c)) 85 else 25
            out.add(Scored(Move.ToFound(false, a), sc))
        }

        val w = s.waste
        if (w != null && !w.unknown) {
            if (s.slotFor(w) >= 0) out.add(Scored(Move.ToFound(true, 0), if (safe(s, w)) 80 else 28))
            for (b in 0 until 7) if (canPlace(s, w, b)) out.add(Scored(Move.WasteTab(b), if (w.rank == 13) 62 else 60))
        }

        for (a in 0 until 7) {
            for (idx in s.up[a].indices) {
                val card = s.up[a][idx]
                if (card.unknown) continue
                for (b in 0 until 7) {
                    if (a == b || !canPlace(s, card, b)) continue
                    val whole = idx == 0
                    val flips = whole && s.down[a] > 0
                    val below = if (idx > 0) s.up[a][idx - 1] else null
                    val exposes = below != null && !below.unknown && s.slotFor(below) >= 0
                    val makesEmpty = whole && s.down[a] == 0 && s.up[b].isNotEmpty()
                    val sc = when {
                        flips -> 100 + s.down[a]
                        exposes -> 30
                        makesEmpty && kingWaiting(s) -> 15
                        else -> -1
                    }
                    if (sc >= 0) out.add(Scored(Move.Tab(a, idx, b), sc))
                }
            }
        }

        if (s.stock || s.waste != null) out.add(Scored(Move.Draw, 1))
        return out
    }

    private fun flipIfNeeded(n: State, col: Int) {
        if (n.up[col].isEmpty() && n.down[col] > 0) {
            n.down[col]--
            n.up[col].add(Card.UNKNOWN)
        }
    }

    fun apply(s: State, m: Move): State {
        val n = s.copy()
        n.wildWaste = false
        n.wildStock = false
        when (m) {
            is Move.ToFound -> {
                val c: Card
                if (m.fromWaste) {
                    c = n.waste!!; n.waste = null; n.wildWaste = true
                } else {
                    c = n.up[m.col].removeAt(n.up[m.col].size - 1)
                    flipIfNeeded(n, m.col)
                }
                n.found[n.slotFor(c)] = c
            }
            is Move.Tab -> {
                val run = ArrayList(n.up[m.from].subList(m.idx, n.up[m.from].size))
                repeat(run.size) { n.up[m.from].removeAt(n.up[m.from].size - 1) }
                n.up[m.to].addAll(run)
                flipIfNeeded(n, m.from)
            }
            is Move.WasteTab -> {
                n.up[m.to].add(n.waste!!); n.waste = null; n.wildWaste = true
            }
            Move.Draw -> {
                if (n.stock) { n.waste = Card.UNKNOWN; n.wildStock = true }
                else { n.stock = true; n.waste = null }
            }
        }
        return n
    }
}
