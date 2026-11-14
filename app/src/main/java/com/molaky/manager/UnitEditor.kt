package com.molaky.manager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitEditorScreen(
    unit: UnitEntity?,
    onBack: () -> Unit,
    onSave: (UnitEntity, Double?) -> Unit
) {
    val base = unit ?: UnitEntity()
    var type by rememberSaveable { mutableStateOf(base.propertyType) }
    var address by rememberSaveable { mutableStateOf(base.address) }
    var building by rememberSaveable { mutableStateOf(base.building) }
    var apartment by rememberSaveable { mutableStateOf(base.apartment) }
    var floor by rememberSaveable { mutableStateOf(base.floor) }
    var mapsUrl by rememberSaveable { mutableStateOf(base.mapsUrl) }
    var tenantName by rememberSaveable { mutableStateOf(base.tenantName) }
    var tenantPhone by rememberSaveable { mutableStateOf(base.tenantPhone) }
    var contractStart by rememberSaveable { mutableStateOf(base.contractStart) }
    var contractEnd by rememberSaveable { mutableStateOf(base.contractEnd) }
    var rent by rememberSaveable { mutableStateOf(fmtNum(base.rent)) }
    var dueDay by rememberSaveable { mutableStateOf(base.dueDay.toString()) }
    var track by rememberSaveable {
        mutableStateOf(base.trackFrom.ifBlank { YearMonth.now().toString() })
    }
    var incType by rememberSaveable { mutableStateOf(base.increaseType) }
    var incValue by rememberSaveable { mutableStateOf(fmtNum(base.increaseValue)) }
    var incDate by rememberSaveable { mutableStateOf(base.increaseDate) }
    var notes by rememberSaveable { mutableStateOf(base.notes) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        val rentVal = if (rent.isBlank()) 0.0 else parseNum(rent)
        val incVal = if (incValue.isBlank()) 0.0 else parseNum(incValue)
        val day = normalizeNumber(dueDay).toIntOrNull()
        val start = parseDate(contractStart)
        val end = parseDate(contractEnd)
        when {
            address.isBlank() && apartment.isBlank() -> error = "اكتب العنوان أو رقم الوحدة"
            rentVal == null -> error = "الإيجار لازم يكون رقم صحيح"
            day == null || day !in 1..28 -> error = "يوم الاستحقاق لازم يكون من 1 إلى 28"
            incVal == null -> error = "قيمة الزيادة لازم تكون رقم صحيح"
            start != null && end != null && end.isBefore(start) -> error = "تاريخ نهاية العقد قبل بدايته"
            else -> {
                error = null
                onSave(
                    base.copy(
                        propertyType = type,
                        address = address.trim(),
                        building = building.trim(),
                        apartment = apartment.trim(),
                        floor = floor.trim(),
                        mapsUrl = mapsUrl.trim(),
                        tenantName = tenantName.trim(),
                        tenantPhone = tenantPhone.trim(),
                        contractStart = contractStart,
                        contractEnd = contractEnd,
                        rent = rentVal,
                        dueDay = day,
                        increaseType = incType,
                        increaseValue = incVal,
                        increaseDate = incDate,
                        notes = notes.trim(),
                        trackFrom = track
                    ),
                    unit?.rent
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (unit == null) "إضافة وحدة" else "تعديل الوحدة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    val e = error
                    if (e != null) {
                        Text(
                            e,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("حفظ") }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("الوحدة", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            item { TypeDropdown(type, "نوع الوحدة") { type = it } }
            item {
                OutlinedTextField(
                    address, { address = it }, label = { Text("العنوان") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        building, { building = it }, label = { Text("العقار") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        apartment, { apartment = it }, label = { Text("رقم الوحدة") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        floor, { floor = it }, label = { Text("الدور") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                OutlinedTextField(
                    mapsUrl, { mapsUrl = it }, label = { Text("رابط الموقع على الخريطة (اختياري)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Text("المستأجر", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            item {
                OutlinedTextField(
                    tenantName, { tenantName = it }, label = { Text("اسم المستأجر") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    tenantPhone, { tenantPhone = it }, label = { Text("رقم الهاتف") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Text("العقد والإيجار", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            item { DateField("بداية العقد", contractStart) { contractStart = it } }
            item { DateField("نهاية العقد", contractEnd) { contractEnd = it } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        rent, { rent = it }, label = { Text("الإيجار الشهري (ج.م)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        dueDay, { dueDay = it }, label = { Text("يوم الاستحقاق") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("بداية تتبع الإيجار", style = MaterialTheme.typography.labelLarge)
                    MonthStepper(parseMonth(track) ?: YearMonth.now()) { track = it.toString() }
                    Text(
                        "الشهور اللي قبل هذا الشهر مش هتتحسب كمتأخرات.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Text("الزيادة السنوية (اختياري)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = incType == INC_PERCENT,
                        onClick = { incType = INC_PERCENT },
                        label = { Text(INC_PERCENT) }
                    )
                    FilterChip(
                        selected = incType == INC_FIXED,
                        onClick = { incType = INC_FIXED },
                        label = { Text(INC_FIXED) }
                    )
                }
            }
            item {
                OutlinedTextField(
                    incValue, { incValue = it },
                    label = { Text(if (incType == INC_FIXED) "قيمة الزيادة (ج.م)" else "نسبة الزيادة (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { DateField("موعد الزيادة القادم", incDate) { incDate = it } }

            item {
                OutlinedTextField(
                    notes, { notes = it }, label = { Text("ملاحظات") },
                    minLines = 2, modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
