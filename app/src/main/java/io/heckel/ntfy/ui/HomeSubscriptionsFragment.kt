package io.heckel.ntfy.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.db.Subscription
import io.heckel.ntfy.util.displayName
import io.heckel.ntfy.util.topicShortUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Home screen subscriptions" settings screen:
 * - Mode: show messages from ALL subscriptions, or only from SELECTED ones
 * - In SELECTED mode, a Material 3 checkbox list of the current subscriptions
 *
 * The selection is stored as a set of subscription IDs, so renaming a topic
 * keeps the selection intact and deleting a subscription simply orphans the
 * ID (it is cleaned up here and by the home screen observer).
 */
class HomeSubscriptionsFragment : BasePreferenceFragment() {
    private lateinit var repository: Repository
    private lateinit var appBaseUrl: String

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()
        repository = Repository.getInstance(context)
        appBaseUrl = context.getString(R.string.app_base_url)

        val screen = preferenceManager.createPreferenceScreen(context)
        preferenceScreen = screen

        // Mode: radio-style single choice (rendered as a Material 3 dialog)
        val modePref = ListPreference(context).apply {
            key = KEY_HOME_MODE
            title = getString(R.string.home_subs_mode_title)
            entries = arrayOf(
                getString(R.string.home_subs_mode_all),
                getString(R.string.home_subs_mode_selected)
            )
            entryValues = arrayOf(Repository.HOME_MODE_ALL, Repository.HOME_MODE_SELECTED)
            setValue(repository.getHomeMode())
            isIconSpaceReserved = false
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
        modePref.setOnPreferenceChangeListener { _, newValue ->
            repository.setHomeMode(newValue as String)
            reloadSubscriptions()
            true
        }
        screen.addPreference(modePref)

        // Checkbox list of subscriptions (only relevant in SELECTED mode)
        val category = PreferenceCategory(context).apply {
            key = KEY_SUBSCRIPTIONS_CATEGORY
            title = getString(R.string.home_subs_list_title)
            isIconSpaceReserved = false
        }
        screen.addPreference(category)

        reloadSubscriptions()
    }

    private fun reloadSubscriptions() {
        lifecycleScope.launch(Dispatchers.IO) {
            val subscriptions = repository.getSubscriptions()
            launch(Dispatchers.Main) {
                populateSubscriptionList(subscriptions)
            }
        }
    }

    private fun populateSubscriptionList(subscriptions: List<Subscription>) {
        if (!isAdded) {
            return
        }
        val category = findPreference<PreferenceCategory>(KEY_SUBSCRIPTIONS_CATEGORY) ?: return
        category.removeAll()

        val selected = repository.getHomeSelectedSubscriptionIds()
        val selectedMode = repository.getHomeMode() == Repository.HOME_MODE_SELECTED

        // Clean up stale IDs of deleted subscriptions while we're at it
        val existingIds = subscriptions.map { it.id }.toSet()
        val stale = selected - existingIds
        if (stale.isNotEmpty()) {
            repository.removeFromHomeSelection(stale)
        }

        category.summary = if (selectedMode) {
            getString(R.string.home_subs_list_summary_selected, selected.size)
        } else {
            getString(R.string.home_subs_list_summary_all)
        }

        for (subscription in subscriptions) {
            val pref = CheckBoxPreference(requireContext()).apply {
                key = KEY_SUBSCRIPTION_PREFIX + subscription.id
                title = displayName(appBaseUrl, subscription)
                summary = topicShortUrl(subscription.baseUrl, subscription.topic)
                isChecked = subscription.id in selected
                isEnabled = selectedMode
                setOnPreferenceChangeListener { _, newValue ->
                    val checked = newValue as Boolean
                    val current = repository.getHomeSelectedSubscriptionIds()
                    val updated = if (checked) current + subscription.id else current - subscription.id
                    repository.setHomeSelectedSubscriptionIds(updated)
                    category.summary = getString(R.string.home_subs_list_summary_selected, updated.size)
                    true
                }
            }
            category.addPreference(pref)
        }
    }

    companion object {
        const val KEY_HOME_MODE = "home_mode"
        const val KEY_SUBSCRIPTIONS_CATEGORY = "home_subscriptions_category"
        const val KEY_SUBSCRIPTION_PREFIX = "home_subscription_"
    }
}
