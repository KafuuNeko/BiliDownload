//
// Created by kafuu on 2024/4/3.
//
#include <vector>

#include "android_log.hpp"
#include "av_format.hpp"
#include "ffmpeg_utils.hpp"

using namespace ffmpeg;

static auto TAG = "AvFormat";

AvFormat::AvFormat(const std::string &filename) {
    //尝试打开输入文件（fmt置空，自动通过文件名检测格式）
    mInputFormatCtx = utils::avformatOpenInputPtr(filename.c_str(), nullptr, nullptr, &ec);
    if (!mInputFormatCtx) {
        log::error(TAG, "Could not open input file, error code: %d", ec);
        return;
    }

    //查找信息
    if ((ec = avformat_find_stream_info(mInputFormatCtx.get(), 0)) < 0) {
        log::error(TAG, "Failed to retrieve input stream information, error code: %d", ec);
        return;
    }

    // 输出调试信息
    // av_dump_format(mInputFormatCtx, 0, inputFile.c_str(), 0);
}

bool AvFormat::extract(
        const std::string &outputFilename,
        std::function<bool(size_t index, AVStream *stream)> streamFilter,
        std::function<bool(size_t index, AVPacket *packet)> packetFilter) {

    auto outputContext = utils::avformatAllocOutputContextUniquePtr(
            nullptr, nullptr, outputFilename.c_str(), &ec);
    if (!outputContext) {
        log::error(TAG, "Cannot alloc output context, error code: %d", ec);
        return false;
    }

    ec = utils::copyStreamSettings(mInputFormatCtx.get(), outputContext.get(), streamFilter);
    if (ec < 0) {
        log::error(TAG, "Cannot copy stream settings, error code: %d", ec);
        return false;
    }

    if (!(outputContext->oformat->flags & AVFMT_NOFILE)) {
        ec = avio_open(&(outputContext->pb), outputFilename.c_str(), AVIO_FLAG_WRITE);
        if (ec < 0) {
            log::error(TAG, "Could not open output file '%s'", outputFilename.c_str());
            return false;
        }
    }

    //写入文件头
    ec = avformat_write_header(outputContext.get(), NULL);
    if (ec < 0) {
        log::error(TAG, "Error occurred when opening audio output file");
        return false;
    }

    //复制并转换输入流中的所有数据包到输出流
    ec = utils::copyAndConvertPackets(mInputFormatCtx.get(), outputContext.get(), packetFilter);
    if (ec < 0) {
        log::error(TAG, "Failed to copy and convert packets, error code: %d", ec);
        return false;
    }

    //写文件尾
    ec = av_write_trailer(outputContext.get());
    if (ec < 0) {
        log::error(TAG, "Failed to write trailer, error code: %d", ec);
        return false;
    }

    return true;
}

bool AvFormat::extractAudio(const std::string &outputFilename) {
    size_t audioSteamIndex = -1;
    // 音频流筛选器
    auto audioSteamFilter = [&](size_t index, AVStream *stream) -> bool {
        if (stream->codecpar->codec_type != AVMEDIA_TYPE_AUDIO) {
            return false;
        }
        audioSteamIndex = index;
        return true;
    };
    // 只取音频packet
    auto packetFilter = [&](size_t index, AVPacket *packet) -> bool {
        return packet->stream_index == audioSteamIndex;
    };

    if (!extract(outputFilename, audioSteamFilter, packetFilter) || audioSteamIndex == -1) {
        log::error(TAG, "Cannot copy stream settings, error code: %d, audio index: %d",
                   ec, audioSteamIndex);
        return false;
    } else {
        return true;
    }
}

std::string AvFormat::getAudioFormat() {
    for (int32_t i = 0; i < mInputFormatCtx->nb_streams; ++i) {
        AVStream *in_stream = mInputFormatCtx->streams[i];
        if (mInputFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            AVCodec *codec = avcodec_find_decoder(in_stream->codecpar->codec_id);
            if (codec == nullptr) {
                codec = avcodec_find_encoder(in_stream->codecpar->codec_id);
            }
            if (codec == nullptr) {
                log::error(TAG, "Not found codec");
                break;
            } else {
                return codec->name;
            }
        }
    }
    return "unknown";
}

std::string AvFormat::getFormat() {
    return "";
}

bool ffmpeg::mergeAVFormatContexts(const std::string &output, const std::initializer_list<AvFormat> &formats) {
    int32_t ec = 0;
    auto outputContext = utils::avformatAllocOutputContextUniquePtr(
            nullptr,
            nullptr,
            output.c_str(),
            &ec);

    if (!outputContext) {
        log::error(TAG, "Cannot alloc output context, error code: %d", ec);
        return false;
    }

    if (!(outputContext->oformat->flags & AVFMT_NOFILE)) {
        ec = avio_open(&(outputContext->pb), output.c_str(), AVIO_FLAG_WRITE);
        if (ec < 0) {
            log::error(TAG, "Could not open output file '%s'", output.c_str());
            return false;
        }
    }

    std::vector<AVFormatContext*> contexts;
    for (auto& format : formats) {
        contexts.emplace_back(format.getFormatContext().get());
    }

    if ((ec = utils::mergeAVFormatContexts(outputContext.get(), contexts) < 0)) {
        log::error(TAG, "Failed to merge AVFormatContexts, error code: %d", ec);
    }

    return ec == 0;
}

AvFormat ffmpeg::getFormat(const char *filename) {
    return AvFormat(filename);
}
