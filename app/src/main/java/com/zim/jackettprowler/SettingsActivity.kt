package com.zim.jackettprowler

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zim.jackettprowler.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.settings_title)

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)

        val enabled = prefs.getBoolean(getString(R.string.pref_qb_enabled), false)
        val baseUrl = prefs.getString(getString(R.string.pref_qb_base_url), "") ?: ""
        val username = prefs.getString(getString(R.string.pref_qb_username), "") ?: ""
        val password = prefs.getString(getString(R.string.pref_qb_password), "") ?: ""

        binding.checkEnableQb.isChecked = enabled
        binding.editQbBaseUrl.setText(baseUrl)
        binding.editQbUsername.setText(username)
        binding.editQbPassword.setText(password)

        binding.buttonSaveSettings.setOnClickListener {
            prefs.edit()
                .putBoolean(getString(R.string.pref_qb_enabled), binding.checkEnableQb.isChecked)
                .putString(
                    getString(R.string.pref_qb_base_url),
                    binding.editQbBaseUrl.text.toString().trim()
                )
                .putString(
                    getString(R.string.pref_qb_username),
                    binding.editQbUsername.text.toString().trim()
                )
                .putString(
                    getString(R.string.pref_qb_password),
                    binding.editQbPassword.text.toString()
                )
                .apply()
            finish()
        }
    }
}