package com.CampusGO.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.CampusGO.app.adapter.MessageAdapter
import com.CampusGO.app.databinding.ActivityChatBinding
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
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private lateinit var adapter: MessageAdapter

    private var currentTask: Task? = null
    private var currentChat: Chat? = null

    private var messagesListener: ValueEventListener? = null
    private var chatListener: ValueEventListener? = null
    private var taskListener: ValueEventListener? = null

    private lateinit var chatId: String
    private lateinit var taskId: String

    companion object {
        private const val TAG = "ChatActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        taskId = intent.getStringExtra("taskId")?.takeIf { it.isNotBlank() } ?: run {
            Toast.makeText(this, "Missing task ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        chatId = intent.getStringExtra("chatId")?.takeIf { it.isNotBlank() } ?: run {
            Toast.makeText(this, "Missing chat ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "Current user: ${auth.currentUser?.uid}")
        Log.d(TAG, "Opened taskId: $taskId")
        Log.d(TAG, "Opened chatId: $chatId")

        setupRecyclerView()
        setupButtons()

        loadTask()
        loadChat()
        listenMessages()
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(
            currentUserId = auth.currentUser?.uid ?: "",
            originalPrice = 0.0,
            onAcceptPrice = { msg ->
                acceptPriceOffer(msg)
            },
            onEditPrice = { msg ->
                showEditPriceOfferDialog(msg)
            },
            canEditPrice = { msg ->
                canCurrentUserEditPriceOffer(msg)
            },
            onRejectPrice = { msg ->
                rejectPriceOffer(msg)
            },
            canRejectPrice = { msg ->
                canCurrentUserEditPriceOffer(msg)
            }
        )

        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        binding.rvMessages.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSend.setOnClickListener {
            sendTextMessage()
        }

        binding.btnPriceOffer.setOnClickListener {
            showPriceOfferDialog()
        }

        binding.btnConfirmNow.setOnClickListener {
            navigateToConfirm()
        }

        binding.btnPriceOffer.visibility = View.GONE
        binding.btnConfirmNow.visibility = View.GONE
        binding.llAgreedBanner.visibility = View.GONE
    }

    private fun loadTask() {
        taskListener = db.child("tasks")
            .child(taskId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    currentTask = snapshot.getValue(Task::class.java)

                    if (currentTask == null) {
                        Log.e(TAG, "Task not found for taskId: $taskId")
                    }

                    updateToolbar()
                    updateChatButtons()
                    updateAgreedBanner()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to load task: ${error.message}", error.toException())

                    Toast.makeText(
                        this@ChatActivity,
                        "Failed to load task: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun loadChat() {
        chatListener = db.child("chats")
            .child(chatId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    currentChat = snapshot.getValue(Chat::class.java)

                    if (currentChat == null) {
                        Log.e(TAG, "Chat not found for chatId: $chatId")
                    }

                    updateToolbar()
                    updateChatButtons()
                    updateAgreedBanner()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to load chat: ${error.message}", error.toException())

                    Toast.makeText(
                        this@ChatActivity,
                        "Failed to load chat: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun updateToolbar() {
        val uid = auth.currentUser?.uid ?: return
        val chat = currentChat
        val task = currentTask

        if (chat != null) {
            val otherName = when (uid) {
                chat.posterId -> chat.runnerName.ifBlank { "Runner" }
                chat.runnerId -> chat.posterName.ifBlank { "Poster" }
                else -> "Chat"
            }

            binding.tvOtherName.text = otherName

            Log.d(TAG, "Toolbar current uid: $uid")
            Log.d(TAG, "Toolbar poster: ${chat.posterId} / ${chat.posterName}")
            Log.d(TAG, "Toolbar runner: ${chat.runnerId} / ${chat.runnerName}")
            Log.d(TAG, "Toolbar showing: $otherName")
        } else if (task != null) {
            val otherName = if (task.posterId == uid) {
                task.runnerName.ifBlank { "Runner" }
            } else {
                task.posterName.ifBlank { "Poster" }
            }

            binding.tvOtherName.text = otherName
        }

        binding.tvTaskRef.text = task?.taskNumber ?: ""
    }

    private fun updateChatButtons() {
        val uid = auth.currentUser?.uid ?: return
        val chat = currentChat ?: return

        val isRunner = chat.runnerId == uid
        val finalPrice = chat.finalPrice

        binding.btnPriceOffer.visibility = if (isRunner && finalPrice <= 0.0) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.btnConfirmNow.visibility = if (isRunner && finalPrice > 0.0) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun updateAgreedBanner() {
        val uid = auth.currentUser?.uid
        val chat = currentChat
        val task = currentTask

        if (uid == null || chat == null || task == null) {
            binding.llAgreedBanner.visibility = View.GONE
            return
        }

        val isRunner = chat.runnerId == uid
        val hasAgreedPrice = chat.finalPrice > 0.0
        val taskStillOpen = task.status == TaskStatus.OPEN

        if (isRunner && hasAgreedPrice && taskStillOpen) {
            showAgreedBanner(chat.finalPrice)
        } else {
            binding.llAgreedBanner.visibility = View.GONE
        }
    }

    private fun listenMessages() {
        messagesListener = db.child("messages")
            .child(chatId)
            .orderByChild("createdAt")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = mutableListOf<Message>()

                    for (child in snapshot.children) {
                        val message = child.getValue(Message::class.java)

                        if (message != null) {
                            messages.add(message)
                        }
                    }

                    messages.sortWith(
                        compareBy<Message> { it.createdAt }
                            .thenBy { it.id }
                    )

                    Log.d(TAG, "Messages received from chatId $chatId: ${messages.size}")

                    adapter.setMessages(messages)

                    if (messages.isNotEmpty()) {
                        binding.rvMessages.post {
                            binding.rvMessages.scrollToPosition(messages.size - 1)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Message listener cancelled: ${error.message}", error.toException())

                    Toast.makeText(
                        this@ChatActivity,
                        "Chat error: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun sendTextMessage() {
        val content = binding.etMessage.text.toString().trim()

        if (content.isEmpty()) return

        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val senderName = auth.currentUser?.displayName ?: "Unknown"

        val msgRef = db.child("messages")
            .child(chatId)
            .push()

        val msgId = msgRef.key ?: run {
            Toast.makeText(this, "Failed to create message ID", Toast.LENGTH_SHORT).show()
            return
        }

        val msgMap = mapOf<String, Any>(
            "id" to msgId,
            "senderId" to uid,
            "senderName" to senderName,
            "content" to content,
            "type" to MessageType.TEXT,
            "priceAmount" to 0.0,
            "priceStatus" to "",
            "createdAt" to ServerValue.TIMESTAMP
        )

        msgRef.setValue(msgMap)
            .addOnSuccessListener {
                Log.d(TAG, "Message sent successfully to chatId: $chatId")

                db.child("chats")
                    .child(chatId)
                    .updateChildren(
                        mapOf<String, Any>(
                            "lastMessage" to content,
                            "lastMessageTime" to ServerValue.TIMESTAMP
                        )
                    )
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update last message", e)
                    }

                binding.etMessage.setText("")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send message", e)

                Toast.makeText(
                    this,
                    "Failed to send message: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showPriceOfferDialog() {
        val task = currentTask ?: run {
            Toast.makeText(this, "Task not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }

        val chat = currentChat ?: run {
            Toast.makeText(this, "Chat not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        if (chat.runnerId != uid) {
            Toast.makeText(this, "Only the runner can make a price offer", Toast.LENGTH_SHORT).show()
            return
        }

        if (chat.finalPrice > 0.0) {
            Toast.makeText(this, "Price has already been agreed", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
            hint = "Enter your price (RM)"
            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(40, 20, 40, 20)
        }

        AlertDialog.Builder(this)
            .setTitle("Make a Price Offer")
            .setMessage("Original price: RM ${String.format("%.2f", task.price)}")
            .setView(input)
            .setPositiveButton("Send Offer") { _, _ ->
                val price = input.text.toString().toDoubleOrNull()

                if (price != null && price > 0) {
                    sendPriceOffer(price)
                } else {
                    Toast.makeText(this, "Invalid price", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendPriceOffer(offerPrice: Double) {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val chat = currentChat ?: run {
            Toast.makeText(this, "Chat not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }

        if (chat.runnerId != uid) {
            Toast.makeText(this, "Only the runner can make a price offer", Toast.LENGTH_SHORT).show()
            return
        }

        if (chat.finalPrice > 0.0) {
            Toast.makeText(this, "Price has already been agreed", Toast.LENGTH_SHORT).show()
            return
        }

        val senderName = auth.currentUser?.displayName ?: "Unknown"

        val msgRef = db.child("messages")
            .child(chatId)
            .push()

        val msgId = msgRef.key ?: run {
            Toast.makeText(this, "Failed to create message ID", Toast.LENGTH_SHORT).show()
            return
        }

        val offerText = "Price offer: RM ${String.format("%.2f", offerPrice)}"

        val msgMap = mapOf<String, Any>(
            "id" to msgId,
            "senderId" to uid,
            "senderName" to senderName,
            "content" to offerText,
            "type" to MessageType.PRICE_OFFER,
            "priceAmount" to offerPrice,
            "priceStatus" to PriceStatus.PENDING,
            "createdAt" to ServerValue.TIMESTAMP
        )

        msgRef.setValue(msgMap)
            .addOnSuccessListener {
                Log.d(TAG, "Price offer sent successfully to chatId: $chatId")

                db.child("chats")
                    .child(chatId)
                    .updateChildren(
                        mapOf<String, Any>(
                            "lastMessage" to offerText,
                            "lastMessageTime" to ServerValue.TIMESTAMP
                        )
                    )
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update chat after price offer", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send price offer", e)

                Toast.makeText(
                    this,
                    "Failed to send price offer: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun canCurrentUserEditPriceOffer(priceMsg: Message): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val chat = currentChat ?: return false

        val isParticipant = uid == chat.posterId || uid == chat.runnerId
        val offerFromOtherUser = priceMsg.senderId != uid
        val isPending = priceMsg.priceStatus == PriceStatus.PENDING
        val isPriceOffer = priceMsg.type == MessageType.PRICE_OFFER
        val noFinalPriceYet = chat.finalPrice <= 0.0

        return isParticipant && offerFromOtherUser && isPending && isPriceOffer && noFinalPriceYet
    }

    private fun showEditPriceOfferDialog(priceMsg: Message) {
        val chat = currentChat ?: run {
            Toast.makeText(this, "Chat not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val isParticipant = uid == chat.posterId || uid == chat.runnerId

        if (!isParticipant) {
            Toast.makeText(this, "You are not part of this chat", Toast.LENGTH_SHORT).show()
            return
        }

        if (priceMsg.senderId == uid) {
            Toast.makeText(this, "You cannot counter your own offer", Toast.LENGTH_SHORT).show()
            return
        }

        if (priceMsg.priceStatus != PriceStatus.PENDING) {
            Toast.makeText(this, "This offer is no longer pending", Toast.LENGTH_SHORT).show()
            return
        }

        if (chat.finalPrice > 0.0) {
            Toast.makeText(this, "Price has already been agreed", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
            hint = "Enter counter price (RM)"
            setText(
                if (priceMsg.priceAmount > 0) {
                    String.format("%.2f", priceMsg.priceAmount)
                } else {
                    ""
                }
            )
            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(40, 20, 40, 20)
        }

        AlertDialog.Builder(this)
            .setTitle("Send Counter Offer")
            .setMessage("${priceMsg.senderName} offered RM ${String.format("%.2f", priceMsg.priceAmount)}")
            .setView(input)
            .setPositiveButton("Send Counter") { _, _ ->
                val counterPrice = input.text.toString().toDoubleOrNull()

                if (counterPrice != null && counterPrice > 0) {
                    sendCounterOffer(counterPrice, priceMsg)
                } else {
                    Toast.makeText(this, "Invalid price", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendCounterOffer(counterPrice: Double, originalOfferMsg: Message) {
        val chat = currentChat ?: run {
            Toast.makeText(this, "Chat not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val isParticipant = uid == chat.posterId || uid == chat.runnerId

        if (!isParticipant) {
            Toast.makeText(this, "You are not part of this chat", Toast.LENGTH_SHORT).show()
            return
        }

        if (originalOfferMsg.senderId == uid) {
            Toast.makeText(this, "You cannot counter your own offer", Toast.LENGTH_SHORT).show()
            return
        }

        if (originalOfferMsg.priceStatus != PriceStatus.PENDING) {
            Toast.makeText(this, "This offer is no longer pending", Toast.LENGTH_SHORT).show()
            return
        }

        if (chat.finalPrice > 0.0) {
            Toast.makeText(this, "Price has already been agreed", Toast.LENGTH_SHORT).show()
            return
        }

        val senderName = auth.currentUser?.displayName ?: "Unknown"

        db.child("messages")
            .child(chatId)
            .child(originalOfferMsg.id)
            .child("priceStatus")
            .setValue(PriceStatus.REJECTED)
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to mark original offer as replaced", e)
            }

        val msgRef = db.child("messages")
            .child(chatId)
            .push()

        val msgId = msgRef.key ?: run {
            Toast.makeText(this, "Failed to create counter offer ID", Toast.LENGTH_SHORT).show()
            return
        }

        val counterText = "Counter offer: RM ${String.format("%.2f", counterPrice)}"

        val msgMap = mapOf<String, Any>(
            "id" to msgId,
            "senderId" to uid,
            "senderName" to senderName,
            "content" to counterText,
            "type" to MessageType.PRICE_OFFER,
            "priceAmount" to counterPrice,
            "priceStatus" to PriceStatus.PENDING,
            "createdAt" to ServerValue.TIMESTAMP
        )

        msgRef.setValue(msgMap)
            .addOnSuccessListener {
                Log.d(TAG, "Counter offer sent successfully")

                db.child("chats")
                    .child(chatId)
                    .updateChildren(
                        mapOf<String, Any>(
                            "lastMessage" to counterText,
                            "lastMessageTime" to ServerValue.TIMESTAMP
                        )
                    )
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update chat after counter offer", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send counter offer", e)

                Toast.makeText(
                    this,
                    "Failed to send counter offer: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun acceptPriceOffer(priceMsg: Message) {
        val chat = currentChat ?: run {
            Toast.makeText(this, "Chat not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val myName = auth.currentUser?.displayName ?: "Unknown"

        if (priceMsg.senderId == uid) {
            Toast.makeText(this, "You cannot accept your own offer", Toast.LENGTH_SHORT).show()
            return
        }

        if (priceMsg.type != MessageType.PRICE_OFFER) {
            Toast.makeText(this, "This is not a price offer", Toast.LENGTH_SHORT).show()
            return
        }

        if (priceMsg.priceStatus != PriceStatus.PENDING) {
            Toast.makeText(this, "This offer is no longer pending", Toast.LENGTH_SHORT).show()
            return
        }

        if (chat.finalPrice > 0.0) {
            Toast.makeText(this, "Price has already been agreed", Toast.LENGTH_SHORT).show()
            return
        }

        val offerFromRunner = priceMsg.senderId == chat.runnerId
        val offerFromPoster = priceMsg.senderId == chat.posterId

        val currentUserIsPoster = uid == chat.posterId
        val currentUserIsRunner = uid == chat.runnerId

        val allowedToAccept =
            (offerFromRunner && currentUserIsPoster) ||
                    (offerFromPoster && currentUserIsRunner)

        if (!allowedToAccept) {
            Toast.makeText(this, "You are not allowed to accept this offer", Toast.LENGTH_SHORT).show()
            return
        }

        db.child("messages")
            .child(chatId)
            .child(priceMsg.id)
            .child("priceStatus")
            .setValue(PriceStatus.ACCEPTED)
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update price status", e)
            }

        val msgRef = db.child("messages")
            .child(chatId)
            .push()

        val msgId = msgRef.key ?: run {
            Toast.makeText(this, "Failed to create message ID", Toast.LENGTH_SHORT).show()
            return
        }

        val agreedText = "Price agreed at RM ${String.format("%.2f", priceMsg.priceAmount)}"

        val msgMap = mapOf<String, Any>(
            "id" to msgId,
            "senderId" to uid,
            "senderName" to myName,
            "content" to agreedText,
            "type" to MessageType.PRICE_AGREED,
            "priceAmount" to priceMsg.priceAmount,
            "priceStatus" to PriceStatus.ACCEPTED,
            "createdAt" to ServerValue.TIMESTAMP
        )

        msgRef.setValue(msgMap)
            .addOnSuccessListener {
                Log.d(TAG, "Price agreement message sent successfully")

                db.child("chats")
                    .child(chatId)
                    .updateChildren(
                        mapOf<String, Any>(
                            "finalPrice" to priceMsg.priceAmount,
                            "lastMessage" to "Price agreed RM ${String.format("%.2f", priceMsg.priceAmount)}",
                            "lastMessageTime" to ServerValue.TIMESTAMP
                        )
                    )
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update chat final price", e)
                    }

                updateAgreedBanner()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send agreed price message", e)

                Toast.makeText(
                    this,
                    "Failed to accept price: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun rejectPriceOffer(priceMsg: Message) {
        val chat = currentChat ?: run {
            Toast.makeText(this, "Chat not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val isParticipant = uid == chat.posterId || uid == chat.runnerId

        if (!isParticipant) {
            Toast.makeText(this, "You are not part of this chat", Toast.LENGTH_SHORT).show()
            return
        }

        if (priceMsg.senderId == uid) {
            Toast.makeText(this, "You cannot reject your own offer", Toast.LENGTH_SHORT).show()
            return
        }

        if (priceMsg.priceStatus != PriceStatus.PENDING) {
            Toast.makeText(this, "This offer is no longer pending", Toast.LENGTH_SHORT).show()
            return
        }

        if (chat.finalPrice > 0.0) {
            Toast.makeText(this, "Price has already been agreed", Toast.LENGTH_SHORT).show()
            return
        }

        val rejectText = "Offer rejected"

        db.child("messages")
            .child(chatId)
            .child(priceMsg.id)
            .child("priceStatus")
            .setValue(PriceStatus.REJECTED)
            .addOnSuccessListener {
                db.child("chats")
                    .child(chatId)
                    .updateChildren(
                        mapOf<String, Any>(
                            "lastMessage" to rejectText,
                            "lastMessageTime" to ServerValue.TIMESTAMP
                        )
                    )
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update chat after rejecting offer", e)
                    }

                Toast.makeText(this, rejectText, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to reject offer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAgreedBanner(price: Double) {
        binding.llAgreedBanner.visibility = View.VISIBLE
        binding.tvAgreedPrice.text =
            "Price agreed: RM ${String.format("%.2f", price)} · Tap to confirm"

        binding.llAgreedBanner.setOnClickListener {
            navigateToConfirm()
        }
    }

    private fun navigateToConfirm() {
        val task = currentTask ?: run {
            Toast.makeText(this, "Task not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val chat = currentChat ?: run {
            Toast.makeText(this, "Chat not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }

        if (chat.runnerId != uid) {
            Toast.makeText(this, "Only the runner can confirm this task", Toast.LENGTH_SHORT).show()
            return
        }

        val agreedPrice = if (chat.finalPrice > 0.0) {
            chat.finalPrice
        } else {
            task.price
        }

        startActivity(Intent(this, ConfirmAcceptActivity::class.java).apply {
            putExtra("taskId", task.id)
            putExtra("agreedPrice", agreedPrice)
            putExtra("chatId", chatId)
        })
    }

    override fun onDestroy() {
        super.onDestroy()

        messagesListener?.let {
            db.child("messages")
                .child(chatId)
                .removeEventListener(it)
        }

        chatListener?.let {
            db.child("chats")
                .child(chatId)
                .removeEventListener(it)
        }

        taskListener?.let {
            db.child("tasks")
                .child(taskId)
                .removeEventListener(it)
        }
    }
}