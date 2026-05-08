#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>

#include <memory>
#include <string>
#include <vector>

#include "layers/model/ModelInstance.hpp"
#include "layers/model/ModelLayer.hpp"

namespace {

constexpr const char* log_tag = "NativeModelLayer";

std::string readString(JNIEnv* env, jobject source, jmethodID getter) {
    auto stringValue = static_cast<jstring>(env->CallObjectMethod(source, getter));
    if (stringValue == nullptr) {
        return {};
    }

    const char* utfChars = env->GetStringUTFChars(stringValue, nullptr);
    if (utfChars == nullptr) {
        env->DeleteLocalRef(stringValue);
        return {};
    }

    std::string value(utfChars);
    env->ReleaseStringUTFChars(stringValue, utfChars);
    env->DeleteLocalRef(stringValue);
    return value;
}

std::vector<custom_map_layers::layers::model::ModelInstance> readModelInstances(JNIEnv* env, jobjectArray models) {
    using custom_map_layers::layers::model::ModelInstance;

    std::vector<ModelInstance> instances;
    if (models == nullptr) {
        return instances;
    }

    const jsize modelCount = env->GetArrayLength(models);
    if (modelCount <= 0) {
        return instances;
    }

    jclass modelSpecClass = env->FindClass("com/github/arhor/journey/feature/map/viewinterop/NativeMapModelSpec");
    if (modelSpecClass == nullptr) {
        return instances;
    }

    const jmethodID getAssetPath = env->GetMethodID(modelSpecClass, "getAssetPath", "()Ljava/lang/String;");
    const jmethodID getLatitude = env->GetMethodID(modelSpecClass, "getLatitude", "()D");
    const jmethodID getLongitude = env->GetMethodID(modelSpecClass, "getLongitude", "()D");
    const jmethodID getAltitudeMeters = env->GetMethodID(modelSpecClass, "getAltitudeMeters", "()D");
    const jmethodID getScaleMetersPerModelUnit =
            env->GetMethodID(modelSpecClass, "getScaleMetersPerModelUnit", "()D");
    const jmethodID getHeadingDegrees = env->GetMethodID(modelSpecClass, "getHeadingDegrees", "()D");

    if (getAssetPath == nullptr || getLatitude == nullptr || getLongitude == nullptr || getAltitudeMeters == nullptr ||
        getScaleMetersPerModelUnit == nullptr || getHeadingDegrees == nullptr) {
        env->DeleteLocalRef(modelSpecClass);
        return instances;
    }

    instances.reserve(static_cast<size_t>(modelCount));
    for (jsize index = 0; index < modelCount; index++) {
        jobject modelSpec = env->GetObjectArrayElement(models, index);
        if (modelSpec == nullptr) {
            continue;
        }

        ModelInstance instance;
        instance.assetPath = readString(env, modelSpec, getAssetPath);
        instance.latitude = env->CallDoubleMethod(modelSpec, getLatitude);
        instance.longitude = env->CallDoubleMethod(modelSpec, getLongitude);
        instance.altitudeMeters = env->CallDoubleMethod(modelSpec, getAltitudeMeters);
        instance.scaleMetersPerModelUnit = env->CallDoubleMethod(modelSpec, getScaleMetersPerModelUnit);
        instance.headingRadians =
                custom_map_layers::layers::model::degreesToRadians(env->CallDoubleMethod(modelSpec, getHeadingDegrees));
        instances.push_back(std::move(instance));

        env->DeleteLocalRef(modelSpec);
    }

    env->DeleteLocalRef(modelSpecClass);
    return instances;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeModelLayer_createContextNative(
        JNIEnv* env,
        jclass,
        jobject assetManager,
        jobjectArray models
) {
    __android_log_write(ANDROID_LOG_INFO, log_tag, "nativeCreateContext");
    AAssetManager* nativeAssetManager = AAssetManager_fromJava(env, assetManager);
    auto layer = std::make_unique<custom_map_layers::layers::model::ModelLayer>(
            nativeAssetManager,
            readModelInstances(env, models)
    );
    return reinterpret_cast<jlong>(layer.release());
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeModelLayer_destroyContextNative(
        JNIEnv*,
        jclass,
        jlong context
) {
    __android_log_write(ANDROID_LOG_INFO, log_tag, "nativeDestroyContext");
    delete reinterpret_cast<custom_map_layers::layers::model::ModelLayer*>(context);
}
