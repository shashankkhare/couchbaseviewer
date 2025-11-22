package com.example.couchbaseviewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker
import com.example.couchbaseviewer.ui.theme.CouchbaseviewerTheme
import com.russhwolf.settings.Settings
import kotlinx.coroutines.launch

data class Document(val id: String, val properties: Map<String, Any?>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val coroutineScope = rememberCoroutineScope()
    val databaseManager = remember { DatabaseManager().apply { init() } }
    val settings = remember { Settings() }
    val lastDbPathKey = "lastDbPath"

    // Initialize the dbPath state directly from settings. This is the key to the fix.
    var dbPath by remember { mutableStateOf(settings.getStringOrNull(lastDbPathKey)) }

    var documentTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var documents by remember { mutableStateOf<List<Document>>(emptyList()) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedDocument by remember { mutableStateOf<Document?>(null) }
    var showDirPicker by remember { mutableStateOf(false) }
    // State for the custom filter text
    var filterText by remember { mutableStateOf("") }

    val isDbOpen = dbPath != null

    // A single, robust LaunchedEffect to handle all db path changes.
    LaunchedEffect(dbPath) {
        // Always reset the UI state when the path changes.
        databaseManager.closeDatabase()
        documentTypes = emptyList()
        documents = emptyList()
        selectedType = null
        selectedDocument = null

        val path = dbPath
        if (path == null) {
            // If the path is null (closed or invalid), remove the setting.
            settings.remove(lastDbPathKey)
        } else {
            // If a path exists, try to open it.
            coroutineScope.launch {
                try {
                    databaseManager.openDatabase(path)
                    documentTypes = databaseManager.getDocumentTypes()
                    // Confirm the path is valid by re-saving it.
                    settings.putString(lastDbPathKey, path)
                } catch (e: Exception) {
                    // If open fails, set path to null, which re-triggers this effect to clear the invalid setting.
                    dbPath = null
                }
            }
        }
    }

    // Effect to load or reload documents when the type or filter changes
    LaunchedEffect(selectedType, filterText) {
        selectedDocument = null
        documents = emptyList()

        selectedType?.let {
            coroutineScope.launch {
                documents = databaseManager.getDocumentsByType(it, filterText)
            }
        }
    }

    CouchbaseviewerTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = dbPath ?: "",
                        onValueChange = { /* Read-only */ },
                        readOnly = true,
                        label = { Text("Database Path") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = { showDirPicker = true }) { Text("Open DB") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { dbPath = null }) { Text("Close DB") }
                }
            }

            // MAIN CONTENT ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
            ) {

                // LEFT PANE - Document Types
                Surface(
                    modifier = Modifier
                        .weight(0.3f)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isDbOpen) {
                        DocumentTypeList(documentTypes) { 
                            selectedType = it
                            filterText = "" // Reset filter when type changes
                         }
                    } else {
                        PanePlaceholder("Database closed")
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // RIGHT PANE - Documents and Properties
                Surface(
                    modifier = Modifier
                        .weight(0.7f)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    when {
                        !isDbOpen ->
                            PanePlaceholder("Click 'Open DB' to select a database directory.")
                        selectedType == null ->
                            PanePlaceholder("Select a document type from the left")
                        selectedDocument == null ->
                            DocumentList(documents, filterText, onFilterChanged = { filterText = it }) { selectedDocument = it }
                        else ->
                            DocumentPropertiesView(selectedDocument!!) { selectedDocument = null }
                    }
                }
            }
        }

        DirectoryPicker(show = showDirPicker) { path ->
            showDirPicker = false
            if (path != null) dbPath = path
        }
    }
}

@Composable
fun PanePlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun DocumentList(
    documents: List<Document>,
    initialFilter: String,
    onFilterChanged: (String) -> Unit,
    onDocumentSelected: (Document) -> Unit
) {
    var localFilterText by remember(initialFilter) { mutableStateOf(initialFilter) }
    var scrollPosition by remember { mutableStateOf(0) }
    val visibleItems = 20 // Number of items visible at once
    val totalItems = documents.size

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Documents", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("Found: $totalItems", style = MaterialTheme.typography.bodySmall)
        }
        
        // Filter UI
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = localFilterText,
                onValueChange = { localFilterText = it },
                label = { Text("WHERE clause") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onFilterChanged(localFilterText) }) {
                Text("Filter")
            }
        }

        // Pagination controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${scrollPosition + 1}-${(scrollPosition + visibleItems).coerceAtMost(totalItems)} of $totalItems",
                style = MaterialTheme.typography.bodySmall)

            Row {
                Button(
                    onClick = { scrollPosition = (scrollPosition - visibleItems).coerceAtLeast(0) },
                    enabled = scrollPosition > 0
                ) { Text("Previous") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { scrollPosition = (scrollPosition + visibleItems).coerceAtMost(totalItems - visibleItems) },
                    enabled = scrollPosition < totalItems - visibleItems
                ) { Text("Next") }
            }
        }

        // Document list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
        ) {
            val visibleDocuments = documents
                .drop(scrollPosition)
                .take(visibleItems)

            if (visibleDocuments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No documents match filter")
                }
            } else {
                visibleDocuments.forEach { doc ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDocumentSelected(doc) }
                            .padding(16.dp)
                            .background(Color.White)
                    ) {
                        Text(doc.id)
                    }
                    Divider()
                }
            }
        }
    }
}

@Composable
fun DocumentTypeList(documentTypes: List<String>, onTypeSelected: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Document Types", style = MaterialTheme.typography.titleMedium)
        }

        // Scrollable list
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(documentTypes) { type ->
                    Text(
                        text = type,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTypeSelected(type) }
                            .padding(16.dp)
                    )
                    Divider()
                }
            }
        }
    }
}

// THIS IS THE NEW, INTELLIGENT PROPERTIES VIEW
@Composable
fun DocumentPropertiesView(document: Document, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) { Text("← Back to list") }
        }

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // This ensures the column takes remaining space
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    "Properties for ${document.id}:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Start the recursive rendering from the top-level properties
                RecursivePropertyView(document.properties)

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun RecursivePropertyView(data: Any?, indent: Dp = 0.dp) {
    when (data) {
        is Map<*, *> -> {
            // Render a Map (JSON Object)
            Column(modifier = Modifier.padding(start = indent)) {
                data.forEach { (key, value) ->
                    if (value is Map<*, *> || value is List<*>) {
                        // Key for a nested object/array
                        Text(
                            text = "$key:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        // Recursively render the nested value
                        RecursivePropertyView(value, indent = 16.dp)
                    } else {
                        // Key-value pair for simple types
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = key.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.4f)
                            )
                            Text(
                                text = value?.toString() ?: "null",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(0.6f)
                            )
                        }
                    }
                    Divider()
                }
            }
        }
        is List<*> -> {
            // Render a List (JSON Array)
            Column(
                modifier = Modifier
                    .padding(start = indent, top = 4.dp, bottom = 4.dp)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
                    .padding(8.dp)
            ) {
                data.forEachIndexed { index, item ->
                    Text(
                        text = "[$index]:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    // Recursively render the list item
                    RecursivePropertyView(item, indent = 16.dp)
                    if (index < data.size - 1) Divider(modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        else -> {
            // Render a primitive value (String, Number, Boolean, null)
            Text(
                text = data?.toString() ?: "null",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = indent)
            )
        }
    }
}
