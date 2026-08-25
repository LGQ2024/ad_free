package com.example.jingwang.data

import com.example.jingwang.core.model.VpnRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VpnStateRepository {
    private val mutableState = MutableStateFlow(VpnRuntimeState())
    val state: StateFlow<VpnRuntimeState> = mutableState.asStateFlow()

    fun update(value: VpnRuntimeState) {
        mutableState.value = value
    }
}
