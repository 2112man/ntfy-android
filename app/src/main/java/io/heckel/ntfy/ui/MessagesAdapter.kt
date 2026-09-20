package io.heckel.ntfy.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import io.heckel.ntfy.R
import io.heckel.ntfy.db.MessageWithSubscription
import io.heckel.ntfy.util.HomeDateGrouping
import io.heckel.ntfy.util.VerificationCode
import io.heckel.ntfy.util.copyToClipboard
import io.heckel.ntfy.util.decodeMessage

/**
 * Adapter for the home ("messages") screen: shows messages from all (or the
 * user-selected) subscribed topics in Material 3 cards, newest first, grouped by
 * day (a date header is shown before the first message of each day).
 *
 * Each card shows: subscription name badge, time, title, body and — when the
 * body contains a verification code (detected with the same VerificationCode
 * rules used for the notification action) — a "copy verification code" button
 * that copies exactly the code. The badge shows the subscription's custom
 * display name (falling back to the topic) and is hidden when the home screen
 * shows only a single subscription (the app bar shows that name instead).
 * Messages that still have an active system notification (notificationId != 0)
 * are visually marked as unread.
 *
 * Day grouping and time labels are computed once per submitted list (in
 * [submitMessages]) using HomeDateGrouping, so the grouping logic stays in one
 * place and headers stay correct after inserts, deletions and search filtering.
 */
class MessagesAdapter(
    private val onClick: (MessageWithSubscription) -> Unit,
    private val onLongClick: (MessageWithSubscription) -> Unit = {}
) : ListAdapter<MessagesAdapter.Row, MessagesAdapter.ViewHolder>(RowDiff) {

    /** A list row: a message plus its pre-computed date labels. */
    data class Row(
        val msg: MessageWithSubscription,
        val headerLabel: String?, // null when the row continues the previous day group
        val timeLabel: String
    )

    /**
     * Whether the subscription name badge is shown on each card. Set to false
     * when the home screen only shows a single subscription, because the app bar
     * already shows that subscription's name.
     */
    var showSubscriptionBadge: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount, PAYLOAD_BADGE)
            }
        }

    /**
     * Submits the messages (expected newest-first) and computes the date group
     * headers in one pass.
     */
    fun submitMessages(context: Context, messages: List<MessageWithSubscription>) {
        var lastHeader: String? = null
        val rows = messages.map { msg ->
            val labels = HomeDateGrouping.labels(context, msg.notification.timestamp)
            val header = if (labels.header != lastHeader) labels.header else null
            lastHeader = labels.header
            Row(msg, header, labels.time)
        }
        submitList(rows)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: View = view.findViewById(R.id.home_item_card)
        private val dateHeader: TextView = view.findViewById(R.id.home_item_date_header)
        private val topic: TextView = view.findViewById(R.id.home_item_topic)
        private val time: TextView = view.findViewById(R.id.home_item_time)
        private val title: TextView = view.findViewById(R.id.home_item_title)
        private val message: TextView = view.findViewById(R.id.home_item_message)
        private val copyCodeButton: MaterialButton = view.findViewById(R.id.home_item_copy_code)
        private val unreadDot: View = view.findViewById(R.id.home_item_unread_dot)

        fun bind(row: Row, payloads: List<Any>) {
            val context = itemView.context
            val msg = row.msg
            val n = msg.notification

            // Date group header + per-message time label
            if (row.headerLabel != null) {
                dateHeader.visibility = View.VISIBLE
                dateHeader.text = row.headerLabel
            } else {
                dateHeader.visibility = View.GONE
            }
            time.text = row.timeLabel

            // Message source: the subscription's custom display name, falling back
            // to the topic (never the URL or the message body).
            // INVISIBLE (not GONE) keeps the row height so the dot/time/title stay
            // in place when the badge is hidden in single-subscription mode.
            topic.text = msg.displayName?.takeIf { it.isNotBlank() } ?: msg.topic
            if (payloads.isEmpty() || payloads.contains(PAYLOAD_BADGE)) {
                topic.visibility = if (showSubscriptionBadge) View.VISIBLE else View.INVISIBLE
            }

            if (n.title.isBlank()) {
                title.visibility = View.GONE
            } else {
                title.visibility = View.VISIBLE
                title.text = n.title
            }
            message.text = decodeMessage(n).replace("\n", " ")
            unreadDot.visibility = if (n.notificationId != 0) View.VISIBLE else View.GONE

            // Reuse the exact same verification-code detection as the notification
            // action (Phase 1). Show the copy button only when a code is detected;
            // clicking copies exactly the code via the existing Util.copyToClipboard.
            val code = VerificationCode.extract(decodeMessage(n))
            if (code != null) {
                copyCodeButton.visibility = View.VISIBLE
                copyCodeButton.setOnClickListener {
                    copyToClipboard(context, context.getString(R.string.notification_popup_action_copy_code), code)
                }
            } else {
                copyCodeButton.visibility = View.GONE
                copyCodeButton.setOnClickListener(null)
            }

            // Listeners go on the card itself: the card is clickable (ripple + touch),
            // so events never reach the outer row container.
            card.setOnClickListener { onClick(msg) }
            card.setOnLongClickListener {
                onLongClick(msg)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_home_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        holder.bind(getItem(position), payloads)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), emptyList())
    }

    object RowDiff : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(oldItem: Row, newItem: Row): Boolean {
            return oldItem.msg.notification.id == newItem.msg.notification.id &&
                oldItem.msg.notification.subscriptionId == newItem.msg.notification.subscriptionId
        }

        override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val PAYLOAD_BADGE = "badge"
    }
}
