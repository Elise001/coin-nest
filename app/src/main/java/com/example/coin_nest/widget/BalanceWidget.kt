package com.example.coin_nest.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.coin_nest.di.ServiceLocator
import com.example.coin_nest.util.DateRangeUtils
import com.example.coin_nest.util.MoneyFormat
import kotlinx.coroutines.flow.first

class BalanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        ServiceLocator.init(context)
        val range = DateRangeUtils.monthRange()
        val summary = ServiceLocator.repository().observeSummary(range.first, range.last + 1).first()
        provideContent {
            WidgetContent(summaryText = MoneyFormat.fromCents(summary.balanceCents))
        }
    }
}

@Composable
private fun WidgetContent(summaryText: String) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            text = "Monthly Balance",
            style = TextStyle(fontSize = 14.sp)
        )
        Text(
            text = summaryText,
            style = TextStyle(fontSize = 20.sp)
        )
    }
}

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidget()
}
