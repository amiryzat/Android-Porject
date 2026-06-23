package com.CampusGO.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.CampusGO.app.R
import com.CampusGO.app.util.MalaysianBanks

class BankOptionAdapter(
    private val onBankSelected: (String) -> Unit
) : RecyclerView.Adapter<BankOptionAdapter.ViewHolder>() {

    private val fullList = MalaysianBanks.banks
    private var displayList = fullList.toMutableList()

    inner class ViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_bank_option, parent, false)
    ) {
        val tvBankName: TextView = itemView.findViewById(R.id.tvBankName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bank = displayList[position]
        holder.tvBankName.text = bank
        holder.itemView.setOnClickListener { onBankSelected(bank) }
    }

    override fun getItemCount() = displayList.size

    fun filter(query: String) {
        displayList = if (query.isBlank()) {
            fullList.toMutableList()
        } else {
            fullList.filter { it.contains(query.trim(), ignoreCase = true) }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
