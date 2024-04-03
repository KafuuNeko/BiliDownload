#pragma once

#include <string>
#include <memory>
#include <string>

extern "C" {
#include "libavformat/avformat.h"
#include "libavutil/log.h"
}

namespace ffmpeg {
    class AvFormat {
    public:
        AvFormat(const std::string &filename);

        bool extract(const std::string &outputFilename,
                     std::function<bool(size_t index, AVStream *stream)> streamFilter = nullptr,
                     std::function<bool(size_t index, AVPacket *packet)> packetFilter = nullptr);

        bool extractAudio(const std::string &outputFilename);

        std::string getAudioFormat();

        std::string getFormat();

        int32_t errorCode() const noexcept {
            return ec;
        }

    private:
        std::shared_ptr<AVFormatContext> mInputFormatCtx;
        int32_t ec = 0;
    };
}



