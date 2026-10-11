package com.kitsugi.animelist.ui.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.launch
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val settingsDataStore = SettingsDataStore(context)

    // Callback to show messages on UI
    var onShowMessage: ((String) -> Unit)? = null

    fun updateProfileInfo(
        profileName: String,
        listTitle: String,
        anilistUsername: String
    ) {
        viewModelScope.launch {
            settingsDataStore.setProfileInfo(
                profileName = profileName,
                listTitle = listTitle,
                anilistUsername = anilistUsername
            )
            onShowMessage?.invoke(KitsugiStrings.get(R.string.profil_bilgileri_guncellendi))
        }
    }

    fun updateProfileImageUri(uri: String) {
        viewModelScope.launch {
            settingsDataStore.setProfileImageUri(uri)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.profil_resmi_guncellendi))
        }
    }

    fun updateBannerImageUri(uri: String) {
        viewModelScope.launch {
            settingsDataStore.setBannerImageUri(uri)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.banner_resmi_guncellendi))
        }
    }

    fun clearProfileImage() {
        viewModelScope.launch {
            settingsDataStore.clearProfileImageUri()
            onShowMessage?.invoke(KitsugiStrings.get(R.string.profil_resmi_kaldirildi))
        }
    }

    fun clearBannerImage() {
        viewModelScope.launch {
            settingsDataStore.clearBannerImageUri()
            onShowMessage?.invoke(KitsugiStrings.get(R.string.banner_resmi_kaldirildi))
        }
    }
}
