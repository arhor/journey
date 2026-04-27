package com.github.arhor.journey.feature.map.components

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

private const val MaxWaves = 8

@Composable
fun RippleGridOverlayGPU(
    waveOrigin: Offset,
    waveKeyNum: Number,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val runtimeConfig = remember(density) {
        RippleGridRuntimeConfig(
            spacingPx = with(density) { 18.dp.toPx() },
            baseRadiusPx = with(density) { 0.5.dp.toPx() },
            maxGrowthPx = with(density) { 3.dp.toPx() },
            jumpHeightPx = with(density) { 45.dp.toPx() },
            jitterPx = with(density) { 0.12.dp.toPx() },
            waveSpeed = 0.5f,
            waveLifeMs = 4_000f,
            preRevealDurationMs = 150f,
            motionDurationMs = 350f,
            postRevealDurationMs = 150f,
            backgroundColor = Color.Transparent.toFloatArray(),
            dotColor = Color(0xFF82DCFF).toFloatArray(),
        )
    }

    var glView by remember {
        mutableStateOf<RippleGridGLSurfaceView?>(null)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            RippleGridGLSurfaceView(context).also { view ->
                view.setRuntimeConfig(runtimeConfig)
                glView = view
            }
        },
        update = { view ->
            view.setRuntimeConfig(runtimeConfig)
        },
    )

    LaunchedEffect(glView, waveKeyNum) {
        val view = glView ?: return@LaunchedEffect

        if (waveOrigin.isUsable()) {
            view.addWave(waveOrigin)
        }
    }

    DisposableEffect(glView) {
        val view = glView

        view?.onResume()

        onDispose {
            view?.onPause()
        }
    }
}

@Composable
@Preview(showBackground = true)
internal fun RippleGridOverlayGPUPreview() {
    var waveOrigin by remember { mutableStateOf(Offset.Unspecified) }
    var waveKeyNum by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    waveOrigin = down.position
                    waveKeyNum++
                }
            },
    ) {
        RippleGridOverlayGPU(
            waveOrigin = waveOrigin,
            waveKeyNum = waveKeyNum,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/* ------------------------------------------ View bridge ------------------------------------------ */

private class RippleGridGLSurfaceView(
    context: Context,
) : GLSurfaceView(context),
    Choreographer.FrameCallback {

    private val renderer = RippleGridRenderer()

    private var frameCallbackPosted = false
    private var animationUntilMs = 0L
    private var latestWaveLifeMs = 4_000L

    init {
        setEGLContextClientVersion(2)

        // RGBA surface for transparent overlay.
        setEGLConfigChooser(
            8,
            8,
            8,
            8,
            0,
            0,
        )

        holder.setFormat(PixelFormat.TRANSLUCENT)

        /**
         * Important for overlay behavior.
         *
         * Caveat: SurfaceView with z-order-on-top may appear above later Compose UI too.
         * If this overlay must sit below controls, place controls outside/above the AndroidView
         * carefully, or use a TextureView/EGL implementation instead.
         */
        setZOrderOnTop(true)

        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false

        preserveEGLContextOnPause = true

        setRenderer(renderer)

        // We manually request frames only while waves are alive.
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setRuntimeConfig(config: RippleGridRuntimeConfig) {
        latestWaveLifeMs = config.waveLifeMs.toLong()

        queueEvent {
            renderer.setRuntimeConfig(config)
        }

        requestRender()
    }

    fun addWave(origin: Offset) {
        val now = SystemClock.uptimeMillis()

        animationUntilMs = max(
            animationUntilMs,
            now + latestWaveLifeMs + 250L,
        )

        queueEvent {
            renderer.addWave(
                x = origin.x,
                y = origin.y,
                startTimeMs = now,
            )
        }

        ensureFrameLoop()
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameCallbackPosted = false

        requestRender()

        val now = SystemClock.uptimeMillis()

        if (now <= animationUntilMs || renderer.hasActiveWaves) {
            ensureFrameLoop()
        }
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this)
        frameCallbackPosted = false
        super.onDetachedFromWindow()
    }

    private fun ensureFrameLoop() {
        if (!frameCallbackPosted) {
            frameCallbackPosted = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
}

/* ------------------------------------------ Renderer ------------------------------------------ */

private class RippleGridRenderer : GLSurfaceView.Renderer {

    @Volatile
    var hasActiveWaves: Boolean = false
        private set

    private var config = RippleGridRuntimeConfig.Default

    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private var programId = 0

    private var positionBufferId = 0
    private var jitterBufferId = 0
    private var pointCount = 0

    private var dotFieldDirty = true

    private val waveUniformData = FloatArray(MaxWaves * 3)
    private var waveCount = 0

    private var aPosition = -1
    private var aJitter = -1

    private var uResolution = -1
    private var uTime = -1
    private var uWaveCount = -1
    private var uWaves = -1

    private var uBaseRadius = -1
    private var uMaxGrowth = -1
    private var uJumpHeight = -1
    private var uWaveSpeed = -1
    private var uWaveLife = -1
    private var uPreRevealDuration = -1
    private var uMotionDuration = -1
    private var uPostRevealDuration = -1

    private var uDotColor = -1
    private var uBackgroundColor = -1

    override fun onSurfaceCreated(
        gl: GL10?,
        eglConfig: EGLConfig?,
    ) {
        programId = createProgram()

        aPosition = GLES20.glGetAttribLocation(programId, "a_position")
        aJitter = GLES20.glGetAttribLocation(programId, "a_jitter")

        uResolution = GLES20.glGetUniformLocation(programId, "u_resolution")
        uTime = GLES20.glGetUniformLocation(programId, "u_time")
        uWaveCount = GLES20.glGetUniformLocation(programId, "u_waveCount")
        uWaves = GLES20.glGetUniformLocation(programId, "u_waves[0]")

        uBaseRadius = GLES20.glGetUniformLocation(programId, "u_baseRadius")
        uMaxGrowth = GLES20.glGetUniformLocation(programId, "u_maxGrowth")
        uJumpHeight = GLES20.glGetUniformLocation(programId, "u_jumpHeight")
        uWaveSpeed = GLES20.glGetUniformLocation(programId, "u_waveSpeed")
        uWaveLife = GLES20.glGetUniformLocation(programId, "u_waveLife")
        uPreRevealDuration = GLES20.glGetUniformLocation(programId, "u_preRevealDuration")
        uMotionDuration = GLES20.glGetUniformLocation(programId, "u_motionDuration")
        uPostRevealDuration = GLES20.glGetUniformLocation(programId, "u_postRevealDuration")

        uDotColor = GLES20.glGetUniformLocation(programId, "u_dotColor")
        uBackgroundColor = GLES20.glGetUniformLocation(programId, "u_backgroundColor")

        val buffers = IntArray(2)
        GLES20.glGenBuffers(2, buffers, 0)

        positionBufferId = buffers[0]
        jitterBufferId = buffers[1]

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(
            GLES20.GL_SRC_ALPHA,
            GLES20.GL_ONE_MINUS_SRC_ALPHA,
        )

        dotFieldDirty = true
    }

    override fun onSurfaceChanged(
        gl: GL10?,
        width: Int,
        height: Int,
    ) {
        surfaceWidth = width
        surfaceHeight = height

        GLES20.glViewport(0, 0, width, height)

        dotFieldDirty = true
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = SystemClock.uptimeMillis().toFloat()

        removeExpiredWaves(now)

        if (dotFieldDirty) {
            rebuildDotField()
            dotFieldDirty = false
        }

        val bg = config.backgroundColor

        GLES20.glClearColor(
            bg[0],
            bg[1],
            bg[2],
            bg[3],
        )
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (pointCount <= 0 || waveCount <= 0) {
            hasActiveWaves = waveCount > 0
            return
        }

        GLES20.glUseProgram(programId)

        bindAttributes()
        uploadUniforms(now)

        GLES20.glDrawArrays(
            GLES20.GL_POINTS,
            0,
            pointCount,
        )

        hasActiveWaves = waveCount > 0
    }

    fun setRuntimeConfig(config: RippleGridRuntimeConfig) {
        val spacingChanged = this.config.spacingPx != config.spacingPx ||
            this.config.jitterPx != config.jitterPx

        this.config = config

        if (spacingChanged) {
            dotFieldDirty = true
        }
    }

    fun addWave(
        x: Float,
        y: Float,
        startTimeMs: Long,
    ) {
        if (waveCount < MaxWaves) {
            val base = waveCount * 3

            waveUniformData[base] = x
            waveUniformData[base + 1] = y
            waveUniformData[base + 2] = startTimeMs.toFloat()

            waveCount++
        } else {
            // Shift left by one wave.
            for (index in 1 until MaxWaves) {
                val from = index * 3
                val to = (index - 1) * 3

                waveUniformData[to] = waveUniformData[from]
                waveUniformData[to + 1] = waveUniformData[from + 1]
                waveUniformData[to + 2] = waveUniformData[from + 2]
            }

            val base = (MaxWaves - 1) * 3

            waveUniformData[base] = x
            waveUniformData[base + 1] = y
            waveUniformData[base + 2] = startTimeMs.toFloat()
        }

        hasActiveWaves = true
    }

    private fun bindAttributes() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBufferId)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(
            aPosition,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            0,
        )

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, jitterBufferId)
        GLES20.glEnableVertexAttribArray(aJitter)
        GLES20.glVertexAttribPointer(
            aJitter,
            1,
            GLES20.GL_FLOAT,
            false,
            0,
            0,
        )
    }

    private fun uploadUniforms(now: Float) {
        val dot = config.dotColor

        GLES20.glUniform2f(
            uResolution,
            surfaceWidth.toFloat(),
            surfaceHeight.toFloat(),
        )
        GLES20.glUniform1f(uTime, now)

        GLES20.glUniform1f(uBaseRadius, config.baseRadiusPx)
        GLES20.glUniform1f(uMaxGrowth, config.maxGrowthPx)
        GLES20.glUniform1f(uJumpHeight, config.jumpHeightPx)
        GLES20.glUniform1f(uWaveSpeed, config.waveSpeed)
        GLES20.glUniform1f(uWaveLife, config.waveLifeMs)
        GLES20.glUniform1f(uPreRevealDuration, config.preRevealDurationMs)
        GLES20.glUniform1f(uMotionDuration, config.motionDurationMs)
        GLES20.glUniform1f(uPostRevealDuration, config.postRevealDurationMs)

        GLES20.glUniform1i(uWaveCount, waveCount)
        GLES20.glUniform3fv(
            uWaves,
            MaxWaves,
            waveUniformData,
            0,
        )

        GLES20.glUniform4f(
            uDotColor,
            dot[0],
            dot[1],
            dot[2],
            dot[3],
        )

        val bg = config.backgroundColor

        GLES20.glUniform4f(
            uBackgroundColor,
            bg[0],
            bg[1],
            bg[2],
            bg[3],
        )
    }

    private fun rebuildDotField() {
        if (
            surfaceWidth <= 0 ||
            surfaceHeight <= 0 ||
            config.spacingPx <= 0f
        ) {
            pointCount = 0
            return
        }

        val columns = ceil(surfaceWidth / config.spacingPx).toInt()
        val rows = ceil(surfaceHeight / config.spacingPx).toInt()

        pointCount = columns * rows

        val positions = FloatArray(pointCount * 2)
        val jitters = FloatArray(pointCount)

        var index = 0

        var y = config.spacingPx / 2f
        while (y < surfaceHeight) {
            var x = config.spacingPx / 2f

            while (x < surfaceWidth) {
                val positionBase = index * 2

                positions[positionBase] = x
                positions[positionBase + 1] = y

                jitters[index] = Random.nextFloat() * config.jitterPx

                index++
                x += config.spacingPx
            }

            y += config.spacingPx
        }

        pointCount = index

        uploadBuffer(
            bufferId = positionBufferId,
            data = positions,
            usedFloats = pointCount * 2,
        )

        uploadBuffer(
            bufferId = jitterBufferId,
            data = jitters,
            usedFloats = pointCount,
        )
    }

    private fun uploadBuffer(
        bufferId: Int,
        data: FloatArray,
        usedFloats: Int,
    ) {
        val buffer = data.toFloatBuffer(usedFloats)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufferId)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            usedFloats * Float.SIZE_BYTES,
            buffer,
            GLES20.GL_STATIC_DRAW,
        )
    }

    private fun removeExpiredWaves(now: Float) {
        var index = waveCount - 1

        while (index >= 0) {
            val base = index * 3
            val start = waveUniformData[base + 2]

            if (now - start > config.waveLifeMs) {
                removeWaveAt(index)
            }

            index--
        }

        hasActiveWaves = waveCount > 0
    }

    private fun removeWaveAt(index: Int) {
        if (index !in 0..waveCount) return

        for (waveIndex in index + 1 until waveCount) {
            val from = waveIndex * 3
            val to = (waveIndex - 1) * 3

            waveUniformData[to] = waveUniformData[from]
            waveUniformData[to + 1] = waveUniformData[from + 1]
            waveUniformData[to + 2] = waveUniformData[from + 2]
        }

        val lastBase = (waveCount - 1) * 3

        waveUniformData[lastBase] = 0f
        waveUniformData[lastBase + 1] = 0f
        waveUniformData[lastBase + 2] = 0f

        waveCount--
    }
}

/* ------------------------------------------ Runtime config ------------------------------------------ */

private data class RippleGridRuntimeConfig(
    val spacingPx: Float,
    val baseRadiusPx: Float,
    val maxGrowthPx: Float,
    val jumpHeightPx: Float,
    val jitterPx: Float,
    val waveSpeed: Float,
    val waveLifeMs: Float,
    val preRevealDurationMs: Float,
    val motionDurationMs: Float,
    val postRevealDurationMs: Float,
    val backgroundColor: FloatArray,
    val dotColor: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RippleGridRuntimeConfig

        if (spacingPx != other.spacingPx) return false
        if (baseRadiusPx != other.baseRadiusPx) return false
        if (maxGrowthPx != other.maxGrowthPx) return false
        if (jumpHeightPx != other.jumpHeightPx) return false
        if (jitterPx != other.jitterPx) return false
        if (waveSpeed != other.waveSpeed) return false
        if (waveLifeMs != other.waveLifeMs) return false
        if (preRevealDurationMs != other.preRevealDurationMs) return false
        if (motionDurationMs != other.motionDurationMs) return false
        if (postRevealDurationMs != other.postRevealDurationMs) return false
        if (!backgroundColor.contentEquals(other.backgroundColor)) return false
        if (!dotColor.contentEquals(other.dotColor)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = spacingPx.hashCode()
        result = 31 * result + baseRadiusPx.hashCode()
        result = 31 * result + maxGrowthPx.hashCode()
        result = 31 * result + jumpHeightPx.hashCode()
        result = 31 * result + jitterPx.hashCode()
        result = 31 * result + waveSpeed.hashCode()
        result = 31 * result + waveLifeMs.hashCode()
        result = 31 * result + preRevealDurationMs.hashCode()
        result = 31 * result + motionDurationMs.hashCode()
        result = 31 * result + postRevealDurationMs.hashCode()
        result = 31 * result + backgroundColor.contentHashCode()
        result = 31 * result + dotColor.contentHashCode()
        return result
    }

    companion object {
        val Default = RippleGridRuntimeConfig(
            spacingPx = 18f,
            baseRadiusPx = 0.5f,
            maxGrowthPx = 3f,
            jumpHeightPx = 45f,
            jitterPx = 0.12f,
            waveSpeed = 0.5f,
            waveLifeMs = 4_000f,
            preRevealDurationMs = 150f,
            motionDurationMs = 350f,
            postRevealDurationMs = 150f,
            backgroundColor = floatArrayOf(0f, 0f, 0f, 0f),
            dotColor = floatArrayOf(
                130f / 255f,
                220f / 255f,
                255f / 255f,
                1f,
            ),
        )
    }
}

/* ------------------------------------------ Shaders ------------------------------------------ */

private const val VertexShaderSource = """
precision highp float;

const int MAX_WAVES = 8;
const float PI = 3.141592653589793;

attribute vec2 a_position;
attribute float a_jitter;

uniform vec2 u_resolution;
uniform float u_time;
uniform int u_waveCount;
uniform vec3 u_waves[MAX_WAVES];

uniform float u_baseRadius;
uniform float u_maxGrowth;
uniform float u_jumpHeight;
uniform float u_waveSpeed;
uniform float u_waveLife;
uniform float u_preRevealDuration;
uniform float u_motionDuration;
uniform float u_postRevealDuration;

varying float v_alpha;

float clamp01(float x) {
    return clamp(x, 0.0, 1.0);
}

float smoothstep01(float x) {
    float t = clamp01(x);
    return t * t * (3.0 - 2.0 * t);
}

float sizePulseShape(float t) {
    return pow(sin(t * PI), 2.4);
}

float jumpPulseShape(float t) {
    return sin(t * PI);
}

void main() {
    float radius = u_baseRadius + a_jitter;
    float alpha = 0.0;
    float jump = 0.0;

    vec2 basePos = a_position;

    for (int i = 0; i < MAX_WAVES; i++) {
        if (i >= u_waveCount) {
            break;
        }

        vec3 wave = u_waves[i];

        float elapsed = u_time - wave.z;
        float waveFade = 1.0 - elapsed / u_waveLife;

        if (elapsed < 0.0 || waveFade <= 0.0) {
            continue;
        }

        float distanceToWaveOrigin = length(basePos - wave.xy);
        float arrivalTime = distanceToWaveOrigin / u_waveSpeed;
        float localTime = elapsed - arrivalTime;

        float phaseStart = -u_preRevealDuration;
        float phaseEnd = u_motionDuration + u_postRevealDuration;

        if (localTime < phaseStart || localTime > phaseEnd) {
            continue;
        }

        float localAlpha = 0.0;
        float sizePulse = 0.0;
        float jumpPulse = 0.0;

        if (localTime < 0.0) {
            float t = (localTime + u_preRevealDuration) / u_preRevealDuration;
            localAlpha = smoothstep01(t);
        } else if (localTime <= u_motionDuration) {
            float t = localTime / u_motionDuration;
            localAlpha = 1.0;
            sizePulse = sizePulseShape(t);
            jumpPulse = jumpPulseShape(t);
        } else {
            float t = (localTime - u_motionDuration) / u_postRevealDuration;
            localAlpha = 1.0 - smoothstep01(t);
        }

        alpha += localAlpha * 0.95 * waveFade;
        radius += sizePulse * u_maxGrowth * waveFade;
        jump += jumpPulse * u_jumpHeight * waveFade;
    }

    alpha = min(alpha, 1.0);

    vec2 pos = basePos + vec2(0.0, -jump);

    vec2 zeroToOne = pos / u_resolution;
    vec2 clip = zeroToOne * 2.0 - 1.0;
    clip.y *= -1.0;

    gl_Position = vec4(clip, 0.0, 1.0);
    gl_PointSize = max(1.0, radius * 2.0 + 2.0);
    v_alpha = alpha;
}
"""

// language=glsl
private const val FragmentShaderSource = """
precision mediump float;

uniform vec4 u_dotColor;
uniform vec4 u_backgroundColor;

varying float v_alpha;

void main() {
    if (v_alpha <= 0.001) {
        discard;
    }

    vec2 p = gl_PointCoord * 2.0 - 1.0;
    float d = length(p);

    float mask = 1.0 - smoothstep(0.82, 1.0, d);
    float alpha = v_alpha * mask * u_dotColor.a;

    if (alpha <= 0.001) {
        discard;
    }

    gl_FragColor = vec4(u_dotColor.rgb, alpha);
}
"""

/* ------------------------------------------ GL helpers ------------------------------------------ */

private fun createProgram(): Int {
    val vertexShader = createShader(
        type = GLES20.GL_VERTEX_SHADER,
        source = VertexShaderSource,
    )

    val fragmentShader = createShader(
        type = GLES20.GL_FRAGMENT_SHADER,
        source = FragmentShaderSource,
    )

    val program = GLES20.glCreateProgram()

    GLES20.glAttachShader(program, vertexShader)
    GLES20.glAttachShader(program, fragmentShader)
    GLES20.glLinkProgram(program)

    val linkStatus = IntArray(1)
    GLES20.glGetProgramiv(
        program,
        GLES20.GL_LINK_STATUS,
        linkStatus,
        0,
    )

    if (linkStatus[0] == 0) {
        val info = GLES20.glGetProgramInfoLog(program)

        GLES20.glDeleteProgram(program)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        error("OpenGL program link error: $info")
    }

    GLES20.glDeleteShader(vertexShader)
    GLES20.glDeleteShader(fragmentShader)

    return program
}

private fun createShader(
    type: Int,
    source: String,
): Int {
    val shader = GLES20.glCreateShader(type)

    GLES20.glShaderSource(shader, source)
    GLES20.glCompileShader(shader)

    val compileStatus = IntArray(1)
    GLES20.glGetShaderiv(
        shader,
        GLES20.GL_COMPILE_STATUS,
        compileStatus,
        0,
    )

    if (compileStatus[0] == 0) {
        val info = GLES20.glGetShaderInfoLog(shader)

        GLES20.glDeleteShader(shader)

        error("OpenGL shader compile error: $info")
    }

    return shader
}

private fun FloatArray.toFloatBuffer(
    usedFloats: Int = size,
): FloatBuffer {
    return ByteBuffer
        .allocateDirect(usedFloats * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .also { buffer ->
            buffer.put(this, 0, usedFloats)
            buffer.position(0)
        }
}

private fun Color.toFloatArray(): FloatArray {
    return floatArrayOf(
        red,
        green,
        blue,
        alpha,
    )
}

private fun Offset.isUsable(): Boolean {
    return !x.isNaN() &&
        !y.isNaN() &&
        !x.isInfinite() &&
        !y.isInfinite()
}
