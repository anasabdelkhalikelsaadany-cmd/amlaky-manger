package com.molaky.manager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val dao = db.dao()

    /** null = لسه بيتحمّل */
    val summaries: StateFlow<List<UnitSummary>?> = combine(
        dao.observeUnits(), dao.observePayments(), dao.observeRentChanges()
    ) { units, pays, changes ->
        val today = LocalDate.now()
        val payByUnit = pays.groupBy { it.unitId }
        val chgByUnit = changes.groupBy { it.unitId }
        units.map { summarize(it, chgByUnit[it.id].orEmpty(), payByUnit[it.id].orEmpty(), today) }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveUnit(unit: UnitEntity, previousRent: Double?, onDone: () -> Unit) {
        viewModelScope.launch {
            db.withTransaction {
                if (unit.id == 0L) {
                    val id = dao.insertUnit(unit)
                    dao.upsertRentChange(
                        RentChangeEntity(unitId = id, fromMonth = unit.trackFrom, amount = unit.rent)
                    )
                } else {
                    dao.updateUnit(unit)
                    if (previousRent != null && Math.abs(previousRent - unit.rent) > EPS) {
                        dao.upsertRentChange(
                            RentChangeEntity(
                                unitId = unit.id,
                                fromMonth = YearMonth.now().toString(),
                                amount = unit.rent
                            )
                        )
                    }
                }
            }
            onDone()
        }
    }

    fun deleteUnit(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            dao.deleteUnit(id)
            onDone()
        }
    }

    fun setActive(unit: UnitEntity, active: Boolean) {
        viewModelScope.launch { dao.updateUnit(unit.copy(active = active)) }
    }

    fun addPayment(p: PaymentEntity) {
        viewModelScope.launch { dao.insertPayment(p) }
    }

    fun deletePayment(id: Long) {
        viewModelScope.launch { dao.deletePayment(id) }
    }

    fun applyIncrease(unit: UnitEntity) {
        viewModelScope.launch {
            val today = LocalDate.now()
            val newRent = unit.rentAfterIncrease()
            val from = increaseEffectiveMonth(unit, today)
            val nextDate = parseDate(unit.increaseDate)?.plusYears(1)?.toString() ?: ""
            db.withTransaction {
                dao.upsertRentChange(
                    RentChangeEntity(unitId = unit.id, fromMonth = from.toString(), amount = newRent)
                )
                dao.updateUnit(unit.copy(rent = newRent, increaseDate = nextDate))
            }
        }
    }

    suspend fun exportJson(): String = Backup.export(db)

    suspend fun applyBackup(parsed: ParsedBackup) = Backup.apply(db, parsed)
}
