package com.nut.kiosk.model

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import java.util.*

@Entity(tableName = "pages")
data class Page(
        @PrimaryKey var id: String,
        var version: Long,
        var path: String,
        var date: Date,
        var downloaded: Boolean
)

