package com.CampusGO.app.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class CampusNotification(
    var id: String = "",
    var receiverUserId: String = "",
    var title: String = "",
    var body: String = "",
    var type: String = "",
    var timestamp: Long = 0L,
    var taskId: String = "",
    var chatId: String = ""
)
