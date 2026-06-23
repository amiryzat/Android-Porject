package com.CampusGO.app.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.CampusGO.app.R
import com.CampusGO.app.model.Message
import com.CampusGO.app.model.MessageType
import com.CampusGO.app.model.PriceStatus
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val currentUserId: String,
    private val originalPrice: Double,
    private val onAcceptPrice: (Message) -> Unit,
    private val onEditPrice: (Message) -> Unit,
    private val canEditPrice: (Message) -> Boolean,
    private val onRejectPrice: (Message) -> Unit, // ADD THIS
    private val canRejectPrice: (Message) -> Boolean, // ADD THIS
    private val onMessageLongClick: (Message) -> Unit
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
        val ivMedia: android.widget.ImageView = view.findViewById(R.id.ivMedia)
        val flVideoContainer: android.widget.FrameLayout = view.findViewById(R.id.flVideoContainer)
    }

    inner class ReceivedHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvContent: TextView = view.findViewById(R.id.tvContent)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val ivMedia: android.widget.ImageView = view.findViewById(R.id.ivMedia)
        val flVideoContainer: android.widget.FrameLayout = view.findViewById(R.id.flVideoContainer)
    }

    inner class PriceHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPriceLabel: TextView = view.findViewById(R.id.tvPriceLabel)
        val tvPriceAmount: TextView = view.findViewById(R.id.tvPriceAmount)
        val tvPriceNote: TextView = view.findViewById(R.id.tvPriceNote)
        val tvPriceStatus: TextView = view.findViewById(R.id.tvPriceStatus)
        val btnAcceptPrice: MaterialButton = view.findViewById(R.id.btnAcceptPrice)

        val btnRejectPrice: MaterialButton = view.findViewById(R.id.btnRejectPrice)

        // CHANGE: Add this button in item_message_price.xml
        val btnEditPrice: MaterialButton = view.findViewById(R.id.btnEditPrice)

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
            VIEW_SENT -> SentHolder(
                inflater.inflate(R.layout.item_message_sent, parent, false)
            )

            VIEW_PRICE -> PriceHolder(
                inflater.inflate(R.layout.item_message_price, parent, false)
            )

            else -> ReceivedHolder(
                inflater.inflate(R.layout.item_message_received, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        val timeStr = formatTime(msg.createdAt)

        when (holder) {
            is SentHolder -> {
                bindMediaOrText(msg, holder.tvContent, holder.ivMedia, holder.flVideoContainer)
                holder.tvTime.text = timeStr
                holder.itemView.setOnLongClickListener {
                    onMessageLongClick(msg)
                    true
                }
            }

            is ReceivedHolder -> {
                bindMediaOrText(msg, holder.tvContent, holder.ivMedia, holder.flVideoContainer)
                holder.tvTime.text = timeStr
                holder.itemView.setOnLongClickListener {
                    onMessageLongClick(msg)
                    true
                }
            }

            is PriceHolder -> {
                bindPriceMessage(holder, msg, timeStr)
            }
        }
    }

    private fun bindMediaOrText(
        msg: Message,
        tvContent: TextView,
        ivMedia: android.widget.ImageView,
        flVideoContainer: android.widget.FrameLayout
    ) {
        tvContent.visibility = View.GONE
        ivMedia.visibility = View.GONE
        flVideoContainer.visibility = View.GONE

        when (msg.type) {
            MessageType.IMAGE -> {
                ivMedia.visibility = View.VISIBLE
                try {
                    val decodedBytes = android.util.Base64.decode(msg.content, android.util.Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    if (bitmap != null) {
                        ivMedia.setImageBitmap(bitmap)
                    } else {
                        ivMedia.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                } catch (e: Exception) {
                    ivMedia.setImageResource(android.R.drawable.ic_menu_gallery)
                }

                ivMedia.setOnClickListener {
                    val context = it.context
                    val intent = android.content.Intent(context, com.CampusGO.app.FullscreenMediaActivity::class.java).apply {
                        putExtra("MEDIA_TYPE", "IMAGE")
                        putExtra("MEDIA_CONTENT", msg.content)
                    }
                    context.startActivity(intent)
                }
            }
            MessageType.VIDEO -> {
                flVideoContainer.visibility = View.VISIBLE
                flVideoContainer.setOnClickListener {
                    val context = it.context
                    val intent = android.content.Intent(context, com.CampusGO.app.FullscreenMediaActivity::class.java).apply {
                        putExtra("MEDIA_TYPE", "VIDEO")
                        putExtra("MEDIA_CONTENT", msg.content)
                    }
                    context.startActivity(intent)
                }
            }
            else -> {
                tvContent.visibility = View.VISIBLE
                tvContent.text = msg.content
            }
        }
    }

    private fun bindPriceMessage(holder: PriceHolder, msg: Message, timeStr: String) {
        val isMine = msg.senderId == currentUserId

        // Price messages use one shared layout, so align manually.
        // My offers go to the right; the other user's offers go to the left.
        val rootLayout = holder.itemView as? LinearLayout
        rootLayout?.gravity = if (isMine) Gravity.END else Gravity.START

        holder.tvPriceAmount.text = "RM ${String.format("%.2f", msg.priceAmount)}"
        holder.tvTime.text = timeStr

        // CHANGE: Different label for agreed price, own offer, and other person's offer
        holder.tvPriceLabel.text = when {
            msg.type == MessageType.PRICE_AGREED -> {
                "AGREED PRICE ✓"
            }

            isMine -> {
                "PRICE OFFER · from you"
            }

            else -> {
                "PRICE OFFER · from ${msg.senderName}"
            }
        }

        if (originalPrice > 0 && msg.priceAmount != originalPrice) {
            val direction = if (msg.priceAmount > originalPrice) "up from" else "down from"
            holder.tvPriceNote.text = "$direction RM ${String.format("%.2f", originalPrice)}"
            holder.tvPriceNote.visibility = View.VISIBLE
        } else {
            holder.tvPriceNote.visibility = View.GONE
        }

        // Default: hide and re-enable all buttons first.
        holder.btnAcceptPrice.visibility = View.GONE
        holder.btnEditPrice.visibility = View.GONE
        holder.btnRejectPrice.visibility = View.GONE
        holder.btnAcceptPrice.isEnabled = true
        holder.btnEditPrice.isEnabled = true
        holder.btnRejectPrice.isEnabled = true

        when {
            msg.type == MessageType.PRICE_AGREED -> {
                holder.tvPriceStatus.text = "AGREED ✓"
            }

            msg.priceStatus == PriceStatus.ACCEPTED -> {
                holder.tvPriceStatus.text = "ACCEPTED ✓"
            }

            msg.priceStatus == PriceStatus.REJECTED -> {
                holder.tvPriceStatus.text = "REPLACED"
            }

            msg.priceStatus == PriceStatus.PENDING -> {
                holder.tvPriceStatus.text = "PENDING"

                if (!isMine) {
                    holder.btnAcceptPrice.visibility = View.VISIBLE
                    holder.btnAcceptPrice.setOnClickListener {
                        holder.btnAcceptPrice.isEnabled = false
                        onAcceptPrice(msg)
                    }
                }

                if (canEditPrice(msg)) {
                    holder.btnEditPrice.visibility = View.VISIBLE
                    holder.btnEditPrice.setOnClickListener {
                        holder.btnEditPrice.isEnabled = false
                        onEditPrice(msg)
                    }
                }

                if (canRejectPrice(msg)) {
                    holder.btnRejectPrice.visibility = View.VISIBLE
                    holder.btnRejectPrice.setOnClickListener {
                        holder.btnRejectPrice.isEnabled = false
                        onRejectPrice(msg)
                    }
                }
            }

            else -> {
                holder.tvPriceStatus.text = "PENDING"
            }
        }
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    fun setMessages(newMessages: List<Message>) {
        messages.clear()
        // Do not render system status messages as normal chat bubbles.
        // Rejected offers are already shown by the price card status.
        messages.addAll(newMessages.filter { it.type != MessageType.SYSTEM })
        notifyDataSetChanged()
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}