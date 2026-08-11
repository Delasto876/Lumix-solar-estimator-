package com.lumix.estimator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val customerName: String,
    val customerContact: String,
    val parish: String,
    val nearestTown: String,
    val propertyType: String,
    val quoteMode: String,
    val systemMode: String,
    val pvKw: Double,
    val panelCount: Int,
    val inverterName: String,
    val batteryKwh: Double,
    val grandTotal: Double,
    val inputsJson: String,
    val resultJson: String
)
