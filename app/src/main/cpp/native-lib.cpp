#include <jni.h>
#include <string>
#include <cinttypes>
#include <memory>
#include <android/log.h>

extern "C" {
#include "libavformat/avformat.h"
}

inline int32_t videoFormatConversion(const std::string& in_filename, const std::string& out_filename) noexcept {

    //准备阶段
    using FormatPair = std::pair<AVFormatContext *, AVFormatContext *>;

    auto deleter = [](FormatPair *pair){
        //清理工作
        avformat_close_input(&pair->first);

        if (pair->second && !(pair->second->oformat->flags & AVFMT_NOFILE)) {
            avio_close(pair->second->pb);
        }
        avformat_free_context(pair->second);
    };

    FormatPair _FormatPair(nullptr, nullptr);
    //输入对应一个AVFormatContext，输出对应一个AVFormatContext
    //（Input AVFormatContext and Output AVFormatContext）
    std::unique_ptr<FormatPair, decltype(deleter)> ioFormat(&_FormatPair, deleter);

    //尝试打开输入文件
    int32_t rc;
    //fmt置空，自动通过文件名检测格式
    if((rc = avformat_open_input(&ioFormat->first, in_filename.c_str(), nullptr, nullptr)) < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "convert", "Could not open input file.");
        return rc;
    }

    if((rc = avformat_find_stream_info(ioFormat->first, 0)) < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "convert", "Failed to retrieve input stream information");
        return rc;
    }

    av_dump_format(ioFormat->first, 0, in_filename.c_str(), 0);

    //获得一个输出获得一个AVFormatContext，format自动通过文件名推断
    avformat_alloc_output_context2(&ioFormat->second, nullptr, nullptr, out_filename.c_str());
    if(!ioFormat->second) {
        __android_log_print(ANDROID_LOG_DEBUG, "convert", "Could not create output context");
        return AVERROR_UNKNOWN;
    }

    //根据输入流创建输出流（Create output AVStream according to input AVStream）
    for (int i = 0; i < ioFormat->first->nb_streams; ++i) {
        __android_log_print(ANDROID_LOG_DEBUG, "convert", "Create output AVStream according to input AVStream");

        AVStream *in_stream = ioFormat->first->streams[i];

        AVCodec *codec = avcodec_find_decoder(in_stream->codecpar->codec_id);
        if (codec == nullptr) {
            codec = avcodec_find_encoder(in_stream->codecpar->codec_id);
        }

        __android_log_print(ANDROID_LOG_DEBUG, "convert", "codec: %p", codec);

        AVStream *out_stream = avformat_new_stream(ioFormat->second, codec);
        if (!out_stream) {
            __android_log_print(ANDROID_LOG_DEBUG, "convert", "Failed allocating output stream");
            return AVERROR_UNKNOWN;
        }

        //复制AVCodecContext的设置（Copy the settings of AVCodecContext）
        AVCodecContext *codec_ctx = avcodec_alloc_context3(codec);
        rc = avcodec_parameters_to_context(codec_ctx, in_stream->codecpar);
        if (rc < 0) {
            __android_log_print(ANDROID_LOG_DEBUG, "convert",
                                "Failed to copy in_stream codecpar to codec context");
            return rc;
        }

        codec_ctx->codec_tag = 0;
        if (ioFormat->second->oformat->flags & AVFMT_GLOBALHEADER) {
            codec_ctx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
        }

        rc = avcodec_parameters_from_context(out_stream->codecpar, codec_ctx);
        if (rc < 0) {
            __android_log_print(ANDROID_LOG_DEBUG, "convert",
                                "Failed to copy codec context to out_stream codecpar context");
            return rc;
        }
    }

    //输出一下格式
    av_dump_format(ioFormat->second, 0, out_filename.c_str(), 1);

    //打开输出文件（Open output file）
    if (!(ioFormat->second->oformat->flags & AVFMT_NOFILE)) {
        rc = avio_open(&ioFormat->second->pb, out_filename.c_str(), AVIO_FLAG_WRITE);
        if (rc < 0) {
            __android_log_print(ANDROID_LOG_DEBUG, "convert", "Could not open output file '%s'", out_filename.c_str());
            return rc;
        }
    }

    //写文件头（Write file header）
    rc = avformat_write_header(ioFormat->second, NULL);
    if (rc < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "convert", "Error occurred when opening output file");
        return rc;
    }

    AVPacket pkt;
    int32_t frame_index=0;
    //获取AVPacket（Get an AVPacket）
    while ((rc = av_read_frame(ioFormat->first, &pkt)) >= 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "convert", "Get an AVPacket");
        AVStream *in_stream = ioFormat->first->streams[pkt.stream_index];
        AVStream *out_stream = ioFormat->second->streams[pkt.stream_index];

        /* copy packet */
        //转换PTS/DTS（Convert PTS/DTS）
        pkt.pts = av_rescale_q_rnd(pkt.pts, in_stream->time_base, out_stream->time_base, (AVRounding)(AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
        pkt.dts = av_rescale_q_rnd(pkt.dts, in_stream->time_base, out_stream->time_base, (AVRounding)(AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
        pkt.duration = av_rescale_q(pkt.duration, in_stream->time_base, out_stream->time_base);
        pkt.pos = -1;

        //写入（Write）
        rc = av_interleaved_write_frame(ioFormat->second, &pkt);
        if (rc < 0) {
            __android_log_print(ANDROID_LOG_DEBUG, "convert", "Error muxing packet");
            break;
        }
        av_packet_unref(&pkt);
        frame_index++;
    }


    //写文件尾（Write file trailer）
    av_write_trailer(ioFormat->second);

    __android_log_print(ANDROID_LOG_DEBUG, "convert", "rc=%d", rc);
    return (rc != AVERROR_EOF) ? -1 : 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_cc_kafuu_bilidownload_jniexport_JniTools_ffmpegInfo(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(avcodec_configuration());
}

extern "C"
JNIEXPORT jint JNICALL
Java_cc_kafuu_bilidownload_jniexport_JniTools_videoFormatConversion(JNIEnv *env, jclass clazz,
                                                                    jstring in_filename,
                                                                    jstring out_filename) {
    return videoFormatConversion(env->GetStringUTFChars(in_filename, JNI_FALSE),
                                 env->GetStringUTFChars(out_filename, JNI_FALSE));
}

