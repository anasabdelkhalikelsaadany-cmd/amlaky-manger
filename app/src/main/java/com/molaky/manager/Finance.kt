package com.molaky.manager

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

const val EPS = 0.005

fun parseMonth(s: String): YearMonth? = runCatching { YearMonth.parse(s) }.getOrNull()
fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

fun money(v: Double): String {
    val s = if (Math.abs(v % 1.0) < EPS) String.format(Locale.US, "%,.0f", v)
    else String.format(Locale.US, "%,.2f", v)
    return "$s ج.م"
}

fun monthLabel(ym: YearMonth): String =
    "${ym.month.getDisplayName(TextStyle.FULL, Locale("ar"))} ${ym.year}"

/** يحوّل الأرقام العربية/الفارسية إلى إنجليزية ويشيل الفواصل */
fun normalizeNumber(s: String): String {
    val sb = StringBuilder()
    for (c in s.trim()) {
        when (c) {
            in '٠'..'٩' -> sb.append('0' + (c - '٠'))
            in '۰'..'۹' -> sb.append('0' + (c - '۰'))
            '٫' -> sb.append('.')
            '٬', ',', ' ' -> {}
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

fun waNumber(phone: String): String {
    var d = normalizeNumber(phone).filter { it.isDigit() }
    if (d.startsWith("00")) d = d.drop(2)
    else if (d.startsWith("0")) d = "20" + d.drop(1)
    return d
}

fun parseNum(s: String): Double? =
    normalizeNumber(s).toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

fun fmtNum(d: Double): String = when {
    d == 0.0 -> ""
    d % 1.0 == 0.0 -> d.toLong().toString()
    else -> d.toString()
}

fun UnitEntity.title(): String {
    val part = apartment.ifBlank { address }.ifBlank { "وحدة" }
    return "${propertyType.ifBlank { "وحدة" }} • $part"
}

fun UnitEntity.rentAfterIncrease(): Double = round2(
    if (increaseType == INC_FIXED) rent + increaseValue
    else rent * (1 + increaseValue / 100.0)
)

/** الشهر الذي تسري منه الزيادة: شهر موعد الزيادة، أو الشهر الحالي لو الموعد لسه جاي */
fun increaseEffectiveMonth(u: UnitEntity, today: LocalDate): YearMonth {
    val cur = YearMonth.from(today)
    val inc = parseDate(u.increaseDate) ?: return cur
    val incMonth = YearMonth.from(inc)
    return if (incMonth < cur) incMonth else cur
}

data class MonthStatus(
    val month: YearMonth,
    val expected: Double,
    val paid: Double,
    val due: Boolean
) {
    val remaining: Double get() = (expected - paid).coerceAtLeast(0.0)
    val overdue: Boolean get() = due && remaining > EPS
}

enum class UnitState { INACTIVE, OVERDUE, PENDING, PAID }

data class UnitSummary(
    val unit: UnitEntity,
    val statuses: List<MonthStatus>,
    val payments: List<PaymentEntity>,
    val today: LocalDate
) {
    val overdueMonths: List<MonthStatus> = statuses.filter { it.overdue }
    val overdueTotal: Double = overdueMonths.sumOf { it.remaining }
    val thisMonth: MonthStatus? = statuses.firstOrNull { it.month == YearMonth.from(today) }
    val state: UnitState = when {
        !unit.active -> UnitState.INACTIVE
        overdueMonths.isNotEmpty() -> UnitState.OVERDUE
        thisMonth != null && thisMonth.remaining > EPS -> UnitState.PENDING
        else -> UnitState.PAID
    }
}

fun expectedRent(unit: UnitEntity, changes: List<RentChangeEntity>, month: YearMonth): Double {
    if (changes.isEmpty()) return unit.rent
    val sorted = changes.sortedBy { it.fromMonth }
    val key = month.toString()
    val applicable = sorted.lastOrNull { it.fromMonth <= key } ?: sorted.first()
    return applicable.amount
}

fun monthStatuses(
    unit: UnitEntity,
    changes: List<RentChangeEntity>,
    payments: List<PaymentEntity>,
    today: LocalDate
): List<MonthStatus> {
    val current = YearMonth.from(today)
    var start = parseMonth(unit.trackFrom) ?: current
    if (start < current.minusYears(30)) start = current.minusYears(30)
    var last = current
    val end = parseDate(unit.contractEnd)
    if (end != null && YearMonth.from(end) < last) last = YearMonth.from(end)

    val paidByMonth = payments.groupBy { it.monthKey }.mapValues { e -> e.value.sumOf { it.amount } }
    val out = mutableListOf<MonthStatus>()
    var m = start
    while (m <= last) {
        val due = m < current || today.dayOfMonth >= unit.dueDay
        out.add(MonthStatus(m, expectedRent(unit, changes, m), paidByMonth[m.toString()] ?: 0.0, due))
        m = m.plusMonths(1)
    }
    return out
}

fun summarize(
    unit: UnitEntity,
    changes: List<RentChangeEntity>,
    payments: List<PaymentEntity>,
    today: LocalDate
): UnitSummary = UnitSummary(
    unit,
    monthStatuses(unit, changes, payments, today),
    payments.sortedWith(compareByDescending<PaymentEntity> { it.paidAt }.thenByDescending { it.id }),
    today
)

data class UpcomingEvent(val unitId: Long, val text: String, val days: Long)

fun upcomingEvents(list: List<UnitSummary>, today: LocalDate, horizon: Long = 30): List<UpcomingEvent> {
    val out = mutableListOf<UpcomingEvent>()
    for (s in list) {
        val u = s.unit
        if (!u.active) continue
        val end = parseDate(u.contractEnd)
        if (end != null) {
            val d = ChronoUnit.DAYS.between(today, end)
            if (d < 0) out.add(UpcomingEvent(u.id, "انتهى العقد: ${u.title()} (منذ ${-d} يوم)", d))
            else if (d <= horizon) out.add(UpcomingEvent(u.id, "ينتهي العقد: ${u.title()} (بعد $d يوم)", d))
        }
        val inc = parseDate(u.increaseDate)
        if (inc != null && u.increaseValue > 0) {
            val d = ChronoUnit.DAYS.between(today, inc)
            if (d in 0L..horizon) out.add(UpcomingEvent(u.id, "موعد زيادة الإيجار: ${u.title()} (بعد $d يوم)", d))
        }
    }
    return out.sortedBy { it.days }
}

/** سطور التنبيه اليومي: متأخرات كل 3 أيام، وعقود/زيادات قريبة */
fun reminderLines(list: List<UnitSummary>, today: LocalDate): List<String> {
    val lines = mutableListOf<String>()
    for (s in list) {
        val u = s.unit
        if (!u.active) continue
        val who = u.tenantName.ifBlank { "بدون اسم" }

        val oldest = s.overdueMonths.firstOrNull()
        if (oldest != null) {
            val dueDate = oldest.month.atDay(u.dueDay.coerceIn(1, 28))
            val late = ChronoUnit.DAYS.between(dueDate, today)
            if (late >= 0 && late % 3L == 0L) {
                val n = s.overdueMonths.size
                val suffix = if (n > 1) " ($n أشهر)" else ""
                lines.add("$who – ${u.title()}: متأخر ${money(s.overdueTotal)}$suffix")
            }
        }

        val end = parseDate(u.contractEnd)
        if (end != null) {
            val d = ChronoUnit.DAYS.between(today, end)
            if (d in 0L..30L && (d <= 7L || d % 7L == 0L)) {
                lines.add("ينتهي عقد $who – ${u.title()} بعد $d يوم")
            }
        }

        val inc = parseDate(u.increaseDate)
        if (inc != null && u.increaseValue > 0) {
            val d = ChronoUnit.DAYS.between(today, inc)
            if (d in 0L..14L && (d <= 7L || d == 14L)) {
                lines.add("موعد زيادة إيجار $who – ${u.title()} بعد $d يوم")
            }
        }
    }
    return lines
}
