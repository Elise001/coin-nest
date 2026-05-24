package com.example.coin_nest.data

import android.content.Context
import com.example.coin_nest.data.db.CategoryBudgetEntity
import com.example.coin_nest.data.db.CoinNestDbHelper
import com.example.coin_nest.data.db.MonthlyBudgetEntity
import com.example.coin_nest.data.db.SmartCategoryRuleEntity
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.data.model.BalanceSummary
import com.example.coin_nest.data.model.CategoryItem
import com.example.coin_nest.data.model.TransactionInput
import com.example.coin_nest.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class AutoTransactionInsertResult(
    val insertedId: Long?,
    val reason: String,
    val shouldNotify: Boolean
)

data class ExpenseBucketRow(
    val bucket: Int,
    val amountCents: Long
)

data class CategoryExpenseRow(
    val category: String,
    val amountCents: Long
)

class CoinNestRepository(context: Context) {
    private val dbHelper = CoinNestDbHelper(context.applicationContext)
    private val changeTick = MutableStateFlow(0L)
    private val queries = CoinNestQueryStore(dbHelper)
    private val smartCategoryRules = SmartCategoryRuleStore(dbHelper)
    private val autoBookDuplicates = AutoBookDuplicateStore(dbHelper)
    private val defaultCategories = DefaultCategoryStore(dbHelper) { notifyChanged() }
    private val transactionWrites = TransactionWriteStore(dbHelper, queries, smartCategoryRules) { notifyChanged() }
    private val budgets = BudgetStore(dbHelper, queries) { notifyChanged() }
    private val autoTransactions = AutoTransactionStore(
        dbHelper = dbHelper,
        smartCategoryRules = smartCategoryRules,
        autoBookDuplicates = autoBookDuplicates,
        onChanged = { notifyChanged() }
    )
    private val backupStore by lazy { CoinNestBackupStore(dbHelper) { notifyChanged() } }

    fun observeTransactionsInRange(
        startInclusive: Long,
        endExclusive: Long,
        limit: Int
    ): Flow<List<TransactionEntity>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queries.transactionsInRange(startInclusive, endExclusive, limit)
            }
        }
    }

    fun observePendingAutoTransactions(limit: Int = 50): Flow<List<TransactionEntity>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queries.pendingTransactions(limit)
            }
        }
    }

    fun observeSummary(startInclusive: Long, endExclusive: Long): Flow<BalanceSummary> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queries.summary(startInclusive = startInclusive, endExclusive = endExclusive)
            }
        }
    }

    fun observeConfirmedTransactionCountInRange(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<Int> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queries.confirmedTransactionCountInRange(startInclusive, endExclusive)
            }
        }
    }

    fun observeActiveBookkeepingDayCount(): Flow<Int> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queries.activeBookkeepingDayCount()
            }
        }
    }

    fun observeExpenseByDayInRange(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<ExpenseBucketRow>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queries.expenseByDayInRange(startInclusive, endExclusive)
            }
        }
    }

    fun observeExpenseByMonthInRange(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<ExpenseBucketRow>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queries.expenseByMonthInRange(startInclusive, endExclusive)
            }
        }
    }

    fun observeCategoryExpenseInRange(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<CategoryExpenseRow>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queries.categoryExpenseInRange(startInclusive, endExclusive)
            }
        }
    }

    fun observeCategories(): Flow<List<CategoryItem>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queries.categories().map { row -> CategoryItem(row.parent, row.child) }
            }
        }
    }

    fun observeSmartCategoryRules(limit: Int = 200): Flow<List<SmartCategoryRuleEntity>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queries.smartCategoryRules(limit = limit)
            }
        }
    }

    suspend fun ensureDefaultCategories() = withContext(Dispatchers.IO) {
        defaultCategories.ensureDefaultCategories()
    }

    suspend fun addCategory(parent: String, child: String) = withContext(Dispatchers.IO) {
        transactionWrites.addCategory(parent, child)
    }

    suspend fun addTransaction(input: TransactionInput) = withContext(Dispatchers.IO) {
        transactionWrites.addTransaction(input)
    }

    suspend fun confirmPendingTransaction(id: Long) = withContext(Dispatchers.IO) {
        transactionWrites.confirmPendingTransaction(id)
    }

    suspend fun ignorePendingTransaction(id: Long) = withContext(Dispatchers.IO) {
        transactionWrites.ignorePendingTransaction(id)
    }

    suspend fun updateTransactionDetails(
        id: Long,
        parentCategory: String,
        childCategory: String,
        note: String
    ) = withContext(Dispatchers.IO) {
        transactionWrites.updateTransactionDetails(id, parentCategory, childCategory, note)
    }

    suspend fun deleteTransaction(id: Long) = withContext(Dispatchers.IO) {
        transactionWrites.deleteTransaction(id)
    }

    suspend fun exportBackupJson(): String = backupStore.exportBackupJson()

    suspend fun importBackupJson(json: String, replaceExisting: Boolean = false): Pair<Int, Int> =
        backupStore.importBackupJson(json, replaceExisting)

    suspend fun upsertMonthBudget(monthKey: String, limitCents: Long) = withContext(Dispatchers.IO) {
        budgets.upsertMonthBudget(monthKey, limitCents)
    }

    fun observeMonthBudget(monthKey: String): Flow<MonthlyBudgetEntity?> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queries.monthBudget(monthKey)
            }
        }
    }

    fun observeCategoryBudgets(monthKey: String): Flow<List<CategoryBudgetEntity>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queries.categoryBudgets(monthKey)
            }
        }
    }

    suspend fun upsertCategoryBudget(
        monthKey: String,
        parentCategory: String,
        childCategory: String,
        limitCents: Long
    ) = withContext(Dispatchers.IO) {
        budgets.upsertCategoryBudget(monthKey, parentCategory, childCategory, limitCents)
    }

    suspend fun clearSmartCategoryRules() = withContext(Dispatchers.IO) {
        smartCategoryRules.clear()
        notifyChanged()
    }

    suspend fun currentMonthBudgetUsage(
        monthKey: String,
        startInclusive: Long,
        endExclusive: Long
    ): Pair<Long, Long?> = withContext(Dispatchers.IO) {
        budgets.currentMonthBudgetUsage(monthKey, startInclusive, endExclusive)
    }

    suspend fun addAutoTransaction(
        amountCents: Long,
        type: TransactionType,
        source: String,
        note: String,
        fingerprint: String?,
        occurredAtEpochMs: Long,
        channel: String = "NOTIFY",
        parent: String = "\u5f85\u5206\u7c7b",
        child: String = "\u81ea\u52a8\u8bc6\u522b"
    ): AutoTransactionInsertResult = withContext(Dispatchers.IO) {
        autoTransactions.addAutoTransaction(
            amountCents = amountCents,
            type = type,
            source = source,
            note = note,
            fingerprint = fingerprint,
            occurredAtEpochMs = occurredAtEpochMs,
            channel = channel,
            parent = parent,
            child = child
        )
    }

    private fun notifyChanged() {
        changeTick.value = System.currentTimeMillis()
    }
}
