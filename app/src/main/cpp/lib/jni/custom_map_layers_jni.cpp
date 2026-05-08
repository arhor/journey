#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "layers/model/ModelInstance.hpp"
#include "layers/model/ModelLayer.hpp"

namespace {

constexpr const char* log_tag = "NativeModelLayer";

bool readString(JNIEnv* env, jobject source, jmethodID getter, std::string* outValue) {
    auto stringValue = static_cast<jstring>(env->CallObjectMethod(source, getter));
    if (env->ExceptionCheck()) {
        return false;
    }
    if (stringValue == nullptr) {
        outValue->clear();
        return true;
    }

    const char* utfChars = env->GetStringUTFChars(stringValue, nullptr);
    if (env->ExceptionCheck()) {
        env->DeleteLocalRef(stringValue);
        return false;
    }
    if (utfChars == nullptr) {
        env->DeleteLocalRef(stringValue);
        outValue->clear();
        return true;
    }

    outValue->assign(utfChars);
    env->ReleaseStringUTFChars(stringValue, utfChars);
    env->DeleteLocalRef(stringValue);
    return true;
}

std::vector<custom_map_layers::layers::model::ModelInstance> readModelInstances(JNIEnv* env, jobjectArray models) {
    using custom_map_layers::layers::model::ModelInstance;

    std::vector<ModelInstance> instances;
    if (models == nullptr) {
        return instances;
    }

    const jsize modelCount = env->GetArrayLength(models);
    if (env->ExceptionCheck()) {
        return instances;
    }
    if (modelCount <= 0) {
        return instances;
    }

    jclass modelSpecClass = env->FindClass("com/github/arhor/journey/feature/map/viewinterop/NativeMapModelSpec");
    if (env->ExceptionCheck()) {
        return instances;
    }
    if (modelSpecClass == nullptr) {
        return instances;
    }

    const auto readMethod = [&](const char* methodName, const char* signature, jmethodID* outMethod) -> bool {
        *outMethod = env->GetMethodID(modelSpecClass, methodName, signature);
        if (env->ExceptionCheck() || *outMethod == nullptr) {
            env->DeleteLocalRef(modelSpecClass);
            return false;
        }
        return true;
    };

    jmethodID getAssetPath = nullptr;
    if (!readMethod("getAssetPath", "()Ljava/lang/String;", &getAssetPath)) {
        return instances;
    }
    jmethodID getLatitude = nullptr;
    if (!readMethod("getLatitude", "()D", &getLatitude)) {
        return instances;
    }
    jmethodID getLongitude = nullptr;
    if (!readMethod("getLongitude", "()D", &getLongitude)) {
        return instances;
    }
    jmethodID getAltitudeMeters = nullptr;
    if (!readMethod("getAltitudeMeters", "()D", &getAltitudeMeters)) {
        return instances;
    }
    jmethodID getScaleMetersPerModelUnit = nullptr;
    if (!readMethod("getScaleMetersPerModelUnit", "()D", &getScaleMetersPerModelUnit)) {
        return instances;
    }
    jmethodID getHeadingDegrees = nullptr;
    if (!readMethod("getHeadingDegrees", "()D", &getHeadingDegrees)) {
        return instances;
    }

    instances.reserve(static_cast<size_t>(modelCount));
    for (jsize index = 0; index < modelCount; index++) {
        jobject modelSpec = env->GetObjectArrayElement(models, index);
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(modelSpecClass);
            return instances;
        }
        if (modelSpec == nullptr) {
            continue;
        }

        ModelInstance instance;
        if (!readString(env, modelSpec, getAssetPath, &instance.assetPath)) {
            env->DeleteLocalRef(modelSpec);
            env->DeleteLocalRef(modelSpecClass);
            return instances;
        }
        instance.latitude = env->CallDoubleMethod(modelSpec, getLatitude);
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(modelSpec);
            env->DeleteLocalRef(modelSpecClass);
            return instances;
        }
        instance.longitude = env->CallDoubleMethod(modelSpec, getLongitude);
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(modelSpec);
            env->DeleteLocalRef(modelSpecClass);
            return instances;
        }
        instance.altitudeMeters = env->CallDoubleMethod(modelSpec, getAltitudeMeters);
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(modelSpec);
            env->DeleteLocalRef(modelSpecClass);
            return instances;
        }
        instance.scaleMetersPerModelUnit = env->CallDoubleMethod(modelSpec, getScaleMetersPerModelUnit);
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(modelSpec);
            env->DeleteLocalRef(modelSpecClass);
            return instances;
        }
        const double headingDegrees = env->CallDoubleMethod(modelSpec, getHeadingDegrees);
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(modelSpec);
            env->DeleteLocalRef(modelSpecClass);
            return instances;
        }
        instance.headingRadians = custom_map_layers::layers::model::degreesToRadians(headingDegrees);
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
    auto instances = readModelInstances(env, models);
    if (env->ExceptionCheck()) {
        return 0;
    }
    auto layer = std::make_unique<custom_map_layers::layers::model::ModelLayer>(
            nativeAssetManager,
            std::move(instances)
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
