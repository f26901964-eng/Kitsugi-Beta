package com.kitsugi.animelist.data.repository

import com.kitsugi.animelist.data.account.KitsugiAccountRepository
import com.kitsugi.animelist.data.local.SearchHistoryDao
import com.kitsugi.animelist.data.local.SearchHistoryMapper
import com.kitsugi.animelist.ui.screens.search.SearchHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SearchHistoryRepository(private val searchHistoryDao: SearchHistoryDao) {

    fun getRecentSearchHistory(): Flow<List<SearchHistoryItem>> {
        return searchHistoryDao.getRecentSearchHistory().map { list ->
            list.map { SearchHistoryMapper.toDomain(it) }
        }
    }

    suspend fun insertSearchQuery(item: SearchHistoryItem) {
        if (item.query.isBlank()) return
        val entity = SearchHistoryMapper.toEntity(item)
        searchHistoryDao.insertSearchQuery(entity)
        // Giriş yapılmışsa geçmişi buluta da yaz (giriş yoksa hiçbir şey yapmaz)
        KitsugiAccountRepository.onSearchHistoryChanged(searchHistoryDao)
    }

    suspend fun deleteSearchQuery(query: String) {
        searchHistoryDao.deleteSearchQuery(query)
        KitsugiAccountRepository.onSearchHistoryChanged(searchHistoryDao)
    }

    suspend fun clearSearchHistory() {
        searchHistoryDao.clearSearchHistory()
        KitsugiAccountRepository.onSearchHistoryChanged(searchHistoryDao)
    }
}
