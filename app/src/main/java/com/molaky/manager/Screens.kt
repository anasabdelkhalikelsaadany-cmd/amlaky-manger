package com.molaky.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

// ======================= الرئيسية =======================

@Composable
fun DashboardTab(
    list: List<UnitSummary>,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (list.isEmpty()) {
        Column(
            modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("لسه مفيش وحدات", style = MaterialTheme.typography.titleLarge)
            Text(
                "ضيف أول وحدة (شقة أو محل...) وسجّل المستأجر والإيجار، وأنا هتابع المتأخرات وأفكّرك.",
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onAdd) { Text("إضافة وحدة") }
        }
        return
    }

    val today = LocalDate.now()
    val active = list.filter { it.unit.active }
    val overdueUnits = active.filter { it.state == UnitState.OVERDUE }
    val overdueTotal = overdueUnits.sumOf { it.overdueTotal }
    val expected = active.sumOf { it.thisMonth?.expected ?: 0.0 }
    val collected = active.sumOf { it.thisMonth?.paid ?: 0.0 }
    val remaining = active.sumOf { it.thisMonth?.remaining ?: 0.0 }
    val events = upcomingEvents(list, today)

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val bad = overdueTotal > EPS
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (bad) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (bad) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("إجمالي المتأخرات", style = MaterialTheme.typography.labelLarge)
                    Text(money(overdueTotal), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (bad) "${overdueUnits.size} وحدة متأخرة" else "لا توجد متأخرات",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("المتوقع هذا الشهر", money(expected), Modifier.weight(1f))
                StatCard("المحصّل هذا الشهر", money(collected), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("المتبقي هذا الشهر", money(remaining), Modifier.weight(1f))
                StatCard("الوحدات النشطة", "${active.size} من ${list.size}", Modifier.weight(1f))
            }
        }
        if (overdueUnits.isNotEmpty()) {
            item { Text("المتأخرات", style = MaterialTheme.typography.titleMedium) }
            items(overdueUnits, key = { "o" + it.unit.id }) { s -> UnitCard(s) { onOpen(s.unit.id) } }
        }
        if (events.isNotEmpty()) {
            item { Text("قريباً", style = MaterialTheme.typography.titleMedium) }
            items(events) { e ->
                Card(onClick = { onOpen(e.unitId) }, modifier = Modifier.fillMaxWidth()) {
                    Text(e.text, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

// ======================= الوحدات =======================

private fun matches(u: UnitEntity, q: String): Boolean {
    val query = q.trim()
    if (query.isEmpty()) return true
    return listOf(u.tenantName, u.address, u.apartment, u.building, u.tenantPhone, u.propertyType)
        .any { it.contains(query, ignoreCase = true) }
}

@Composable
fun UnitsTab(list: List<UnitSummary>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(-1) }
    val states = listOf(UnitState.OVERDUE, UnitState.PENDING, UnitState.PAID, UnitState.INACTIVE)
    val filtered = list.filter { s ->
        (filter < 0 || s.state.ordinal == filter) && matches(s.unit, query)
    }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("بحث بالاسم أو العنوان أو الهاتف") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = filter == -1,
                    onClick = { filter = -1 },
                    label = { Text("الكل (${list.size})") }
                )
            }
            items(states) { st ->
                FilterChip(
                    selected = filter == st.ordinal,
                    onClick = { filter = st.ordinal },
                    label = { Text("${stateLabel(st)} (${list.count { it.state == st }})") }
                )
            }
        }
        if (filtered.isEmpty()) {
            Text(
                "مفيش نتائج",
                modifier = Modifier.padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.unit.id }) { s -> UnitCard(s) { onOpen(s.unit.id) } }
            }
        }
    }
}

@Composable
fun UnitCard(s: UnitSummary, onClick: () -> Unit) {
    val u = s.unit
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    u.title(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                StatusBadge(s.state)
            }
            if (u.tenantName.isNotBlank()) Text("المستأجر: ${u.tenantName}")
            if (u.address.isNotBlank()) {
                Text(
                    u.address,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الإيجار: ${money(u.rent)}")
                if (u.active && s.overdueTotal > EPS) {
                    Text("متأخر ${money(s.overdueTotal)}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ======================= الإعدادات =======================

@Composable
fun SettingsTab(vm: MainViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("molaky_prefs", Context.MODE_PRIVATE) }
    var lastBackup by remember { mutableStateOf(prefs.getString("last_backup", null)) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ParsedBackup?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = runCatching {
                    val json = vm.exportJson()
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openOutputStream(uri, "wt")!!.use {
                            it.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }
                }.isSuccess
                if (ok) {
                    val stamp = LocalDateTime.now().toString().take(16).replace('T', ' ')
                    prefs.edit().putString("last_backup", stamp).apply()
                    lastBackup = stamp
                    message = "تم حفظ النسخة الاحتياطية"
                } else {
                    message = "فشل حفظ النسخة الاحتياطية"
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val raw = withContext(Dispatchers.IO) {
                        ctx.contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
                    }
                    withContext(Dispatchers.Default) { Backup.parse(raw) }
                }
                val parsed = result.getOrNull()
                if (parsed != null) pending = parsed
                else message = "الملف غير صالح — لم يتم تغيير أي بيانات"
            }
        }
    }

    Column(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("النسخ الاحتياطي") {
            Text(
                "بياناتك محفوظة على الجهاز فقط. خد نسخة احتياطية بشكل دوري واحفظها في Drive أو أرسلها لنفسك، " +
                    "عشان لو الموبايل ضاع أو اتمسح التطبيق ترجّع كل حاجة.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "آخر نسخة: ${lastBackup ?: "لسه ما اتعملتش"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { exportLauncher.launch("molaky-backup-${LocalDate.now()}.json") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("حفظ نسخة احتياطية") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("استرجاع من نسخة احتياطية") }
        }

        SectionCard("التنبيهات") {
            Text(
                "التطبيق بيبعتلك تنبيه يومي الساعة 9 صباحاً بالمتأخرات (كل 3 أيام لكل وحدة) " +
                    "وبالعقود والزيادات القريبة. لو التنبيهات مش بتوصل، اسمح للتطبيق بالإشعارات " +
                    "واستثنيه من توفير البطارية.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = {
                    val denied = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    if (denied) {
                        message = "إذن الإشعارات مش مفعّل — فعّله من إعدادات الجهاز ثم التطبيقات"
                    } else {
                        showNotification(ctx, listOf("هذا تنبيه تجريبي — التنبيهات شغّالة"))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("تجربة تنبيه الآن") }
        }

        val msg = message
        if (msg != null) {
            Text(msg, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "مدير أملاكي — الإصدار 1.0.0",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val p = pending
    if (p != null) {
        ConfirmDialog(
            title = "استرجاع البيانات",
            text = "الملف فيه ${p.units.size} وحدة و ${p.payments.size} دفعة. " +
                "سيتم استبدال كل البيانات الحالية بها. متابعة؟",
            confirmLabel = "استرجاع",
            danger = true,
            onConfirm = {
                pending = null
                scope.launch {
                    val ok = runCatching { vm.applyBackup(p) }.isSuccess
                    message = if (ok) "تم استرجاع البيانات" else "فشل الاسترجاع — لم يتغير شيء"
                }
            },
            onDismiss = { pending = null }
        )
    }
}
