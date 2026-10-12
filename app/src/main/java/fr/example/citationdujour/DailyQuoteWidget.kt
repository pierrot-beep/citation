package fr.example.citationdujour

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class DailyQuoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_quote)
            val quote = quoteForToday()
            views.setTextViewText(R.id.quote_text, "“${quote.text}”")
            views.setTextViewText(R.id.quote_author, "— ${quote.author}")
            views.setTextViewText(R.id.quote_date, LocalDate.now().toString())
            manager.updateAppWidget(widgetId, views)
        }

        private fun quoteForToday(): Quote {
            val day = ChronoUnit.DAYS.between(LocalDate.of(2024, 1, 1), LocalDate.now())
            val index = Math.floorMod(day, QuoteDatabase.all.size.toLong()).toInt()
            return QuoteDatabase.all[index]
        }
    }
}
