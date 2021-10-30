#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_cc_kafuu_bilidownload_jniexport_JniTools_stringFromJNI(JNIEnv *env, jclass thiz) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}


