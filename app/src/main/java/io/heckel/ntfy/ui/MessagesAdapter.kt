package io.heckel.ntfy.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.heckel.ntfy.R
import io.heckel.ntfy.db.MessageWithSubscription
import io.heckel.ntfy.util.decodeMessage
import io.heckel.ntfy.util.formatDateShort
import io.heckel.ntfy.util.shortUrl
import io.heckel.ntfy.util.topicShortUrl

/**
 * Adapter for the home ("messages") screen: shows messages from all subscribed
 * topics in Material 3 cards, newest first, with topic badge, title, body and time.
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
        private val unreadDot: View = view.findViewById(R.id.home_item_unread_dot)

        fun bind(msg: MessageWithSubscription) {
            val n = msg.notification
            topic.text = msg.displayName?.takeIf { it.isNotBlank() } ?: topicShortUrl(msg.baseUrl, msg.topic)
            time.text = formatDateShort(n.timestamp)
            if (n.title.isBlank()) {
                title.visibility = View.GONE
            } else {
                title.visibility = View.VISIBLE
                title.text = n.title
            }
            message.text = decodeMessage(n).replace("\n", " ")
            unreadDot.visibility = if (n.notificationId != 0) View.VISIBLE else View.GONE
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
