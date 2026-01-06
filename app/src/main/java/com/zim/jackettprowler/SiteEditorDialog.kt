/*
// DISABLED: This advanced site editor dialog is replaced by CustomURLsActivity
// Commenting out to avoid build errors with missing layouts
package com.zim.jackettprowler

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.*
import java.util.UUID

/**
 * Dialog for adding/editing custom site configurations
 */
class SiteEditorDialog(
    private val context: Context,
    private val site: CustomSiteConfig?,
    private val onSave: (CustomSiteConfig) -> Unit
) {
    private lateinit var dialog: AlertDialog
    
    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_site_editor, null)
        
        // Input fields
        val nameInput = view.findViewById<EditText>(R.id.siteNameInput)
        val baseUrlInput = view.findViewById<EditText>(R.id.siteBaseUrlInput)
        val searchPathInput = view.findViewById<EditText>(R.id.siteSearchPathInput)
        val searchParamInput = view.findViewById<EditText>(R.id.siteSearchParamInput)
        val onionSiteCheckbox = view.findViewById<CheckBox>(R.id.siteOnionCheckbox)
        val requiresTorCheckbox = view.findViewById<CheckBox>(R.id.siteRequiresTorCheckbox)
        val useJsCheckbox = view.findViewById<CheckBox>(R.id.siteUseJsCheckbox)
        val rateLimitInput = view.findViewById<EditText>(R.id.siteRateLimitInput)
        
        // Selector fields
        val containerSelectorInput = view.findViewById<EditText>(R.id.selectorContainerInput)
        val titleSelectorInput = view.findViewById<EditText>(R.id.selectorTitleInput)
        val downloadUrlSelectorInput = view.findViewById<EditText>(R.id.selectorDownloadUrlInput)
        val magnetUrlSelectorInput = view.findViewById<EditText>(R.id.selectorMagnetUrlInput)
        val sizeSelectorInput = view.findViewById<EditText>(R.id.selectorSizeInput)
        val seedersSelectorInput = view.findViewById<EditText>(R.id.selectorSeedersInput)
        val leechersSelectorInput = view.findViewById<EditText>(R.id.selectorLeechersInput)
        val dateSelectorInput = view.findViewById<EditText>(R.id.selectorDateInput)
        
        // Template spinner
        val templateSpinner = view.findViewById<Spinner>(R.id.templateSpinner)
        val templates = listOf(
            "Custom",
            "1337x Template",
            "TPB Template",
            "RARBG Template",
            "Nyaa Template"
        )
        templateSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, templates)
        
        templateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                when (position) {
                    1 -> applyTemplate1337x(
                        baseUrlInput, searchPathInput, searchParamInput,
                        containerSelectorInput, titleSelectorInput,
                        sizeSelectorInput, seedersSelectorInput, leechersSelectorInput
                    )
                    2 -> applyTemplateTPB(
                        baseUrlInput, searchPathInput, searchParamInput, onionSiteCheckbox, requiresTorCheckbox,
                        containerSelectorInput, titleSelectorInput, magnetUrlSelectorInput,
                        sizeSelectorInput, seedersSelectorInput, leechersSelectorInput
                    )
                    3 -> applyTemplateRARBG(
                        baseUrlInput, searchPathInput, searchParamInput,
                        containerSelectorInput, titleSelectorInput,
                        sizeSelectorInput, seedersSelectorInput, leechersSelectorInput
                    )
                    4 -> applyTemplateNyaa(
                        baseUrlInput, searchPathInput, searchParamInput,
                        containerSelectorInput, titleSelectorInput, downloadUrlSelectorInput, magnetUrlSelectorInput,
                        sizeSelectorInput, seedersSelectorInput, leechersSelectorInput, dateSelectorInput
                    )
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Pre-fill if editing
        site?.let {
            nameInput.setText(it.name)
            baseUrlInput.setText(it.baseUrl)
            searchPathInput.setText(it.searchPath)
            searchParamInput.setText(it.searchParamName)
            onionSiteCheckbox.isChecked = it.isOnionSite
            requiresTorCheckbox.isChecked = it.requiresTor
            useJsCheckbox.isChecked = it.useJavaScript
            rateLimitInput.setText(it.rateLimit.toString())
            
            containerSelectorInput.setText(it.selectors.resultContainer)
            titleSelectorInput.setText(it.selectors.title)
            downloadUrlSelectorInput.setText(it.selectors.downloadUrl ?: "")
            magnetUrlSelectorInput.setText(it.selectors.magnetUrl ?: "")
            sizeSelectorInput.setText(it.selectors.size ?: "")
            seedersSelectorInput.setText(it.selectors.seeders ?: "")
            leechersSelectorInput.setText(it.selectors.leechers ?: "")
            dateSelectorInput.setText(it.selectors.publishDate ?: "")
        }
        
        dialog = AlertDialog.Builder(context)
            .setTitle(if (site == null) "Add Custom Site" else "Edit Site")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newSite = CustomSiteConfig(
                    id = site?.id ?: UUID.randomUUID().toString(),
                    name = nameInput.text.toString(),
                    baseUrl = baseUrlInput.text.toString(),
                    searchPath = searchPathInput.text.toString(),
                    searchParamName = searchParamInput.text.toString(),
                    isOnionSite = onionSiteCheckbox.isChecked,
                    requiresTor = requiresTorCheckbox.isChecked,
                    useJavaScript = useJsCheckbox.isChecked,
                    rateLimit = rateLimitInput.text.toString().toLongOrNull() ?: 1000,
                    enabled = site?.enabled ?: true,
                    selectors = ScraperSelectors(
                        resultContainer = containerSelectorInput.text.toString(),
                        title = titleSelectorInput.text.toString(),
                        downloadUrl = downloadUrlSelectorInput.text.toString().ifBlank { null },
                        magnetUrl = magnetUrlSelectorInput.text.toString().ifBlank { null },
                        size = sizeSelectorInput.text.toString().ifBlank { null },
                        seeders = seedersSelectorInput.text.toString().ifBlank { null },
                        leechers = leechersSelectorInput.text.toString().ifBlank { null },
                        publishDate = dateSelectorInput.text.toString().ifBlank { null }
                    )
                )
                onSave(newSite)
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    private fun applyTemplate1337x(
        baseUrl: EditText, searchPath: EditText, searchParam: EditText,
        container: EditText, title: EditText, size: EditText, seeders: EditText, leechers: EditText
    ) {
        baseUrl.setText("https://1337x.to")
        searchPath.setText("/search/{query}/1/")
        searchParam.setText("query")
        container.setText("table.table-list tbody tr")
        title.setText("td.coll-1 a:nth-child(2)")
        size.setText("td.coll-4")
        seeders.setText("td.coll-2")
        leechers.setText("td.coll-3")
    }
    
    private fun applyTemplateTPB(
        baseUrl: EditText, searchPath: EditText, searchParam: EditText,
        onionSite: CheckBox, requiresTor: CheckBox,
        container: EditText, title: EditText, magnet: EditText,
        size: EditText, seeders: EditText, leechers: EditText
    ) {
        baseUrl.setText("http://piratebayztemzmv.onion")
        searchPath.setText("/search/{query}/1/99/0")
        searchParam.setText("query")
        onionSite.isChecked = true
        requiresTor.isChecked = true
        container.setText("#searchResult tbody tr")
        title.setText("td.vertTh div.detName a")
        magnet.setText("td:nth-child(2) a[href^='magnet:']")
        size.setText("font.detDesc")
        seeders.setText("td:nth-child(3)")
        leechers.setText("td:nth-child(4)")
    }
    
    private fun applyTemplateRARBG(
        baseUrl: EditText, searchPath: EditText, searchParam: EditText,
        container: EditText, title: EditText, size: EditText, seeders: EditText, leechers: EditText
    ) {
        baseUrl.setText("https://rarbg-clone.com")
        searchPath.setText("/torrents.php?search={query}")
        searchParam.setText("query")
        container.setText("table.lista2t tr.lista2")
        title.setText("td:nth-child(2) a[href^='/torrent/']")
        size.setText("td:nth-child(4)")
        seeders.setText("td:nth-child(5)")
        leechers.setText("td:nth-child(6)")
    }
    
    private fun applyTemplateNyaa(
        baseUrl: EditText, searchPath: EditText, searchParam: EditText,
        container: EditText, title: EditText, downloadUrl: EditText, magnetUrl: EditText,
        size: EditText, seeders: EditText, leechers: EditText, date: EditText
    ) {
        baseUrl.setText("https://nyaa.si")
        searchPath.setText("/?q={query}")
        searchParam.setText("query")
        container.setText("table.torrent-list tbody tr")
        title.setText("td:nth-child(2) a:not(.comments)")
        downloadUrl.setText("td:nth-child(3) a[href$='.torrent']")
        magnetUrl.setText("td:nth-child(3) a[href^='magnet:']")
        size.setText("td:nth-child(4)")
        seeders.setText("td:nth-child(6)")
        leechers.setText("td:nth-child(7)")
        date.setText("td:nth-child(5)")
    }
}
*/
