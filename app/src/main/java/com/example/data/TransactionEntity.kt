package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Double,
    val category: String, // e.g. "Food", "Entertainment", "Utilities", "Salary", "Transport", "Shopping", "Others"
    val timestamp: Long,  // Epoch milliseconds
    val isExpense: Boolean,
    val note: String = ""
)
