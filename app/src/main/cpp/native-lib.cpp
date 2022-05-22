#include <jni.h>
#include <string>
#include <cinttypes>
#include <memory>
#include <android/log.h>
#include <sstream>

#include "json.hpp"

extern "C" {
#include "libavformat/avformat.h"
}

inline auto MakeFormatPair() {
    using FormatPair = std::pair<AVFormatContext *, AVFormatContext *>;

    auto deleter = [](FormatPair *pair){
        //清理工作
        avformat_close_input(&pair->first);

        if (pair->second && !(pair->second->oformat->flags & AVFMT_NOFILE)) {
            avio_close(pair->second->pb);
        }
        avformat_free_context(pair->second);

        delete pair;
    };
    return std::unique_ptr<FormatPair, decltype(deleter)>(new FormatPair(nullptr, nullptr), deleter);
}

/**
 * 视频封装格式转换
 * @param inputFile 输入视频文件
 * @param outputFile 输出视频文件，视频格式会根据文件名自动推断
 * */
int32_t VideoFormatConversion(const std::string& inputFile, const std::string& outputFile) noexcept {

    //准备阶段

    //输入对应一个AVFormatContext，输出对应一个AVFormatContext
    //（Input AVFormatContext and Output AVFormatContext）
    auto ioFormatPair = MakeFormatPair();

    //尝试打开输入文件
    int32_t rc;
    //fmt置空，自动通过文件名检测格式
    if((rc = avformat_open_input(&ioFormatPair->first, inputFile.c_str(), nullptr, nullptr)) < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "videoFormatConversion", "Could not open input file.");
        return rc;
    }

    if((rc = avformat_find_stream_info(ioFormatPair->first, 0)) < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "videoFormatConversion", "Failed to retrieve input stream information");
        return rc;
    }

    av_dump_format(ioFormatPair->first, 0, inputFile.c_str(), 0);

    //获得一个输出获得一个AVFormatContext，format自动通过文件名推断
    avformat_alloc_output_context2(&ioFormatPair->second, nullptr, nullptr, outputFile.c_str());
    if(!ioFormatPair->second) {
        __android_log_print(ANDROID_LOG_DEBUG, "videoFormatConversion", "Could not create output context");
        return AVERROR_UNKNOWN;
    }

    //根据输入流创建输出流（Create output AVStream according to input AVStream）
    for (int i = 0; i < ioFormatPair->first->nb_streams; ++i) {
        __android_log_print(ANDROID_LOG_DEBUG, "videoFormatConversion", "Create output AVStream according to input AVStream");

        AVStream *in_stream = ioFormatPair->first->streams[i];

        AVCodec *codec = avcodec_find_decoder(in_stream->codecpar->codec_id);
        if (codec == nullptr) {
            codec = avcodec_find_encoder(in_stream->codecpar->codec_id);
        }
        if (codec == nullptr) {
            __android_log_print(ANDROID_LOG_DEBUG, "videoFormatConversion", "Not found codec");
            return -1;
        }

        __android_log_print(ANDROID_LOG_DEBUG, "videoFormatConversion", "codec: %p", codec);

        AVStream *out_stream = avformat_new_stream(ioFormatPair->second, codec);
        if (!out_stream) {
            __android_log_print(ANDROID_LOG_DEBUG, "videoFormatConversion", "Failed allocating output stream");
            return AVERROR_UNKNOWN;
        }

        //复制AVCodecContext的设置（Copy the settings of AVCodecContext）
        AVCodecContext *codec_ctx = avcodec_alloc_context3(codec);
        rc = avcodec_parameters_to_context(codec_ctx, in_stream->codecpar);
        if (rc < 0) {
            __android_log_print(ANDROID_LOG_DEBUG, "videoFormatConversion",
                                "Failed to copy in_stream codecpar to codec context");
            return rc;
        }

        codec_ctx->codec_tag = 0;
        if (ioFormatPair->second->oformat->flags & AVFMT_GLOBALHEADER) {
            codec_ctx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
        }

        rc = avcodec_parameters_from_context(out_stream->codecpar, codec_ctx);
        if (rc < 0) {
            __android_log_print(ANDROID_LOG_DEBUG, "videoFormatConversion",
                                "Failed to copy codec context to out_stream codecpar context");
            return rc;
        }
    }

    //输出一下格式
    av_dump_format(ioFormatPair->second, 0, outputFile.c_str(), 1);

    //打开输出文件（Open output file）
    if (!(ioFormatPair->second->oformat->flags & AVFMT_NOFILE)) {
        rc = avio_open(&ioFormatPair->second->pb, outputFile.c_str(), AVIO_FLAG_WRITE);
        if (rc < 0) {
            __android_log_print(ANDROID_LOG_DEBUG, "videoFormatConversion", "Could not open output file '%s'", outputFile.c_str());
            return rc;
        }
    }

    //写文件头（Write file header）
    rc = avformat_write_header(ioFormatPair->second, NULL);
    if (rc < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "videoFormatConversion", "Error occurred when opening output file");
        return rc;
    }

    AVPacket pkt;
    int32_t frame_index=0;
    //获取AVPacket（Get an AVPacket）
    while ((rc = av_read_frame(ioFormatPair->first, &pkt)) >= 0) {
        //__android_log_print(ANDROID_LOG_DEBUG, "videoFormatConversion", "Get an AVPacket");
        AVStream *in_stream = ioFormatPair->first->streams[pkt.stream_index];
        AVStream *out_stream = ioFormatPair->second->streams[pkt.stream_index];

        /* copy packet */
        //转换PTS/DTS（Convert PTS/DTS）
        pkt.pts = av_rescale_q_rnd(pkt.pts, in_stream->time_base, out_stream->time_base, (AVRounding)(AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
        pkt.dts = av_rescale_q_rnd(pkt.dts, in_stream->time_base, out_stream->time_base, (AVRounding)(AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
        pkt.duration = av_rescale_q(pkt.duration, in_stream->time_base, out_stream->time_base);
        pkt.pos = -1;

        //写入（Write）
        rc = av_interleaved_write_frame(ioFormatPair->second, &pkt);
        if (rc < 0) {
            __android_log_print(ANDROID_LOG_DEBUG, "videoFormatConversion", "Error muxing packet");
            break;
        }
        av_packet_unref(&pkt);
        frame_index++;
    }


    //写文件尾（Write file trailer）
    av_write_trailer(ioFormatPair->second);

    __android_log_print(ANDROID_LOG_DEBUG, "videoFormatConversion", "rc=%d", rc);
    return (rc != AVERROR_EOF) ? -1 : 0;
}

/**
 * 提取视频音频
 * @param inputFile 输入视频文件
 * @param outputFile 输出音频文件
 * */
int32_t ExtractAudio(const std::string& inputFile, const std::string& outputFile) noexcept {
    auto ioFormatPair = MakeFormatPair();

    int32_t rc;


    rc = avformat_open_input(&ioFormatPair->first, inputFile.c_str(), 0, 0);
    if (rc < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "extractAudio", "Could not open input file.");
        return rc;
    }
    rc = avformat_find_stream_info(ioFormatPair->first, 0);
    if (rc < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "extractAudio", "Failed to retrieve input stream information");
        return rc;
    }

    avformat_alloc_output_context2(&ioFormatPair->second, NULL, NULL, outputFile.c_str());
    if (!ioFormatPair->second) {
        __android_log_print(ANDROID_LOG_DEBUG, "extractAudio", "Could not create output context");
        rc = AVERROR_UNKNOWN;
        return rc;
    }

    int32_t audioIndex = -1;
    const char *audioCodecName = nullptr;

    for (int32_t i = 0; i < ioFormatPair->first->nb_streams; ++i) {
        AVStream *in_stream = ioFormatPair->first->streams[i];
        AVStream *out_stream;

        if (ioFormatPair->first->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            audioIndex = i;

            AVCodec *codec = avcodec_find_decoder(in_stream->codecpar->codec_id);
            if (codec == nullptr) {
                codec = avcodec_find_encoder(in_stream->codecpar->codec_id);
            }
            if (codec == nullptr) {
                __android_log_print(ANDROID_LOG_DEBUG, "extractAudio", "Not found codec");
                return -1;
            }

            audioCodecName = codec->name;
            out_stream = avformat_new_stream(ioFormatPair->second, codec);

            if (!out_stream) {
                __android_log_print(ANDROID_LOG_DEBUG, "extractAudio", "Failed allocating output stream");
                return AVERROR_UNKNOWN;
            }

            //复制AVCodecContext的设置（Copy the settings of AVCodecContext）
            AVCodecContext *codec_ctx = avcodec_alloc_context3(codec);
            rc = avcodec_parameters_to_context(codec_ctx, in_stream->codecpar);
            if (rc < 0) {
                __android_log_print(ANDROID_LOG_DEBUG, "extractAudio",
                                    "Failed to copy in_stream codecpar to codec context");
                return rc;
            }

            codec_ctx->codec_tag = 0;
            if (ioFormatPair->second->oformat->flags & AVFMT_GLOBALHEADER) {
                codec_ctx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
            }

            rc = avcodec_parameters_from_context(out_stream->codecpar, codec_ctx);
            if (rc < 0) {
                __android_log_print(ANDROID_LOG_DEBUG, "extractAudio",
                                    "Failed to copy codec context to out_stream codecpar context");
                return rc;
            }

            break;
        }
    }

    if (audioIndex == -1) {
        __android_log_print(ANDROID_LOG_DEBUG, "extractAudio",
                            "No audio file found");
        return -1;
    }

    if (!(ioFormatPair->second->oformat->flags & AVFMT_NOFILE)) {
        rc = avio_open(&ioFormatPair->second->pb, outputFile.c_str(), AVIO_FLAG_WRITE);
        if (rc < 0) {
            __android_log_print(ANDROID_LOG_DEBUG, "extractAudio",
                                "Could not open output file '%s'", outputFile.c_str());
            return rc;
        }
    }

    //写入文件头
    rc = avformat_write_header(ioFormatPair->second, NULL);
    if (rc < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "extractAudio",
                            "Error occurred when opening audio output file");
        return rc;
    }

    AVPacket pkt;
    while (av_read_frame(ioFormatPair->first, &pkt) >= 0) {
        AVStream *in_stream, *out_stream;

        if (pkt.stream_index != audioIndex) {
            continue;
        }

        out_stream = ioFormatPair->second->streams[0];
        in_stream  = ioFormatPair->first->streams[pkt.stream_index];

        //Convert PTS/DTS
        pkt.pts = av_rescale_q_rnd(pkt.pts, in_stream->time_base, out_stream->time_base, (AVRounding)(AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
        pkt.dts = av_rescale_q_rnd(pkt.dts, in_stream->time_base, out_stream->time_base, (AVRounding)(AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
        pkt.duration = av_rescale_q(pkt.duration, in_stream->time_base, out_stream->time_base);
        pkt.pos = -1;
        pkt.stream_index=0;

        //Write
        rc = av_interleaved_write_frame(ioFormatPair->second, &pkt);
        if (rc < 0) {
            __android_log_print(ANDROID_LOG_DEBUG, "extractAudio","Error muxing packet");
            return rc;
        }

        av_packet_unref(&pkt);
    }

    rc = av_write_trailer(ioFormatPair->second);
    __android_log_print(ANDROID_LOG_DEBUG, "extractAudio","Completed audioCodecName=%s", audioCodecName);

    return rc;
}

/**
 * 获取视频音频格式
 * @param inputFile 输入的视频文件
 * */
const char *GetVideoAudioFormat(const std::string& inputFile) {

    AVFormatContext *tempFormatContext = nullptr;
    auto deleter = [](AVFormatContext *avFormatContext){
        if (avFormatContext != nullptr) {
            avformat_close_input(&avFormatContext);
        }
    };
    std::unique_ptr<AVFormatContext, decltype(deleter)> avFormatContext(nullptr, deleter);


    int32_t rc;
    rc = avformat_open_input(&tempFormatContext, inputFile.c_str(), 0, 0);
    avFormatContext.reset(tempFormatContext);
    if (rc < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "getVideoAudioFormat", "Could not open input file.");
        return nullptr;
    }

    rc = avformat_find_stream_info(avFormatContext.get(), 0);
    if (rc < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "getVideoAudioFormat", "Failed to retrieve input stream information");
        return nullptr;
    }

    for (int32_t i = 0; i < avFormatContext->nb_streams; ++i) {
        AVStream *in_stream = avFormatContext->streams[i];

        if (avFormatContext->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            AVCodec *codec = avcodec_find_decoder(in_stream->codecpar->codec_id);
            if (codec == nullptr) {
                codec = avcodec_find_encoder(in_stream->codecpar->codec_id);
            }
            if (codec == nullptr) {
                __android_log_print(ANDROID_LOG_DEBUG, "getVideoAudioFormat", "Not found codec");
                return nullptr;
            }
            return codec->name;
        }
    }

    return nullptr;
}

/**
 * 取视频或音频信息
 * */
nlohmann::json GetMediaInfo(const std::string& file) {
    nlohmann::json json;

    AVFormatContext *context = nullptr;

    auto deleter = [](AVFormatContext** ptr){
        if (*ptr) {
            avformat_close_input(ptr);
            avformat_free_context(*ptr);
        }
    };
    std::unique_ptr<AVFormatContext*, decltype(deleter)> autoDeleter(&context, deleter);

    auto rc = avformat_open_input(&context, file.c_str(), nullptr, nullptr);
    if (rc < 0) {
        json["code"] = rc;
        json["message"] = "Could not open input file.";
        return json;
    }

    rc = avformat_find_stream_info(context, 0);
    if (rc < 0) {
        json["code"] = rc;
        json["message"] = "Could not find stream info.";
    } else {
        json["code"] = 0;
        //媒体文件时长 微秒
        json["duration"] = context->duration;
        //媒体文件时长 秒
        json["second"] = static_cast<int64_t>(context->duration / AV_TIME_BASE);
        //码率 bps
        json["bit_rate"] = static_cast<int64_t>(context->bit_rate);

        nlohmann::json streams;
        for (int32_t i = 0; i < context->nb_streams; ++i) {
            AVCodec *codec = avcodec_find_decoder(context->streams[i]->codecpar->codec_id);
            if (!codec) {
                continue;
            }

            nlohmann::json item;

            item["name"] = codec->name;
            streams.push_back(item);
        }

        json["streams"] = streams;
    }

    return json;
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
    return VideoFormatConversion(env->GetStringUTFChars(in_filename, JNI_FALSE),
                                 env->GetStringUTFChars(out_filename, JNI_FALSE));
}

extern "C"
JNIEXPORT jint JNICALL
Java_cc_kafuu_bilidownload_jniexport_JniTools_extractAudio(JNIEnv *env, jclass clazz,
                                                           jstring in_filename,
                                                           jstring out_filename) {
    // TODO: implement extractAudio()
    return ExtractAudio(env->GetStringUTFChars(in_filename, JNI_FALSE),
                        env->GetStringUTFChars(out_filename, JNI_FALSE));
}

extern "C"
JNIEXPORT jstring JNICALL
Java_cc_kafuu_bilidownload_jniexport_JniTools_getVideoAudioFormat(JNIEnv *env, jclass clazz,
                                                                  jstring in_filename) {
    // TODO: implement getVideoAudioFormat()
    return env->NewStringUTF(GetVideoAudioFormat(env->GetStringUTFChars(in_filename, JNI_FALSE)));
}

extern "C"
JNIEXPORT jstring JNICALL
Java_cc_kafuu_bilidownload_jniexport_JniTools_getMediaInfo(JNIEnv *env, jclass clazz,
                                                           jstring filename) {
    // TODO: implement getMediaInfo()
    auto json = GetMediaInfo(env->GetStringUTFChars(filename, JNI_FALSE));
    return env->NewStringUTF((std::stringstream() << json).str().c_str());
}
