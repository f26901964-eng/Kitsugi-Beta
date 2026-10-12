package com.kitsugi.animelist.core.notifications

import com.kitsugi.animelist.data.settings.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSchedulerTest {
    @Test
    fun globalAiringNotificationsRequireAtLeastOneLinkedAccount() {
        assertFalse(
            shouldScheduleNotificationWork(
                settings = AppSettings(airingNotificationsEnabled = true),
                accounts = NotificationAccountLinks(
                    aniList = false,
                    mal = false,
                    simkl = false,
                    kitsu = false,
                    shikimori = false,
                    bangumi = false
                )
            )
        )
    }

    @Test
    fun globalAiringNotificationsCanUseAnyLinkedSupportedAccount() {
        assertTrue(
            shouldScheduleNotificationWork(
                settings = AppSettings(airingNotificationsEnabled = true),
                accounts = NotificationAccountLinks(
                    aniList = false,
                    mal = false,
                    simkl = false,
                    kitsu = false,
                    shikimori = false,
                    bangumi = true
                )
            )
        )
    }

    @Test
    fun sourceNotificationRequiresItsOwnLinkedAccount() {
        assertFalse(
            shouldScheduleNotificationWork(
                settings = AppSettings(simklNotificationsEnabled = true),
                accounts = NotificationAccountLinks(
                    aniList = false,
                    mal = false,
                    simkl = false,
                    kitsu = true,
                    shikimori = false,
                    bangumi = false
                )
            )
        )
    }

    @Test
    fun linkedSourceNotificationSchedulesWork() {
        assertTrue(
            shouldScheduleNotificationWork(
                settings = AppSettings(kitsuNotificationsEnabled = true),
                accounts = NotificationAccountLinks(
                    aniList = false,
                    mal = false,
                    simkl = false,
                    kitsu = true,
                    shikimori = false,
                    bangumi = false
                )
            )
        )
    }
}
