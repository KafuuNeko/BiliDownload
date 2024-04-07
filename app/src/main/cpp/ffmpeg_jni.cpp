#include <jni.h>
#include <string>

extern "C" {
#include "libavformat/avformat.h"
}

#include "av_format.hpp"
#include "ffmpeg_utils.hpp"

#define FFMPEG_JNI_FUNC(return_type, func_name) \
extern "C" \
JNIEXPORT return_type JNICALL \
Java_cc_kafuu_bilidownload_common_jniexport_FFMpegJNI_##func_name

FFMPEG_JNI_FUNC(void, init)(JNIEnv *env, jobject thiz) {
    avformat_network_init();
}

static std::string javaStringToCppString(JNIEnv *env, jstring jString) {
    if (!jString) { // 检查是否为 null
        return "";
    }

    const char *ptr = env->GetStringUTFChars(jString, JNI_FALSE);
    if (!ptr) {
        // GetStringUTFChars 调用失败，可能是因为内存不足
        env->ExceptionClear();
        return "";
    }

    std::string stringCpp(ptr);
    env->ReleaseStringUTFChars(jString, ptr);

    return stringCpp;
}

static std::vector<std::string> javaStringArrayToCppStringVector(JNIEnv* env, jobjectArray javaStringArray) {
    std::vector<std::string> cppStringVector;
    if (!javaStringArray) {
        return cppStringVector;
    }

    jsize arrayLength = env->GetArrayLength(javaStringArray);
    for (jsize i = 0; i < arrayLength; i++) {
        jstring javaString = (jstring) env->GetObjectArrayElement(javaStringArray, i);
        cppStringVector.emplace_back(javaStringToCppString(env, javaString));
        env->DeleteLocalRef(javaString);
    }
    return cppStringVector;
}


FFMPEG_JNI_FUNC(jstring, ffmpegInfo)(JNIEnv *env, jobject obj) {
    return env->NewStringUTF(avcodec_configuration());
}

FFMPEG_JNI_FUNC(jint, videoFormatConversion)(JNIEnv *env, jobject obj, jstring from,
                                             jstring to) {
    ffmpeg::AvFormat avFormat(javaStringToCppString(env, from));
    avFormat.extract(javaStringToCppString(env, to));
    return avFormat.errorCode();
}

FFMPEG_JNI_FUNC(jint, extractAudio)(JNIEnv *env, jobject obj, jstring from, jstring to) {
    ffmpeg::AvFormat avFormat(javaStringToCppString(env, from));
    avFormat.extractAudio(javaStringToCppString(env, to));
    return avFormat.errorCode();
}

FFMPEG_JNI_FUNC(jstring, getVideoAudioFormat)(JNIEnv *env, jobject obj, jstring from) {
    ffmpeg::AvFormat avFormat(javaStringToCppString(env, from));
    return env->NewStringUTF(avFormat.getAudioFormat().c_str());
}

FFMPEG_JNI_FUNC(jstring, getMediaInfo)(JNIEnv *env, jobject obj, jstring from) {
    ffmpeg::AvFormat avFormat(javaStringToCppString(env, from));
    return env->NewStringUTF(avFormat.getFormat().c_str());
}

FFMPEG_JNI_FUNC(jboolean, mergeMedia)(JNIEnv *env, jobject thiz,
                                      jstring output,
                                      jobjectArray resources,
                                      jobjectArray mimeTypes) {

    auto resourceVector = javaStringArrayToCppStringVector(env, resources);
    auto mimeTypeVector = javaStringArrayToCppStringVector(env, mimeTypes);

    std::vector<ffmpeg::AvFormat> formats;
    for (size_t i = 0; i < resourceVector.size(); ++i) {
        AVInputFormat *fmt = nullptr;
        if (i < mimeTypeVector.size()) {
            fmt = ffmpeg::utils::findFormatForMimeType(mimeTypeVector[i]);
        }
        formats.emplace_back(ffmpeg::getFormat(resourceVector[i], fmt));
    }

    bool status = ffmpeg::mergeAVFormatContexts(javaStringToCppString(env, output), formats);
    return status ? JNI_TRUE : JNI_FALSE;
}

