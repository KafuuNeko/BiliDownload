#include <jni.h>

extern "C" {
#include "libavformat/avformat.h"
}

#include "av_format.hpp"

#define FFMPEG_JNI_FUNC(return_type, func_name) \
extern "C" \
JNIEXPORT return_type JNICALL \
Java_cc_kafuu_bilidownload_common_jniexport_FFMpegJNI_##func_name

FFMPEG_JNI_FUNC(jstring, ffmpegInfo)(JNIEnv *env, jobject obj) {
    return env->NewStringUTF(avcodec_configuration());
}

FFMPEG_JNI_FUNC(jint, videoFormatConversion)(JNIEnv *env, jobject obj, jstring from,
                                             jstring to) {
    ffmpeg::AvFormat avFormat(env->GetStringUTFChars(from, JNI_FALSE));
    avFormat.extract(env->GetStringUTFChars(to, JNI_FALSE));
    return avFormat.errorCode();
}

FFMPEG_JNI_FUNC(jint, extractAudio)(JNIEnv *env, jobject obj, jstring from, jstring to) {
    ffmpeg::AvFormat avFormat(env->GetStringUTFChars(from, JNI_FALSE));
    avFormat.extractAudio(env->GetStringUTFChars(to, JNI_FALSE));
    return avFormat.errorCode();
}

FFMPEG_JNI_FUNC(jstring, getVideoAudioFormat)(JNIEnv *env, jobject obj, jstring from) {
    ffmpeg::AvFormat avFormat(env->GetStringUTFChars(from, JNI_FALSE));
    return env->NewStringUTF(avFormat.getAudioFormat().c_str());
}

FFMPEG_JNI_FUNC(jstring, getMediaInfo)(JNIEnv *env, jobject obj, jstring from) {
    ffmpeg::AvFormat avFormat(env->GetStringUTFChars(from, JNI_FALSE));
    return env->NewStringUTF(avFormat.getFormat().c_str());
}