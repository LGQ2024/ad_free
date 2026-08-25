package com.example.jingwang

import android.app.Application
import com.example.jingwang.data.PrivacyRepository
import com.example.jingwang.data.QueryLogRepository
import com.example.jingwang.data.RuleRepository
import com.example.jingwang.data.SecureSettingsStore
import com.example.jingwang.data.VpnStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JingwangApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.applicationScope.launch { container.ruleRepository.ensureLoaded() }
    }
}

class AppContainer(application: Application) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val privacyRepository = PrivacyRepository(SecureSettingsStore(application))
    val queryLogRepository = QueryLogRepository()
    val vpnStateRepository = VpnStateRepository()
    val ruleRepository = RuleRepository(application, privacyRepository)
}
