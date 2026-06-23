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
    private lateinit var etSearch: android.widget.EditText
    private lateinit var adapter: ChatListAdapter

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private var listener: ValueEventListener? = null
    private var allChats = mutableListOf<Chat>()
    private var searchQuery: String = ""

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
        etSearch = view.findViewById(R.id.etSearch)

        adapter = ChatListAdapter(
            chats = emptyList(),
            currentUserId = auth.currentUser?.uid ?: "",
            onClick = { chat ->
                openChat(chat)
            },
            onLongClick = { chat ->
                confirmDeleteChat(chat)
            }
        )

        rvChats.layoutManager = LinearLayoutManager(requireContext())
        rvChats.adapter = adapter

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                filterChats()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

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

                    allChats.clear()

                    for (child in snapshot.children) {
                        if (child.value !is Map<*, *>) continue
                        val chat = child.getValue(Chat::class.java) ?: continue

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
                            allChats.add(chat)
                        }
                    }

                    allChats.sortByDescending { it.lastMessageTime }

                    Log.d(TAG, "Loaded chats: ${allChats.size}")
                    filterChats()
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

    private fun confirmDeleteChat(chat: Chat) {
        if (!isAdded) return

        val otherName = if (chat.posterId == auth.currentUser?.uid) chat.runnerName else chat.posterName

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Conversation")
            .setMessage("Are you sure you want to delete your conversation with $otherName? This will delete all messages.")
            .setPositiveButton("Delete") { _, _ ->
                db.child("messages").child(chat.id).removeValue()
                db.child("chats").child(chat.id).removeValue()
                    .addOnSuccessListener {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Conversation deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun filterChats() {
        val filtered = if (searchQuery.isEmpty()) {
            allChats
        } else {
            val uid = auth.currentUser?.uid ?: ""
            allChats.filter { chat ->
                val otherName = if (chat.posterId == uid) chat.runnerName else chat.posterName
                otherName.contains(searchQuery, ignoreCase = true)
            }
        }

        Log.d(TAG, "Filtered chats: ${filtered.size} from ${allChats.size}")
        adapter.updateChats(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        rvChats.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()

        listener?.let {
            db.child("chats").removeEventListener(it)
        }

        listener = null
    }
}