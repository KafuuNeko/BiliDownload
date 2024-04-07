//
// Created by kafuu on 2024/4/3.
//
#ifndef ANDROID_LOG_H
#define ANDROID_LOG_H

#include <string>
#include <android/log.h>

namespace log {
    inline void debug(const char *tag, const char *format, ...) {
        va_list args;
        va_start(args, format);
        __android_log_vprint(ANDROID_LOG_DEBUG, tag, format, args);
        va_end(args);
    }

    inline void error(const char *tag, const char *format, ...) {
        va_list args;
        va_start(args, format);
        __android_log_vprint(ANDROID_LOG_ERROR, tag, format, args);
        va_end(args);
    }
}

#endif