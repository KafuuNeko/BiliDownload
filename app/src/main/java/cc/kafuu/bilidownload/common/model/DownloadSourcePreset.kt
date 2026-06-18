package cc.kafuu.bilidownload.common.model

enum class DownloadSourcePreset(val host: String) {
    COS_OVERSEA("upos-sz-mirrorcosov.bilivideo.com"),
    BSTAR_AKAMAI("upos-bstar1-mirrorakam.akamaized.net"),
    HANGZHOU_AKAMAI("upos-hz-mirrorakam.akamaized.net"),
    ALI_OVERSEA("upos-sz-mirroraliov.bilivideo.com"),
    BAIDU("upos-sz-mirrorbos.bilivideo.com"),
    HUAWEI("upos-sz-mirrorhw.bilivideo.com"),
    MIRROR_08H("upos-sz-mirror08h.bilivideo.com"),
    ALI("upos-sz-mirrorali.bilivideo.com"),
    COS("upos-sz-mirrorcos.bilivideo.com")
}
