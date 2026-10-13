package name.me.voxhub.data.database

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(entities = [UsageRequestEntity::class], version = 1, exportSchema = true)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
}
