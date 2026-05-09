#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>

#include <cstddef>
#include <exception>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "layers/model/ModelInstance.hpp"
#include "layers/model/ModelLayer.hpp"

namespace {

constexpr const char* log_tag = "NativeModelLayer";
constexpr size_t doubles_per_model_record = 5;
constexpr size_t model_record_size_bytes = sizeof(double) * doubles_per_model_record;

class UtfChars {
public:
    UtfChars(JNIEnv* env, jstring value)
        : env_(env), value_(value), chars_(env->GetStringUTFChars(value, nullptr)) {}

    ~UtfChars() {
        if (chars_ != nullptr) {
            env_->ReleaseStringUTFChars(value_, chars_);
        }
    }

    UtfChars(const UtfChars&) = delete;
    UtfChars& operator=(const UtfChars&) = delete;

    const char* get() const {
        return chars_;
    }

private:
    JNIEnv* env_;
    jstring value_;
    const char* chars_;
};

class GlobalRef {
public:
    GlobalRef(JNIEnv* env, jobject value)
        : env_(env), ref_(env->NewGlobalRef(value)) {}

    ~GlobalRef() {
        if (ref_ != nullptr) {
            env_->DeleteGlobalRef(ref_);
        }
    }

    GlobalRef(const GlobalRef&) = delete;
    GlobalRef& operator=(const GlobalRef&) = delete;

    jobject get() const {
        return ref_;
    }

    jobject release() {
        jobject ref = ref_;
        ref_ = nullptr;
        return ref;
    }

private:
    JNIEnv* env_;
    jobject ref_;
};

class LocalRef {
public:
    LocalRef(JNIEnv* env, jobject value)
        : env_(env), ref_(value) {}

    ~LocalRef() {
        if (ref_ != nullptr) {
            env_->DeleteLocalRef(ref_);
        }
    }

    LocalRef(const LocalRef&) = delete;
    LocalRef& operator=(const LocalRef&) = delete;

    jobject get() const {
        return ref_;
    }

private:
    JNIEnv* env_;
    jobject ref_;
};

class NativeModelLayerContext final : public mbgl::style::CustomLayerHost {
public:
    NativeModelLayerContext(
            JavaVM* javaVm,
            jobject assetManagerRef,
            AAssetManager* nativeAssetManager,
            std::vector<custom_map_layers::layers::model::ModelInstance> instances
    )
        : javaVm_(javaVm),
          assetManagerRef_(assetManagerRef),
          layer_(std::make_unique<custom_map_layers::layers::model::ModelLayer>(
                  nativeAssetManager,
                  std::move(instances)
          )) {}

    ~NativeModelLayerContext() override {
        layer_.reset();
        deleteAssetManagerRef();
    }

    NativeModelLayerContext(const NativeModelLayerContext&) = delete;
    NativeModelLayerContext& operator=(const NativeModelLayerContext&) = delete;

    void initialize() override {
        layer_->initialize();
    }

    void render(const mbgl::style::CustomLayerRenderParameters& params) override {
        layer_->render(params);
    }

    void contextLost() override {
        layer_->contextLost();
    }

    void deinitialize() override {
        layer_->deinitialize();
    }

private:
    void deleteAssetManagerRef() {
        if (assetManagerRef_ == nullptr || javaVm_ == nullptr) {
            return;
        }

        JNIEnv* env = nullptr;
        const jint getEnvResult = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);

        bool attached = false;
        if (getEnvResult == JNI_EDETACHED) {
            if (javaVm_->AttachCurrentThread(&env, nullptr) != JNI_OK) {
                return;
            }
            attached = true;
        } else if (getEnvResult != JNI_OK) {
            return;
        }

        env->DeleteGlobalRef(assetManagerRef_);
        assetManagerRef_ = nullptr;

        if (attached) {
            javaVm_->DetachCurrentThread();
        }
    }

    JavaVM* javaVm_;
    jobject assetManagerRef_;
    std::unique_ptr<custom_map_layers::layers::model::ModelLayer> layer_;
};

void throwJavaException(JNIEnv* env, const char* className, const char* message) {
    if (env->ExceptionCheck()) {
        return;
    }

    jclass exceptionClass = env->FindClass(className);
    if (exceptionClass == nullptr || env->ExceptionCheck()) {
        return;
    }

    env->ThrowNew(exceptionClass, message);
    env->DeleteLocalRef(exceptionClass);
}

void throwIllegalArgumentException(JNIEnv* env, const char* message) {
    throwJavaException(env, "java/lang/IllegalArgumentException", message);
}

bool validateModelTransport(
        JNIEnv* env,
        jobject numericRecords,
        jobjectArray assetPaths,
        jint count,
        const double** outNumericValues
) {
    *outNumericValues = nullptr;

    if (count < 0) {
        throwIllegalArgumentException(env, "model count must be non-negative");
        return false;
    }
    if (count == 0) {
        return true;
    }
    if (numericRecords == nullptr) {
        throwIllegalArgumentException(env, "model numeric records buffer must not be null");
        return false;
    }
    if (assetPaths == nullptr) {
        throwIllegalArgumentException(env, "model asset paths must not be null when model count is positive");
        return false;
    }

    void* bufferAddress = env->GetDirectBufferAddress(numericRecords);
    if (env->ExceptionCheck()) {
        return false;
    }
    if (bufferAddress == nullptr) {
        throwIllegalArgumentException(env, "model numeric records must be a direct buffer");
        return false;
    }

    const jlong bufferCapacity = env->GetDirectBufferCapacity(numericRecords);
    if (env->ExceptionCheck()) {
        return false;
    }
    if (bufferCapacity < 0) {
        throwIllegalArgumentException(env, "model numeric records must be a direct buffer");
        return false;
    }

    const auto requiredBytes = static_cast<jlong>(count) * static_cast<jlong>(model_record_size_bytes);
    if (bufferCapacity < requiredBytes) {
        throwIllegalArgumentException(env, "model numeric records buffer is too small");
        return false;
    }

    const jsize assetPathCount = env->GetArrayLength(assetPaths);
    if (env->ExceptionCheck()) {
        return false;
    }
    if (assetPathCount < count) {
        throwIllegalArgumentException(env, "model asset paths length is smaller than model count");
        return false;
    }

    *outNumericValues = static_cast<const double*>(bufferAddress);
    return true;
}

bool readString(JNIEnv* env, jstring stringValue, std::string* outValue) {
    UtfChars utfChars(env, stringValue);
    if (env->ExceptionCheck()) {
        return false;
    }
    if (utfChars.get() == nullptr) {
        throwIllegalArgumentException(env, "model asset path could not be read");
        return false;
    }

    outValue->assign(utfChars.get());
    return true;
}

std::vector<custom_map_layers::layers::model::ModelInstance> readModelInstances(
        JNIEnv* env,
        jobject numericRecords,
        jobjectArray assetPaths,
        jint count
) {
    using custom_map_layers::layers::model::ModelInstance;

    std::vector<ModelInstance> instances;
    const double* numericValues = nullptr;
    if (!validateModelTransport(env, numericRecords, assetPaths, count, &numericValues)) {
        return instances;
    }
    if (env->ExceptionCheck()) {
        return instances;
    }
    if (count == 0) {
        return instances;
    }

    instances.reserve(static_cast<size_t>(count));
    for (jsize index = 0; index < count; index++) {
        LocalRef assetPathRef(env, env->GetObjectArrayElement(assetPaths, index));
        if (env->ExceptionCheck()) {
            return instances;
        }
        auto assetPath = static_cast<jstring>(assetPathRef.get());
        if (assetPath == nullptr) {
            throwIllegalArgumentException(env, "model asset path must not be null");
            return instances;
        }

        const double* record = numericValues + (static_cast<size_t>(index) * doubles_per_model_record);
        ModelInstance instance;
        if (!readString(env, assetPath, &instance.assetPath)) {
            return instances;
        }
        instance.latitude = record[0];
        instance.longitude = record[1];
        instance.altitudeMeters = record[2];
        instance.scaleMetersPerModelUnit = record[3];
        const double headingDegrees = record[4];
        instance.headingRadians = custom_map_layers::layers::model::degreesToRadians(headingDegrees);
        instances.push_back(std::move(instance));
    }

    return instances;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeModelLayer_createContextNative(
        JNIEnv* env,
        jclass,
        jobject assetManager,
        jobject numericRecords,
        jobjectArray assetPaths,
        jint count
) {
    try {
        __android_log_write(ANDROID_LOG_INFO, log_tag, "nativeCreateContext");
        JavaVM* javaVm = nullptr;
        if (env->GetJavaVM(&javaVm) != JNI_OK || javaVm == nullptr) {
            throwJavaException(env, "java/lang/RuntimeException", "failed to get Java VM");
            return 0;
        }

        AAssetManager* nativeAssetManager = AAssetManager_fromJava(env, assetManager);
        if (nativeAssetManager == nullptr) {
            throwIllegalArgumentException(env, "asset manager must not be null");
            return 0;
        }

        auto instances = readModelInstances(env, numericRecords, assetPaths, count);
        if (env->ExceptionCheck()) {
            return 0;
        }
        GlobalRef assetManagerRef(env, assetManager);
        if (env->ExceptionCheck()) {
            return 0;
        }
        if (assetManagerRef.get() == nullptr) {
            throwJavaException(env, "java/lang/OutOfMemoryError", "failed to retain asset manager");
            return 0;
        }
        auto context = std::make_unique<NativeModelLayerContext>(
                javaVm,
                assetManagerRef.get(),
                nativeAssetManager,
                std::move(instances)
        );
        assetManagerRef.release();
        return reinterpret_cast<jlong>(context.release());
    } catch (const std::exception& exception) {
        throwJavaException(env, "java/lang/RuntimeException", exception.what());
        return 0;
    } catch (...) {
        throwJavaException(env, "java/lang/RuntimeException", "unknown native error");
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeModelLayer_destroyContextNative(
        JNIEnv*,
        jclass,
        jlong context
) {
    __android_log_write(ANDROID_LOG_INFO, log_tag, "nativeDestroyContext");
    auto* nativeContext = reinterpret_cast<NativeModelLayerContext*>(context);
    if (nativeContext == nullptr) {
        return;
    }
    delete nativeContext;
}
