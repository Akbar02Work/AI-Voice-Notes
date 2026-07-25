package com.example.voicenotes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO интерфейс для работы с заметками в Room базе данных.
 */
@Dao
interface NoteDao {
    
    /**
     * Добавить новую заметку (suspend - тяжелая операция).
     */
    /**
     * Добавить новую заметку. Возвращает ID вставленной записи.
     */
    @Insert
    suspend fun insertNote(note: NoteEntity): Long
    
    /**
     * Получить все заметки: закреплённые сверху, затем по дате (новые первыми).
     */
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>
    
    /**
     * Получить заметку по ID.
     */
    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Long): NoteEntity?

    /**
     * Наблюдать за заметкой по ID (обновления статуса/текста после AI).
     */
    @Query("SELECT * FROM notes WHERE id = :noteId")
    fun observeNoteById(noteId: Long): Flow<NoteEntity?>

    /**
     * Заметки в указанном статусе (например DRAFT для auto-retry).
     */
    @Query("SELECT * FROM notes WHERE status = :status ORDER BY timestamp ASC")
    suspend fun getNotesByStatus(status: NoteStatus): List<NoteEntity>
    
    /**
     * Удалить заметку по ID.
     */
    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: Long)
    
    /**
     * Обновить заголовок заметки.
     */
    @Query("UPDATE notes SET title = :newTitle WHERE id = :noteId")
    suspend fun updateNoteTitle(noteId: Long, newTitle: String)

    /**
     * Обновить статус заметки.
     */
    @Query("UPDATE notes SET status = :status WHERE id = :noteId")
    suspend fun updateStatus(noteId: Long, status: NoteStatus)

    @Query("UPDATE notes SET isPinned = :isPinned WHERE id = :noteId")
    suspend fun updatePinned(noteId: Long, isPinned: Boolean)

    /**
     * Обновить содержимое заметки (заголовок, текст, саммари, статус).
     */
    @androidx.room.Update
    suspend fun updateNote(note: NoteEntity)
}
