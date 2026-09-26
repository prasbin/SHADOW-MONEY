package com.prasbin.shadowmoney.data

import com.prasbin.shadowmoney.data.model.Category

object SeedCategories {
    fun getSystemCategories(): List<Category> {
        return listOf(
            Category(name = "Salary", direction = 0, isSystem = true),
            Category(name = "Freelance", direction = 0, isSystem = true),
            Category(name = "Investment Return", direction = 0, isSystem = true),
            Category(name = "Gift", direction = 0, isSystem = true),
            Category(name = "Other Income", direction = 0, isSystem = true),
            Category(name = "Food & Dining", direction = 1, isSystem = true),
            Category(name = "Transport", direction = 1, isSystem = true),
            Category(name = "Telecom", direction = 1, isSystem = true),
            Category(name = "Rent & Housing", direction = 1, isSystem = true),
            Category(name = "Utilities", direction = 1, isSystem = true),
            Category(name = "Entertainment", direction = 1, isSystem = true),
            Category(name = "Shopping", direction = 1, isSystem = true),
            Category(name = "Health", direction = 1, isSystem = true),
            Category(name = "Education", direction = 1, isSystem = true),
            Category(name = "Savings & Investment", direction = 1, isSystem = true),
            Category(name = "Debt Repayment", direction = 1, isSystem = true),
            Category(name = "Other Expense", direction = 1, isSystem = true)
        )
    }
}
