package fr.bcolombani.bibli.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base locale de la bibliothèque.
 *
 * `exportSchema = true` et les schémas commités dans `app/schemas/` : le jour où le modèle
 * gagnera une couverture, un éditeur, une année ou des tags, la migration versionnée sera
 * écrite à partir de ces fichiers. Aucune migration n'est nécessaire en v1.
 */
@Database(
    entities = [BookEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BibliDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        private const val NAME = "bibli.db"

        fun build(context: Context): BibliDatabase =
            Room.databaseBuilder(context.applicationContext, BibliDatabase::class.java, NAME)
                .build()
    }
}
