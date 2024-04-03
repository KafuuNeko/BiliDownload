# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.10.2)

# Declares and names the project.

project("bilidownload")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
        ffmpeg-lib

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        ffmpeg-lib.cpp)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log )



#add libavcodec
add_library(libavcodec
        SHARED
        IMPORTED
)

SET_TARGET_PROPERTIES(
        libavcodec
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/libs/${ANDROID_ABI}/libavcodec.so
)

#add libavdevice
add_library(libavdevice
        SHARED
        IMPORTED
)

SET_TARGET_PROPERTIES(
        libavdevice
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/libs/${ANDROID_ABI}/libavdevice.so
)


#add libavfilter
add_library(libavfilter
        SHARED
        IMPORTED
)

SET_TARGET_PROPERTIES(
        libavfilter
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/libs/${ANDROID_ABI}/libavfilter.so
)


#add libavformat
add_library(libavformat
        SHARED
        IMPORTED
)

SET_TARGET_PROPERTIES(
        libavformat
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/libs/${ANDROID_ABI}/libavformat.so
)


#add libavutil
add_library(libavutil
        SHARED
        IMPORTED
)

SET_TARGET_PROPERTIES(
        libavutil
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/libs/${ANDROID_ABI}/libavutil.so
)

#add libswresample
add_library(libswresample
        SHARED
        IMPORTED
)

SET_TARGET_PROPERTIES(
        libswresample
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/libs/${ANDROID_ABI}/libswresample.so
)

#add libswscale
add_library(libswscale
        SHARED
        IMPORTED
)

SET_TARGET_PROPERTIES(
        libswscale
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/libs/${ANDROID_ABI}/libswscale.so
)

include_directories(ffmpeg/)


# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        ffmpeg-lib
        libavcodec
        libavdevice
        libavfilter
        libavformat
        libavutil
        libswresample
        libswscale
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib} )