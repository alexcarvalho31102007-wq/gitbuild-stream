package com.example.data.storage

import android.content.Context
import android.content.SharedPreferences

class TokenStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("git_compiler_prefs", Context.MODE_PRIVATE)

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    var selectedRepo: String
        get() = prefs.getString(KEY_SELECTED_REPO, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SELECTED_REPO, value.trim()).apply()

    var targetBranch: String
        get() = prefs.getString(KEY_TARGET_BRANCH, "main") ?: "main"
        set(value) = prefs.edit().putString(KEY_TARGET_BRANCH, value.trim()).apply()

    var lastUsername: String
        get() = prefs.getString(KEY_LAST_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_USERNAME, value.trim()).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "github_token"
        private const val KEY_SELECTED_REPO = "selected_repo"
        private const val KEY_TARGET_BRANCH = "target_branch"
        private const val KEY_LAST_USERNAME = "last_username"
    }
}
