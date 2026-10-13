package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.example.compiler.data.BuildDao
import com.example.compiler.data.BuildRecordEntity
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "installed_snapshots")
data class InstalledSnapshotEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val originLabel: String,
    val installerPackageName: String?,
    val lastSeenTimestamp: Long
)

@Entity(tableName = "uninstalled_apps")
data class UninstalledAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val originLabel: String,
    val installerPackageName: String?,
    val uninstalledTimestamp: Long,
    val uninstalledReason: String,
    val isRestorableViaShizuku: Boolean
)

@Dao
interface AppDao {
    // Snapshots
    @Query("SELECT * FROM installed_snapshots")
    suspend fun getAllSnapshots(): List<InstalledSnapshotEntity>

    @Query("SELECT * FROM installed_snapshots WHERE packageName = :packageName LIMIT 1")
    suspend fun getSnapshot(packageName: String): InstalledSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(snapshots: List<InstalledSnapshotEntity>)

    @Query("DELETE FROM installed_snapshots WHERE packageName = :packageName")
    suspend fun deleteSnapshot(packageName: String)

    @Query("DELETE FROM installed_snapshots")
    suspend fun clearSnapshots()

    // Uninstalled apps (Lixeira)
    @Query("SELECT * FROM uninstalled_apps ORDER BY uninstalledTimestamp DESC")
    fun getAllUninstalledAppsFlow(): Flow<List<UninstalledAppEntity>>

    @Query("SELECT * FROM uninstalled_apps ORDER BY uninstalledTimestamp DESC")
    suspend fun getAllUninstalledApps(): List<UninstalledAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUninstalledApp(app: UninstalledAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUninstalledApps(apps: List<UninstalledAppEntity>)

    @Query("DELETE FROM uninstalled_apps WHERE packageName = :packageName")
    suspend fun deleteUninstalledApp(packageName: String)

    @Query("DELETE FROM uninstalled_apps WHERE packageName IN (:packageNames)")
    suspend fun deleteUninstalledApps(packageNames: List<String>)

    @Query("DELETE FROM uninstalled_apps")
    suspend fun clearUninstalledApps()

    /**
     * Execução atômica da sincronização completa de snapshots e lixeira.
     * Garante que caso o processo seja interrompido pelo sistema, não ocorram dados corrompidos.
     */
    @Transaction
    suspend fun syncSnapshotsAndUninstalls(
        newSnapshots: List<InstalledSnapshotEntity>,
        externalUninstalls: List<UninstalledAppEntity>,
        reinstalledPackageNames: List<String>
    ) {
        if (externalUninstalls.isNotEmpty()) {
            insertUninstalledApps(externalUninstalls)
        }
        if (reinstalledPackageNames.isNotEmpty()) {
            deleteUninstalledApps(reinstalledPackageNames)
        }
        clearSnapshots()
        insertSnapshots(newSnapshots)
    }
}

@Database(
    entities = [InstalledSnapshotEntity::class, UninstalledAppEntity::class, BuildRecordEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun buildDao(): BuildDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "uninstallx_database.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
