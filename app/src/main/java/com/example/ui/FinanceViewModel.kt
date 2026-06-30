package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.data.api.GenerateContentRequest
import com.example.data.api.Content
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = FinanceRepository(db.financeDao())

    // All transactions ordered by timestamp desc
    val transactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // All budgets
    val budgets: StateFlow<List<BudgetEntity>> = repository.allBudgets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // AI Insight states
    private val _aiInsight = MutableStateFlow<String?>(null)
    val aiInsight: StateFlow<String?> = _aiInsight.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    // Add prepopulated sample data on first launch if empty
    init {
        viewModelScope.launch {
            transactions.firstOrNull()?.let { list ->
                if (list.isEmpty()) {
                    prepopulateSampleData()
                }
            }
        }
    }

    private suspend fun prepopulateSampleData() {
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L

        val samples = listOf(
            TransactionEntity(title = "Monthly Salary", amount = 3500.00, category = "Salary", timestamp = now - 5 * oneDay, isExpense = false, note = "Tech Corp Payroll"),
            TransactionEntity(title = "Whole Foods", amount = 84.50, category = "Food", timestamp = now - 1 * oneDay, isExpense = true, note = "Weekly groceries"),
            TransactionEntity(title = "Gas Station", amount = 45.00, category = "Transport", timestamp = now - 2 * oneDay, isExpense = true, note = "Car refueling"),
            TransactionEntity(title = "Netflix", amount = 15.49, category = "Bills", timestamp = now - 4 * oneDay, isExpense = true, note = "Monthly subscription"),
            TransactionEntity(title = "Coffee Shop", amount = 6.75, category = "Food", timestamp = now, isExpense = true, note = "Latte & Croissant"),
            TransactionEntity(title = "Movie Night", amount = 28.00, category = "Entertainment", timestamp = now, isExpense = true, note = "Cinema tickets")
        )

        for (sample in samples) {
            repository.insertTransaction(sample)
        }

        val sampleBudgets = listOf(
            BudgetEntity(category = "Food", limitAmount = 400.00),
            BudgetEntity(category = "Transport", limitAmount = 150.00),
            BudgetEntity(category = "Entertainment", limitAmount = 200.00),
            BudgetEntity(category = "Bills", limitAmount = 500.00),
            BudgetEntity(category = "Shopping", limitAmount = 300.00)
        )

        for (budget in sampleBudgets) {
            repository.insertBudget(budget)
        }
    }

    fun addTransaction(title: String, amount: Double, category: String, isExpense: Boolean, note: String, timestamp: Long) {
        viewModelScope.launch {
            repository.insertTransaction(
                TransactionEntity(
                    title = title,
                    amount = amount,
                    category = category,
                    isExpense = isExpense,
                    note = note,
                    timestamp = timestamp
                )
            )
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun setBudget(category: String, limitAmount: Double) {
        viewModelScope.launch {
            repository.insertBudget(BudgetEntity(category, limitAmount))
        }
    }

    fun deleteBudget(budget: BudgetEntity) {
        viewModelScope.launch {
            repository.deleteBudget(budget)
        }
    }

    // Helper functions for summaries
    fun getStartOfToday(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getStartOfMonth(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    // AI Insight Generator
    fun fetchFinancialInsights(userQuery: String = "Please analyze my spending habits.") {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiError.value = null
            _aiInsight.value = null

            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                _aiInsight.value = "⚠️ **API Key is Missing or Placeheld**\n\nTo get personalized smart financial insights, please add your real Google Gemini API Key in the **Secrets Panel** (top-right menu in Google AI Studio) with the key name `GEMINI_API_KEY`.\n\n*Once added, the Money Manager will provide expert expense analysis and saving recommendations based on your transactions!*"
                _isAiLoading.value = false
                return@launch
            }

            // Compile information
            val currentTransactions = transactions.value
            val currentBudgets = budgets.value

            val transactionsStr = currentTransactions.joinToString("\n") { tx ->
                val type = if (tx.isExpense) "Expense" else "Income"
                "- [${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(tx.timestamp)}] ${tx.title}: $${String.format("%.2f", tx.amount)} (${tx.category}) - $type"
            }

            val budgetsStr = currentBudgets.joinToString("\n") { bg ->
                "- ${bg.category}: Budget Limit $${String.format("%.2f", bg.limitAmount)}"
            }

            val prompt = """
                You are "WalletWise", an expert personal finance coach and AI budget optimizer.
                Analyze the user's spending habits below and provide helpful, extremely specific, and actionable advice to help them save money.
                
                MONTHLY BUDGET LIMITS:
                $budgetsStr
                
                RECENT TRANSACTIONS:
                $transactionsStr
                
                USER'S QUERY/FOCUS:
                "$userQuery"
                
                INSTRUCTIONS FOR RESPONSE:
                1. Point out any specific categories where they spent the most.
                2. Explicitly reference any categories that are near or exceed their budget limit.
                3. Offer 3 direct, practical, and non-generic tips (e.g. "You spent ${'$'}XX on eating out. Cooking on weekends can save you ${'$'}50 next month").
                4. Keep the tone friendly, supportive, yet highly professional and analytical.
                5. Format with beautiful Markdown headings and clean bullet points. Keep it brief and focused.
            """.trimIndent()

            try {
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    systemInstruction = Content(parts = listOf(Part(text = "You are WalletWise, a helpful and smart personal finance assistant. Format your response beautifully in Markdown.")))
                )
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (textResponse != null) {
                    _aiInsight.value = textResponse
                } else {
                    _aiError.value = "Failed to receive a response from WalletWise."
                }
            } catch (e: Exception) {
                _aiError.value = "Error calling WalletWise: ${e.localizedMessage}"
            } finally {
                _isAiLoading.value = false
            }
        }
    }
}
