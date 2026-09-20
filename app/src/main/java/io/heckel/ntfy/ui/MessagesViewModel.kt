package io.heckel.ntfy.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import io.heckel.ntfy.db.HomeConfig
import io.heckel.ntfy.db.MessageWithSubscription
import io.heckel.ntfy.db.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * View model for the home ("messages") screen. Shows messages from the
 * subscriptions configured in the home visibility setting: either ALL
 * subscriptions, or only the user-selected ones. Filtering happens in the
 * database query (WHERE s.id IN (...)), not in the UI layer.
 *
 * The query is re-created automatically when
 * - messages are added/updated/deleted (Room invalidation), or
 * - the home visibility config changes (SharedPreferences flow).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModel(private val repository: Repository) : ViewModel() {

    private val _config = MutableLiveData<HomeConfig>()
    val config: LiveData<HomeConfig> = _config

    private val _searchQuery = MutableLiveData<String?>(null)
    val searchQuery: LiveData<String?> = _searchQuery

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _config.postValue(repository.getHomeConfig())
        }
    }

    fun setSearchQuery(query: String?) {
        _searchQuery.value = query?.takeIf { it.isNotBlank() }
    }

    fun list(): LiveData<List<MessageWithSubscription>> {
        io.heckel.ntfy.util.Log.d("NtfyMessagesVM", "list() subscribed, building flow")
        return repository.getHomeConfigFlow()
            .onEach { config ->
                io.heckel.ntfy.util.Log.d("NtfyMessagesVM", "config emitted: mode=${config.mode}, ids=${config.selectedSubscriptionIds.size}")
                _config.postValue(config)
            }
            .flatMapLatest { config ->
                io.heckel.ntfy.util.Log.d("NtfyMessagesVM", "querying for mode=${config.mode}")
                val flow: Flow<List<MessageWithSubscription>> = when (config.mode) {
                    Repository.HOME_MODE_SELECTED ->
                        if (config.selectedSubscriptionIds.isEmpty()) {
                            flowOf(emptyList())
                        } else {
                            repository.getMessagesBySubscriptionsFlow(config.selectedSubscriptionIds)
                        }
                    else -> repository.getAllMessagesFlow()
                }
                flow
            }
            .onEach { messages ->
                io.heckel.ntfy.util.Log.d("NtfyMessagesVM", "messages emitted: ${messages.size}")
            }
            .asLiveData()
    }
}

class MessagesViewModelFactory(private val repository: Repository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MessagesViewModel(repository) as T
    }
}
