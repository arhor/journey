package com.github.arhor.journey.data.local.db

import androidx.room.TypeConverter
import java.time.Instant

class InstantTypeConverter {

    @TypeConverter
    fun fromEpochMillis(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun intoEpochMillis(value: Instant?): Long? = value?.toEpochMilli()
}
