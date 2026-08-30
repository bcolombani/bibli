package fr.bcolombani.bibli.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import fr.bcolombani.bibli.core.metadata.BookSource
import fr.bcolombani.bibli.core.model.Book

/**
 * Un livre de la bibliothèque.
 *
 * `isbn13` porte un **index unique** : c'est lui qui garantit qu'un livre déjà scanné
 * ne peut pas être inséré deux fois (cas « coche bleue » de l'écran de scan).
 */
@Entity(
    tableName = "books",
    indices = [Index(value = ["isbn13"], unique = true)],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** ISBN normalisé : 13 chiffres, sans tiret. */
    @ColumnInfo(name = "isbn13")
    val isbn13: String,

    /** Contenu brut du code-barres tel que lu par le scanner. */
    @ColumnInfo(name = "rawScan")
    val rawScan: String,

    @ColumnInfo(name = "title")
    val title: String,

    /** Auteurs aplatis, joints par `", "`. */
    @ColumnInfo(name = "authors")
    val authors: String,

    /** Nom de la valeur de [BookSource]. */
    @ColumnInfo(name = "source")
    val source: String,

    /** Date d'ajout, en millisecondes epoch. */
    @ColumnInfo(name = "addedAt")
    val addedAt: Long,
)

fun BookEntity.toBook(): Book = Book(
    id = id,
    isbn13 = isbn13,
    rawScan = rawScan,
    title = title,
    authors = authors,
    source = BookSource.fromName(source),
    addedAt = addedAt,
)

fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    isbn13 = isbn13,
    rawScan = rawScan,
    title = title,
    authors = authors,
    source = source.name,
    addedAt = addedAt,
)
