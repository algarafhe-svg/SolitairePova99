package com.solitaire.pova

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class BotRunner(private val svc: BotService, private val say: (String) -> Unit) {

    suspend fun run() {
        val first = Capture.frame()
        if (first == null) { say("لا يوجد التقاط للشاشة"); return }
        val v = Vision(first.width, first.height)
        var s = v.initialScan(first)
        if (s == null) {
            say("لم أتعرف على اللوحة. ابدأ لعبة جديدة قبل أي حركة، أو اضغط مطولاً على الزر لحفظ صور التشخيص")
            return
        }
        say("بدأ البوت")
        val visited = HashSet<String>()
        var draws = 0

        while (currentCoroutineContext().isActive) {
            if (s!!.won) { say("اكتملت اللعبة"); return }
            visited.add(s.key())
            val cands = Solver.moves(s).sortedByDescending { it.score }
            var pick: Solver.Scored? = null
            for (c in cands) {
                if (c.move is Move.Draw) {
                    if (draws <= 56) { pick = c; break } else continue
                }
                if (Solver.apply(s, c.move).key() in visited) continue
                pick = c
                break
            }
            if (pick == null) { say("لا توجد حركات أخرى"); return }
            val ns = execute(v, s, pick.move) ?: return
            draws = if (pick.move is Move.Draw) draws + 1 else 0
            s = ns
            delay(Cfg.betweenMs)
        }
    }

    // ---------- تنفيذ حركة مع تحقق وإعادة محاولة ----------
    private suspend fun execute(v: Vision, s: State, m: Move): State? {
        val after = Solver.apply(s, m)
        for (attempt in 0 until 3) {
            val f0 = Capture.grab() ?: return null
            val (g, d) = points(v, f0, s, m)
            val sent = if (d == null) svc.tap(g.x, g.y)
            else svc.drag(g.x, g.y, d.x, d.y,
                Cfg.holdMs + attempt * 60, Cfg.moveMs + attempt * 120, Cfg.endHoldMs + attempt * 60)
            if (!sent) { delay(300); continue }

            waitStable()
            var fr = Capture.grab() ?: return null
            var notMoved = false
            for (round in 0 until 2) {
                val oa = v.observe(fr, after)
                if (matches(after, oa) && drawChanged(s, m, oa)) return sync(v, fr, after, oa)
                val ob = v.observe(fr, s)
                if (matches(s, ob)) { notMoved = true; break }     // الورقة لم تتحرك → إعادة آمنة
                delay(500)
                waitStable()
                fr = Capture.grab() ?: return null
            }
            if (!notMoved) {
                say("اختلاف غير متوقع في اللوحة — توقفت (حُفظت صور التشخيص)")
                svc.dumpNow()
                return null
            }
        }
        say("فشل تنفيذ الحركة بعد 3 محاولات")
        svc.dumpNow()
        return null
    }

    private fun points(v: Vision, bmp: Bitmap, s: State, m: Move): Pair<PointF, PointF?> {
        val g = v.geo
        fun foundPt(slot: Int) = PointF(g.colX(Cfg.foundCols[slot]), g.rowCenterY())
        fun wastePt() = PointF(v.wasteCx(bmp) ?: g.colX(Cfg.wasteCol), g.rowCenterY())
        return when (m) {
            Move.Draw -> PointF(g.colX(Cfg.stockCol), g.rowCenterY()) to null
            is Move.ToFound -> {
                val card = if (m.fromWaste) s.waste!! else s.up[m.col].last()
                val from = if (m.fromWaste) wastePt() else g.grab(m.col, s.down[m.col], s.up[m.col].size - 1)
                from to foundPt(s.slotFor(card))
            }
            is Move.Tab -> g.grab(m.from, s.down[m.from], m.idx) to g.drop(m.to, s.down[m.to], s.up[m.to].size)
            is Move.WasteTab -> wastePt() to g.drop(m.to, s.down[m.to], s.up[m.to].size)
        }
    }

    // ---------- مطابقة المتوقع بالمرصود ----------
    private fun expect(s: State): Snap {
        val tab = Array<Obs>(7) { c ->
            val t = s.up[c].lastOrNull()
            when {
                t != null -> Obs.Face(if (t.unknown) null else t)
                s.down[c] > 0 -> Obs.Back
                else -> Obs.Empty
            }
        }
        val w = s.waste
        val waste: Obs = if (w == null) Obs.Empty else Obs.Face(if (w.unknown) null else w)
        val stock: Obs = if (s.stock) Obs.Back else Obs.Empty
        val found = Array<Obs>(4) { i -> s.found[i]?.let { Obs.Face(it) } ?: Obs.Empty }
        return Snap(tab, waste, stock, found)
    }

    private fun obsEq(e: Obs, g: Obs): Boolean {
        if (e is Obs.Face && g is Obs.Face) {
            val a = e.card
            val b = g.card
            return a == null || b == null || a.unknown || b.unknown || a == b
        }
        return e == g
    }

    private fun matches(s: State, o: Snap): Boolean {
        val e = expect(s)
        for (c in 0 until 7) if (!obsEq(e.tab[c], o.tab[c])) return false
        if (!s.wildWaste && !obsEq(e.waste, o.waste)) return false
        if (!s.wildStock && !obsEq(e.stock, o.stock)) return false
        val ef = e.found.mapNotNull { (it as? Obs.Face)?.card }
        val og = o.found.filterIsInstance<Obs.Face>()
        if (ef.size != og.size) return false
        for (f in og) {
            val c = f.card ?: continue
            if (c !in ef) return false
        }
        return true
    }

    /** منع الالتباس: بعد السحب من المخزون يجب أن تتغير ورقة المهملات فعلاً */
    private fun drawChanged(s: State, m: Move, o: Snap): Boolean {
        if (m !is Move.Draw || !s.stock) return true
        val w = o.waste as? Obs.Face ?: return false
        val prev = s.waste
        return prev == null || w.card == null || w.card != prev
    }

    /** ملء الأوراق المجهولة (المقلوبة حديثاً) من الشاشة ومزامنة الأساسات/المهملات/المخزون */
    private suspend fun sync(v: Vision, fr0: Bitmap, st: State, o0: Snap): State? {
        var fr = fr0
        var o = o0
        for (attempt in 0 until 3) {
            val n = st.copy()
            var ok = true
            for (c in 0 until 7) {
                if (n.up[c].lastOrNull()?.unknown == true) {
                    val cd = (o.tab[c] as? Obs.Face)?.card
                    if (cd == null) ok = false else n.up[c][n.up[c].size - 1] = cd
                }
            }
            when (val w = o.waste) {
                is Obs.Face -> if (w.card == null) ok = false else n.waste = w.card
                else -> n.waste = null
            }
            n.stock = o.stock is Obs.Back
            for (i in 0 until 4) {
                when (val f = o.found[i]) {
                    is Obs.Face -> if (f.card == null) ok = false else n.found[i] = f.card
                    else -> n.found[i] = null
                }
            }
            n.wildWaste = false
            n.wildStock = false
            if (ok) return n
            delay(400)
            fr = Capture.grab() ?: return null
            o = v.observe(fr, st)
        }
        say("تعذّرت قراءة ورقة بعد الحركة — توقفت")
        svc.dumpNow()
        return null
    }

    // ---------- انتظار استقرار الشاشة بعد الحركة ----------
    private fun small(b: Bitmap): IntArray {
        val t = Bitmap.createScaledBitmap(b, 36, 82, true)
        val a = IntArray(36 * 82)
        t.getPixels(a, 0, 36, 0, 0, 36, 82)
        return IntArray(a.size) { (Color.red(a[it]) * 3 + Color.green(a[it]) * 6 + Color.blue(a[it])) / 10 }
    }

    private fun diff(a: IntArray, b: IntArray): Double {
        var s = 0L
        for (i in a.indices) s += kotlin.math.abs(a[i] - b[i])
        return s.toDouble() / a.size
    }

    private suspend fun waitStable(maxMs: Long = 1500) {
        val t0 = System.currentTimeMillis()
        delay(Cfg.afterDropMs)
        var prev = Capture.grab()?.let { small(it) }
        var stable = 0
        while (System.currentTimeMillis() - t0 < maxMs) {
            delay(80)
            val cur = Capture.grab()?.let { small(it) }
            if (prev != null && cur != null && diff(prev, cur) < 1.5) {
                stable++
                if (stable >= 2) return
            } else stable = 0
            prev = cur
        }
    }
}
