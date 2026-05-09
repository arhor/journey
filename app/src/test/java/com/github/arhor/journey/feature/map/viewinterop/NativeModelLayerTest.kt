package com.github.arhor.journey.feature.map.viewinterop

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.nio.ByteOrder
import org.junit.Test

class NativeModelLayerTest {

    private fun token(vararg parts: String): String = parts.joinToString(separator = "")

    @Test
    fun `layerId should return native model layer id`() {
        // Given

        // When
        val actual = NativeModelLayer.layerId()

        // Then
        actual shouldBe "native-model-layer"
    }

    @Test
    fun `NativeMapModelSpec should expose all fields when constructed with explicit values`() {
        // Given
        val spec = NativeMapModelSpec(
            assetPath = "models/animal-tiger.glb",
            latitude = 54.3738000,
            longitude = 18.6508750,
            altitudeMeters = 3.0,
            scaleMetersPerModelUnit = 45.0,
            headingDegrees = 90.0,
        )

        // When

        // Then
        spec.assetPath shouldBe "models/animal-tiger.glb"
        spec.latitude shouldBe 54.3738000
        spec.longitude shouldBe 18.6508750
        spec.altitudeMeters shouldBe 3.0
        spec.scaleMetersPerModelUnit shouldBe 45.0
        spec.headingDegrees shouldBe 90.0
    }

    @Test
    fun `NativeMapModelSpec should default altitude and heading to zero when omitted`() {
        // Given
        val spec = NativeMapModelSpec(
            assetPath = "models/animal-tiger.glb",
            latitude = 54.3738000,
            longitude = 18.6508750,
            scaleMetersPerModelUnit = 45.0,
        )

        // When

        // Then
        spec.altitudeMeters shouldBe 0.0
        spec.headingDegrees shouldBe 0.0
    }

    @Test
    fun `addToWithManagedContext should pass model specs into context creation`() {
        // Given
        val model = NativeMapModelSpec(
            assetPath = "models/animal-tiger.glb",
            latitude = 54.3738000,
            longitude = 18.6508750,
            altitudeMeters = 0.0,
            scaleMetersPerModelUnit = 45.0,
            headingDegrees = 0.0,
        )
        val createdContext = 42L
        val createdModels = mutableListOf<List<NativeMapModelSpec>>()
        val addedContexts = mutableListOf<Long>()
        var repaintCalls = 0

        // When
        NativeModelLayer.addToWithManagedContext(
            models = listOf(model),
            createContext = { models ->
                createdModels += models
                createdContext
            },
            destroyContext = {},
            addLayer = { context -> addedContexts += context },
            repaint = { repaintCalls += 1 },
        )

        // Then
        createdModels shouldBe listOf(listOf(model))
        addedContexts shouldBe listOf(createdContext)
        repaintCalls shouldBe 1
    }

    @Test
    fun `addToWithManagedContext should repaint when layer is added`() {
        // Given
        val createdContext = 42L
        val addedContexts = mutableListOf<Long>()
        val destroyedContexts = mutableListOf<Long>()
        var repaintCalls = 0

        // When
        NativeModelLayer.addToWithManagedContext(
            models = emptyList(),
            createContext = { createdContext },
            destroyContext = { context -> destroyedContexts += context },
            addLayer = { context -> addedContexts += context },
            repaint = { repaintCalls += 1 },
        )

        // Then
        addedContexts shouldBe listOf(createdContext)
        destroyedContexts shouldBe emptyList()
        repaintCalls shouldBe 1
    }

    @Test
    fun `addToWithManagedContext should destroy context and rethrow when add layer throws after context creation`() {
        // Given
        val createdContext = 42L
        val expected = IllegalStateException("add-layer-failed")
        val destroyedContexts = mutableListOf<Long>()
        var repaintCalls = 0

        // When
        val actual = try {
            NativeModelLayer.addToWithManagedContext(
                models = emptyList(),
                createContext = { createdContext },
                destroyContext = { context -> destroyedContexts += context },
                addLayer = { throw expected },
                repaint = { repaintCalls += 1 },
            )
            null
        } catch (throwable: Throwable) {
            throwable
        }

        // Then
        actual shouldBe expected
        destroyedContexts shouldBe listOf(createdContext)
        repaintCalls shouldBe 0
    }

    @Test
    fun `buildNativePayload should create direct native-order AoS numeric records and asset paths`() {
        // Given
        val models = listOf(
            NativeMapModelSpec(
                assetPath = "models/one.glb",
                latitude = 54.1,
                longitude = 18.2,
                altitudeMeters = 3.3,
                scaleMetersPerModelUnit = 4.4,
                headingDegrees = 5.5,
            ),
            NativeMapModelSpec(
                assetPath = "models/two.glb",
                latitude = 64.6,
                longitude = 28.7,
                altitudeMeters = 8.8,
                scaleMetersPerModelUnit = 9.9,
                headingDegrees = 10.1,
            ),
        )

        // When
        val payload = NativeModelLayer.buildNativePayload(models)
        val numericRecords = payload.numericRecords.duplicate().order(ByteOrder.nativeOrder())

        // Then
        payload.count shouldBe 2
        payload.assetPaths.toList() shouldBe listOf("models/one.glb", "models/two.glb")
        payload.numericRecords.isDirect shouldBe true
        payload.numericRecords.order() shouldBe ByteOrder.nativeOrder()
        payload.numericRecords.position() shouldBe 0
        payload.numericRecords.limit() shouldBe 2 * 5 * java.lang.Double.BYTES
        numericRecords.double shouldBe 54.1
        numericRecords.double shouldBe 18.2
        numericRecords.double shouldBe 3.3
        numericRecords.double shouldBe 4.4
        numericRecords.double shouldBe 5.5
        numericRecords.double shouldBe 64.6
        numericRecords.double shouldBe 28.7
        numericRecords.double shouldBe 8.8
        numericRecords.double shouldBe 9.9
        numericRecords.double shouldBe 10.1
    }

    @Test
    fun `buildNativePayload should handle empty model list`() {
        // Given
        val models = emptyList<NativeMapModelSpec>()

        // When
        val payload = NativeModelLayer.buildNativePayload(models)

        // Then
        payload.count shouldBe 0
        payload.assetPaths.toList() shouldBe emptyList()
        payload.numericRecords.isDirect shouldBe true
        payload.numericRecords.order() shouldBe ByteOrder.nativeOrder()
        payload.numericRecords.position() shouldBe 0
        payload.numericRecords.limit() shouldBe 0
    }

    @Test
    fun `map screen should pass current tiger model spec from kotlin`() {
        // Given
        val source = File("src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt")
            .readText()
        val addToCallStartToken = "NativeModelLayer.addTo("
        val viewportAttachToken = "viewportReporter.attach(map)"

        // When
        val addToCallStartIndex = source.indexOf(addToCallStartToken)
        val viewportAttachIndex = source.indexOf(viewportAttachToken)
        val modelLayerCallStartIndex = addToCallStartIndex + addToCallStartToken.length

        // Then
        (addToCallStartIndex >= 0) shouldBe true
        (viewportAttachIndex >= 0) shouldBe true
        (viewportAttachIndex > modelLayerCallStartIndex) shouldBe true
        val modelLayerCall = source.substring(modelLayerCallStartIndex, viewportAttachIndex)
        modelLayerCall shouldContain "models = listOf("
        modelLayerCall shouldContain "NativeMapModelSpec("
        modelLayerCall shouldContain "assetPath = \"models/animal-tiger.glb\""
        modelLayerCall shouldContain "latitude = 54.3738000"
        modelLayerCall shouldContain "longitude = 18.6508750"
        modelLayerCall shouldContain "altitudeMeters = 0.0"
        modelLayerCall shouldContain "scaleMetersPerModelUnit = 45.0"
        modelLayerCall shouldContain "headingDegrees = 0.0"
    }

    @Test
    fun `native model layer context creation should pass direct numeric records and asset paths`() {
        // Given
        val kotlinSource = File("src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayer.kt")
            .readText()
        val jniSource = File("src/main/cpp/lib/jni/custom_map_layers_jni.cpp").readText()

        // When

        // Then
        kotlinSource shouldContain "models: List<NativeMapModelSpec>,"
        kotlinSource shouldNotContain "models: List<NativeMapModelSpec> = emptyList()"
        kotlinSource shouldContain "numericRecords: ByteBuffer,"
        kotlinSource shouldContain "assetPaths: Array<String>,"
        kotlinSource shouldContain "count: Int,"
        kotlinSource shouldContain "createContextNative(assetManager, payload.numericRecords, payload.assetPaths, payload.count)"
        kotlinSource shouldContain "Math.multiplyExact(models.size, RECORD_SIZE_BYTES)"
        kotlinSource shouldContain ".allocateDirect(bufferSizeBytes)"
        kotlinSource shouldContain ".order(ByteOrder.nativeOrder())"
        kotlinSource shouldContain "buffer.flip()"
        jniSource shouldContain "jobject numericRecords"
        jniSource shouldContain "jobjectArray assetPaths"
        jniSource shouldContain "jint count"
        jniSource shouldContain "readModelInstances"
        jniSource shouldContain "GetDirectBufferAddress(numericRecords)"
        jniSource shouldContain "GetDirectBufferCapacity(numericRecords)"
        jniSource shouldContain "GetStringUTFChars"
        jniSource shouldContain "ReleaseStringUTFChars"
        jniSource shouldContain "DeleteLocalRef"
        jniSource shouldContain "if (count == 0) {\n        return true;\n    }\n    if (numericRecords == nullptr)"
        jniSource shouldContain "constexpr size_t doubles_per_model_record = 5"
        jniSource shouldContain "class UtfChars"
        jniSource shouldContain "UtfChars(const UtfChars&) = delete"
        jniSource shouldContain "GlobalRef(const GlobalRef&) = delete"
        jniSource shouldContain "class LocalRef"
        jniSource shouldContain "LocalRef(const LocalRef&) = delete"
        jniSource shouldContain "catch (const std::exception& exception)"
        jniSource shouldContain "java/lang/RuntimeException"
        jniSource shouldContain "env->GetJavaVM(&javaVm)"
        jniSource shouldContain "GlobalRef assetManagerRef(env, assetManager)"
        jniSource shouldContain "NewGlobalRef(value)"
        jniSource shouldContain "~NativeModelLayerContext() override"
        jniSource shouldContain "layer_.reset();\n        deleteAssetManagerRef();"
        jniSource shouldContain "std::unique_ptr<custom_map_layers::layers::model::ModelLayer> layer_"
        jniSource shouldContain "layer_->initialize()"
        jniSource shouldContain "javaVm_->GetEnv"
        jniSource shouldContain "javaVm_->AttachCurrentThread"
        jniSource shouldContain "javaVm_->DetachCurrentThread"
        jniSource shouldContain "DeleteGlobalRef(assetManagerRef_)"
        jniSource shouldContain "static_cast<jlong>(count) * static_cast<jlong>(model_record_size_bytes)"
        jniSource shouldContain "delete nativeContext"
        jniSource shouldNotContain "nativeContext->destroy(env)"
        jniSource shouldNotContain "FindClass(\"com/github/arhor/journey/feature/map/viewinterop/NativeMapModelSpec\")"
        jniSource shouldNotContain "getAssetPath"
        jniSource shouldNotContain "getLatitude"
        jniSource shouldNotContain "getLongitude"
        jniSource shouldNotContain "getAltitudeMeters"
        jniSource shouldNotContain "getScaleMetersPerModelUnit"
        jniSource shouldNotContain "getHeadingDegrees"
    }

    @Test
    fun `native model layer should be constructed with model instances`() {
        // Given
        val header = File("src/main/cpp/lib/layers/model/ModelLayer.hpp").readText()
        val source = File("src/main/cpp/lib/layers/model/ModelLayer.cpp").readText()

        // When

        // Then
        header shouldContain "std::vector<ModelInstance>"
        header shouldContain "ModelLayer(AAssetManager* assetManager, std::vector<ModelInstance> instances)"
        source shouldContain "ModelLayer::ModelLayer(AAssetManager* assetManager, std::vector<ModelInstance> instances)"
        source shouldContain "instances_(std::move(instances))"
        source shouldNotContain "Task 3 limitation: rendering only first model instance; supplied=%zu"
    }

    @Test
    fun `native model renderer should use MapLibre projection matrix instead of manual camera rotation`() {
        // Given
        val source = File("src/main/cpp/lib/layers/model/ModelLayer.cpp").readText()

        // When
        val usesMapLibreProjectionMatrix = source.contains("params.projectionMatrix")

        // Then
        usesMapLibreProjectionMatrix shouldBe true
        source shouldContain "projectWorldToClip"
        source shouldContain "std::pow(2.0, params.zoom)"
        source shouldContain "worldPixelsPerMeter"
        source shouldNotContain token("project", "Meters", "Offset", "To", "Ndc")
    }

    @Test
    fun `native model renderer should upload clip coordinates instead of large world pixel coordinates`() {
        // Given
        val source = File("src/main/cpp/lib/layers/model/ModelLayer.cpp").readText()
        val vertexShader = source.substringAfter("kVertexShaderSource").substringBefore("kFragmentShaderSource")

        // When
        val uploadsClipPosition = vertexShader.contains("in vec4 a_clip_pos")

        // Then
        uploadsClipPosition shouldBe true
        vertexShader shouldContain "gl_Position = a_clip_pos"
        source shouldContain "glEnableVertexAttribArray(0)"
        source shouldContain "glEnableVertexAttribArray(1)"
        source shouldContain "glVertexAttribPointer(0, 4, GL_FLOAT"
        source shouldContain "projectWorldToClip"
    }

    @Test
    fun `native model renderer should depth test tiger triangles when rendering opaque 3d mesh`() {
        // Given
        val source = File("src/main/cpp/lib/layers/model/ModelLayer.cpp").readText()

        // When
        val usesDepthTestingForModelDraw = source.contains("glEnable(GL_DEPTH_TEST)")

        // Then
        usesDepthTestingForModelDraw shouldBe true
        source shouldContain "glDepthFunc(GL_LEQUAL)"
        source shouldContain "glDepthMask(GL_TRUE)"
    }

    @Test
    fun `native geo helpers should not keep obsolete manual NDC projection API`() {
        // Given
        val header = File("src/main/cpp/lib/geo/WebMercator.hpp").readText()
        val source = File("src/main/cpp/lib/geo/WebMercator.cpp").readText()

        // When
        val combined = header + "\n" + source

        // Then
        combined shouldNotContain token("Screen", "Point")
        combined shouldNotContain token("project", "To", "Ndc")
        combined shouldNotContain token("project", "Meters", "Offset", "To", "Ndc")
        combined shouldNotContain token("degrees", "To", "Radians", "If", "Needed")
        combined shouldNotContain token("k", "Tile", "Size")
    }

    @Test
    fun `native gltf loader should not depend on no-op texture coordinate helper`() {
        // Given
        val helper = File(
            "src/main/cpp/lib/gltf/" + token("Texture", "Coordinate", ".hpp"),
        )
        val loader = File("src/main/cpp/lib/gltf/GltfModelLoader.cpp").readText()

        // When
        val helperExists = helper.exists()

        // Then
        helperExists shouldBe false
        loader shouldNotContain token("Texture", "Coordinate", ".hpp")
        loader shouldNotContain token("renderer", "Texture", "V")
    }

    @Test
    fun `native gltf loader should load caller supplied model asset path`() {
        // Given
        val header = File("src/main/cpp/lib/gltf/GltfModelLoader.hpp").readText()
        val source = File("src/main/cpp/lib/gltf/GltfModelLoader.cpp").readText()

        // When

        // Then
        header shouldContain "load(const std::string& assetPath, const char* logTag)"
        header shouldNotContain "loadTiger"
        source shouldContain "reader.readBytes(assetPath, logTag)"
        source shouldNotContain "tiger_model_path"
        source shouldNotContain "loadTiger"
    }

    @Test
    fun `native model renderer should use per instance location scale and heading`() {
        // Given
        val source = File("src/main/cpp/lib/layers/model/ModelLayer.cpp").readText()

        // When

        // Then
        source shouldContain "instance.latitude"
        source shouldContain "instance.longitude"
        source shouldContain "instance.altitudeMeters"
        source shouldContain "instance.scaleMetersPerModelUnit"
        source shouldContain "instance.headingRadians"
        source shouldNotContain token("marker", "_", "latitude")
        source shouldNotContain token("marker", "_", "longitude")
        source shouldNotContain token("marker", "_", "altitude", "_", "meters")
        source shouldNotContain token("model", "_", "meters", "_", "per", "_", "unit")
        source shouldNotContain token("model", "_", "heading", "_", "radians")
    }

    @Test
    fun `native model resource loading should continue when one asset fails and keep valid models`() {
        // Given
        val source = File("src/main/cpp/lib/layers/model/ModelLayer.cpp").readText()

        // When

        // Then
        source shouldContain "bool hasLoadedModel = false"
        source shouldContain "if (!loadModelAndTexture(instance.assetPath)) {"
        source shouldContain "continue;"
        source shouldContain "hasLoadedModel = true"
        source shouldContain "loaded_ = hasLoadedModel;"
        source shouldContain "return loaded_;"
    }

    @Test
    fun `native gltf texture path resolution should be relative to glb asset directory`() {
        // Given
        val source = File("src/main/cpp/lib/gltf/GltfModelLoader.cpp").readText()

        // When

        // Then
        source shouldContain "resolveTexturePath(const std::string& assetPath, const char* uri)"
        source shouldContain "assetPath.find_last_of('/')"
        source shouldContain "assetPath.substr(0, separatorIndex + 1)"
        source shouldContain "return assetDirectory + uri"
        source shouldNotContain "return std::string(\"models/\") + uri;"
    }

    @Test
    fun `native render summary log should not print stale pre-loop vertex count`() {
        // Given
        val source = File("src/main/cpp/lib/layers/model/ModelLayer.cpp").readText()

        // When

        // Then
        source shouldNotContain "pitch=%.4f instances=%zu vertices=%d"
    }
}
