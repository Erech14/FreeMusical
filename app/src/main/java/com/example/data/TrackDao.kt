
package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title ASC")
    abstract fun getAllTracks(): Flow<List<Track>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTracks(tracks: List<Track>)

    @Query("DELETE FROM tracks")
    abstract suspend fun clearAll()

    @Query("DELETE FROM tracks WHERE uriString = :uriString")
    abstract suspend fun deleteByUri(uriString: String)

    @Transaction
    open suspend fun replaceAll(tracks: List<Track>) {
        clearAll()
        insertTracks(tracks)
    }
}
