package io.heckel.ntfy.util

import android.content.Context
import io.heckel.ntfy.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Date labels for the home message list: a group header label and a per-message
 * time label. Grouping logic lives here (not in the adapter) so it stays in one
 * place and can be unit tested.
 *
 * - today      -> header "Today",      time "13:38"
 * - yesterday  -> header "Yesterday",  time "Yesterday 13:38"
 * - this year  -> header "Sep 19" / "9月19日", time "Sep 19 13:38"
 * - older      -> header "2025/9/19",  time "2025/9/19 13:38"
 *
 * The labels are derived from the message's timestamp (seconds since epoch) using
 * the device's current time zone and locale. Message ordering is not affected.
 */
data class MessageDateLabels(val header: String, val time: String)

object HomeDateGrouping {

    fun labels(
        context: Context,
        timestampSeconds: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): MessageDateLabels {
        val date = Date(timestampSeconds * 1000)
        val locale = Locale.getDefault()
        val messageDay = Calendar.getInstance().apply { timeInMillis = date.time }
        val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }

        val timePattern = context.getString(R.string.home_date_time_pattern)
        val time = SimpleDateFormat(timePattern, locale).format(date)

        return when {
            isSameDay(messageDay, today) -> {
                MessageDateLabels(context.getString(R.string.home_date_today), time)
            }
            isSameDay(messageDay, yesterday) -> {
                val day = context.getString(R.string.home_date_yesterday)
                MessageDateLabels(day, "$day $time")
            }
            messageDay.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> {
                val day = SimpleDateFormat(context.getString(R.string.home_date_month_day_pattern), locale).format(date)
                MessageDateLabels(day, "$day $time")
            }
            else -> {
                val day = SimpleDateFormat(context.getString(R.string.home_date_full_pattern), locale).format(date)
                MessageDateLabels(day, "$day $time")
            }
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}
