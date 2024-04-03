cmake_minimum_required(VERSION 3.10.2)

project("bilidownload")

find_library(log-lib log)


#add libavcodec
add_library(libavcodec SHARED IMPORTED)

SET_TARGET_PROPERTIES(
        libavcodec
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/libs/${ANDROID_ABI}/libavcodec.so
)

#add libavdevice
add_library(libavdevice SHARED IMPORTED)

SET_TARGET_PROPERTIES(
        libavdevice
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/libs/${ANDROID_ABI}/libavdevice.so
)


#add libavfilter
add_library(libavfilter SHARED IMPORTED)

SET_TARGET_PROPERTIES(
        libavfilter
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/libs/${ANDROID_ABI}/libavfilter.so
)


#add libavformat
add_library(libavformat SHARED IMPORTED)

SET_TARGET_PROPERTIES(
        libavformat
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/libs/${ANDROID_ABI}/libavformat.so
)


#add libavutil
add_library(libavutil SHARED IMPORTED)

SET_TARGET_PROPERTIES(
        libavutil
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/libs/${ANDROID_ABI}/libavutil.so
)

#add libswresample
add_library(libswresample SHARED IMPORTED)

SET_TARGET_PROPERTIES(
        libswresample
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/libs/${ANDROID_ABI}/libswresample.so
)

#add libswscale
add_library(libswscale SHARED IMPORTED)

SET_TARGET_PROPERTIES(
        libswscale
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/libs/${ANDROID_ABI}/libswscale.so
)

include_directories(ffmpeg utils)

add_library(ffmpeg-lib SHARED
        ffmpeg_jni.cpp
        ffmpeg/av_format.cpp
)
target_link_libraries(ffmpeg-lib
        libavcodec
        libavdevice
        libavfilter
        libavformat
        libavutil
        libswresample
        libswscale
        ${log-lib}
)