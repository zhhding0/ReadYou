package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.ui.ext.PreferencesKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalFlowSort = compositionLocalOf { FlowSortPreference.Latest }

enum class FlowSortPreference(val value: String, val label: Int) {
    Latest("latest", R.string.latest),
    Recommended("recommended", R.string.for_you),
    UnreadFirst("unread", R.string.unread_first),
    Oldest("oldest", R.string.earliest);

    @Composable fun description(): String = stringResource(label)

    fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(stringPreferencesKey(PreferencesKey.flowSortArticles), value)
        }
    }

    companion object {
        fun fromPreferences(preferences: Preferences): FlowSortPreference {
            val mode = preferences[stringPreferencesKey(PreferencesKey.flowSortArticles)]
            if (mode != null) return entries.firstOrNull { it.value == mode } ?: Latest
            return if (preferences[booleanPreferencesKey(PreferencesKey.flowSortUnreadArticles)] == true) Oldest else Latest
        }
    }
}
