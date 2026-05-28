package com.CampusGO.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.CampusGO.app.databinding.ActivityChatBinding
import com.CampusGO.app.adapter.MessageAdapter
import com.CampusGO.app.model.Chat
import com.CampusGO.app.model.Message
import com.CampusGO.app.model.MessageType
import com.CampusGO.app.model.PriceStatus
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private lateinit var adapter: MessageAdapter
    private var currentTask: Task? = null
    private var currentChat: Chat? = null
    private var messagesListener: ValueEventListener? = null

    private lateinit var chatId: String
    private lateinit var taskId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        taskId = intent.getStringExtra("taskId") ?: run { finish(); return }
        chatId = intent.getStringExtra("chatId") ?: run { finish(); return }

        binding.btnBack.setOnClickListener { finish() }

        adapter = MessageAdapter(
            currentUserId = auth.currentUser?.uid ?: "",
            originalPrice = 0.0,
            onAcceptPrice = { msg -> acceptPriceOffer(msg) }
        )

        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.adapter = adapter

        binding.btnSend.setOnClickListener { sendTextMessage() }
        binding.btnPriceOffer.setOnClickListener { showPriceOfferDialog() }
        binding.btnConfirmNow.setOnClickListener { navigateToConfirm() }

        loadTask()
        loadChat()
        listenMessages()
    }

    private fun loadTask() {
        db.child("tasks").child(taskId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentTask = snapshot.getValue(Task::class.java)
                updateToolbar()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadChat() {
        db.child("chats").child(chatId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentChat = snapshot.getValue(Chat::class.java)
                updateToolbar()
                if ((currentChat?.finalPrice ?: 0.0) > 0) {
                    showAgreedBanner(currentChat!!.finalPrice)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateToolbar() {
        val uid = auth.currentUser?.uid ?: return
        val chat = currentChat
        val task = currentTask
        if (chat != null) {
            val otherName = if (chat.posterId == uid) chat.runnerName else chat.posterName
            binding.tvOtherName.text = otherName
        } else if (task != null) {
            val otherName = if (task.posterId == uid) "Runner" else task.posterName
            binding.tvOtherName.text = otherName
        }
        binding.tvTaskRef.text = task?.taskNumber ?: ""
    }

    private fun listenMessages() {
        messagesListener = db.child("messages").child(chatId)
            .orderByChild("createdAt")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = mutableListOf<Message>()
                    for (child in snapshot.children) {
                        child.getValue(Message::class.java)?.let { messages.add(it) }
                    }
                    adapter.setMessages(messages)
                    binding.rvMessages.scrollToPosition(messages.size - 1)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun sendTextMessage() {
        val content = binding.etMessage.text.toString().trim()
        if (content.isEmpty()) return

        val uid = auth.currentUser?.uid ?: return
        val senderName = auth.currentUser?.displayName ?: "Unknown"

        val msgRef = db.child("messages").child(chatId).push()
        val msgId = msgRef.key ?: return
        val msg = Message(
            id = msgId,
            senderId = uid,
            senderName = senderName,
            content = content,
            type = MessageType.TEXT,
            createdAt = System.currentTimeMillis()
        )
        msgRef.setValue(msg)
        db.child("chats").child(chatId).updateChildren(mapOf(
            "lastMessage" to content,
            "lastMessageTime" to msg.createdAt
        ))
        binding.etMessage.setText("")
    }

    private fun showPriceOfferDialog() {
        val task = currentTask ?: return
        val input = EditText(this).apply {
            hint = "Enter your price (RM)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(40, 20, 40, 20)
        }
        AlertDialog.Builder(this)
            .setTitle("Make a Price Offer")
            .setMessage("Original price: RM ${String.format("%.2f", task.price)}")
            .setView(input)
            .setPositiveButton("Send Offer") { _, _ ->
                val price = input.text.toString().toDoubleOrNull()
                if (price != null && price > 0) {
                    sendPriceOffer(price, task.price)
                } else {
                    Toast.makeText(this, "Invalid price", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendPriceOffer(offerPrice: Double, originalPrice: Double) {
        val uid = auth.currentUser?.uid ?: return
        val senderName = auth.currentUser?.displayName ?: "Unknown"

        val msgRef = db.child("messages").child(chatId).push()
        val msgId = msgRef.key ?: return
        val msg = Message(
            id = msgId,
            senderId = uid,
            senderName = senderName,
            content = "Price offer: RM ${String.format("%.2f", offerPrice)}",
            type = MessageType.PRICE_OFFER,
            priceAmount = offerPrice,
            priceStatus = PriceStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )
        msgRef.setValue(msg)
        db.child("chats").child(chatId).updateChildren(mapOf(
            "lastMessage" to "Price offer: RM ${String.format("%.2f", offerPrice)}",
            "lastMessageTime" to msg.createdAt
        ))
    }

    private fun acceptPriceOffer(priceMsg: Message) {
        val task = currentTask ?: return
        val uid = auth.currentUser?.uid ?: return
        val myName = auth.currentUser?.displayName ?: "Unknown"

        db.child("messages").child(chatId).child(priceMsg.id)
            .child("priceStatus").setValue(PriceStatus.ACCEPTED)

        val msgRef = db.child("messages").child(chatId).push()
        val msgId = msgRef.key ?: return
        val agreedMsg = Message(
            id = msgId,
            senderId = uid,
            senderName = myName,
            content = "Price agreed at RM ${String.format("%.2f", priceMsg.priceAmount)}",
            type = MessageType.PRICE_AGREED,
            priceAmount = priceMsg.priceAmount,
            priceStatus = PriceStatus.ACCEPTED,
            createdAt = System.currentTimeMillis()
        )
        msgRef.setValue(agreedMsg)

        db.child("chats").child(chatId).updateChildren(mapOf(
            "finalPrice" to priceMsg.priceAmount,
            "lastMessage" to "Price agreed RM ${String.format("%.2f", priceMsg.priceAmount)}",
            "lastMessageTime" to agreedMsg.createdAt
        ))

        showAgreedBanner(priceMsg.priceAmount)
    }

    private fun showAgreedBanner(price: Double) {
        binding.llAgreedBanner.visibility = View.VISIBLE
        binding.tvAgreedPrice.text = "Price agreed: RM ${String.format("%.2f", price)} · Tap to confirm"
    }

    private fun navigateToConfirm() {
        val task = currentTask ?: return
        val agreedPrice = currentChat?.finalPrice ?: task.price
        startActivity(Intent(this, ConfirmAcceptActivity::class.java).apply {
            putExtra("taskId", task.id)
            putExtra("agreedPrice", agreedPrice)
            putExtra("chatId", chatId)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.let {
            db.child("messages").child(chatId).removeEventListener(it)
        }
    }
}
