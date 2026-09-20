package io.heckel.ntfy.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.heckel.ntfy.db.MessageWithSubscription
import io.heckel.ntfy.db.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * View model for the home ("messages") screen that shows all messages from all
 * subscribed topics, newest first. Data is re-emitted automatically by Room whenever
 * a message is added/updated/deleted, so new messages appear without manual refresh.
 */
class MessagesViewModel(private val repository: Repository) : ViewModel() {
    private val _searchQuery = MutableLiveData<String?>(null)
    val searchQuery: LiveData<String?> = _searchQuery

    fun list(): LiveData<List<MessageWithSubscription>> {
        return repository.getAllMessagesLiveData()
    }

    fun setSearchQuery(query: String?) {
        _searchQuery.value = query?.takeIf { it.isNotBlank() }
    }
}

class MessagesViewModelFactory(private val repository: Repository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MessagesViewModel(repository) as T
    }
}
