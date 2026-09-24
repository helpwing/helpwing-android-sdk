package ru.fanyagin.helpwing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fanyagin.helpwing.core.HelpwingStorage

/** The default storage: a private SharedPreferences file. */
class SharedPreferencesStorage(context: Context, name: String = "helpwing") : HelpwingStorage {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override suspend fun getItem(key: String): String? = withContext(Dispatchers.IO) {
        preferences.getString(key, null)
    }

    override suspend fun setItem(key: String, value: String) {
        withContext(Dispatchers.IO) { preferences.edit().putString(key, value).apply() }
    }

    override suspend fun removeItem(key: String) {
        withContext(Dispatchers.IO) { preferences.edit().remove(key).apply() }
    }
}
