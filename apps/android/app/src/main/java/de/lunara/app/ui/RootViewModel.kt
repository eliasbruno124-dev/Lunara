package de.lunara.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.preferences.AppPreferencesState
import de.lunara.app.data.repository.AccountRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    preferences: AppPreferences,
    private val accountRepository: AccountRepository,
) : ViewModel() {
    private val _loggingOut = MutableStateFlow(false)
    val loggingOut = _loggingOut.asStateFlow()
    val preferences: StateFlow<AppPreferencesState?> = preferences.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun logout() {
        if (_loggingOut.value) return
        viewModelScope.launch {
            _loggingOut.value = true
            try {
                accountRepository.eraseLocalAccount()
            } finally {
                _loggingOut.value = false
            }
        }
    }
}
