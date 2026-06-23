package com.CampusGO.app.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Wallet(
    var userId: String = "",
    var balance: Double = 0.0,
    var totalEarned: Double = 0.0,
    var totalSpent: Double = 0.0,
    var updatedAt: Long = 0L
)
