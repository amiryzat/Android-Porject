package com.CampusGO.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.CampusGO.app.R
import com.CampusGO.app.model.Message
import com.CampusGO.app.model.MessageType
import com.CampusGO.app.model.PriceStatus
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val currentUserId: String,
    private val originalPrice: Double,
    private val onAcceptPrice: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<Message>()

    companion object {
        private const val VIEW_SENT = 1
        private const val VIEW_RECEIVED = 2
        private const val VIEW_PRICE = 3
    }

    inner class SentHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvContent: TextView = view.findViewById(R.id.tvContent)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    inner class ReceivedHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvContent: TextView = view.findViewById(R.id.tvContent)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    inner class PriceHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPriceLabel: TextView = view.findViewById(R.id.tvPriceLabel)
        val tvPriceAmount: TextView = view.findViewById(R.id.tvPriceAmount)
        val tvPriceNote: TextView = view.findViewById(R.id.tvPriceNote)
        val tvPriceStatus: TextView = view.findViewById(R.id.tvPriceStatus)
        val btnAcceptPrice: MaterialButton = view.findViewById(R.id.btnAcceptPrice)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when {
            msg.type == MessageType.PRICE_OFFER || msg.type == MessageType.PRICE_AGREED -> VIEW_PRICE
            msg.senderId == currentUserId -> VIEW_SENT
            else -> VIEW_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_SENT -> SentHolder(inflater.inflate(R.layout.item_message_sent, parent, false))
            VIEW_PRICE -> PriceHolder(inflater.inflate(R.layout.item_message_price, parent, false))
            else -> ReceivedHolder(inflater.inflate(R.layout.item_message_received, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        val timeStr = formatTime(msg.createdAt)

        when (holder) {
            is SentHolder -> {
                holder.tvContent.text = msg.content
                holder.tvTime.text = timeStr
            }
            is ReceivedHolder -> {
                holder.tvContent.text = msg.content
                holder.tvTime.text = timeStr
            }
            is PriceHolder -> {
                val isMine = msg.senderId == currentUserId
                val fromLabel = if (isMine) "PRICE OFFER · from you" else "PRICE OFFER · from ${msg.senderName}"
                holder.tvPriceLabel.text = if (msg.type == MessageType.PRICE_AGREED) "AGREED PRICE ✓" else fromLabel
                holder.tvPriceAmount.text = "RM ${String.format("%.2f", msg.priceAmount)}"
                if (originalPrice > 0 && msg.priceAmount != originalPrice) {
                    holder.tvPriceNote.text = "up from RM ${String.format("%.2f", originalPrice)}"
                    holder.tvPriceNote.visibility = View.VISIBLE
                } else {
                    holder.tvPriceNote.visibility = View.GONE
                }
                when {
                    msg.type == MessageType.PRICE_AGREED -> {
                        holder.tvPriceStatus.text = "AGREED ✓"
                        holder.btnAcceptPrice.visibility = View.GONE
                    }
                    msg.priceStatus == PriceStatus.PENDING && !isMine -> {
                        holder.tvPriceStatus.text = "PENDING"
                        holder.btnAcceptPrice.visibility = View.VISIBLE
                        holder.btnAcceptPrice.setOnClickListener { onAcceptPrice(msg) }
                    }
                    msg.priceStatus == PriceStatus.ACCEPTED -> {
                        holder.tvPriceStatus.text = "ACCEPTED ✓"
                        holder.btnAcceptPrice.visibility = View.GONE
                    }
                    else -> {
                        holder.tvPriceStatus.text = "PENDING"
                        holder.btnAcceptPrice.visibility = View.GONE
                    }
                }
                holder.tvTime.text = timeStr
            }
        }
    }

    override fun getItemCount() = messages.size

    fun setMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
