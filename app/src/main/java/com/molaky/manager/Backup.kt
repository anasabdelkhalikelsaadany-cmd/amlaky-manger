package com.molaky.manager

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.YearMonth

data class ParsedBackup(
    val units: List<UnitEntity>,
    val payments: List<PaymentEntity>,
    val changes: List<RentChangeEntity>
)

object Backup {

    suspend fun export(db: AppDatabase): String {
        val dao = db.dao()
        return db.withTransaction {
            val root = JSONObject()
            root.put("app", "molaky-manager")
            root.put("version", 2)
            root.put("exportedAt", LocalDateTime.now().toString())

            val units = JSONArray()
            for (u in dao.allUnits()) {
                units.put(
                    JSONObject()
                        .put("id", u.id)
                        .put("propertyType", u.propertyType)
                        .put("address", u.address)
                        .put("building", u.building)
                        .put("apartment", u.apartment)
                        .put("floor", u.floor)
                        .put("mapsUrl", u.mapsUrl)
                        .put("tenantName", u.tenantName)
                        .put("tenantPhone", u.tenantPhone)
                        .put("contractStart", u.contractStart)
                        .put("contractEnd", u.contractEnd)
                        .put("rent", u.rent)
                        .put("dueDay", u.dueDay)
                        .put("increaseType", u.increaseType)
                        .put("increaseValue", u.increaseValue)
                        .put("increaseDate", u.increaseDate)
                        .put("notes", u.notes)
                        .put("active", u.active)
                        .put("trackFrom", u.trackFrom)
                )
            }
            root.put("units", units)

            val pays = JSONArray()
            for (p in dao.allPayments()) {
                pays.put(
                    JSONObject()
                        .put("unitId", p.unitId)
                        .put("monthKey", p.monthKey)
                        .put("amount", p.amount)
                        .put("paidAt", p.paidAt)
                        .put("note", p.note)
                )
            }
            root.put("payments", pays)

            val changes = JSONArray()
            for (c in dao.allRentChanges()) {
                changes.put(
                    JSONObject()
                        .put("unitId", c.unitId)
                        .put("fromMonth", c.fromMonth)
                        .put("amount", c.amount)
                )
            }
            root.put("rentChanges", changes)
            root.toString(2)
        }
    }

    private fun dbl(o: JSONObject, key: String): Double =
        o.optDouble(key, 0.0).let { if (it.isFinite()) it else 0.0 }

    private fun jsonToUnit(o: JSONObject, currentMonth: String): UnitEntity = UnitEntity(
        id = o.optLong("id", 0),
        propertyType = o.optString("propertyType", "شقة"),
        address = o.optString("address"),
        building = o.optString("building"),
        apartment = o.optString("apartment"),
        floor = o.optString("floor"),
        mapsUrl = o.optString("mapsUrl"),
        tenantName = o.optString("tenantName"),
        tenantPhone = o.optString("tenantPhone"),
        contractStart = o.optString("contractStart"),
        contractEnd = o.optString("contractEnd"),
        rent = dbl(o, "rent"),
        dueDay = o.optInt("dueDay", 5).coerceIn(1, 28),
        increaseType = o.optString("increaseType", INC_PERCENT),
        increaseValue = dbl(o, "increaseValue"),
        increaseDate = o.optString("increaseDate"),
        notes = o.optString("notes"),
        active = o.optBoolean("active", true),
        trackFrom = o.optString("trackFrom").takeIf { parseMonth(it) != null } ?: currentMonth
    )

    /** يقرأ ويتحقق من الملف بالكامل بدون أي تغيير في قاعدة البيانات. يرمي استثناء لو الملف غير صالح. */
    fun parse(raw: String): ParsedBackup {
        val root = JSONObject(raw)
        val currentMonth = YearMonth.now().toString()

        val ua = root.getJSONArray("units")
        val units = (0 until ua.length()).map { jsonToUnit(ua.getJSONObject(it), currentMonth) }
        require(units.all { it.id > 0 }) { "ids" }
        require(units.map { it.id }.toSet().size == units.size) { "duplicate ids" }
        val ids = units.map { it.id }.toSet()

        val pa = root.optJSONArray("payments") ?: JSONArray()
        val payments = (0 until pa.length()).map {
            val p = pa.getJSONObject(it)
            PaymentEntity(
                unitId = p.getLong("unitId"),
                monthKey = p.getString("monthKey"),
                amount = p.getDouble("amount"),
                paidAt = p.getString("paidAt"),
                note = p.optString("note")
            )
        }.filter { it.unitId in ids && parseMonth(it.monthKey) != null && it.amount.isFinite() }

        val ca = root.optJSONArray("rentChanges")
        val changes = if (ca != null) {
            (0 until ca.length()).map {
                val c = ca.getJSONObject(it)
                RentChangeEntity(
                    unitId = c.getLong("unitId"),
                    fromMonth = c.getString("fromMonth"),
                    amount = c.getDouble("amount")
                )
            }.filter { it.unitId in ids && parseMonth(it.fromMonth) != null && it.amount.isFinite() }
        } else {
            units.map { RentChangeEntity(unitId = it.id, fromMonth = it.trackFrom, amount = it.rent) }
        }
        return ParsedBackup(units, payments, changes)
    }

    suspend fun apply(db: AppDatabase, b: ParsedBackup) {
        val dao = db.dao()
        db.withTransaction {
            dao.clearPayments()
            dao.clearRentChanges()
            dao.clearUnits()
            b.units.forEach { dao.insertUnit(it) }
            b.changes.forEach { dao.upsertRentChange(it.copy(id = 0)) }
            b.payments.forEach { dao.insertPayment(it.copy(id = 0)) }
        }
    }
}
