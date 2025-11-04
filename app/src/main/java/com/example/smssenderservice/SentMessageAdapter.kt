package com.example.smssenderservice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class SentMessageAdapter(
    private val onItemClick: (SentMessageInfo) -> Unit
) : ListAdapter<SentMessageInfo, SentMessageAdapter.SentMessageViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SentMessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sent_message, parent, false)
        return SentMessageViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: SentMessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SentMessageViewHolder(
        itemView: View,
        private val onItemClick: (SentMessageInfo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val phoneNumber: TextView = itemView.findViewById(R.id.itemPhoneNumberTextView)
        private val status: TextView = itemView.findViewById(R.id.itemStatusTextView)
        private val date: TextView = itemView.findViewById(R.id.itemDateTextView)

        fun bind(item: SentMessageInfo) {
            phoneNumber.text = item.phoneNumber.ifEmpty { itemView.context.getString(R.string.unknown_number_placeholder) }
            status.text = item.status.label(itemView.context)
            status.setTextColor(resolveStatusColor(item.status))
            date.text = SentMessagesRepository.formatTimestamp(item.timestamp)
            itemView.setOnClickListener { onItemClick(item) }
        }

        private fun resolveStatusColor(status: MessageSendStatus): Int {
            val context = itemView.context
            val colorRes = when (status) {
                MessageSendStatus.PENDING -> R.color.status_pending
                MessageSendStatus.SENT -> R.color.status_sent
                MessageSendStatus.DELIVERED -> R.color.status_delivered
                MessageSendStatus.ERROR -> R.color.status_error
            }
            return ContextCompat.getColor(context, colorRes)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<SentMessageInfo>() {
        override fun areItemsTheSame(oldItem: SentMessageInfo, newItem: SentMessageInfo): Boolean =
            oldItem.messageId == newItem.messageId

        override fun areContentsTheSame(oldItem: SentMessageInfo, newItem: SentMessageInfo): Boolean =
            oldItem == newItem
    }
}
