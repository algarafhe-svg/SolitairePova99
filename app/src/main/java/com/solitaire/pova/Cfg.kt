package com.solitaire.pova

/**
 * كل القيم نسب من عرض/ارتفاع الشاشة. عدّلها بعد النظر إلى صورة التشخيص (ضغطة مطولة على الزر العائم).
 */
object Cfg {
    var mirror = false          // true إذا كانت اللوحة من اليمين لليسار
    var cardW = 0.125f
    var cardH = 0.090f
    var tableauTop = 0.32f      // الحافة العلوية لأول ورقة في كل عمود
    var downStep = 0.024f       // المسافة بين الأوراق المقلوبة
    var upStep = 0.040f         // المسافة بين الأوراق المكشوفة
    var topRowTop = 0.17f       // الحافة العلوية لصف (المخزون/المهملات/الأساسات)
    var stockCol = 0
    var wasteCol = 1
    val foundCols = intArrayOf(3, 4, 5, 6)

    // توقيت السحب (مللي ثانية)
    var holdMs = 70L
    var moveMs = 240L
    var endHoldMs = 70L
    var afterDropMs = 220L
    var betweenMs = 100L
}
