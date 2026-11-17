package com.ste4n1.blechat

import android.app.Application
import android.content.Context
import com.ste4n1.blechat.bluetooth.ChatManager
import com.ste4n1.blechat.data.AppDatabase
import com.ste4n1.blechat.data.ChatRepository
import com.ste4n1.blechat.data.SettingsRepository

class BleChatApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    private val database = AppDatabase.get(context)
    val repository = ChatRepository(database.chatDao(), context.applicationContext)
    val settings = SettingsRepository(context)
    val chatManager = ChatManager(context, repository)
}
