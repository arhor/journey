#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>

#include <memory>

#include "layers/model/ModelLayer.hpp"

namespace {

constexpr const char* LOG_TAG = "NativeModelLayer";

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeModelLayer_createContextNative(
        JNIEnv* env,
        jclass,
        jobject assetManager
) {
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "nativeCreateContext");
    AAssetManager* nativeAssetManager = AAssetManager_fromJava(env, assetManager);
    auto layer = std::make_unique<custom_map_layers::layers::model::ModelLayer>(nativeAssetManager);
    return reinterpret_cast<jlong>(layer.release());
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeModelLayer_destroyContextNative(
        JNIEnv*,
        jclass,
        jlong context
) {
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "nativeDestroyContext");
    delete reinterpret_cast<custom_map_layers::layers::model::ModelLayer*>(context);
}
