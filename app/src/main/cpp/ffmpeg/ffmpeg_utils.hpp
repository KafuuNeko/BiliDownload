//
// Created by kafuu on 2024/4/3.
//
#pragma once

extern "C" {
#include "libavformat/avformat.h"
#include "libavutil/log.h"
}

#include "android_log.hpp"
#include <memory>

namespace ffmpeg::utils {

    constexpr auto TAG = "ffmpeg_utils";

    /**
     * @brief 复制输入流的设置到输出流。
     *
     * 此函数遍历输入格式上下文中的所有流（例如视频、音频流等），为每个流找到对应的编解码器，
     * 并在输出格式上下文中创建新的流。然后，将输入流的编解码器参数复制到新创建的输出流中，
     * 以确保输出流保持与输入流相同的编解码参数。
     *
     * @param inputCtx 输入的AVFormatContext，包含要被复制的流信息。
     * @param outputCtx 输出的AVFormatContext，新流将被添加到此上下文中。
     * @param filter 筛选拷贝的stream
     * @return int 操作成功返回0，失败返回非0错误码。
     *
     * @note 此函数不仅复制流的编解码器参数，还会根据输出格式上下文的要求设置全局头部标志（如果必要）。
     *       如果找不到对应的编解码器或者在复制参数过程中遇到错误，函数会返回相应的错误码。
     */
    inline int copyStreamSettings(
            AVFormatContext *inputCtx,
            AVFormatContext *outputCtx,
            std::function<bool(size_t index, AVStream *stream)> filter = nullptr) {
        int32_t rc;
        for (size_t i = 0; i < inputCtx->nb_streams; ++i) {
            AVStream *inStream = inputCtx->streams[i];
            AVCodec *codec = avcodec_find_decoder(inStream->codecpar->codec_id);

            if (filter && !filter(i, inStream)) {
                continue;
            }

            if (!codec) {
                codec = avcodec_find_encoder(inStream->codecpar->codec_id);
            }
            if (!codec) {
                log::error(TAG, "Not found codec");
                return -1;
            }

            AVStream *outStream = avformat_new_stream(outputCtx, codec);
            if (!outStream) {
                log::error(TAG, "Failed allocating output stream");
                return AVERROR_UNKNOWN;
            }

            AVCodecContext *codecCtx = avcodec_alloc_context3(codec);
            rc = avcodec_parameters_to_context(codecCtx, inStream->codecpar);
            if (rc < 0) {
                log::error(TAG, "Failed to copy inStream codec-par to codec context");
                return rc;
            }

            codecCtx->codec_tag = 0;
            if (outputCtx->oformat->flags & AVFMT_GLOBALHEADER) {
                codecCtx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
            }

            rc = avcodec_parameters_from_context(outStream->codecpar, codecCtx);
            if (rc < 0) {
                log::error(TAG, "Failed to copy codec context to outStream codec-par context");
                return rc;
            }
        }

        return 0;
    }


    /**
     * @brief 复制并转换输入流中的所有数据包到输出流。
     *
     * 此函数逐个读取输入流中的AVPacket，将时间戳从输入流时间基转换到输出流时间基，
     * 然后将调整后的数据包写入输出流。这一过程持续到无法从输入流读取更多的数据包为止。
     *
     * @param inputCtx 输入流的AVFormatContext。
     * @param outputCtx 输出流的AVFormatContext。
     * @param filter 筛选拷贝的packet
     * @return int 成功返回0，失败返回非0错误码。
     */
    inline int copyAndConvertPackets(
            AVFormatContext *inputCtx,
            AVFormatContext *outputCtx,
            std::function<bool(size_t index, AVPacket *packet)> filter = nullptr) {
        AVPacket pkt;
        int32_t rc;
        size_t frameIndex = 0;

        while ((rc = av_read_frame(inputCtx, &pkt)) >= 0) {
            AVStream *inStream = inputCtx->streams[pkt.stream_index];
            AVStream *outStream = outputCtx->streams[pkt.stream_index];

            if (filter && !filter(frameIndex, &pkt)) {
                continue;
            }

            // 转换PTS/DTS
            pkt.pts = av_rescale_q_rnd(pkt.pts, inStream->time_base, outStream->time_base,
                                       (AVRounding) (AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));
            pkt.dts = av_rescale_q_rnd(pkt.dts, inStream->time_base, outStream->time_base,
                                       (AVRounding) (AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));
            pkt.duration = av_rescale_q(pkt.duration, inStream->time_base, outStream->time_base);
            pkt.pos = -1;

            // 写入调整后的数据包到输出流
            rc = av_interleaved_write_frame(outputCtx, &pkt);
            if (rc < 0) {
                log::error(TAG, "Error maxing packet");
                av_packet_unref(&pkt);
                return rc;
            }
            av_packet_unref(&pkt);
            frameIndex++;
        }

        return 0;
    }

    /**
     * 创建并返回一个管理AVFormatContext资源的std::unique_ptr。
     *
     * 该函数尝试创建一个AVFormatContext，该上下文用于输出媒体文件。创建过程中使用的参数包括输出格式、格式名称和文件名。
     * 创建成功后，返回的std::unique_ptr确保了资源在不再使用时能够被自动释放，包括关闭相关的IO上下文和释放AVFormatContext。
     *
     * @param outputFormat 指向AVOutputFormat的指针，指定输出文件的格式。如果为nullptr，则通过formatName或filename推断。
     * @param formatName 指定输出文件的格式名称。如果为nullptr，则通过filename推断。
     * @param filename 输出文件的名称。这也可以是URL，例如流媒体的URL。
     * @param rc 用于接收avformat_alloc_output_context2函数调用的返回码。如果不需要这个信息，可以省略此参数。
     *            成功时，*rc被设置为0；失败时，*rc被设置为一个负数错误码。
     *
     * @return 返回一个管理AVFormatContext的std::unique_ptr。如果创建过程中发生错误，返回的unique_ptr将不管理任何资源。
     *         调用者可以通过检查unique_ptr是否管理一个非空指针来判断创建过程是否成功。
     *
     * @note 函数内部使用了一个自定义删除器，确保在资源不再需要时能够自动调用avformat_close_input释放资源。
     */
    auto avformatAllocOutputContextUniquePtr(
            AVOutputFormat *outputFormat,
            const char *formatName,
            const char *filename,
            int32_t *rc = nullptr) {
        auto deleter = [](AVFormatContext *ctx) {
            if (!ctx) return;
            if (!(ctx->oformat->flags & AVFMT_NOFILE)) {
                // 关闭与格式上下文关联的IO上下文
                avio_closep(&ctx->pb);
            }
            avformat_free_context(ctx);
        };
        AVFormatContext *outputContext = nullptr;
        auto ec = avformat_alloc_output_context2(
                &outputContext, outputFormat, formatName, filename);
        if (rc) *rc = ec;
        if (ec < 0) {
            log::error(TAG, "Could not create output context, error code: %d", ec);
        }
        return std::unique_ptr<AVFormatContext, decltype(deleter)>(outputContext, deleter);
    }

    /**
     * 尝试打开指定的媒体文件或流，并返回一个管理AVFormatContext的std::unique_ptr。
     *
     * @param url 指向媒体资源的URL，可以是文件路径或网络流地址。
     * @param fmt 指定输入格式的AVInputFormat，如果为nullptr，则自动探测格式。
     * @param options 指向AVDictionary对象的指针的地址，用于传递选项给解复用器；调用结束后可以检查选项的处理情况。
     *                如果不需要选项，此参数可以为nullptr。
     * @param rc 指向int32_t变量的指针，用于接收函数的返回代码：成功时为0，失败时为负数错误码。
     *           如果不关心返回代码，此参数可以省略。
     *
     * @return 返回一个std::unique_ptr<AVFormatContext>，它负责管理AVFormatContext资源。
     *         如果打开操作失败，则返回的unique_ptr不会管理任何资源。
     *
     * @note 函数内部使用了一个自定义删除器，确保在资源不再需要时能够自动调用avformat_close_input释放资源。
     */
    auto avformatOpenInputPtr(
            const char *url,
            AVInputFormat *fmt,
            AVDictionary **options,
            int32_t *rc = nullptr) {
        auto deleter = [](AVFormatContext *ctx) {
            if (!ctx) return;
            avformat_close_input(&ctx);
        };
        AVFormatContext *inputContext = nullptr;
        auto ec = avformat_open_input(&inputContext, url, fmt, options);
        if (rc) *rc = ec;
        if (ec < 0) {
            log::error(TAG, "Could not open input file, error code: %d", ec);
        }
        return std::unique_ptr<AVFormatContext, decltype(deleter)>(inputContext, deleter);
    }
}