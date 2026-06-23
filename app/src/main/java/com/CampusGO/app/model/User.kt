package com.CampusGO.app.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    var uid: String = "",
    var fullName: String = "",
    var email: String = "",
    var rating: Double = 5.0,
    var totalReviews: Int = 0,
    var profilePicture: String = "",
    var createdAt: Long = 0L
)
