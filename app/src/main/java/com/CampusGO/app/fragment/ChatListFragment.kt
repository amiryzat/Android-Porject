package com.CampusGO.app.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.CampusGO.app.ChatActivity
import com.CampusGO.app.InsetsHelper
import com.CampusGO.app.R
import com.CampusGO.app.adapter.ChatListAdapter
import com.CampusGO.app.model.Chat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChatListFragment : Fragment() {

    private lateinit var rvChats: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: ChatListAdapter

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private var listener: ValueEventListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_chat_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        InsetsHelper.applyTopInsetPadding(view.findViewById(R.id.toolbarContainer))
        rvChats = view.findViewById(R.id.rvChats)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        adapter = ChatListAdapter(emptyList(), auth.currentUser?.uid ?: "") { chat ->
            startActivity(Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra("chatId", chat.id)
                putExtra("taskId", chat.taskId)
            })
        }
        rvChats.layoutManager = LinearLayoutManager(requireContext())
        rvChats.adapter = adapter

        loadChats()
    }

    private fun loadChats() {
        val uid = auth.currentUser?.uid ?: return
        listener?.let { db.child("chats").removeEventListener(it) }
        listener = db.child("chats").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val chats = mutableListOf<Chat>()
                for (child in snapshot.children) {
                    val chat = child.getValue(Chat::class.java) ?: continue
                    if (chat.posterId == uid || chat.runnerId == uid) {
                        chats.add(chat)
                    }
                }
                chats.sortByDescending { it.lastMessageTime }
                adapter.updateChats(chats)
                tvEmpty.visibility = if (chats.isEmpty()) View.VISIBLE else View.GONE
                rvChats.visibility = if (chats.isEmpty()) View.GONE else View.VISIBLE
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatListFragment", "DB error: ${error.message}")
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.let { db.child("chats").removeEventListener(it) }
    }
}
