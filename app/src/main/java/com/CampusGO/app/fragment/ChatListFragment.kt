package com.CampusGO.app.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast // CHANGE: Added Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.CampusGO.app.ChatActivity
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

    companion object {
        private const val TAG = "ChatListFragment" // CHANGE: Added TAG for Logcat
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_chat_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvChats = view.findViewById(R.id.rvChats)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        adapter = ChatListAdapter(
            emptyList(),
            auth.currentUser?.uid ?: ""
        ) { chat ->
            openChat(chat)
        }

        rvChats.layoutManager = LinearLayoutManager(requireContext())
        rvChats.adapter = adapter

        loadChats()
    }

    private fun loadChats() {
        val uid = auth.currentUser?.uid ?: return

        listener?.let {
            db.child("chats").removeEventListener(it)
        }

        listener = db.child("chats")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return

                    val chats = mutableListOf<Chat>()

                    for (child in snapshot.children) {
                        if (child.key == "_placeholder") continue

                        val chat = try {
                            child.getValue(Chat::class.java)
                        } catch (e: Exception) {
                            Log.e("ChatListFragment", "Invalid chat data at ${child.key}", e)
                            null
                        } ?: continue

                        val firebaseChatId = child.key ?: ""

                        if (chat.id.isEmpty()) {
                            chat.id = firebaseChatId
                        }

                        val isParticipantByOldFields =
                            chat.posterId == uid || chat.runnerId == uid

                        val isParticipantByMap =
                            child.child("participants")
                                .child(uid)
                                .getValue(Boolean::class.java) == true

                        if ((isParticipantByOldFields || isParticipantByMap) && chat.id.isNotEmpty()) {
                            chats.add(chat)
                        }
                    }

                    chats.sortByDescending { it.lastMessageTime }

                    Log.d(TAG, "Loaded chats: ${chats.size}")

                    adapter.updateChats(chats)

                    tvEmpty.visibility = if (chats.isEmpty()) View.VISIBLE else View.GONE
                    rvChats.visibility = if (chats.isEmpty()) View.GONE else View.VISIBLE
                }

                override fun onCancelled(error: DatabaseError) {
                    // CHANGE: Better error handling
                    Log.e(TAG, "DB error: ${error.message}", error.toException())

                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            "Failed to load chats: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
    }

    private fun openChat(chat: Chat) {
        if (!isAdded) return

        if (chat.id.isBlank()) {
            Toast.makeText(requireContext(), "Chat ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        if (chat.taskId.isBlank()) {
            Toast.makeText(requireContext(), "Chat has no linked task", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Opening chat from ChatList")
        Log.d(TAG, "chatId: ${chat.id}")
        Log.d(TAG, "taskId: ${chat.taskId}")

        startActivity(Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("chatId", chat.id)
            putExtra("taskId", chat.taskId)
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()

        listener?.let {
            db.child("chats").removeEventListener(it)
        }

        listener = null
    }
}