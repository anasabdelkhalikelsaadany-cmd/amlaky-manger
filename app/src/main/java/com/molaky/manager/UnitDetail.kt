package com.molaky.manager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitDetailScreen(
    s: UnitSummary,
    vm: MainViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit
) {
    val u = s.unit
    val ctx = LocalContext.current
    val today = LocalDate.now()
    var payMonth by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmIncrease by remember { mutableStateOf(false) }
    var paymentToDelete by remember { mutableStateOf<PaymentEntity?>(null) }

    val defaultPayMonth: YearMonth =
        s.overdueMonths.firstOrNull()?.month ?: s.thisMonth?.month ?: YearMonth.from(today)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(u.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // الحالة
            item {
                SectionCard("الحالة") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadge(s.state)
                        if (u.active && s.overdueTotal > EPS) {
                            Text(
                                "المتأخر: ${money(s.overdueTotal)}",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (u.active) {
                        Button(
                            onClick = { payMonth = defaultPayMonth.toString() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("تسجيل دفعة") }
                    }
                }
            }

            // المستأجر
            item {
                SectionCard("المستأجر") {
                    InfoRow("الاسم", u.tenantName)
                    InfoRow("الهاتف", u.tenantPhone)
                    if (u.tenantPhone.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { dial(ctx, u.tenantPhone) }) {
                                Icon(Icons.Default.Call, contentDescription = null)
                                Text("اتصال", modifier = Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(onClick = {
                                val msg = if (s.overdueMonths.isNotEmpty()) {
                                    "السلام عليكم ${u.tenantName}، تذكير بسداد إيجار ${u.title()} عن: " +
                                        s.overdueMonths.joinToString("، ") { monthLabel(it.month) } +
                                        " — المبلغ المستحق ${money(s.overdueTotal)}. شكراً لك."
                                } else {
                                    "السلام عليكم ${u.tenantName}، تذكير بموعد سداد إيجار ${u.title()} " +
                                        "يوم ${u.dueDay} من كل شهر. شكراً لك."
                                }
                                openWhatsApp(ctx, u.tenantPhone, msg)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                Text("تذكير واتساب", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                    if (u.tenantName.isBlank() && u.tenantPhone.isBlank()) {
                        Text("لم يتم تسجيل مستأجر", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // الوحدة والعقد
            item {
                SectionCard("الوحدة والعقد") {
                    InfoRow("العنوان", u.address)
                    InfoRow("العقار", u.building)
                    InfoRow("رقم الوحدة", u.apartment)
                    InfoRow("الدور", u.floor)
                    InfoRow("الإيجار الشهري", money(u.rent))
                    InfoRow("يوم الاستحقاق", "${u.dueDay} من كل شهر")
                    InfoRow("بداية العقد", u.contractStart)
                    InfoRow("نهاية العقد", u.contractEnd)
                    InfoRow("تتبع الإيجار من", monthLabel(parseMonth(u.trackFrom) ?: YearMonth.from(today)))
                    if (u.mapsUrl.isNotBlank()) {
                        OutlinedButton(onClick = { openUrl(ctx, u.mapsUrl) }) {
                            Icon(Icons.Default.Place, contentDescription = null)
                            Text("فتح الموقع على الخريطة", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }

            // الزيادة
            if (u.increaseValue > 0) {
                item {
                    SectionCard("الزيادة السنوية") {
                        val kind = if (u.increaseType == INC_FIXED) money(u.increaseValue)
                        else "${fmtNum(u.increaseValue)}%"
                        InfoRow("قيمة الزيادة", kind)
                        InfoRow("موعد الزيادة", u.increaseDate)
                        InfoRow("الإيجار بعد الزيادة", money(u.rentAfterIncrease()))
                        OutlinedButton(onClick = { confirmIncrease = true }) { Text("تطبيق الزيادة الآن") }
                    }
                }
            }

            // الأشهر
            item { Text("حالة الأشهر", style = MaterialTheme.typography.titleMedium) }
            if (s.statuses.isEmpty()) {
                item {
                    Text(
                        "لا توجد أشهر مستحقة للتتبع حالياً.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(s.statuses.asReversed().take(12), key = { "m" + it.month }) { m ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(monthLabel(m.month), fontWeight = FontWeight.Medium)
                            Text(
                                "المطلوب ${money(m.expected)} • المدفوع ${money(m.paid)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val (txt, col) = when {
                            m.remaining <= EPS -> "مسدد" to MaterialTheme.colorScheme.primary
                            m.overdue -> "متأخر ${money(m.remaining)}" to MaterialTheme.colorScheme.error
                            else -> "مستحق لاحقاً" to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(txt, color = col, style = MaterialTheme.typography.labelMedium)
                        if (m.remaining > EPS && u.active) {
                            TextButton(onClick = { payMonth = m.month.toString() }) { Text("دفعة") }
                        }
                    }
                    HorizontalDivider()
                }
            }

            // سجل الدفعات
            item { Text("سجل الدفعات", style = MaterialTheme.typography.titleMedium) }
            if (s.payments.isEmpty()) {
                item { Text("لا توجد دفعات بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(s.payments, key = { "p" + it.id }) { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(money(p.amount), fontWeight = FontWeight.Medium)
                            val label = parseMonth(p.monthKey)?.let { monthLabel(it) } ?: p.monthKey
                            Text(
                                "عن $label • بتاريخ ${p.paidAt}" + if (p.note.isNotBlank()) " • ${p.note}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { paymentToDelete = p }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف الدفعة")
                        }
                    }
                    HorizontalDivider()
                }
            }

            if (u.notes.isNotBlank()) {
                item { SectionCard("ملاحظات") { Text(u.notes) } }
            }

            // إجراءات
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.setActive(u, !u.active) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (u.active) "إيقاف الوحدة (إنهاء التعاقد)" else "إعادة تفعيل الوحدة") }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("حذف الوحدة نهائياً", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    // ---- الحوارات ----
    val pm = payMonth
    if (pm != null) {
        PaymentDialog(
            s = s,
            initialMonth = parseMonth(pm) ?: defaultPayMonth,
            onDismiss = { payMonth = null },
            onConfirm = { month, amount, date, note ->
                vm.addPayment(
                    PaymentEntity(
                        unitId = u.id,
                        monthKey = month.toString(),
                        amount = amount,
                        paidAt = date,
                        note = note
                    )
                )
                payMonth = null
            }
        )
    }

    if (confirmIncrease) {
        val from = increaseEffectiveMonth(u, today)
        ConfirmDialog(
            title = "تطبيق الزيادة",
            text = "الإيجار هيتغيّر من ${money(u.rent)} إلى ${money(u.rentAfterIncrease())} " +
                "ويسري من ${monthLabel(from)}، وموعد الزيادة القادم هيتجدد بعد سنة.",
            confirmLabel = "تطبيق",
            onConfirm = { vm.applyIncrease(u); confirmIncrease = false },
            onDismiss = { confirmIncrease = false }
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "حذف الوحدة",
            text = "سيتم حذف الوحدة وكل دفعاتها نهائياً ولا يمكن التراجع. متأكد؟",
            confirmLabel = "حذف",
            danger = true,
            onConfirm = { confirmDelete = false; vm.deleteUnit(u.id) { onDeleted() } },
            onDismiss = { confirmDelete = false }
        )
    }

    val pd = paymentToDelete
    if (pd != null) {
        ConfirmDialog(
            title = "حذف الدفعة",
            text = "حذف دفعة ${money(pd.amount)}؟",
            confirmLabel = "حذف",
            danger = true,
            onConfirm = { vm.deletePayment(pd.id); paymentToDelete = null },
            onDismiss = { paymentToDelete = null }
        )
    }
}

@Composable
fun PaymentDialog(
    s: UnitSummary,
    initialMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth, Double, String, String) -> Unit
) {
    fun defaultAmount(m: YearMonth): String {
        val st = s.statuses.firstOrNull { it.month == m }
        val v = when {
            st == null -> s.unit.rent
            st.remaining > EPS -> st.remaining
            else -> st.expected
        }
        return fmtNum(round2(v))
    }

    var monthKey by rememberSaveable { mutableStateOf(initialMonth.toString()) }
    var amount by rememberSaveable { mutableStateOf(defaultAmount(initialMonth)) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val month = parseMonth(monthKey) ?: initialMonth

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل دفعة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الدفعة عن شهر:", style = MaterialTheme.typography.labelLarge)
                MonthStepper(month) { monthKey = it.toString(); amount = defaultAmount(it) }
                OutlinedTextField(
                    amount, { amount = it },
                    label = { Text("المبلغ (ج.م)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                DateField("تاريخ الدفع", date) { date = it }
                OutlinedTextField(
                    note, { note = it },
                    label = { Text("ملاحظة (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val e = error
                if (e != null) Text(e, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = parseNum(amount)
                if (a == null || a <= 0.0) {
                    error = "اكتب مبلغ صحيح"
                } else if (parseDate(date) == null) {
                    error = "اختر تاريخ الدفع"
                } else {
                    onConfirm(month, a, date, note.trim())
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
