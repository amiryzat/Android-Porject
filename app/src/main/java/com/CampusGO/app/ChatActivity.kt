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
import com.CampusGO.app.util.applyStatusBarInsets
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private var otherUserPresenceListener: ValueEventListener? = null
    private var otherUserId: String? = null

    private lateinit var chatId: String
    private lateinit var taskId: String

    private val pickImageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handleImageUri(uri)
        }
    }

    private val pickVideoLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            sendVideoMessage("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            sendImageMessage(bitmap)
        }
    }

    private var isRequestingPermissionForIssue = false
    private var issuePhotoBitmap: android.graphics.Bitmap? = null
    private var issueDialogImageView: android.widget.ImageView? = null

    private val takeIssuePictureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            issuePhotoBitmap = bitmap
            issueDialogImageView?.setImageBitmap(bitmap)
            issueDialogImageView?.visibility = View.VISIBLE
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (isRequestingPermissionForIssue) {
                takeIssuePictureLauncher.launch(null)
            } else {
                takePictureLauncher.launch(null)
            }
        } else {
            Toast.makeText(this, "Camera permission required to take pictures", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "ChatActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.llHeader.applyStatusBarInsets()

        taskId = intent.getStringExtra("taskId") ?: run {
            Toast.makeText(this, "Missing task ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        chatId = intent.getStringExtra("chatId") ?: run {
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
            },
            onMessageLongClick = { msg ->
                showManageMessageDialog(msg)
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

        binding.btnAttach.setOnClickListener {
            showAttachmentOptionsDialog()
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
                    updateProgressCard(currentTask)
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

                    val chat = currentChat
                    if (chat != null) {
                        val uid = auth.currentUser?.uid ?: return
                        val otherId = if (uid == chat.posterId) chat.runnerId else chat.posterId
                        if (otherId.isNotEmpty()) {
                            listenOtherUserPresence(otherId)
                        }
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

                val chat = currentChat
                if (chat != null) {
                    val receiverId = if (uid == chat.posterId) chat.runnerId else chat.posterId
                    NotificationHelper.sendNotificationToUser(
                        receiverUserId = receiverId,
                        title = "New Message!",
                        body = "$senderName: $content",
                        type = "CHAT",
                        taskId = taskId,
                        chatId = chatId
                    )
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

                val receiverId = if (uid == chat.posterId) chat.runnerId else chat.posterId
                NotificationHelper.sendNotificationToUser(
                    receiverUserId = receiverId,
                    title = "New Price Offer! 💰",
                    body = "$senderName: $offerText",
                    type = "CHAT",
                    taskId = taskId,
                    chatId = chatId
                )
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

        val isPoster = uid == chat.posterId
        if (isPoster) {
            db.child("wallets").child(uid).get()
                .addOnSuccessListener { snapshot ->
                    val wallet = snapshot.getValue(com.CampusGO.app.model.Wallet::class.java)
                    val balance = wallet?.balance ?: 0.0
                    if (balance < counterPrice) {
                        showInsufficientBalanceDialog(counterPrice, balance)
                    } else {
                        executeSendCounterOffer(counterPrice, originalOfferMsg, chat, uid, senderName)
                    }
                }
                .addOnFailureListener {
                    showInsufficientBalanceDialog(counterPrice, 0.0)
                }
        } else {
            executeSendCounterOffer(counterPrice, originalOfferMsg, chat, uid, senderName)
        }
    }

    private fun executeSendCounterOffer(
        counterPrice: Double,
        originalOfferMsg: Message,
        chat: com.CampusGO.app.model.Chat,
        uid: String,
        senderName: String
    ) {
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

                val receiverId = if (uid == chat.posterId) chat.runnerId else chat.posterId
                NotificationHelper.sendNotificationToUser(
                    receiverUserId = receiverId,
                    title = "Counter Offer Sent! 💰",
                    body = "$senderName: $counterText",
                    type = "CHAT",
                    taskId = taskId,
                    chatId = chatId
                )
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

        if (currentUserIsPoster) {
            db.child("wallets").child(uid).get()
                .addOnSuccessListener { snapshot ->
                    val wallet = snapshot.getValue(com.CampusGO.app.model.Wallet::class.java)
                    val balance = wallet?.balance ?: 0.0
                    if (balance < priceMsg.priceAmount) {
                        showInsufficientBalanceDialog(priceMsg.priceAmount, balance)
                    } else {
                        executeAcceptPriceOffer(priceMsg, chat, uid, myName)
                    }
                }
                .addOnFailureListener {
                    showInsufficientBalanceDialog(priceMsg.priceAmount, 0.0)
                }
        } else {
            executeAcceptPriceOffer(priceMsg, chat, uid, myName)
        }
    }

    private fun executeAcceptPriceOffer(
        priceMsg: Message,
        chat: com.CampusGO.app.model.Chat,
        uid: String,
        myName: String
    ) {
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

                val receiverId = if (uid == chat.posterId) chat.runnerId else chat.posterId
                NotificationHelper.sendNotificationToUser(
                    receiverUserId = receiverId,
                    title = "Price Agreed! 🤝",
                    body = "$myName: $agreedText",
                    type = "CHAT",
                    taskId = taskId,
                    chatId = chatId
                )

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

    private fun showInsufficientBalanceDialog(required: Double, current: Double) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Insufficient Wallet Balance")
            .setMessage(
                "You need RM ${String.format(java.util.Locale.US, "%.2f", required)} to proceed with this price.\n" +
                "Current Balance: RM ${String.format(java.util.Locale.US, "%.2f", current)}\n\n" +
                "Please top up your wallet first."
            )
            .setPositiveButton("Top Up Wallet") { _, _ ->
                startActivity(Intent(this, WalletTopUpActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun showManageMessageDialog(msg: Message) {
        val isText = msg.type == MessageType.TEXT
        val isMedia = msg.type == MessageType.IMAGE || msg.type == MessageType.VIDEO

        if (!isText && !isMedia) return

        val uid = auth.currentUser?.uid ?: return
        val isMyMsg = msg.senderId == uid

        val options = if (isMyMsg && isText) {
            arrayOf("Edit Message", "Delete Message")
        } else {
            arrayOf("Delete Message")
        }

        AlertDialog.Builder(this)
            .setTitle("Manage Message")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Edit Message" -> showEditMessageDialog(msg)
                    "Delete Message" -> confirmDeleteMessage(msg)
                }
            }
            .show()
    }

    private fun showEditMessageDialog(msg: Message) {
        val input = EditText(this).apply {
            setText(msg.content)
            setSelection(msg.content.length)
            setPadding(40, 20, 40, 20)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Message")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newContent = input.text.toString().trim()
                if (newContent.isNotEmpty()) {
                    db.child("messages")
                        .child(chatId)
                        .child(msg.id)
                        .child("content")
                        .setValue(newContent)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Message updated", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update message: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteMessage(msg: Message) {
        AlertDialog.Builder(this)
            .setTitle("Delete Message")
            .setMessage("Are you sure you want to delete this message?")
            .setPositiveButton("Delete") { _, _ ->
                db.child("messages")
                    .child(chatId)
                    .child(msg.id)
                    .removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to delete message: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun listenOtherUserPresence(otherId: String) {
        if (otherUserId == otherId && otherUserPresenceListener != null) return

        otherUserId?.let { oldId ->
            otherUserPresenceListener?.let { listener ->
                FirebaseDatabase.getInstance().getReference("presence/$oldId").removeEventListener(listener)
            }
        }

        otherUserId = otherId

        val presenceRef = FirebaseDatabase.getInstance().getReference("presence/$otherId")
        otherUserPresenceListener = presenceRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L

                updatePresenceStatus(online, lastSeen)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to read presence: ${error.message}")
            }
        })
    }

    private fun updatePresenceStatus(online: Boolean, lastSeen: Long) {
        if (online) {
            binding.tvTaskRef.text = "Online"
            binding.tvTaskRef.setTextColor(android.graphics.Color.parseColor("#10B981"))
        } else {
            if (lastSeen > 0L) {
                val timeStr = formatLastSeenTime(lastSeen)
                binding.tvTaskRef.text = "Last seen $timeStr"
            } else {
                binding.tvTaskRef.text = "Offline"
            }
            binding.tvTaskRef.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
        }
    }

    private fun formatLastSeenTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diff)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            else -> {
                val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    private fun showAttachmentOptionsDialog() {
        val options = arrayOf("Take Photo", "Choose Photo", "Choose Video")
        AlertDialog.Builder(this)
            .setTitle("Send Media")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndTakePhoto()
                    1 -> pickImageLauncher.launch("image/*")
                    2 -> pickVideoLauncher.launch("video/*")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkCameraPermissionAndTakePhoto() {
        isRequestingPermissionForIssue = false
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            takePictureLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun handleImageUri(uri: android.net.Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                sendImageMessage(bitmap)
            } else {
                Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image from URI", e)
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendImageMessage(bitmap: android.graphics.Bitmap) {
        val maxDim = 400
        val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val (w, h) = if (ratio > 1) {
                Pair(maxDim, (maxDim / ratio).toInt())
            } else {
                Pair((maxDim * ratio).toInt(), maxDim)
            }
            android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }

        val baos = java.io.ByteArrayOutputStream()
        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, baos)
        val bytes = baos.toByteArray()
        val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)

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
            "content" to base64Str,
            "type" to MessageType.IMAGE,
            "priceAmount" to 0.0,
            "priceStatus" to "",
            "createdAt" to ServerValue.TIMESTAMP
        )

        msgRef.setValue(msgMap)
            .addOnSuccessListener {
                Log.d(TAG, "Image message sent successfully to chatId: $chatId")

                db.child("chats")
                    .child(chatId)
                    .updateChildren(
                        mapOf<String, Any>(
                            "lastMessage" to "🖼️ Photo",
                            "lastMessageTime" to ServerValue.TIMESTAMP
                        )
                    )
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update last message", e)
                    }

                val chat = currentChat
                if (chat != null) {
                    val receiverId = if (uid == chat.posterId) chat.runnerId else chat.posterId
                    NotificationHelper.sendNotificationToUser(
                        receiverUserId = receiverId,
                        title = "New Message!",
                        body = "$senderName sent a photo",
                        type = "CHAT",
                        taskId = taskId,
                        chatId = chatId
                    )
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send image message", e)
                Toast.makeText(this, "Failed to send photo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendVideoMessage(videoUrl: String) {
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
            "content" to videoUrl,
            "type" to MessageType.VIDEO,
            "priceAmount" to 0.0,
            "priceStatus" to "",
            "createdAt" to ServerValue.TIMESTAMP
        )

        msgRef.setValue(msgMap)
            .addOnSuccessListener {
                Log.d(TAG, "Video message sent successfully to chatId: $chatId")

                db.child("chats")
                    .child(chatId)
                    .updateChildren(
                        mapOf<String, Any>(
                            "lastMessage" to "🎥 Video",
                            "lastMessageTime" to ServerValue.TIMESTAMP
                        )
                    )
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update last message", e)
                    }

                val chat = currentChat
                if (chat != null) {
                    val receiverId = if (uid == chat.posterId) chat.runnerId else chat.posterId
                    NotificationHelper.sendNotificationToUser(
                        receiverUserId = receiverId,
                        title = "New Message!",
                        body = "$senderName sent a video",
                        type = "CHAT",
                        taskId = taskId,
                        chatId = chatId
                    )
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send video message", e)
                Toast.makeText(this, "Failed to send video: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateProgressCard(task: Task?) {
        val chat = currentChat
        if (task == null || chat == null) {
            binding.llTaskProgressCard.visibility = View.GONE
            return
        }

        if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.CANCELLED) {
            binding.llTaskProgressCard.visibility = View.GONE
            return
        }

        // If the task is accepted by someone else, hide the progress card
        if (task.runnerId.isNotEmpty() && task.runnerId != chat.runnerId) {
            binding.llTaskProgressCard.visibility = View.GONE
            return
        }

        binding.llTaskProgressCard.visibility = View.VISIBLE
        val uid = auth.currentUser?.uid ?: return
        val isRunner = task.runnerId == uid
        val isPoster = task.posterId == uid

        // 1. Set Status Text
        binding.tvProgressStatus.text = when (task.status) {
            TaskStatus.OPEN -> "POSTED"
            TaskStatus.ACCEPTED -> "ACCEPTED"
            TaskStatus.ON_THE_WAY -> "ON THE WAY"
            TaskStatus.DELIVERED -> "DELIVERED"
            else -> task.status
        }

        // 2. Set Steps and Connecting Line Animation
        val statusOrder = listOf(
            TaskStatus.OPEN,
            TaskStatus.ACCEPTED,
            TaskStatus.ON_THE_WAY,
            TaskStatus.DELIVERED,
            TaskStatus.COMPLETED
        )
        val currentIndex = statusOrder.indexOf(task.status).coerceAtLeast(0)

        val dots = listOf(
            binding.dotPosted,
            binding.dotAccepted,
            binding.dotOnTheWay,
            binding.dotDelivered,
            binding.dotCompleted
        )

        val lines = listOf(
            binding.line1,
            binding.line2,
            binding.line3,
            binding.line4
        )

        val labels = listOf(
            binding.lblPosted,
            binding.lblAccepted,
            binding.lblOnTheWay,
            binding.lblDelivered,
            binding.lblCompleted
        )

        // Format all dots and labels
        for (i in 0..4) {
            val dot = dots[i]
            val label = labels[i]
            when {
                i < currentIndex -> {
                    // Completed step
                    dot.background = getCircleDrawable("#10B981") // Emerald green
                    dot.text = "✓"
                    dot.setTextColor(android.graphics.Color.WHITE)
                    label.setTextColor(android.graphics.Color.parseColor("#10B981"))
                }
                i == currentIndex -> {
                    // Current active step / source of active transition
                    dot.background = getCircleDrawable("#3B82F6") // Active blue
                    dot.text = (i + 1).toString()
                    dot.setTextColor(android.graphics.Color.WHITE)
                    label.setTextColor(android.graphics.Color.parseColor("#3B82F6"))
                }
                else -> {
                    // Future step
                    dot.background = getCircleDrawable("#E2E8F0") // Light slate
                    dot.text = (i + 1).toString()
                    dot.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                    label.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                }
            }
        }

        // Format and animate lines
        for (i in 0..3) {
            val line = lines[i]
            when {
                i < currentIndex -> {
                    // Completed transition segment: static green
                    line.isIndeterminate = false
                    line.progress = 100
                    line.setIndicatorColor(android.graphics.Color.parseColor("#10B981"))
                }
                i == currentIndex -> {
                    // Current active transition segment: animating/moving blue
                    line.isIndeterminate = true
                    line.setIndicatorColor(android.graphics.Color.parseColor("#3B82F6"))
                }
                else -> {
                    // Future transition segment: static gray
                    line.isIndeterminate = false
                    line.progress = 0
                    line.setIndicatorColor(android.graphics.Color.parseColor("#E2E8F0"))
                }
            }
        }

        // 3. Set Action Buttons visibility/interactivity
        if (isRunner) {
            binding.llRunnerProgressActions.visibility = View.VISIBLE

            // Configure Next Action Button
            when (task.status) {
                TaskStatus.OPEN -> {
                    binding.btnNextProgress.visibility = View.GONE
                }
                TaskStatus.ACCEPTED -> {
                    binding.btnNextProgress.visibility = View.VISIBLE
                    binding.btnNextProgress.isEnabled = true
                    binding.btnNextProgress.text = "Start Journey (On Way)"
                    binding.btnNextProgress.setOnClickListener {
                        db.child("tasks").child(task.id).child("status").setValue(TaskStatus.ON_THE_WAY)
                    }
                }
                TaskStatus.ON_THE_WAY -> {
                    binding.btnNextProgress.visibility = View.VISIBLE
                    binding.btnNextProgress.isEnabled = true
                    binding.btnNextProgress.text = "Mark as Delivered"
                    binding.btnNextProgress.setOnClickListener {
                        db.child("tasks").child(task.id).child("status").setValue(TaskStatus.DELIVERED)
                    }
                }
                TaskStatus.DELIVERED -> {
                    binding.btnNextProgress.visibility = View.VISIBLE
                    binding.btnNextProgress.text = "Waiting for Poster Confirm"
                    binding.btnNextProgress.isEnabled = false
                }
                else -> {
                    binding.llRunnerProgressActions.visibility = View.GONE
                }
            }

            // Configure Issue Button
            binding.btnProgressIssue.setOnClickListener {
                showReportIssueDialog(task.id)
            }

        } else {
            binding.llRunnerProgressActions.visibility = View.GONE
        }

        // 4. Poster Actions Buttons configuration
        if (isPoster && task.status != TaskStatus.OPEN) {
            binding.llPosterProgressActions.visibility = View.VISIBLE
            binding.btnCancelRunner.setOnClickListener {
                showPosterCancelOptions(task)
            }
        } else {
            binding.llPosterProgressActions.visibility = View.GONE
        }
    }

    private fun getCircleDrawable(colorStr: String): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.parseColor(colorStr))
        }
    }

    private fun showReportIssueDialog(taskId: String) {
        issuePhotoBitmap = null

        val ctx = this
        val dialogLayout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val tvLabel = android.widget.TextView(ctx).apply {
            text = "Describe your issue:"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#0F172A"))
            setPadding(0, 0, 0, 10)
        }
        dialogLayout.addView(tvLabel)

        val etExcuse = android.widget.EditText(ctx).apply {
            hint = "e.g., Traffic jam, vehicle issue..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            textSize = 14f
        }
        dialogLayout.addView(etExcuse)

        val btnCamera = com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.style.Widget_Material3_Button_OutlinedButton).apply {
            text = "Take Proof Picture 📷"
            textSize = 12f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 20
                bottomMargin = 10
            }
        }
        dialogLayout.addView(btnCamera)

        val ivPreview = android.widget.ImageView(ctx).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                300,
                300
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = 10
                bottomMargin = 10
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        dialogLayout.addView(ivPreview)

        issueDialogImageView = ivPreview

        btnCamera.setOnClickListener {
            isRequestingPermissionForIssue = true
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    ctx,
                    android.Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                takeIssuePictureLauncher.launch(null)
            } else {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }

        android.app.AlertDialog.Builder(ctx)
            .setTitle("Report Task Issue")
            .setView(dialogLayout)
            .setPositiveButton("Submit Report") { _, _ ->
                val excuse = etExcuse.text.toString().trim()
                if (excuse.isNotEmpty()) {
                    submitIssueReport(excuse, issuePhotoBitmap)
                } else {
                    Toast.makeText(ctx, "Please describe the issue.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Cancel & Release Task") { _, _ ->
                val excuse = etExcuse.text.toString().trim()
                if (excuse.isNotEmpty()) {
                    submitIssueReport(excuse, issuePhotoBitmap)
                    releaseTask(taskId, excuse)
                } else {
                    Toast.makeText(ctx, "Please describe the issue to release the task.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .setOnDismissListener {
                issueDialogImageView = null
            }
            .show()
    }

    private fun releaseTask(taskId: String, excuse: String) {
        val uid = auth.currentUser?.uid ?: return
        val senderName = auth.currentUser?.displayName ?: "Unknown"

        val textMsgRef = db.child("messages").child(chatId).push()
        val textMsgId = textMsgRef.key ?: return
        val textContent = "🚨 TASK RELEASED: Runner $senderName has released this task. Excuse: \"$excuse\""

        val textMsgMap = mapOf<String, Any>(
            "id" to textMsgId,
            "senderId" to uid,
            "senderName" to senderName,
            "content" to textContent,
            "type" to MessageType.TEXT,
            "priceAmount" to 0.0,
            "priceStatus" to "",
            "createdAt" to ServerValue.TIMESTAMP
        )
        textMsgRef.setValue(textMsgMap)

        db.child("chats").child(chatId).updateChildren(
            mapOf<String, Any>(
                "lastMessage" to "🚨 Task released: $excuse",
                "lastMessageTime" to ServerValue.TIMESTAMP
            )
        )

        db.child("tasks").child(taskId).updateChildren(
            mapOf<String, Any?>(
                "status" to TaskStatus.OPEN,
                "runnerId" to "",
                "runnerName" to "",
                "agreedPrice" to 0.0
            )
        ).addOnSuccessListener {
            Toast.makeText(this, "Task released. Returned to feed.", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to release task: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPosterCancelOptions(task: Task) {
        val ctx = this
        android.app.AlertDialog.Builder(ctx)
            .setTitle("Cancel & Remove Runner")
            .setMessage("Do you want to return this task to the feed so other runners can accept it, or cancel it permanently?")
            .setPositiveButton("Return to Feed") { _, _ ->
                returnTaskToFeed(task)
            }
            .setNeutralButton("Cancel Permanently") { _, _ ->
                cancelTaskPermanently(task)
            }
            .setNegativeButton("Go Back", null)
            .show()
    }

    private fun returnTaskToFeed(task: Task) {
        val uid = auth.currentUser?.uid ?: return
        val senderName = auth.currentUser?.displayName ?: "Unknown"

        val textMsgRef = db.child("messages").child(chatId).push()
        val textMsgId = textMsgRef.key ?: return
        val textContent = "🚨 RUNNER REMOVED: Task poster has returned this task to the feed."

        val textMsgMap = mapOf<String, Any>(
            "id" to textMsgId,
            "senderId" to uid,
            "senderName" to senderName,
            "content" to textContent,
            "type" to MessageType.TEXT,
            "priceAmount" to 0.0,
            "priceStatus" to "",
            "createdAt" to ServerValue.TIMESTAMP
        )
        textMsgRef.setValue(textMsgMap)

        db.child("chats").child(chatId).updateChildren(
            mapOf<String, Any>(
                "lastMessage" to "🚨 Runner removed: task returned to feed",
                "lastMessageTime" to ServerValue.TIMESTAMP
            )
        )

        db.child("tasks").child(task.id).updateChildren(
            mapOf<String, Any?>(
                "status" to TaskStatus.OPEN,
                "runnerId" to "",
                "runnerName" to "",
                "agreedPrice" to 0.0
            )
        ).addOnSuccessListener {
            Toast.makeText(this, "Runner removed. Task returned to feed.", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to update task: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelTaskPermanently(task: Task) {
        val uid = auth.currentUser?.uid ?: return
        val senderName = auth.currentUser?.displayName ?: "Unknown"

        val textMsgRef = db.child("messages").child(chatId).push()
        val textMsgId = textMsgRef.key ?: return
        val textContent = "🚨 TASK CANCELLED: Task poster has cancelled this task permanently."

        val textMsgMap = mapOf<String, Any>(
            "id" to textMsgId,
            "senderId" to uid,
            "senderName" to senderName,
            "content" to textContent,
            "type" to MessageType.TEXT,
            "priceAmount" to 0.0,
            "priceStatus" to "",
            "createdAt" to ServerValue.TIMESTAMP
        )
        textMsgRef.setValue(textMsgMap)

        db.child("chats").child(chatId).updateChildren(
            mapOf<String, Any>(
                "lastMessage" to "🚨 Task cancelled permanently",
                "lastMessageTime" to ServerValue.TIMESTAMP
            )
        )

        db.child("tasks").child(task.id).child("status").setValue(TaskStatus.CANCELLED)
            .addOnSuccessListener {
                Toast.makeText(this, "Task cancelled permanently.", Toast.LENGTH_SHORT).show()
                showRepostPrompt(task)
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to cancel task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRepostPrompt(task: Task) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Repost Task?")
            .setMessage("Would you like to repost this task now to invite new offers?")
            .setPositiveButton("Repost") { _, _ ->
                val intent = Intent(this, CreateTaskActivity::class.java).apply {
                    putExtra("repostTitle", task.title)
                    putExtra("repostDescription", task.description)
                    putExtra("repostPrice", task.price)
                    putExtra("repostCategory", task.category)
                    putExtra("repostIsNegotiable", task.isNegotiable)
                    putExtra("repostIsEmergency", task.isEmergency)
                    putExtra("repostPickupLat", task.pickupLatitude)
                    putExtra("repostPickupLon", task.pickupLongitude)
                    putExtra("repostPickupAddress", task.pickup)
                    putExtra("repostDropoffLat", task.dropoffLatitude)
                    putExtra("repostDropoffLon", task.dropoffLongitude)
                    putExtra("repostDropoffAddress", task.dropoff)
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("No, Thanks") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun submitIssueReport(excuse: String, photo: android.graphics.Bitmap?) {
        val uid = auth.currentUser?.uid ?: return
        val senderName = auth.currentUser?.displayName ?: "Unknown"

        val textMsgRef = db.child("messages").child(chatId).push()
        val textMsgId = textMsgRef.key ?: return
        val textContent = "🚨 ISSUE REPORTED: \"$excuse\""

        val textMsgMap = mapOf<String, Any>(
            "id" to textMsgId,
            "senderId" to uid,
            "senderName" to senderName,
            "content" to textContent,
            "type" to MessageType.TEXT,
            "priceAmount" to 0.0,
            "priceStatus" to "",
            "createdAt" to ServerValue.TIMESTAMP
        )
        textMsgRef.setValue(textMsgMap)

        if (photo != null) {
            sendImageMessage(photo)
        }

        db.child("chats").child(chatId).updateChildren(
            mapOf<String, Any>(
                "lastMessage" to "🚨 Issue: $excuse",
                "lastMessageTime" to ServerValue.TIMESTAMP
            )
        )

        Toast.makeText(this, "Issue reported to the poster.", Toast.LENGTH_SHORT).show()
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

        otherUserId?.let { oldId ->
            otherUserPresenceListener?.let { listener ->
                FirebaseDatabase.getInstance().getReference("presence/$oldId").removeEventListener(listener)
            }
        }
    }
}