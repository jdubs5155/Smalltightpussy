package com.zim.jackettprowler

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

class CustomURLsActivity : AppCompatActivity() {

    private lateinit var editSiteName: EditText
    private lateinit var editBaseUrl: EditText
    private lateinit var checkboxOnionSite: CheckBox
    private lateinit var editSearchPath: EditText
    private lateinit var editResultContainer: EditText
    private lateinit var editTitleSelector: EditText
    private lateinit var editMagnetSelector: EditText
    private lateinit var editSizeSelector: EditText
    private lateinit var editSeedersSelector: EditText
    private lateinit var editLeechersSelector: EditText
    private lateinit var buttonSaveCustomSite: Button
    private lateinit var buttonViewSavedSites: Button
    
    private lateinit var customSiteManager: CustomSiteManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_urls)
        title = "Add Custom Torrent Sites"

        customSiteManager = CustomSiteManager(this)

        editSiteName = findViewById(R.id.editSiteName)
        editBaseUrl = findViewById(R.id.editBaseUrl)
        checkboxOnionSite = findViewById(R.id.checkboxOnionSite)
        editSearchPath = findViewById(R.id.editSearchPath)
        editResultContainer = findViewById(R.id.editResultContainer)
        editTitleSelector = findViewById(R.id.editTitleSelector)
        editMagnetSelector = findViewById(R.id.editMagnetSelector)
        editSizeSelector = findViewById(R.id.editSizeSelector)
        editSeedersSelector = findViewById(R.id.editSeedersSelector)
        editLeechersSelector = findViewById(R.id.editLeechersSelector)
        buttonSaveCustomSite = findViewById(R.id.buttonSaveCustomSite)
        buttonViewSavedSites = findViewById(R.id.buttonViewSavedSites)

        buttonSaveCustomSite.setOnClickListener {
            saveCustomSite()
        }

        buttonViewSavedSites.setOnClickListener {
            viewSavedSites()
        }
    }

    private fun saveCustomSite() {
        val name = editSiteName.text.toString().trim()
        val baseUrl = editBaseUrl.text.toString().trim()
        val searchPath = editSearchPath.text.toString().trim()
        val resultContainer = editResultContainer.text.toString().trim()
        val titleSelector = editTitleSelector.text.toString().trim()

        if (name.isEmpty() || baseUrl.isEmpty() || searchPath.isEmpty() || 
            resultContainer.isEmpty() || titleSelector.isEmpty()) {
            Toast.makeText(this, "Please fill required fields (Name, URL, Search Path, Container, Title)", Toast.LENGTH_LONG).show()
            return
        }

        val isOnion = checkboxOnionSite.isChecked || baseUrl.contains(".onion")

        val config = CustomSiteConfig(
            id = UUID.randomUUID().toString(),
            name = name,
            baseUrl = baseUrl,
            isOnionSite = isOnion,
            enabled = true,
            searchPath = searchPath,
            searchParamName = "q",
            selectors = ScraperSelectors(
                resultContainer = resultContainer,
                title = titleSelector,
                downloadUrl = null,
                magnetUrl = editMagnetSelector.text.toString().trim().takeIf { it.isNotEmpty() },
                size = editSizeSelector.text.toString().trim().takeIf { it.isNotEmpty() },
                seeders = editSeedersSelector.text.toString().trim().takeIf { it.isNotEmpty() },
                leechers = editLeechersSelector.text.toString().trim().takeIf { it.isNotEmpty() }
            ),
            requiresTor = isOnion,
            category = if (isOnion) "onion-custom" else "custom"
        )

        customSiteManager.addSite(config)

        Toast.makeText(this, "✅ Custom site '$name' saved!", Toast.LENGTH_LONG).show()

        // Clear fields
        clearFields()
    }

    private fun clearFields() {
        editSiteName.text.clear()
        editBaseUrl.text.clear()
        checkboxOnionSite.isChecked = false
        editSearchPath.text.clear()
        editResultContainer.text.clear()
        editTitleSelector.text.clear()
        editMagnetSelector.text.clear()
        editSizeSelector.text.clear()
        editSeedersSelector.text.clear()
        editLeechersSelector.text.clear()
    }

    private fun viewSavedSites() {
        val sites = customSiteManager.getSites()

        if (sites.isEmpty()) {
            Toast.makeText(this, "No custom sites saved yet", Toast.LENGTH_SHORT).show()
            return
        }

        val siteNames = sites.map { "${it.name} (${if (it.isOnionSite) "onion" else "clearnet"})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Saved Custom Sites (${sites.size})")
            .setItems(siteNames) { _, which ->
                val site = sites[which]
                showSiteDetails(site)
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showSiteDetails(site: CustomSiteConfig) {
        val details = """
            Name: ${site.name}
            URL: ${site.baseUrl}
            Type: ${if (site.isOnionSite) "Onion (Tor)" else "Clearnet"}
            Search Path: ${site.searchPath}
            Enabled: ${if (site.enabled) "Yes" else "No"}
            Category: ${site.category}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(site.name)
            .setMessage(details)
            .setPositiveButton("Enable") { _, _ ->
                val updatedConfig = site.copy(enabled = true)
                val sites = customSiteManager.getSites().map { 
                    if (it.id == site.id) updatedConfig else it 
                }
                customSiteManager.saveSites(sites)
                Toast.makeText(this, "${site.name} enabled", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Disable") { _, _ ->
                val updatedConfig = site.copy(enabled = false)
                val sites = customSiteManager.getSites().map { 
                    if (it.id == site.id) updatedConfig else it 
                }
                customSiteManager.saveSites(sites)
                Toast.makeText(this, "${site.name} disabled", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Delete") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Site?")
                    .setMessage("Are you sure you want to delete ${site.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        customSiteManager.removeSite(site.id)
                        Toast.makeText(this, "${site.name} deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .show()
    }
}
