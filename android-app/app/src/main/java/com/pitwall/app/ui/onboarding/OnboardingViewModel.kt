package com.pitwall.app.ui.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OnboardingViewModel : ViewModel() {
    private val _driverName = MutableStateFlow("")
    val driverName: StateFlow<String> = _driverName.asStateFlow()

    private val _skillLevel = MutableStateFlow("beginner")
    val skillLevel: StateFlow<String> = _skillLevel.asStateFlow()

    fun setDriverName(value: String) {
        _driverName.value = value
    }

    fun setSkillLevel(value: String) {
        _skillLevel.value = value
    }

    fun reset() {
        _driverName.value = ""
        _skillLevel.value = "beginner"
    }
}
