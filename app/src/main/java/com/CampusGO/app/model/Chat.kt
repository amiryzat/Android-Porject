package com.CampusGO.app.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Chat(
    var id: String = "",
    var taskId: String = "",
    var taskTitle: String = "",
    var taskNumber: String = "",
    var posterId: String = "",
    var posterName: String = "",
    var runnerId: String = "",
    var runnerName: String = "",
    var lastMessage: String = "",
    var lastMessageTime: Long = 0L,
    var finalPrice: Double = 0.0
)
