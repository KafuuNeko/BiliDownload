package cc.kafuu.bilidownload.common.model.event

import cc.kafuu.bilidownload.common.model.MainTabType

data class MainTabSwitchEvent(
    @MainTabType val mainTabType: Int
)
