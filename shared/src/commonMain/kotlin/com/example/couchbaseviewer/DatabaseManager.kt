package com.example.couchbaseviewer

import kotbase.CouchbaseLite
import kotbase.Database
import kotbase.DatabaseConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseManager {
    private var database: Database? = null

    fun init() {
        CouchbaseLite.init()
    }

    suspend fun openDatabase(path: String) {
        withContext(Dispatchers.IO) {
            database?.close()
            val lastSeparator = path.lastIndexOf('/')
            val directory = if (lastSeparator > 0) path.substring(0, lastSeparator) else ""
            val name = path.substring(lastSeparator + 1).removeSuffix(".cblite2")
            val config = DatabaseConfiguration().setDirectory(directory)
            database = Database(name, config)
        }
    }

    suspend fun getDocumentTypes(): List<String> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext emptyList()
        val query = db.createQuery("SELECT DISTINCT type FROM _ WHERE type IS NOT NULL ORDER BY type")
        query.execute().allResults().mapNotNull { it.getString("type") }
    }

    // MODIFIED TO ACCEPT A CUSTOM FILTER CLAUSE
    suspend fun getDocumentsByType(type: String, filterClause: String? = null): List<Document> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext emptyList()

        // Build the base query
        var queryString = "SELECT META().id, * FROM _ WHERE type = \"$type\""

        // Append the custom filter if it exists and is not blank
        if (!filterClause.isNullOrBlank()) {
            queryString += " AND ($filterClause)"
        }

        queryString += " ORDER BY META().id"

        val query = db.createQuery(queryString)

        query.execute().allResults().mapNotNull {
            val id = it.getString("id") ?: return@mapNotNull null
            val props = it.getDictionary(db.name)?.toMap() ?: it.toMap()
            Document(id, props)
        }
    }

    suspend fun closeDatabase() {
        withContext(Dispatchers.IO) {
            database?.close()
            database = null
        }
    }
}
