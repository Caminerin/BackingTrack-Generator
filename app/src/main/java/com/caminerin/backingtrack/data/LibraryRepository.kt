package com.caminerin.backingtrack.data

import android.content.Context
import com.caminerin.backingtrack.model.LibraryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

/**
 * Persists the backing-track library to a single JSON index file in the app's
 * private storage. Rendered audio lives under filesDir/tracks/.
 */
class LibraryRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val indexFile = File(appContext.filesDir, "library.json")
    val tracksDir = File(appContext.filesDir, "tracks").apply { mkdirs() }

    private val _items = MutableStateFlow<List<LibraryItem>>(emptyList())
    val items: StateFlow<List<LibraryItem>> = _items

    init {
        load()
    }

    private fun load() {
        if (!indexFile.exists()) {
            _items.value = emptyList()
            return
        }
        runCatching {
            val arr = JSONArray(indexFile.readText())
            val list = ArrayList<LibraryItem>(arr.length())
            for (i in 0 until arr.length()) list.add(RecipeJson.itemFromJson(arr.getJSONObject(i)))
            _items.value = list.sortedByDescending { it.createdAt }
        }.onFailure {
            _items.value = emptyList()
        }
    }

    private fun persist() {
        val arr = JSONArray()
        _items.value.forEach { arr.put(RecipeJson.itemToJson(it)) }
        indexFile.writeText(arr.toString())
    }

    fun upsert(item: LibraryItem) {
        val current = _items.value.toMutableList()
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx >= 0) current[idx] = item else current.add(0, item)
        _items.value = current.sortedByDescending { it.createdAt }
        persist()
    }

    fun delete(id: String) {
        val item = _items.value.firstOrNull { it.id == id }
        item?.audioFilePath?.let { if (it.isNotEmpty()) File(it).delete() }
        _items.value = _items.value.filterNot { it.id == id }
        persist()
    }

    fun toggleFavorite(id: String) {
        _items.value = _items.value.map {
            if (it.id == id) it.copy(favorite = !it.favorite) else it
        }
        persist()
    }

    fun markPlayed(id: String) {
        _items.value = _items.value.map {
            if (it.id == id) it.copy(lastPlayed = System.currentTimeMillis()) else it
        }
        persist()
    }

    fun get(id: String): LibraryItem? = _items.value.firstOrNull { it.id == id }

    fun audioFile(id: String, ext: String): File = File(tracksDir, "$id.$ext")

    companion object {
        @Volatile private var instance: LibraryRepository? = null
        fun get(context: Context): LibraryRepository =
            instance ?: synchronized(this) {
                instance ?: LibraryRepository(context).also { instance = it }
            }
    }
}
