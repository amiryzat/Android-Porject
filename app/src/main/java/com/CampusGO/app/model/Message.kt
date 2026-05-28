package com.CampusGO.app.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Message(
    var id: String = "",
    var senderId: String = "",
    var senderName: String = "",
    var content: String = "",
    var type: String = MessageType.TEXT,
    var priceAmount: Double = 0.0,
    var priceStatus: String = "",
    var createdAt: Long = 0L
)

object MessageType {
    const val TEXT = "TEXT"
    const val PRICE_OFFER = "PRICE_OFFER"
    const val PRICE_AGREED = "PRICE_AGREED"
    const val SYSTEM = "SYSTEM"
}

object PriceStatus {
    const val PENDING = "PENDING"
    const val ACCEPTED = "ACCEPTED"
    const val REJECTED = "REJECTED"
}
