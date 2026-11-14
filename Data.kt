package com.molaky.manager

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

const val INC_PERCENT = "نسبة مئوية"
const val INC_FIXED = "مبلغ ثابت"

@Entity(tableName = "units")
data class UnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val propertyType: String = "شقة",
    val address: String = "",
    val building: String = "",
    val apartment: String = "",
    val floor: String = "",
    val mapsUrl: String = "",
    val tenantName: String = "",
    val tenantPhone: String = "",
    val contractStart: String = "",
    val contractEnd: String = "",
    val rent: Double = 0.0,
    val dueDay: Int = 5,
    val increaseType: String = INC_PERCENT,
    val increaseValue: Double = 0.0,
    val increaseDate: String = "",
    val notes: String = "",
    val active: Boolean = true,
    /** أول شهر يتم تتبع الإيجار منه بصيغة YYYY-MM */
    val trackFrom: String = ""
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["unitId", "monthKey"])]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val unitId: Long,
    /** الشهر الذي تغطيه الدفعة YYYY-MM */
    val monthKey: String,
    val amount: Double,
    /** تاريخ الدفع YYYY-MM-DD */
    val paidAt: String,
    val note: String = ""
)

/** تغيّر الإيجار من شهر معين (يسمح بحساب المتأخرات بدقة بعد الزيادة) */
@Entity(
    tableName = "rent_changes",
    foreignKeys = [
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["unitId", "fromMonth"], unique = true)]
)
data class RentChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val unitId: Long,
    val fromMonth: String,
    val amount: Double
)

@Dao
interface PropertyDao {
    @Query("SELECT * FROM units ORDER BY id DESC")
    fun observeUnits(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units ORDER BY id DESC")
    suspend fun allUnits(): List<UnitEntity>

    @Insert
    suspend fun insertUnit(unit: UnitEntity): Long

    @Update
    suspend fun updateUnit(unit: UnitEntity)

    @Query("DELETE FROM units WHERE id = :id")
    suspend fun deleteUnit(id: Long)

    @Query("SELECT * FROM payments ORDER BY paidAt DESC, id DESC")
    fun observePayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments")
    suspend fun allPayments(): List<PaymentEntity>

    @Insert
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deletePayment(id: Long)

    @Query("SELECT * FROM rent_changes")
    fun observeRentChanges(): Flow<List<RentChangeEntity>>

    @Query("SELECT * FROM rent_changes")
    suspend fun allRentChanges(): List<RentChangeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRentChange(change: RentChangeEntity)

    @Query("DELETE FROM payments")
    suspend fun clearPayments()

    @Query("DELETE FROM rent_changes")
    suspend fun clearRentChanges()

    @Query("DELETE FROM units")
    suspend fun clearUnits()
}

@Database(
    entities = [UnitEntity::class, PaymentEntity::class, RentChangeEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PropertyDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "molaky_manager.db"
                ).build().also { instance = it }
            }
    }
}
