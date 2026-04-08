package com.example.coin_nest.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.core.content.FileProvider
import com.example.coin_nest.util.MoneyFormat
import java.io.File
import java.io.FileOutputStream

internal fun shareReportImage(context: android.content.Context, snapshot: ReportSnapshot) {
    val width = 1080
    val height = 1350
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.parseColor("#FFF7EA"))

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#7A3B18")
        textSize = 64f
        isFakeBoldText = true
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#4B3424")
        textSize = 42f
    }
    val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#8B6B51")
        textSize = 34f
    }
    val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#FFE6C4")
    }

    canvas.drawRoundRect(48f, 48f, width - 48f, height - 48f, 30f, 30f, blockPaint)
    canvas.drawText("Coin Nest ${snapshot.title}", 96f, 170f, titlePaint)
    canvas.drawText("总支出：${MoneyFormat.fromCents(snapshot.totalExpenseCents)}", 96f, 300f, bodyPaint)
    canvas.drawText("Top 分类：${snapshot.topCategoryName}", 96f, 390f, bodyPaint)
    canvas.drawText("分类金额：${MoneyFormat.fromCents(snapshot.topCategoryExpenseCents)}", 96f, 470f, bodyPaint)
    canvas.drawText("最大单笔：${MoneyFormat.fromCents(snapshot.maxExpenseCents)}", 96f, 560f, bodyPaint)
    canvas.drawText(snapshot.maxExpenseLabel, 96f, 640f, hintPaint)
    canvas.drawText(snapshot.changeSummary, 96f, 760f, bodyPaint)
    canvas.drawText("记录越轻松，洞察越清晰", 96f, 1180f, hintPaint)

    val dir = File(context.cacheDir, "shares")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "coin_nest_report_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, "分享报告"))
}

