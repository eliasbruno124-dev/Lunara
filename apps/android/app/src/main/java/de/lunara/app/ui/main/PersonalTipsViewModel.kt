package de.lunara.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.remote.dto.PersonalTipDto
import de.lunara.app.data.remote.dto.CreatePersonalTipRequest
import de.lunara.app.data.repository.PersonalTipsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonalTipsState(val userId: String? = null, val baseUrl: String? = null, val tips: List<PersonalTipDto> = emptyList(), val busy: Boolean = false, val error: Boolean = false, val savedRevision: Int = 0)

@HiltViewModel
class PersonalTipsViewModel @Inject constructor(private val preferences: AppPreferences, private val repository: PersonalTipsRepository) : ViewModel() {
    private val mutable = MutableStateFlow(PersonalTipsState())
    val state = mutable.asStateFlow()
    private var binding: Pair<String, String>? = null
    private var epoch = 0

    init {
        viewModelScope.launch {
            preferences.state.map { pref ->
                pref.session?.takeIf { it.role == UserRole.TRACKER && pref.appMode == AppMode.SERVER }
                    ?.let { it.userId to pref.baseUrl.orEmpty() }
            }.distinctUntilChanged().collectLatest { account ->
                binding = account
                epoch++
                mutable.value = PersonalTipsState(userId = account?.first, baseUrl = account?.second)
                if (account != null) reload()
            }
        }
    }

    fun reload() = run { account -> repository.load(account.first, account.second) }
    fun save(draft: CreatePersonalTipRequest) = run(saved = true) { account ->
        val created = repository.create(account.first, account.second, draft)
        mutable.value.tips.filterNot { it.id == created.id } + created
    }
    fun disable(id: String) = run { account ->
        repository.disable(account.first, account.second, id)
        mutable.value.tips.filterNot { it.id == id }
    }

    private fun run(saved: Boolean = false, action: suspend (Pair<String, String>) -> List<PersonalTipDto>) {
        val account = binding ?: return
        val startedEpoch = epoch
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = false)
        viewModelScope.launch {
            try {
                val tips = action(account)
                if (epoch == startedEpoch) mutable.value = mutable.value.copy(tips = tips, savedRevision = mutable.value.savedRevision + if (saved) 1 else 0)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (epoch == startedEpoch) mutable.value = mutable.value.copy(error = true) }
            finally { if (epoch == startedEpoch) mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
