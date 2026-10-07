// Test-only annotation signatures; these do NOT emulate SQLite transactions.
package androidx.room
annotation class Dao
annotation class Entity(val tableName: String)
annotation class PrimaryKey(val autoGenerate: Boolean = false)
annotation class Query(val value: String)
annotation class Insert(val onConflict: Int)
annotation class Update
annotation class Transaction
object OnConflictStrategy { const val REPLACE = 1 }
