package com.solitaire.pova

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

class Geo(val w: Int, val h: Int) {
    val cw = Cfg.cardW * w
    val ch = Cfg.cardH * h
    val tabTop = Cfg.tableauTop * h
    val rowTop = Cfg.topRowTop * h

    fun colX(c: Int): Float = ((if (Cfg.mirror) 6 - c else c) + 0.5f) / 7f * w
    fun downTop(i: Int): Float = tabTop + i * Cfg.downStep * h
    fun upTop(d: Int, i: Int): Float = tabTop + d * Cfg.downStep * h + i * Cfg.upStep * h

    /** نقطة الإمساك: داخل الشريط الظاهر من الورقة المراد سحبها */
    fun grab(col: Int, d: Int, idx: Int) =
        PointF(colX(col), upTop(d, idx) + minOf(Cfg.upStep * h * 0.5f, ch * 0.35f))

    /** نقطة الإفلات: مركز آخر ورقة في العمود الهدف (أو الخانة الفارغة) */
    fun drop(col: Int, d: Int, upCount: Int): PointF {
        val top = if (upCount == 0) tabTop else upTop(d, upCount - 1)
        return PointF(colX(col), (top + ch * 0.5f).coerceAtMost(h - 5f))
    }

    fun rowCenterY() = rowTop + ch * 0.5f
}

class Vision(val w: Int, val h: Int) {
    val geo = Geo(w, h)
    private val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val cache = HashMap<Long, Card>()

    companion object {
        const val EMPTY = 0
        const val BACK = 1
        const val FACE = 2
    }

    // ---------- تصنيف المنطقة: فارغ / ظهر ورقة / وجه ورقة ----------
    fun bg(b: Bitmap): Int {
        val x = ((geo.colX(0) + geo.colX(1)) / 2f).toInt().coerceIn(3, w - 4)
        val y = (geo.tabTop + geo.ch * 0.5f).toInt().coerceIn(3, h - 4)
        var r = 0; var g = 0; var bl = 0; var n = 0
        for (dx in -2..2) for (dy in -2..2) {
            val p = b.getPixel(x + dx, y + dy)
            r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++
        }
        return Color.rgb(r / n, g / n, bl / n)
    }

    fun classify(b: Bitmap, cx: Float, top: Float, hgt: Float, bg: Int): Int {
        val x0 = (cx - geo.cw * 0.38f).toInt().coerceIn(0, w - 2)
        val x1 = (cx + geo.cw * 0.38f).toInt().coerceIn(x0 + 1, w - 1)
        val y0 = (top + 3).toInt().coerceIn(0, h - 2)
        val y1 = (top + hgt - 3).toInt().coerceIn(y0 + 1, h - 1)
        val rw = x1 - x0
        val rh = y1 - y0
        val a = IntArray(rw * rh)
        b.getPixels(a, 0, rw, x0, y0, rw, rh)
        var white = 0
        var sr = 0L; var sg = 0L; var sb = 0L
        for (p in a) {
            val r = Color.red(p); val g = Color.green(p); val bl = Color.blue(p)
            if (r > 200 && g > 200 && bl > 200 && maxOf(r, g, bl) - minOf(r, g, bl) < 40) white++
            sr += r; sg += g; sb += bl
        }
        val n = a.size
        if (white * 100 / n > 42) return FACE
        val d = abs(sr / n - Color.red(bg)) + abs(sg / n - Color.green(bg)) + abs(sb / n - Color.blue(bg))
        return if (d < 45) EMPTY else BACK
    }

    private suspend fun look(b: Bitmap, cx: Float, top: Float, bg: Int, hgt: Float): Obs =
        when (classify(b, cx, top, hgt, bg)) {
            FACE -> Obs.Face(readCard(b, cx, top))
            BACK -> Obs.Back
            else -> Obs.Empty
        }

    // ---------- أوراق المهملات (تدعم السحب 1 أو 3) ----------
    private fun whiteCol(b: Bitmap, x: Int, ys: IntArray): Boolean {
        var n = 0
        for (y in ys) {
            val p = b.getPixel(x, y)
            if (Color.red(p) > 200 && Color.green(p) > 200 && Color.blue(p) > 200) n++
        }
        return n >= ys.size - 1
    }

    /** مركز آخر ورقة ظاهرة في المهملات أو null إذا فارغة */
    fun wasteCx(b: Bitmap): Float? {
        val y0 = geo.rowTop + geo.ch * 0.25f
        val ys = IntArray(6) { (y0 + it * geo.ch * 0.08f).toInt().coerceIn(0, h - 1) }
        val base = geo.colX(Cfg.wasteCol)
        val dir = if (Cfg.mirror) -1 else 1
        val far = base + dir * geo.cw * 1.1f
        val limit = (base - dir * geo.cw * 0.6f).toInt()
        var x = far.toInt()
        var run = 0
        var start = 0
        while (if (dir > 0) x >= limit else x <= limit) {
            if (x in 1 until w - 1 && whiteCol(b, x, ys)) {
                if (run == 0) start = x
                run++
                if (run >= 6) return start - dir * geo.cw / 2f
            } else run = 0
            x -= dir
        }
        return null
    }

    // ---------- قراءة الأوراق ----------
    private suspend fun ocr(bm: Bitmap): Text? = suspendCancellableCoroutine { cont ->
        rec.process(InputImage.fromBitmap(bm, 0))
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
    }

    private fun fingerprint(b: Bitmap): Long {
        val t = Bitmap.createScaledBitmap(b, 16, 16, true)
        var hs = 1125899906842597L
        for (y in 0 until 16) for (x in 0 until 16) {
            val p = t.getPixel(x, y)
            val v = ((Color.red(p) * 3 + Color.green(p) * 6 + Color.blue(p)) / 10) shr 4
            hs = 31 * hs + v
        }
        return hs
    }

    suspend fun readCard(b: Bitmap, cx: Float, top: Float): Card? {
        val x0 = (cx - geo.cw / 2f).toInt().coerceIn(0, w - 10)
        val y0 = top.toInt().coerceIn(0, h - 10)
        val cwid = minOf((geo.cw * 0.46f).toInt(), w - x0)
        val chei = minOf((geo.ch * 0.55f).toInt(), h - y0)
        if (cwid < 12 || chei < 12) return null
        val crop = Bitmap.createBitmap(b, x0, y0, cwid, chei)
        val key = fingerprint(crop)
        cache[key]?.let { return it }
        val card = parse(crop)
        if (card != null) cache[key] = card
        return card
    }

    private class Comp(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int, val pix: IntArray)

    private fun components(ink: BooleanArray, cw: Int, chh: Int): List<Comp> {
        val seen = BooleanArray(ink.size)
        val stack = IntArray(ink.size)
        val out = ArrayList<Comp>()
        for (s in ink.indices) {
            if (!ink[s] || seen[s]) continue
            var sp = 0
            stack[sp++] = s; seen[s] = true
            val list = ArrayList<Int>()
            var x0 = cw; var y0 = chh; var x1 = 0; var y1 = 0
            while (sp > 0) {
                val i = stack[--sp]
                list.add(i)
                val x = i % cw
                val y = i / cw
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
                if (x > 0 && ink[i - 1] && !seen[i - 1]) { seen[i - 1] = true; stack[sp++] = i - 1 }
                if (x < cw - 1 && ink[i + 1] && !seen[i + 1]) { seen[i + 1] = true; stack[sp++] = i + 1 }
                if (y > 0 && ink[i - cw] && !seen[i - cw]) { seen[i - cw] = true; stack[sp++] = i - cw }
                if (i + cw < ink.size && ink[i + cw] && !seen[i + cw]) { seen[i + cw] = true; stack[sp++] = i + cw }
            }
            out.add(Comp(x0, y0, x1, y1, list.toIntArray()))
        }
        return out
    }

    private fun parseRank(t0: String): Int? {
        val t = t0.uppercase()
        if (t.isEmpty()) return null
        if (t.startsWith("10") || t.startsWith("1O") || t.startsWith("IO") || t.startsWith("LO") || t.startsWith("1D")) return 10
        return when (t[0]) {
            'A' -> 1
            '2' -> 2
            '3' -> 3
            '4' -> 4
            '5', 'S' -> 5
            '6', 'G' -> 6
            '7' -> 7
            '8', 'B' -> 8
            '9' -> 9
            '0', '1', 'I', 'L' -> 10
            'J' -> 11
            'Q', 'O' -> 12
            'K' -> 13
            else -> null
        }
    }

    /** الرتبة عبر OCR، والنوع عبر لون الرمز + شكله (عرض الصفوف) */
    private suspend fun parse(crop: Bitmap): Card? {
        val scale = 3
        val big = Bitmap.createScaledBitmap(crop, crop.width * scale, crop.height * scale, true)
        val res = ocr(big) ?: return null
        val el = res.textBlocks.flatMap { it.lines }.flatMap { it.elements }.firstOrNull() ?: return null

        val txt = StringBuilder()
        var box: Rect? = null
        for (sy in el.symbols) {
            if (!sy.text.all { it.isLetterOrDigit() }) break
            txt.append(sy.text)
            val bb = sy.boundingBox ?: continue
            val cur = box
            if (cur == null) box = Rect(bb) else { cur.union(bb); box = cur }
        }
        if (txt.isEmpty()) {
            txt.append(el.text.takeWhile { it.isLetterOrDigit() })
            box = el.boundingBox?.let { Rect(it) }
        }
        val rb = box ?: return null
        val rank = parseRank(txt.toString()) ?: return null

        val cw = crop.width
        val chh = crop.height
        val px = IntArray(cw * chh)
        crop.getPixels(px, 0, cw, 0, 0, cw, chh)

        val rl = rb.left / scale - 3; val rt = rb.top / scale - 3
        val rr = rb.right / scale + 3; val rbm = rb.bottom / scale + 3
        val rankH = (rb.height() / scale).coerceAtLeast(6)

        val ink = BooleanArray(px.size)
        for (i in px.indices) {
            val x = i % cw; val y = i / cw
            if (x in rl..rr && y in rt..rbm) continue
            val p = px[i]
            val r = Color.red(p); val g = Color.green(p); val bl = Color.blue(p)
            val lum = (r * 299 + g * 587 + bl * 114) / 1000
            ink[i] = lum < 150 || (r > 150 && g < 120 && bl < 120)
        }

        val cx = (rl + rr) / 2f
        val cy = (rt + rbm) / 2f
        val suitComp = components(ink, cw, chh)
            .filter {
                val bw = it.maxX - it.minX + 1
                val bh = it.maxY - it.minY + 1
                it.pix.size >= 10 && bh >= rankH * 0.5 && bh <= rankH * 2.0 && bw >= rankH * 0.4 && bw <= rankH * 2.0
            }
            .minByOrNull {
                val dx = (it.minX + it.maxX) / 2f - cx
                val dy = (it.minY + it.maxY) / 2f - cy
                dx * dx + dy * dy
            } ?: return null

        return Card(rank, suitOf(suitComp, px, cw))
    }

    private fun suitOf(c: Comp, px: IntArray, cw: Int): Int {
        val bh = c.maxY - c.minY + 1
        val rows = IntArray(bh)
        var r = 0L; var g = 0L; var bl = 0L
        for (i in c.pix) {
            rows[i / cw - c.minY]++
            val p = px[i]
            r += Color.red(p); g += Color.green(p); bl += Color.blue(p)
        }
        val n = c.pix.size
        val red = r / n > g / n + 40 && r / n > bl / n + 40
        fun wAt(f: Float): Float {
            val m = (f * (bh - 1)).roundToInt()
            var s = 0
            var k = 0
            for (y in (m - 1)..(m + 1)) if (y in 0 until bh) { s += rows[y]; k++ }
            return s.toFloat() / k
        }
        val maxW = rows.max().toFloat()
        return if (red) {
            if (wAt(0.25f) > 1.45f * wAt(0.75f)) 1 else 2      // قلب / ديناري
        } else {
            if (wAt(0.35f) / maxW > 0.62f) 0 else 3            // بستوني / سباتي
        }
    }

    // ---------- لقطة كاملة للوحة حسب نموذج الحالة ----------
    suspend fun observe(b: Bitmap, s: State): Snap {
        val bg = bg(b)
        val tab = Array<Obs>(7) { Obs.Empty }
        for (c in 0 until 7) {
            val top = when {
                s.up[c].isNotEmpty() -> geo.upTop(s.down[c], s.up[c].size - 1)
                s.down[c] > 0 -> geo.downTop(s.down[c] - 1)
                else -> geo.tabTop
            }
            tab[c] = look(b, geo.colX(c), top, bg, geo.ch * 0.3f)
        }
        val stock: Obs =
            if (classify(b, geo.colX(Cfg.stockCol), geo.rowTop, geo.ch * 0.5f, bg) == BACK) Obs.Back else Obs.Empty
        val wx = wasteCx(b)
        val waste: Obs = if (wx == null) Obs.Empty else Obs.Face(readCard(b, wx, geo.rowTop))
        val found = Array<Obs>(4) { Obs.Empty }
        for (i in 0 until 4) found[i] = look(b, geo.colX(Cfg.foundCols[i]), geo.rowTop, bg, geo.ch * 0.3f)
        return Snap(tab, waste, stock, found)
    }

    /** مسح اللوحة عند بداية لعبة جديدة (قبل أي حركة) */
    suspend fun initialScan(b: Bitmap): State? {
        val bg = bg(b)
        val s = State()
        val seen = HashSet<Card>()
        for (c in 0 until 7) {
            var k = 0
            while (true) {
                if (k > 7) return null
                val top = geo.downTop(k)
                when (classify(b, geo.colX(c), top, Cfg.downStep * h, bg)) {
                    BACK -> k++
                    FACE -> {
                        val cd = readCard(b, geo.colX(c), top) ?: return null
                        if (!seen.add(cd)) return null
                        s.down[c] = k
                        s.up[c].add(cd)
                        break
                    }
                    else -> return null
                }
            }
        }
        if (s.down.sum() != 21) return null
        s.stock = classify(b, geo.colX(Cfg.stockCol), geo.rowTop, geo.ch * 0.5f, bg) == BACK
        return s
    }

    /** صورة تشخيص: مستطيلات المواضع المفترضة + ما تعرّف عليه البوت */
    suspend fun debugImage(src: Bitmap): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val cv = Canvas(out)
        val p = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 3f }
        val t = Paint().apply { color = Color.MAGENTA; textSize = 30f; isFakeBoldText = true }
        val bg = bg(src)

        fun label(kind: Int) = when (kind) { FACE -> "F"; BACK -> "B"; else -> "E" }

        for (c in 0 until 7) {
            val cx = geo.colX(c)
            p.color = Color.GREEN
            cv.drawRect(cx - geo.cw / 2, geo.tabTop, cx + geo.cw / 2, geo.tabTop + geo.ch, p)
            p.color = Color.YELLOW
            for (k in 1..6) cv.drawLine(cx - geo.cw / 2, geo.downTop(k), cx - geo.cw / 2 + 25f, geo.downTop(k), p)
            val kind = classify(src, cx, geo.tabTop, Cfg.downStep * h, bg)
            var txt = label(kind)
            if (kind == FACE) txt += " " + (readCard(src, cx, geo.tabTop)?.toString() ?: "?")
            cv.drawText(txt, cx - geo.cw / 2 + 4, geo.tabTop + 34, t)
        }
        p.color = Color.CYAN
        val sx = geo.colX(Cfg.stockCol)
        cv.drawRect(sx - geo.cw / 2, geo.rowTop, sx + geo.cw / 2, geo.rowTop + geo.ch, p)
        cv.drawText("S:" + label(classify(src, sx, geo.rowTop, geo.ch * 0.5f, bg)), sx - geo.cw / 2 + 4, geo.rowTop + 34, t)
        for (i in 0 until 4) {
            val fx = geo.colX(Cfg.foundCols[i])
            cv.drawRect(fx - geo.cw / 2, geo.rowTop, fx + geo.cw / 2, geo.rowTop + geo.ch, p)
            val kind = classify(src, fx, geo.rowTop, geo.ch * 0.3f, bg)
            var txt = label(kind)
            if (kind == FACE) txt += " " + (readCard(src, fx, geo.rowTop)?.toString() ?: "?")
            cv.drawText(txt, fx - geo.cw / 2 + 4, geo.rowTop + 34, t)
        }
        p.color = Color.RED
        val wx = wasteCx(src)
        if (wx != null) {
            cv.drawRect(wx - geo.cw / 2, geo.rowTop, wx + geo.cw / 2, geo.rowTop + geo.ch, p)
            cv.drawText("W " + (readCard(src, wx, geo.rowTop)?.toString() ?: "?"), wx - geo.cw / 2 + 4, geo.rowTop + 34, t)
        }
        return out
    }
}
