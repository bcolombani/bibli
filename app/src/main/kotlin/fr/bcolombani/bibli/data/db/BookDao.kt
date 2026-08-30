package fr.bcolombani.bibli.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    /** Flux de toute la bibliothèque : la liste de l'UI se met à jour seule après chaque scan. */
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT COUNT(*) FROM books")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM books WHERE isbn13 = :isbn13 LIMIT 1")
    suspend fun findByIsbn(isbn13: String): BookEntity?

    /**
     * [OnConflictStrategy.IGNORE] + index unique : une insertion en doublon renvoie `-1`
     * au lieu d'écraser la fiche existante.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    suspend fun snapshot(): List<BookEntity>
}
