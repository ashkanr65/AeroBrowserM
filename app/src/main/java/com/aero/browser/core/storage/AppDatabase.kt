package com.aero.browser.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aero.browser.core.security.DatabasePassphrase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Encrypted-at-rest Room database backed by SQLCipher.
 *
 * The passphrase is generated once on first launch, stored wrapped by an
 * Android Keystore (hardware-backed where available) key, and unwrapped in
 * memory only on each cold start — see [DatabasePassphrase]. Nothing is
 * hard-coded; the database is unreadable without the device Keystore.
 *
 * NOTE: `System.loadLibrary("sqlcipher")` must run before the first open. We
 * do it in [initialize] so both prod and instrumentation paths control it
 * explicitly rather than relying on companion-object init order.
 */
@Database(
    entities = [BookmarkEntity::class, HistoryEntity::class, SettingsEntity::class, TabEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun tabDao(): TabDao

    companion object {
        const val DATABASE_NAME = "aero.db"

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tabs` (`id` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `favicon` TEXT, `position` INTEGER NOT NULL, `isPrivate` INTEGER NOT NULL, `webViewState` BLOB, PRIMARY KEY(`id`))",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tabs_position` ON `tabs` (`position`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tabs_isPrivate` ON `tabs` (`isPrivate`)")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `favicon` TEXT")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        /** SQLCipher native library, loaded once per process before Room opens the file. */
        @Volatile private var libraryLoaded = false

        fun initialize(context: Context): AppDatabase {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }

                if (!libraryLoaded) {
                    System.loadLibrary("sqlcipher")
                    libraryLoaded = true
                }

                val passphrase = DatabasePassphrase.getOrCreate(context.applicationContext)
                val database = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                )
                    .openHelperFactory(SupportOpenHelperFactory(passphrase))
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()

                // Room opens the encrypted file lazily on the first DAO operation.
                instance = database
                return database
            }
        }

        @JvmStatic fun get(context: Context): AppDatabase = initialize(context)
    }
}
