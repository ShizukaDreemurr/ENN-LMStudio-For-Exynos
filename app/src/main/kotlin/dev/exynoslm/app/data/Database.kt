package dev.exynoslm.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Message content is stored encrypted (AES-GCM, Android Keystore). See [Crypto]. */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val contentEnc: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "agent_runs")
data class AgentRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalEnc: String,
    val traceJsonEnc: String,
    val stopReason: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "benchmarks")
data class BenchmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modelId: String,
    val backend: String,
    val loadMs: Long,
    val ttftMs: Long,
    val prefillTokPerSec: Double,
    val decodeTokPerSec: Double,
    val peakPssMb: Int,
    val batteryDropPct: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :cid ORDER BY id") suspend fun messages(cid: Long): List<ChatMessageEntity>
    @Insert suspend fun insert(m: ChatMessageEntity): Long
    @Query("DELETE FROM chat_messages WHERE conversationId = :cid") suspend fun clear(cid: Long)
}

@Dao
interface AgentRunDao {
    @Insert suspend fun insert(r: AgentRunEntity): Long
    @Query("SELECT * FROM agent_runs ORDER BY id DESC LIMIT 50") fun recent(): Flow<List<AgentRunEntity>>
}

@Dao
interface BenchmarkDao {
    @Insert suspend fun insert(b: BenchmarkEntity): Long
    @Query("SELECT * FROM benchmarks ORDER BY id DESC") fun all(): Flow<List<BenchmarkEntity>>
}

@Database(entities = [ChatMessageEntity::class, AgentRunEntity::class, BenchmarkEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chat(): ChatDao
    abstract fun agentRuns(): AgentRunDao
    abstract fun benchmarks(): BenchmarkDao
}
