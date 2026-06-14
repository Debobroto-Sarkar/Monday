package com.monday.assistant.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.monday.assistant.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * CHAT ADAPTER — Drives the conversation RecyclerView
 * Two view types: USER (right, blue) and MONDAY (left, cyan)
 */
class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_MONDAY = 1
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clear() {
        val size = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, size)
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].isMonday) VIEW_TYPE_MONDAY else VIEW_TYPE_USER

    override fun getItemCount() = messages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == VIEW_TYPE_MONDAY)
            R.layout.item_message_monday
        else
            R.layout.item_message_user

        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        holder.tvMessage.text = msg.text
        holder.tvTime?.text = timeFormat.format(Date(msg.timestamp))
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvTime: TextView? = itemView.findViewById(R.id.tvTime)
    }
}

data class ChatMessage(
    val text: String,
    val isMonday: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
