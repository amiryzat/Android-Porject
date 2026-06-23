package com.CampusGO.app.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Task(
    var id: String = "",
    var taskNumber: String = "",
    var title: String = "",
    var category: String = TaskCategory.OTHER,
    var pickup: String = "",
    var dropoff: String = "",
    var description: String = "",
    var price: Double = 0.0,
    var isNegotiable: Boolean = false,
    var isEmergency: Boolean = false,
    var status: String = TaskStatus.OPEN,
    var posterId: String = "",
    var posterName: String = "",
    var posterRating: Double = 5.0,
    var posterReviews: Int = 0,
    var runnerId: String = "",
    var runnerName: String = "",
    var agreedPrice: Double = 0.0,
    var createdAt: Long = 0L,
    var pickupLatitude: Double = 0.0,
    var pickupLongitude: Double = 0.0,
    var dropoffLatitude: Double = 0.0,
    var dropoffLongitude: Double = 0.0,
    var priority: String = TaskPriority.MEDIUM,
    var paymentStatus: String = PaymentStatus.UNPAID,
    var paymentTransferredAt: Long = 0L,
    var paymentAmount: Double = 0.0
)

object TaskPriority {
    const val LOW = "LOW"
    const val MEDIUM = "MEDIUM"
    const val HIGH = "HIGH"
}

object TaskStatus {
    const val OPEN = "OPEN"
    const val ACCEPTED = "ACCEPTED"
    const val ON_THE_WAY = "ON_THE_WAY"
    const val DELIVERED = "DELIVERED"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"
}

object TaskCategory {
    const val FOOD = "FOOD"
    const val PRINT = "PRINT"
    const val PARCEL = "PARCEL"
    const val ERRAND = "ERRAND"
    const val OTHER = "OTHER"
}

object PaymentStatus {
    const val UNPAID = "UNPAID"
    const val PAID = "PAID"
}
