package com.planttracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.planttracker.data.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    val soundEnabled: StateFlow<Boolean> = settingsManager.soundEnabled
    val vibrationEnabled: StateFlow<Boolean> = settingsManager.vibrationEnabled

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setSoundEnabled(enabled)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setVibrationEnabled(enabled)
        }
    }
}
