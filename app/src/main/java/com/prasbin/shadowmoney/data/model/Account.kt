package com.prasbin.shadowmoney.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val ACCOUNT_TYPE_WALLET = 0
const val ACCOUNT_TYPE_BANK = 1
const val ACCOUNT_TYPE_CASH = 2
const val ACCOUNT_TYPE_DIGITAL_WALLET = 3

const val CATEGORY_DIRECTION_INCOME = 0
const val CATEGORY_DIRECTION_OUTFLOW = 1
const val CATEGORY_DIRECTION_BOTH = 2

const val TRANSACTION_DIRECTION_INCOME = 0
const val TRANSACTION_DIRECTION_OUTFLOW = 1

const val TRANSACTION_SOURCE_IMPORT_FILE = "IMPORT_FILE"

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val type: Int = ACCOUNT_TYPE_WALLET,
    val openingBalanceMinor: Long = 0L,
    val isActive: Boolean = true,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories"
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val direction: Int = CATEGORY_DIRECTION_OUTFLOW,
    val isActive: Boolean = true,
    val isSystem: Boolean = false,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = WorkItem::class,
            parentColumns = ["id"],
            childColumns = ["workItemId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("accountId"),
        Index("categoryId"),
        Index("transactionTimestamp"),
        Index("workItemId")
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long = 0L,
    val categoryId: Long? = null,
    val workItemId: Long? = null,
    val amountMinor: Long = 0L,
    val direction: Int = TRANSACTION_DIRECTION_OUTFLOW,
    val transactionTimestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val createdTimestamp: Long = System.currentTimeMillis(),
    val source: String = "",
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val externalRef: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val statementId: Long? = null
)

@Entity(
    tableName = "goals",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("accountId")]
)
data class Goal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val targetAmountMinor: Long = 0L,
    val accountId: Long? = null,
    val deadlineTimestamp: Long = 0L,
    val isActive: Boolean = true,
    val isCompleted: Boolean = false,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val updatedTimestamp: Long = System.currentTimeMillis()
)
