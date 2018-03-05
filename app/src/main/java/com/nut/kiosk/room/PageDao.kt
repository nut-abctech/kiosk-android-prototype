package com.nut.kiosk.room;

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy.REPLACE
import android.arch.persistence.room.Query
import android.arch.persistence.room.Update
import com.nut.kiosk.model.Page
import io.reactivex.Flowable
import io.reactivex.Single

@Dao
interface PageDao {

    @Insert(onConflict = REPLACE)
    fun insert(currencies: List<Page>)

    @Insert(onConflict = REPLACE)
    fun insert(currencies: Page)

    @Update(onConflict = REPLACE)
    fun update(page: Page)

    @Query("SELECT * FROM pages WHERE downloaded = 1")
    fun findAllStoredPage(): List<Page>

    @Query("SELECT * FROM pages WHERE id = :id")
    fun findById(id: String): Page

    @Query("SELECT * FROM pages WHERE version = :version")
    fun findByVersion(version: Long): Page

    @Query("SELECT * FROM pages ORDER BY date DESC LIMIT 1")
    fun findLatest(): Page
}