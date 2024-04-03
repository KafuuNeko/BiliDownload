//
// Created by kafuu on 2024/4/3.
//
#pragma once

#include <string>
#include <android/log.h>

namespace log {
    void debug(const char *tag, const char *format, ...) {
        va_list args;
        va_start(args, format);
        __android_log_vprint(ANDROID_LOG_DEBUG, tag, format, args);
        va_end(args);
    }

    void error(const char *tag, const char *format, ...) {
        va_list args;
        va_start(args, format);
        __android_log_vprint(ANDROID_LOG_ERROR, tag, format, args);
        va_end(args);
    }
}

