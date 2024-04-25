package cc.kafuu.bilidownload.common.network.manager

import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.repository.BiliAccountRepository
import cc.kafuu.bilidownload.common.network.repository.BiliSearchRepository
import cc.kafuu.bilidownload.common.network.repository.BiliVideoRepository
import cc.kafuu.bilidownload.common.network.repository.BiliWbiRepository


object NetworkManager {
    val biliWbiResponse = BiliWbiRepository(NetworkConfig.biliService)

    val biliVideoRepository by lazy { BiliVideoRepository(NetworkConfig.biliService) }

    val biliAccountRepository by lazy { BiliAccountRepository(NetworkConfig.biliService) }

    val biliSearchRepository by lazy { BiliSearchRepository(NetworkConfig.biliService) }
}