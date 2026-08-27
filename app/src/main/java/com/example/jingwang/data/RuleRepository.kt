package com.example.jingwang.data

import android.content.Context
import android.util.AtomicFile
import com.example.jingwang.core.model.RuleMetadata
import com.example.jingwang.core.rules.ParsedRuleList
import com.example.jingwang.core.rules.RuleListException
import com.example.jingwang.core.rules.RuleListParser
import com.example.jingwang.core.rules.RuleMatcher
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class RuleState(
    val ready: Boolean = false,
    val metadata: RuleMetadata = RuleMetadata(),
    val error: String? = null,
)

class RuleRepository(
    private val context: Context,
    private val privacyRepository: PrivacyRepository,
) {
    private val ruleDirectory = File(context.noBackupFilesDir, "rules")
    private val updatedRuleFile = AtomicFile(File(ruleDirectory, "anti-ad-domains.txt"))
    private val mutableState = MutableStateFlow(RuleState())
    private val loadLock = Any()
    @Volatile private var blockedDomains: Set<String> = emptySet()

    val state: StateFlow<RuleState> = mutableState.asStateFlow()

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        if (mutableState.value.ready) return@withContext
        synchronized(loadLock) {
            if (mutableState.value.ready) return@synchronized
            val parsed = try {
                if (updatedRuleFile.baseFile.isFile) {
                    updatedRuleFile.openRead().use(RuleListParser::parse)
                } else {
                    context.assets.open(ASSET_FILE).use(RuleListParser::parse)
                }
            } catch (error: Exception) {
                mutableState.value = RuleState(error = "规则加载失败：${safeMessage(error)}")
                return@synchronized
            }
            installParsed(parsed, if (updatedRuleFile.baseFile.isFile) "anti-AD 手动更新" else "内置 anti-AD 快照")
        }
    }

    fun matcher(whitelist: Set<String>, customBlockedDomains: Set<String>): RuleMatcher =
        RuleMatcher(blockedDomains, whitelist, customBlockedDomains)

    suspend fun updateManually(): Result<RuleMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            ensureLoaded()
            val connection = openFixedConnection()
            val parsed = try {
                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK) throw RuleListException("服务器返回 HTTP $status")
                val declaredLength = connection.contentLengthLong
                if (declaredLength > RuleListParser.MAX_BYTES) throw RuleListException("服务器声明的文件过大")
                connection.inputStream.use(RuleListParser::parse)
            } finally {
                connection.disconnect()
            }
            RuleListParser.validateRemote(parsed, blockedDomains.size)
            writeAtomically(parsed.rawBytes)
            installParsed(parsed, "anti-AD 手动更新")
            mutableState.value.metadata
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(error = "更新失败，已保留旧规则：${safeMessage(error)}")
        }
    }

    private fun openFixedConnection(): HttpsURLConnection {
        val url = URL(UPDATE_URL)
        check(url.protocol == "https" && url.host == UPDATE_HOST && url.port == -1)
        return (url.openConnection() as HttpsURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "text/plain")
            setRequestProperty("User-Agent", "Jingwang/1.0")
            useCaches = false
        }
    }

    private fun writeAtomically(bytes: ByteArray) {
        ruleDirectory.mkdirs()
        val stream = updatedRuleFile.startWrite()
        try {
            stream.write(bytes)
            updatedRuleFile.finishWrite(stream)
        } catch (error: Throwable) {
            updatedRuleFile.failWrite(stream)
            throw error
        }
    }

    private fun installParsed(parsed: ParsedRuleList, source: String) {
        blockedDomains = parsed.domains.toHashSet()
        val metadata = RuleMetadata(
            source = source,
            version = parsed.version,
            updatedAtEpochMillis = if (source.startsWith("内置")) 0L else System.currentTimeMillis(),
            entryCount = parsed.domains.size,
            sha256 = parsed.sha256,
        )
        privacyRepository.setRuleMetadata(metadata)
        mutableState.value = RuleState(ready = true, metadata = metadata)
    }

    private fun safeMessage(error: Throwable): String = when (error) {
        is RuleListException -> error.message ?: "规则验证失败"
        else -> "网络或文件操作失败（${error.javaClass.simpleName}）"
    }

    companion object {
        const val UPDATE_URL = "https://anti-ad.net/domains.txt"
        const val UPDATE_HOST = "anti-ad.net"
        const val ASSET_FILE = "anti-ad-domains.txt"
    }
}
