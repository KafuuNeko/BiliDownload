package cc.kafuu.bilidownload.model.event

import cc.kafuu.bilidownload.model.MainTabType

data class MainTabSwitchEvent(
    @MainTabType val mainTabType: Int
)
