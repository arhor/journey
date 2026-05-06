#include "layers/exclamation/ExclamationLayer.hpp"

#include <android/log.h>
#include <jni.h>

#include <memory>

namespace {

constexpr const char* LOG_TAG = "NativeExclamationLayer";

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeExclamationLayer_nativeCreateContext(
        JNIEnv*,
        jclass
) {
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "nativeCreateContext");
    auto layer = std::make_unique<custom_map_layers::layers::exclamation::ExclamationLayer>();
    return reinterpret_cast<jlong>(layer.release());
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeExclamationLayer_nativeDestroyContext(
        JNIEnv*,
        jclass,
        jlong context
) {
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "nativeDestroyContext");
    delete reinterpret_cast<custom_map_layers::layers::exclamation::ExclamationLayer*>(context);
}
