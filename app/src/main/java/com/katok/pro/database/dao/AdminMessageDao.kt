package com.katok.pro.database.dao

import androidx.room.*
import com.katok.pro.model.admin.AdminMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface AdminMessageDao {
    @Query("SELECT * FROM admin_messages_local ORDER BY createdAt DESC")
    fun getAll(): Flow<List<AdminMessage>>

    @Query("SELECT COUNT(*) FROM admin_messages_local WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<AdminMessage>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: AdminMessage)

    @Update
    suspend fun update(message: AdminMessage)

    @Query("UPDATE admin_messages_local SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE admin_messages_local SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM admin_messages_local")
    suspend fun clear()

    @Query("DELETE FROM admin_messages_local WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM admin_messages_local")
    suspend fun deleteAll()

}