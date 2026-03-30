package com.example.coin_nest

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.coin_nest.budget.BudgetNotifier
import com.example.coin_nest.di.ServiceLocator
import com.example.coin_nest.ui.CoinNestViewModel
import com.example.coin_nest.ui.CoinNestViewModelFactory
import com.example.coin_nest.ui.HomeScreen
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.coin_nest.ui.theme.CoinnestTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<CoinNestViewModel> {
        CoinNestViewModelFactory(ServiceLocator.repository())
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(applicationContext)
        BudgetNotifier.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            CoinnestTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
                    HomeScreen(
                        state = state,
                        onAddTransaction = viewModel::addTransaction,
                        onConfirmPendingAuto = viewModel::confirmPendingAuto,
                        onIgnorePendingAuto = viewModel::ignorePendingAuto,
                        onAddCategory = viewModel::addCategory,
                        onSetMonthBudget = viewModel::setCurrentMonthBudget,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
