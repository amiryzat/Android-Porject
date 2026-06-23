package com.CampusGO.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.CampusGO.app.R
import com.CampusGO.app.model.WalletTransaction
import com.CampusGO.app.model.WalletTransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WalletTransactionAdapter : RecyclerView.Adapter<WalletTransactionAdapter.ViewHolder>() {

    private val items = mutableListOf<WalletTransaction>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivTransactionIcon)
        val tvDescription: TextView = view.findViewById(R.id.tvTransactionDescription)
        val tvDate: TextView = view.findViewById(R.id.tvTransactionDate)
        val tvAmount: TextView = view.findViewById(R.id.tvTransactionAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wallet_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tx = items[position]

        when (tx.type) {
            WalletTransactionType.TOP_UP -> {
                holder.ivIcon.setImageResource(R.drawable.ic_topup)
                holder.ivIcon.setBackgroundResource(R.drawable.bg_icon_topup)
                holder.tvDescription.text = tx.description.ifBlank { "Wallet Top Up" }
                holder.tvDate.text = formatDate(tx.createdAt)
                holder.tvAmount.text = "+RM ${String.format("%.2f", tx.amount)}"
                holder.tvAmount.setTextColor(holder.itemView.context.getColor(R.color.campusgo_success))
            }

            WalletTransactionType.CASH_OUT -> {
                holder.ivIcon.setImageResource(R.drawable.ic_cashout)
                holder.ivIcon.setBackgroundResource(R.drawable.bg_icon_cashout)
                holder.tvDescription.text = tx.description.ifBlank { "Cash Out" }
                holder.tvDate.text = formatDate(tx.createdAt)
                holder.tvAmount.text = "-RM ${String.format("%.2f", tx.amount)}"
                holder.tvAmount.setTextColor(holder.itemView.context.getColor(R.color.campusgo_error))
            }

            WalletTransactionType.TASK_PAYMENT_SENT -> {
                holder.ivIcon.setImageResource(R.drawable.ic_cashout)
                holder.ivIcon.setBackgroundResource(R.drawable.bg_icon_cashout)
                holder.tvDescription.text = if (tx.otherUserName.isNotBlank()) {
                    "Paid ${tx.otherUserName}"
                } else {
                    tx.description.ifBlank { "Task Payment Sent" }
                }
                holder.tvDate.text = buildSubtitle(tx)
                holder.tvAmount.text = "-RM ${String.format("%.2f", tx.amount)}"
                holder.tvAmount.setTextColor(holder.itemView.context.getColor(R.color.campusgo_error))
            }

            WalletTransactionType.TASK_PAYMENT_RECEIVED -> {
                holder.ivIcon.setImageResource(R.drawable.ic_topup)
                holder.ivIcon.setBackgroundResource(R.drawable.bg_icon_topup)
                holder.tvDescription.text = if (tx.otherUserName.isNotBlank()) {
                    "Received from ${tx.otherUserName}"
                } else {
                    tx.description.ifBlank { "Task Payment Received" }
                }
                holder.tvDate.text = buildSubtitle(tx)
                holder.tvAmount.text = "+RM ${String.format("%.2f", tx.amount)}"
                holder.tvAmount.setTextColor(holder.itemView.context.getColor(R.color.campusgo_success))
            }

            else -> {
                holder.ivIcon.setImageResource(R.drawable.ic_topup)
                holder.ivIcon.setBackgroundResource(R.drawable.bg_icon_topup)
                holder.tvDescription.text = tx.description
                holder.tvDate.text = formatDate(tx.createdAt)
                holder.tvAmount.text = "RM ${String.format("%.2f", tx.amount)}"
                holder.tvAmount.setTextColor(holder.itemView.context.getColor(R.color.campusgo_text_primary))
            }
        }
    }

    override fun getItemCount() = items.size

    fun setTransactions(transactions: List<WalletTransaction>) {
        items.clear()
        items.addAll(transactions)
        notifyDataSetChanged()
    }

    private fun buildSubtitle(tx: WalletTransaction): String {
        val taskPart = tx.taskNumber.takeIf { it.isNotBlank() } ?: ""
        val datePart = formatDate(tx.createdAt)
        return if (taskPart.isNotEmpty()) "$taskPart · $datePart" else datePart
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        return when {
            diff < 24 * 60 * 60 * 1000L -> "Today · ${timeFormat.format(Date(timestamp))}"
            diff < 48 * 60 * 60 * 1000L -> "Yesterday · ${timeFormat.format(Date(timestamp))}"
            else -> SimpleDateFormat("d MMM · h:mm a", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
