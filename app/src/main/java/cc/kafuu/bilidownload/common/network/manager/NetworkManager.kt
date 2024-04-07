package cc.kafuu.bilidownload.common.network.manager

import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.repository.BiliVideoRepository
import cc.kafuu.bilidownload.common.network.repository.BiliWbiRepository


object NetworkManager {
    val biliWbiResponse = BiliWbiRepository(NetworkConfig.biliService)

    val biliVideoRepository by lazy { BiliVideoRepository(NetworkConfig.biliService) }
}