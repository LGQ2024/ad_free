package com.example.jingwang.data

import com.example.jingwang.core.model.QueryLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class QueryLogRepository {
    private val lock = Any()
    private val entries = ArrayDeque<QueryLogEntry>(MAX_ENTRIES)
    private val mutableLogs = MutableStateFlow<List<QueryLogEntry>>(emptyList())
    private var nextId = 0L
    val logs: StateFlow<List<QueryLogEntry>> = mutableLogs.asStateFlow()

    fun add(domain: String, blocked: Boolean) = synchronized(lock) {
        if (entries.size == MAX_ENTRIES) entries.removeFirst()
        entries.addLast(QueryLogEntry(nextId++, System.currentTimeMillis(), domain, blocked))
        mutableLogs.value = entries.toList().asReversed()
    }

    fun clear() = synchronized(lock) {
        entries.clear()
        mutableLogs.value = emptyList()
    }

    companion object {
        const val MAX_ENTRIES = 500
    }
}
