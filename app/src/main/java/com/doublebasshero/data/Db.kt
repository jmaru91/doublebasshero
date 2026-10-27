package com.doublebasshero.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Single-row user profile and settings. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    val onboarded: Boolean = false,
    val trueBpm: Int = 100,
    val restDay1: Int = 3,
    val restDay2: Int = 6,
    val programStart: Long = 0,
    val cycleStart: Long = 0,
    val benchmarkUnlocked: Boolean = false,
    val prioritizeWeak: Boolean = true,
    /** Monday (epoch day) of the last week whose end was processed. */
    val lastProcessedWeek: Long = 0,
    /** Monday (epoch day) of the ended week whose review waits for the user, or -1. */
    val pendingReviewWeek: Long = -1,
    /** Reminder minute-of-day per weekday, comma separated, Monday first. */
    val reminderMinutes: String = "1080,1080,1080,1080,1080,1080,1080",
    val missedMinutes: Int = 1260,
    /** Disabled notification types, comma separated. */
    val disabledNotifications: String = "REST_DAY",
    val restCue: Boolean = true,
    val voicePrompts: Boolean = true,
    /** One-time message shown on Today (e.g. an interrupted session saved as partial). */
    val notice: String = "",
    /** Floor exercises only, for periods without pedals. */
    @ColumnInfo(defaultValue = "0") val noPedals: Boolean = false,
    /** Return date (epoch day) from which the mode is off by itself, or -1. */
    @ColumnInfo(defaultValue = "-1") val noPedalsUntil: Long = -1,
    /** RIGHT, LEFT or NONE: the weaker foot gets an extra Solo Leg pass in the warm-up. */
    @ColumnInfo(defaultValue = "NONE") val weakFoot: String = "NONE",
    /**
     * How saved start BPMs were computed: 1 = below the True BPM so the ramp ended
     * on it (before v0.3), 2 = starting at the True BPM. Rows from rule 1 are
     * shifted once on startup.
     */
    @ColumnInfo(defaultValue = "1") val startRule: Int = 2,
)

@Entity(tableName = "start_bpm", primaryKeys = ["exercise", "dayType"])
data class StartBpmEntity(val exercise: String, val dayType: String, val bpm: Int)

object SessionStatus {
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETE = "COMPLETE"
    const val PARTIAL = "PARTIAL"
}

@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch day. */
    val date: Long,
    val slot: String,
    val dayType: String,
    val level: Int,
    val deload: Boolean,
    val status: String,
    val startedAt: Long,
    val updatedAt: Long,
    /** Map<Exercise, List<SetResult>> as JSON. */
    val setsJson: String = "{}",
    /** RunnerState JSON while the session is in progress (crash-safe snapshot). */
    val snapshot: String? = null,
    val benchmarkBpm: Int? = null,
    val benchmarkPassed: Boolean? = null,
)

@Entity(tableName = "week_override")
data class WeekOverrideEntity(
    @PrimaryKey val weekStart: Long,
    /** Epoch days, comma separated. */
    val skipped: String = "",
    val unavailable: String = "",
    /** -1 undecided, 0 declined, 1 accepted. */
    val makeup: Int = -1,
)

@Entity(tableName = "level_event")
data class LevelEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val fromLevel: Int,
    val toLevel: Int,
    val trueBpm: Int,
)

@Dao
interface TrainingDao {
    @Query("SELECT * FROM profile WHERE id = 0")
    fun profile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 0")
    suspend fun profileNow(): ProfileEntity?

    @Upsert
    suspend fun saveProfile(p: ProfileEntity)

    @Query("SELECT * FROM start_bpm")
    fun startBpms(): Flow<List<StartBpmEntity>>

    @Query("SELECT * FROM start_bpm")
    suspend fun startBpmsNow(): List<StartBpmEntity>

    @Upsert
    suspend fun saveStartBpms(list: List<StartBpmEntity>)

    @Query("DELETE FROM start_bpm")
    suspend fun clearStartBpms()

    @Query("SELECT * FROM session WHERE status != 'IN_PROGRESS' ORDER BY date")
    fun sessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session WHERE status != 'IN_PROGRESS' ORDER BY date")
    suspend fun sessionsNow(): List<SessionEntity>

    @Query("SELECT * FROM session WHERE status = 'IN_PROGRESS' ORDER BY updatedAt DESC LIMIT 1")
    fun inProgress(): Flow<SessionEntity?>

    @Query("SELECT * FROM session WHERE status = 'IN_PROGRESS' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun inProgressNow(): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(s: SessionEntity): Long

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM session WHERE date = :date AND status != 'IN_PROGRESS'")
    suspend fun deleteSessionsOn(date: Long)

    @Query("SELECT * FROM week_override WHERE weekStart = :weekStart")
    fun override(weekStart: Long): Flow<WeekOverrideEntity?>

    @Query("SELECT * FROM week_override WHERE weekStart = :weekStart")
    suspend fun overrideNow(weekStart: Long): WeekOverrideEntity?

    @Upsert
    suspend fun saveOverride(o: WeekOverrideEntity)

    @Insert
    suspend fun addLevelEvent(e: LevelEventEntity)

    @Query("SELECT * FROM level_event ORDER BY date")
    fun levelEvents(): Flow<List<LevelEventEntity>>
}

@Database(
    entities = [ProfileEntity::class, StartBpmEntity::class, SessionEntity::class, WeekOverrideEntity::class, LevelEventEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class DbhDatabase : RoomDatabase() {
    abstract fun dao(): TrainingDao

    companion object {
        /** v2: no-pedals mode. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN noPedals INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE profile ADD COLUMN noPedalsUntil INTEGER NOT NULL DEFAULT -1")
            }
        }

        /** v3: weaker foot for the warm-up. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN weakFoot TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        /** v4: start BPM rule version (existing rows were computed with rule 1). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN startRule INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun create(context: Context): DbhDatabase =
            Room.databaseBuilder(context, DbhDatabase::class.java, "dbh.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
