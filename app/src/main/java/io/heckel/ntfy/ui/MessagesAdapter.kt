package io.heckel.ntfy.ui

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
import io.heckel.ntfy.util.VerificationCode
import io.heckel.ntfy.util.copyToClipboard
import io.heckel.ntfy.util.decodeMessage
import io.heckel.ntfy.util.formatDateShort

/**
 * Adapter for the home ("messages") screen: shows messages from all (or the
 * user-selected) subscribed topics in Material 3 cards, newest first.
 *
 * Each card shows: topic badge, time, title, body, the full subscription
 * address of the message source, and — when the body contains a verification
 * code (detected with the same VerificationCode rules used for the notification
 * action) — a "copy verification code" button that copies exactly the code.
 * Messages that still have an active system notification (notificationId != 0)
 * are visually marked as unread.
 */
class MessagesAdapter(
    private val onClick: (MessageWithSubscription) -> Unit
) : ListAdapter<MessageWithSubscription, MessagesAdapter.ViewHolder>(MessageDiff) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val topic: TextView = view.findViewById(R.id.home_item_topic)
        private val time: TextView = view.findViewById(R.id.home_item_time)
        private val title: TextView = view.findViewById(R.id.home_item_title)
        private val message: TextView = view.findViewById(R.id.home_item_message)
        private val url: TextView = view.findViewById(R.id.home_item_url)
        private val copyCodeButton: MaterialButton = view.findViewById(R.id.home_item_copy_code)
        private val unreadDot: View = view.findViewById(R.id.home_item_unread_dot)

        fun bind(msg: MessageWithSubscription) {
            val context = itemView.context
            val n = msg.notification

            // Message source: user-friendly topic name + full subscription address,
            // both taken from the database-linked subscription data (not the body)
            topic.text = msg.displayName?.takeIf { it.isNotBlank() } ?: msg.topic
            url.text = "${msg.baseUrl}/${msg.topic}"
            time.text = formatDateShort(n.timestamp)

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

            itemView.setOnClickListener { onClick(msg) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_home_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    object MessageDiff : DiffUtil.ItemCallback<MessageWithSubscription>() {
        override fun areItemsTheSame(oldItem: MessageWithSubscription, newItem: MessageWithSubscription): Boolean {
            return oldItem.notification.id == newItem.notification.id &&
                oldItem.notification.subscriptionId == newItem.notification.subscriptionId
        }

        override fun areContentsTheSame(oldItem: MessageWithSubscription, newItem: MessageWithSubscription): Boolean {
            return oldItem == newItem
        }
    }
}
