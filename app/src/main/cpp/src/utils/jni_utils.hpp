#pragma once
//
// Created by kafuu on 2025/6/4.
//

#include <jni.h>
#include <vector>
#include <type_traits>
#include <string>
#include <functional>

/**
 * jobject 局部通用删除器
 */
struct JLocalRefDeleter {
    JNIEnv *env;

    void operator()(jobject obj) const {
        if (obj) env->DeleteLocalRef(obj);
    }
};

/**
 * jobject raii 局部引用通用包装方法
 */
template<typename T, typename = std::enable_if_t<std::is_convertible<T, jobject>::value>>
inline std::unique_ptr<std::remove_pointer_t<T>, JLocalRefDeleter>
WrapLocalRef(JNIEnv *env, T obj) {
    return std::unique_ptr<std::remove_pointer_t<T>, JLocalRefDeleter>(obj, JLocalRefDeleter{env});
}

/**
 * jobject 全局通用删除器
 */
struct JGlobalRefDeleter {
    JNIEnv *env;

    void operator()(jobject obj) const {
        if (obj) env->DeleteGlobalRef(obj);
    }
};

/**
 * jobject raii 全局引用通用包装方法
 */
template<typename T, typename = std::enable_if_t<std::is_convertible<T, jobject>::value>>
inline std::unique_ptr<std::remove_pointer_t<T>, JGlobalRefDeleter>
WrapGlobalRef(JNIEnv *env, T obj) {
    if (!obj) return nullptr;
    jobject globalRef = env->NewGlobalRef(obj);
    return std::unique_ptr<std::remove_pointer_t<T>, JGlobalRefDeleter>(
            globalRef, JGlobalRefDeleter{env}
    );
}

/**
 * 查找Java Class
 */
inline auto FindClass(JNIEnv *env, const char *name) {
    return WrapLocalRef(env, env->FindClass(name));
}

/**
 * @brief 创建Java整型数据包装类
 */
inline auto CreateJavaInteger(JNIEnv *env, jint value) {
    auto integer_class_ptr = FindClass(env, "java/lang/Integer");
    jmethodID int_ctor = env->GetMethodID(integer_class_ptr.get(), "<init>", "(I)V");
    return WrapLocalRef(env, env->NewObject(integer_class_ptr.get(), int_ctor, value));
}

/**
 * @brief 创建Java String型数据包装类
 */
inline auto CreateJavaString(JNIEnv *env, const std::string &value) {
    return WrapLocalRef(env, env->NewStringUTF(value.c_str()));
}


/**
 * @brief 创建Kotlin二元组
 */
inline auto CreateKotlinPair(JNIEnv *env, jobject first, jobject second) {
    auto pair_class_ptr = FindClass(env, "kotlin/Pair");
    jmethodID pair_ctor = env->GetMethodID(
            pair_class_ptr.get(), "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V"
    );
    return WrapLocalRef(env, env->NewObject(pair_class_ptr.get(), pair_ctor, first, second));
}

/**
 * @brief 创建Java布尔型数组
 */
template<
        class Iterator,
        typename = std::enable_if_t<std::is_convertible<decltype(*std::declval<Iterator>()), bool>::value>
>
inline auto CreateJBooleanArray(JNIEnv *env, Iterator begin, Iterator end) {
    auto size = std::distance(begin, end);
    auto array_ptr = WrapLocalRef(env, env->NewBooleanArray(size));
    if (!array_ptr) return WrapLocalRef(env, static_cast<jbooleanArray>(nullptr));
    std::vector<jboolean> j_booleans(size);
    std::transform(begin, end, j_booleans.begin(), [](auto v) { return static_cast<jboolean>(v); });
    env->SetBooleanArrayRegion(array_ptr.get(), 0, static_cast<jsize>(size), j_booleans.data());
    return array_ptr;
}

/**
 * @brief 创建Java String型数组
 */
template<
        class Iterator,
        typename = std::enable_if_t<
                std::is_convertible<decltype(*std::declval<Iterator>()), std::string>::value
                || std::is_convertible<decltype(*std::declval<Iterator>()), const char *>::value>
>
static auto CreateJStringArray(JNIEnv *env, Iterator begin, Iterator end) {
    auto size = std::distance(begin, end);
    auto string_class_ptr = FindClass(env, "java/lang/String");
    if (!string_class_ptr) return WrapLocalRef(env, static_cast<jobjectArray>(nullptr));
    auto array_ptr = WrapLocalRef(
            env, env->NewObjectArray(static_cast<jsize>(size), string_class_ptr.get(), nullptr)
    );
    if (!array_ptr) return WrapLocalRef(env, static_cast<jobjectArray>(nullptr));
    jsize index = 0;
    for (auto it = begin; it != end; ++it, ++index) {
        const char *cstr;
        if constexpr (std::is_same_v<decltype(*it), std::string> ||
                      std::is_same_v<decltype(*it), const std::string &>) {
            cstr = it->c_str();
        } else {
            cstr = *it;
        }
        auto jstr_ptr = WrapLocalRef(env, env->NewStringUTF(cstr));
        env->SetObjectArrayElement(array_ptr.get(), index, jstr_ptr.get());
    }
    return array_ptr;
}

/**
 * @brief 创建Java int型数组
 */
template<
        class Iterator,
        typename = std::enable_if_t<std::is_convertible<decltype(*std::declval<Iterator>()), jint>::value>
>
inline auto CreateJIntArray(JNIEnv *env, Iterator begin, Iterator end) {
    auto size = std::distance(begin, end);
    auto array = WrapLocalRef(env, env->NewIntArray(static_cast<jsize>(size)));
    if (array == nullptr) return WrapLocalRef(env, static_cast<jintArray>(nullptr));
    std::vector<jint> j_ints(size);
    std::transform(begin, end, j_ints.begin(), [](auto v) { return static_cast<jint>(v); });
    env->SetIntArrayRegion(array.get(), 0, static_cast<jsize>(size), j_ints.data());
    return array;
}

/**
 * @brief 基于Java String构建std::string
 */
inline std::string JStringToCString(JNIEnv *env, jstring text) {
    if (text == nullptr) return {};
    const char *c_text = env->GetStringUTFChars(text, nullptr);
    if (c_text == nullptr) return {};
    std::unique_ptr<const char, std::function<void(const char *)>> guard(
            c_text,
            [env, text](const char *p) { env->ReleaseStringUTFChars(text, p); }
    );
    return {c_text};
}

/**
 * @brief 基于 Java Integer(int) 构建 int32_t
 */
inline int32_t JIntToCInt(JNIEnv *env, jobject integer_obj) {
    if (integer_obj == nullptr) return 0;
    auto integer_class_ptr = FindClass(env, "java/lang/Integer");
    auto int_value_method = env->GetMethodID(integer_class_ptr.get(), "intValue", "()I");
    return env->CallIntMethod(integer_obj, int_value_method);
}

/**
 * @brief 基于 Java Long(long) 构建 int64_t
 */
inline int64_t JLongToCLong(JNIEnv *env, jobject long_obj) {
    if (long_obj == nullptr) return 0;
    auto long_class_ptr = FindClass(env, "java/lang/Long");
    auto long_value_method = env->GetMethodID(long_class_ptr.get(), "longValue", "()J");
    return static_cast<int64_t>(env->CallLongMethod(long_obj, long_value_method));
}

/**
 * @brief 基于 Java Boolean(boolean) 构建 C++ bool
 */
inline bool JBooleanToCBool(JNIEnv *env, jobject boolean_obj) {
    if (boolean_obj == nullptr) return false;
    auto boolean_class_ptr = FindClass(env, "java/lang/Boolean");
    auto bool_value_method = env->GetMethodID(boolean_class_ptr.get(), "booleanValue", "()Z");
    return env->CallBooleanMethod(boolean_obj, bool_value_method) == JNI_TRUE;
}

/**
 * @brief 基于 Java String[] 构建 std::vector<std::string>
 */
static std::vector<std::string> JStringArrayToCVector(JNIEnv *env, jobjectArray string_array) {
    std::vector<std::string> result;
    if (string_array == nullptr) return result;

    auto length = env->GetArrayLength(string_array);
    result.reserve(static_cast<size_t>(length));

    for (jsize i = 0; i < length; ++i) {
        auto element_ptr = WrapLocalRef(
                env, reinterpret_cast<jstring>(env->GetObjectArrayElement(string_array, i))
        );
        if (!element_ptr) {
            result.emplace_back("");
            continue;
        }
        result.emplace_back(JStringToCString(env, element_ptr.get()));
    }

    return result;
}

/**
 * @brief 将 Java List<String> 转换为 std::vector<std::string>
 */
static std::vector<std::string> JStringListToCVector(JNIEnv *env, jobject string_list) {
    std::vector<std::string> result;
    if (string_list == nullptr) return result;

    auto list_class_ptr = FindClass(env, "java/util/List");
    auto size_method = env->GetMethodID(list_class_ptr.get(), "size", "()I");
    auto get_method = env->GetMethodID(list_class_ptr.get(), "get", "(I)Ljava/lang/Object;");

    auto size = env->CallIntMethod(string_list, size_method);
    result.reserve(size);

    for (jint i = 0; i < size; ++i) {
        auto element_obj_ptr = WrapLocalRef(
                env, env->CallObjectMethod(string_list, get_method, i)
        );
        if (element_obj_ptr) {
            auto element_str = reinterpret_cast<jstring>(element_obj_ptr.get());
            result.emplace_back(JStringToCString(env, element_str));
        } else {
            result.emplace_back("");
        }
    }

    return result;
}

static void CallNativeCallback(JNIEnv *env, jobject listener, const std::vector<jobject> &args) {
    if (!listener) return;

    auto callback_class_ptr = WrapLocalRef(env, env->GetObjectClass(listener));
    if (!callback_class_ptr) return;

    auto invoke_method = env->GetMethodID(callback_class_ptr.get(), "invoke",
                                          "([Ljava/lang/Object;)V");
    if (!invoke_method) return;

    auto object_class_ptr = FindClass(env, "java/lang/Object");
    auto arg_array_ptr = WrapLocalRef(
            env,
            env->NewObjectArray(static_cast<jsize>(args.size()), object_class_ptr.get(), nullptr)
    );

    jsize i = 0;
    for (auto obj: args) {
        env->SetObjectArrayElement(arg_array_ptr.get(), i++, obj);
    }

    env->CallVoidMethod(listener, invoke_method, arg_array_ptr.get());

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}
