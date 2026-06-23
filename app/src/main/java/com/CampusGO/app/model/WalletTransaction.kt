package com.CampusGO.app.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class WalletTransaction(
    var id: String = "",
    var type: String = WalletTransactionType.TOP_UP,
    var amount: Double = 0.0,
    var status: String = WalletTransactionStatus.COMPLETED,
    var description: String = "",
    var taskId: String = "",
    var taskNumber: String = "",
    var otherUserId: String = "",
    var otherUserName: String = "",
    var createdAt: Long = 0L
)

object WalletTransactionType {
    const val TOP_UP = "TOP_UP"
    const val CASH_OUT = "CASH_OUT"
    const val TASK_PAYMENT_SENT = "TASK_PAYMENT_SENT"
    const val TASK_PAYMENT_RECEIVED = "TASK_PAYMENT_RECEIVED"
}

object WalletTransactionStatus {
    const val COMPLETED = "COMPLETED"
    const val PROCESSING = "PROCESSING"
    const val FAILED = "FAILED"
}
