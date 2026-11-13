package com.example.data

import androidx.room.TypeConverter
import com.example.model.BarcodeFormatType
import com.example.model.CardCategory

class Converters {
    @TypeConverter
    fun fromCategory(category: CardCategory): String = category.name

    @TypeConverter
    fun toCategory(value: String): CardCategory =
        runCatching { CardCategory.valueOf(value) }.getOrDefault(CardCategory.AUTRE)

    @TypeConverter
    fun fromBarcodeFormat(format: BarcodeFormatType): String = format.name

    @TypeConverter
    fun toBarcodeFormat(value: String): BarcodeFormatType =
        runCatching { BarcodeFormatType.valueOf(value) }.getOrDefault(BarcodeFormatType.CODE_128)
}
