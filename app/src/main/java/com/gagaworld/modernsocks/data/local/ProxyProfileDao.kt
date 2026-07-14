package com.gagaworld.modernsocks.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyProfileDao {
    @Query("SELECT * FROM proxy_profiles ORDER BY isSelected DESC, updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<ProxyProfileEntity>>

    @Query("SELECT * FROM proxy_profiles WHERE isSelected = 1 LIMIT 1")
    fun observeSelected(): Flow<ProxyProfileEntity?>

    @Query("SELECT * FROM proxy_profiles WHERE id = :id")
    suspend fun getById(id: Long): ProxyProfileEntity?

    @Query("SELECT * FROM proxy_profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelected(): ProxyProfileEntity?

    @Query("SELECT * FROM proxy_profiles ORDER BY updatedAt DESC, id DESC LIMIT 1")
    suspend fun getFirst(): ProxyProfileEntity?

    @Query("SELECT COUNT(*) FROM proxy_profiles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: ProxyProfileEntity): Long

    @Update
    suspend fun update(profile: ProxyProfileEntity): Int

    @Delete
    suspend fun delete(profile: ProxyProfileEntity)

    @Query("UPDATE proxy_profiles SET isSelected = 0 WHERE isSelected = 1")
    suspend fun clearSelection()

    @Query("UPDATE proxy_profiles SET isSelected = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun select(id: Long, updatedAt: Long): Int
}
