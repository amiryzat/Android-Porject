package com.CampusGO.app.util

object ChatIdHelper {

    fun buildChatId(taskId: String, userA: String, userB: String): String {
        val users = listOf(userA, userB).sorted()
        return "chat_${users[0]}_${users[1]}"
    }
}