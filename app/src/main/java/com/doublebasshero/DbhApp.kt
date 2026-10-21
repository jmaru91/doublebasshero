package com.doublebasshero

import android.app.Application
import android.content.Context
import com.doublebasshero.data.DbhDatabase
import com.doublebasshero.data.ProfileEntity
import com.doublebasshero.data.Repository
import com.doublebasshero.notify.Channels
import com.doublebasshero.session.SessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DbhApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val db by lazy { DbhDatabase.create(this) }
    val repository by lazy { Repository(db.dao()) }
    val profile: StateFlow<ProfileEntity?> by lazy {
        repository.profileFlow.stateIn(scope, SharingStarted.Eagerly, null)
    }
    val controller by lazy { SessionController(this, repository, scope) { profile.value } }

    override fun onCreate() {
        super.onCreate()
        Channels.ensure(this)
        profile
    }
}

val Context.app: DbhApp get() = applicationContext as DbhApp
