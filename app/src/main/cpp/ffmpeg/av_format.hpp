#ifndef AV_FORMAT_H
#define AV_FORMAT_H

#include <string>
#include <memory>
#include <string>
#include <vector>

extern "C" {
#include "libavformat/avformat.h"
#include "libavutil/log.h"
}

namespace ffmpeg {
    class AvFormat {
    public:
        AvFormat(const std::string &filename, AVInputFormat *fmt = nullptr);

        bool extract(const std::string &outputFilename,
                     std::function<bool(size_t index, AVStream *stream)> streamFilter = nullptr,
                     std::function<bool(size_t index, AVPacket *packet)> packetFilter = nullptr);

        bool extractAudio(const std::string &outputFilename);

        std::string getAudioFormat();

        std::string getFormat();

        int32_t errorCode() const noexcept {
            return ec;
        }

        std::shared_ptr<AVFormatContext> getFormatContext() const noexcept {
            return mInputFormatCtx;
        }

    private:
        std::shared_ptr<AVFormatContext> mInputFormatCtx;
        int32_t ec = 0;
    };

    bool mergeAVFormatContexts(const std::string &output, const std::vector<AvFormat> &formats);

    AvFormat getFormat(const std::string &filename, AVInputFormat *fmt = nullptr);
}

#endif