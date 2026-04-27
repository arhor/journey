package com.github.arhor.journey.feature.map

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.SurfaceHolder
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

private const val MAX_WAVES = 1

private const val WAVE_SPEED_PX_PER_SEC = 100f
private const val FRONT_WIDTH_PX = 100f
private const val TRAIL_WIDTH_PX = 10f
private const val WAVE_LIFETIME_SEC = 7.0f
private const val MAX_JUMP_PX = 30f
private const val JUMP_SIGMA_PX = 10f

private const val CELL_SIZE_PX = 25f
private const val DEPTH_X = 0.1f
private const val DEPTH_Y = 0.7f

internal data class RippleLaunchRequest(
    val id: Long,
)

private data class VoxelWave(
    val x: Float,
    val y: Float,
    val startedAtMs: Long,
)

private data class PendingWave(
    val x: Float,
    val y: Float,
)

@Composable
internal fun VoxelRippleOverlay(
    launchRequest: RippleLaunchRequest?,
    waveOrigin: Offset?,
    modifier: Modifier = Modifier,
) {
    var lastHandledRequestId by remember { mutableLongStateOf(-1L) }

    val renderer = remember {
        VoxelRippleRenderer()
    }

    LaunchedEffect(launchRequest, waveOrigin) {
        if (launchRequest == null || launchRequest.id == lastHandledRequestId) {
            return@LaunchedEffect
        }

        if (waveOrigin != null) {
            renderer.launchWave(waveOrigin.x, waveOrigin.y)
            lastHandledRequestId = launchRequest.id
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            VoxelRippleGLSurfaceView(
                context = context,
                renderer = renderer,
            )
        },
    )
}

@Composable
@Preview(showBackground = true)
private fun VoxelRippleOverlayPreview() {
    var launchId by remember { mutableLongStateOf(1L) }
    var waveOrigin by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1D))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    waveOrigin = down.position
                    launchId += 1L
                }
            },
    ) {
        VoxelRippleOverlay(
            launchRequest = RippleLaunchRequest(id = launchId),
            waveOrigin = waveOrigin,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private class VoxelRippleGLSurfaceView(
    context: Context,
    renderer: VoxelRippleRenderer,
) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(3)

        holder.setFormat(PixelFormat.TRANSLUCENT)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        setZOrderOnTop(true)

        renderer.requestNextFrame = {
            requestRender()
        }

        setRenderer(renderer)

        // Critical optimization:
        // do not render forever while there are no active waves.
        renderMode = RENDERMODE_WHEN_DIRTY

        preserveEGLContextOnPause = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        super.surfaceCreated(holder)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}

private class VoxelRippleRenderer : GLSurfaceView.Renderer {

    @Volatile
    var requestNextFrame: (() -> Unit)? = null

    private var programId = 0
    private var vaoId = 0

    private var vertexBufferId = 0
    private var cellBufferId = 0
    private var jumpRandomBufferId = 0
    private var colorRandomBufferId = 0

    private var widthPx = 0
    private var heightPx = 0
    private var columns = 0
    private var rows = 0
    private var instanceCount = 0

    private val waves = ArrayDeque<VoxelWave>()
    private val pendingWaves = ArrayDeque<PendingWave>()

    private val waveUniformData = FloatArray(MAX_WAVES * 4)

    private var uResolution = -1
    private var uCellSize = -1
    private var uWaveCount = -1
    private var uWaves = -1
    private var uFrontWidth = -1
    private var uTrailWidth = -1
    private var uWaveLife = -1
    private var uMaxJump = -1
    private var uJumpSigma = -1
    private var uDepthX = -1
    private var uDepthY = -1

    fun launchWave(x: Float, y: Float) {
        synchronized(pendingWaves) {
            pendingWaves += PendingWave(x = x, y = y)
        }
        requestNextFrame?.invoke()
    }

    override fun onSurfaceCreated(
        gl: GL10?,
        config: EGLConfig?,
    ) {
        programId = createProgram(
            vertexShaderSource = VERTEX_SHADER,
            fragmentShaderSource = FRAGMENT_SHADER,
        )

        GLES30.glUseProgram(programId)

        uResolution = GLES30.glGetUniformLocation(programId, "u_resolution")
        uCellSize = GLES30.glGetUniformLocation(programId, "u_cellSize")
        uWaveCount = GLES30.glGetUniformLocation(programId, "u_waveCount")
        uWaves = GLES30.glGetUniformLocation(programId, "u_waves[0]")
        uFrontWidth = GLES30.glGetUniformLocation(programId, "u_frontWidth")
        uTrailWidth = GLES30.glGetUniformLocation(programId, "u_trailWidth")
        uWaveLife = GLES30.glGetUniformLocation(programId, "u_waveLife")
        uMaxJump = GLES30.glGetUniformLocation(programId, "u_maxJump")
        uJumpSigma = GLES30.glGetUniformLocation(programId, "u_jumpSigma")
        uDepthX = GLES30.glGetUniformLocation(programId, "u_depthX")
        uDepthY = GLES30.glGetUniformLocation(programId, "u_depthY")

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        GLES30.glClearColor(0f, 0f, 0f, 0f)
    }

    override fun onSurfaceChanged(
        gl: GL10?,
        width: Int,
        height: Int,
    ) {
        widthPx = width
        heightPx = height

        GLES30.glViewport(0, 0, width, height)

        columns = ceil(width / CELL_SIZE_PX).toInt()
        rows = ceil(height / CELL_SIZE_PX).toInt()
        instanceCount = columns * rows

        createStaticGeometry()
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowMs = SystemClock.uptimeMillis()

        consumePendingWaves(nowMs)
        removeExpiredWaves(nowMs)

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        if (widthPx <= 0 || heightPx <= 0 || instanceCount <= 0) {
            return
        }

        // Critical optimization:
        // if there are no waves, clear once and stop rendering.
        if (waves.isEmpty()) {
            return
        }

        updateUniforms(nowMs)

        GLES30.glUseProgram(programId)
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArraysInstanced(
            GLES30.GL_TRIANGLES,
            0,
            VERTEX_COUNT_PER_INSTANCE,
            instanceCount,
        )
        GLES30.glBindVertexArray(0)

        // Render next frame only while the animation is alive.
        if (waves.isNotEmpty()) {
            requestNextFrame?.invoke()
        }
    }

    private fun consumePendingWaves(nowMs: Long) {
        synchronized(pendingWaves) {
            while (pendingWaves.isNotEmpty()) {
                val pending = pendingWaves.removeFirst()

                waves += VoxelWave(
                    x = pending.x,
                    y = pending.y,
                    startedAtMs = nowMs,
                )

                while (waves.size > MAX_WAVES) {
                    waves.removeFirst()
                }
            }
        }
    }

    private fun removeExpiredWaves(nowMs: Long) {
        while (waves.isNotEmpty()) {
            val ageSec = (nowMs - waves.first().startedAtMs) / 1_000f
            if (ageSec <= WAVE_LIFETIME_SEC) {
                break
            }
            waves.removeFirst()
        }
    }

    private fun updateUniforms(nowMs: Long) {
        waveUniformData.fill(0f)

        waves.forEachIndexed { index, wave ->
            val offset = index * 4
            val ageSec = (nowMs - wave.startedAtMs) / 1_000f

            waveUniformData[offset] = wave.x
            waveUniformData[offset + 1] = wave.y
            waveUniformData[offset + 2] = ageSec
            waveUniformData[offset + 3] = WAVE_SPEED_PX_PER_SEC
        }

        GLES30.glUniform2f(uResolution, widthPx.toFloat(), heightPx.toFloat())
        GLES30.glUniform1f(uCellSize, CELL_SIZE_PX)
        GLES30.glUniform1f(uWaveCount, waves.size.toFloat())
        GLES30.glUniform4fv(uWaves, MAX_WAVES, waveUniformData, 0)
        GLES30.glUniform1f(uFrontWidth, FRONT_WIDTH_PX)
        GLES30.glUniform1f(uTrailWidth, TRAIL_WIDTH_PX)
        GLES30.glUniform1f(uWaveLife, WAVE_LIFETIME_SEC)
        GLES30.glUniform1f(uMaxJump, MAX_JUMP_PX)
        GLES30.glUniform1f(uJumpSigma, JUMP_SIGMA_PX)
        GLES30.glUniform1f(uDepthX, DEPTH_X)
        GLES30.glUniform1f(uDepthY, DEPTH_Y)
    }

    private fun createStaticGeometry() {
        if (vaoId != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
            vaoId = 0
        }

        val buffersToDelete = intArrayOf(
            vertexBufferId,
            cellBufferId,
            jumpRandomBufferId,
            colorRandomBufferId,
        ).filter { it != 0 }.toIntArray()

        if (buffersToDelete.isNotEmpty()) {
            GLES30.glDeleteBuffers(buffersToDelete.size, buffersToDelete, 0)
        }

        vertexBufferId = 0
        cellBufferId = 0
        jumpRandomBufferId = 0
        colorRandomBufferId = 0

        val vao = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        vaoId = vao[0]
        GLES30.glBindVertexArray(vaoId)

        vertexBufferId = createArrayBuffer(STATIC_VERTEX_DATA)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0,
            1,
            GLES30.GL_FLOAT,
            false,
            2 * Float.SIZE_BYTES,
            0,
        )
        GLES30.glVertexAttribDivisor(0, 0)

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
            1,
            1,
            GLES30.GL_FLOAT,
            false,
            2 * Float.SIZE_BYTES,
            Float.SIZE_BYTES,
        )
        GLES30.glVertexAttribDivisor(1, 0)

        val cells = FloatArray(instanceCount * 2)
        val jumpRandoms = FloatArray(instanceCount)
        val colorRandoms = FloatArray(instanceCount)

        var index = 0
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                cells[index * 2] = column * CELL_SIZE_PX
                cells[index * 2 + 1] = row * CELL_SIZE_PX

                jumpRandoms[index] = randomForCell(
                    column = column,
                    row = row,
                    salt = 1f,
                )
                colorRandoms[index] = randomForCell(
                    column = column,
                    row = row,
                    salt = 7f,
                )

                index++
            }
        }

        cellBufferId = createArrayBuffer(cells)

        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(
            2,
            2,
            GLES30.GL_FLOAT,
            false,
            0,
            0,
        )
        GLES30.glVertexAttribDivisor(2, 1)

        jumpRandomBufferId = createArrayBuffer(jumpRandoms)

        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(
            3,
            1,
            GLES30.GL_FLOAT,
            false,
            0,
            0,
        )
        GLES30.glVertexAttribDivisor(3, 1)

        colorRandomBufferId = createArrayBuffer(colorRandoms)

        GLES30.glEnableVertexAttribArray(4)
        GLES30.glVertexAttribPointer(
            4,
            1,
            GLES30.GL_FLOAT,
            false,
            0,
            0,
        )
        GLES30.glVertexAttribDivisor(4, 1)

        GLES30.glBindVertexArray(0)
    }

    private fun randomForCell(
        column: Int,
        row: Int,
        salt: Float,
    ): Float {
        val value = sin(column * 127.1f + row * 311.7f + salt * 91.13f) * 43758.5453123f
        return value - floor(value)
    }

    private fun createArrayBuffer(data: FloatArray): Int {
        val bufferId = IntArray(1)
        GLES30.glGenBuffers(1, bufferId, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufferId[0])
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            data.size * Float.SIZE_BYTES,
            data.toFloatBuffer(),
            GLES30.GL_STATIC_DRAW,
        )
        return bufferId[0]
    }

    private fun createProgram(
        vertexShaderSource: String,
        fragmentShaderSource: String,
    ): Int {
        val vertexShader = createShader(
            type = GLES30.GL_VERTEX_SHADER,
            source = vertexShaderSource,
        )
        val fragmentShader = createShader(
            type = GLES30.GL_FRAGMENT_SHADER,
            source = fragmentShaderSource,
        )

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)

        if (linkStatus[0] == 0) {
            val error = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            error("Failed to link OpenGL program: $error")
        }

        return program
    }

    private fun createShader(
        type: Int,
        source: String,
    ): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)

        if (compileStatus[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Failed to compile OpenGL shader: $error")
        }

        return shader
    }

    companion object {
        private const val VERTEX_COUNT_PER_INSTANCE = 18

        private val STATIC_VERTEX_DATA = floatArrayOf(
            // Top face
            0f, 0f, 1f, 0f, 2f, 0f,
            0f, 0f, 2f, 0f, 3f, 0f,

            // Left side face
            0f, 1f, 1f, 1f, 2f, 1f,
            0f, 1f, 2f, 1f, 3f, 1f,

            // Right side face
            0f, 2f, 1f, 2f, 2f, 2f,
            0f, 2f, 2f, 2f, 3f, 2f,
        )
    }
}

private fun FloatArray.toFloatBuffer(): FloatBuffer {
    return ByteBuffer
        .allocateDirect(size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(this@toFloatBuffer)
            position(0)
        }
}

private val VERTEX_SHADER = """
#version 300 es
precision highp float;

layout(location = 0) in float a_corner;
layout(location = 1) in float a_face;
layout(location = 2) in vec2 a_cell;
layout(location = 3) in float a_jumpRandom;
layout(location = 4) in float a_colorRandom;

uniform vec2 u_resolution;
uniform float u_cellSize;
uniform float u_waveCount;
uniform vec4 u_waves[3];
uniform float u_frontWidth;
uniform float u_trailWidth;
uniform float u_waveLife;
uniform float u_maxJump;
uniform float u_jumpSigma;
uniform float u_depthX;
uniform float u_depthY;

out float v_alpha;
out float v_face;
out float v_height;
out float v_colorRandom;

float clamp01(float value) {
    return clamp(value, 0.0, 1.0);
}

float smoothstep01(float edge0, float edge1, float value) {
    float x = clamp01((value - edge0) / (edge1 - edge0));
    return x * x * (3.0 - 2.0 * x);
}

void main() {
    vec2 center = a_cell + vec2(u_cellSize * 0.5);

    float alpha = 0.0;
    float jump = 0.0;

    for (int i = 0; i < 3; i++) {
        if (float(i) >= u_waveCount) {
            break;
        }

        vec4 wave = u_waves[i];
        vec2 waveCenter = wave.xy;
        float age = wave.z;
        float speed = wave.w;

        float radius = age * speed;
        float distanceToWave = distance(center, waveCenter);
        float delta = distanceToWave - radius;

        float front = 1.0 - smoothstep01(0.0, u_frontWidth, abs(delta));

        float trail = delta < 0.0
            ? 1.0 - smoothstep01(0.0, u_trailWidth, abs(delta))
            : 0.0;

        float lifeFade = 1.0 - smoothstep01(u_waveLife * 0.75, u_waveLife, age);

        float waveAlpha = max(front, trail * 0.32) * lifeFade;

        // Cheaper gaussian-like bell.
        // Keeps the "hat" silhouette without using exp().
        float bell = 1.0 - smoothstep01(0.0, u_jumpSigma * 2.4, abs(delta));
        float waveJump = bell * bell * lifeFade;

        alpha = max(alpha, waveAlpha);
        jump = max(jump, waveJump);
    }

    float randomJumpFactor = 0.65 + a_jumpRandom * 0.70;
    float columnHeight = jump * u_maxJump * randomJumpFactor;

    float brightnessFactor = 0.34 + jump * 0.66 * randomJumpFactor;
    alpha = clamp01(alpha * brightnessFactor);

    float pixelSize = max(2.0, floor(u_cellSize * (0.72 + alpha * 0.18)));
    float offset = floor((u_cellSize - pixelSize) * 0.5);

    float dx = max(1.0, pixelSize * u_depthX);
    float dy = max(1.0, pixelSize * u_depthY);

    vec2 p = a_cell + vec2(offset);
    float topY = p.y - columnHeight;

    vec2 position;
    int corner = int(a_corner);

    if (a_face < 0.5) {
        vec2 topCorners[4];

        topCorners[0] = vec2(p.x, topY);
        topCorners[1] = vec2(p.x + pixelSize, topY);
        topCorners[2] = vec2(p.x + pixelSize + dx, topY + dy);
        topCorners[3] = vec2(p.x + dx, topY + dy);

        position = topCorners[corner];
    } else if (a_face < 1.5) {
        vec2 leftCorners[4];

        leftCorners[0] = vec2(p.x, p.y);
        leftCorners[1] = vec2(p.x, topY);
        leftCorners[2] = vec2(p.x + dx, topY + dy);
        leftCorners[3] = vec2(p.x + dx, p.y + dy);

        position = leftCorners[corner];
    } else {
        vec2 rightCorners[4];

        rightCorners[0] = vec2(p.x + pixelSize, p.y);
        rightCorners[1] = vec2(p.x + pixelSize, topY);
        rightCorners[2] = vec2(p.x + pixelSize + dx, topY + dy);
        rightCorners[3] = vec2(p.x + dx, p.y + dy);

        position = rightCorners[corner];
    }

    vec2 clip = (position / u_resolution) * 2.0 - 1.0;
    clip.y = -clip.y;

    gl_Position = vec4(clip, 0.0, 1.0);

    v_alpha = alpha;
    v_face = a_face;
    v_height = columnHeight;
    v_colorRandom = a_colorRandom;
}
""".trimStart()

private val FRAGMENT_SHADER = """
#version 300 es
precision highp float;

in float v_alpha;
in float v_face;
in float v_height;
in float v_colorRandom;

out vec4 outColor;

void main() {
    if (v_alpha < 0.025) {
        discard;
    }

    float tint = smoothstep(0.0, 1.0, v_colorRandom);

    vec3 dullCyan = vec3(0.12, 0.34, 0.42);
    vec3 midCyan = vec3(0.24, 0.68, 0.82);
    vec3 brightCyan = vec3(0.44, 0.92, 1.00);

    vec3 baseColor;

    if (tint < 0.55) {
        baseColor = mix(dullCyan, midCyan, tint / 0.55);
    } else {
        baseColor = mix(midCyan, brightCyan, (tint - 0.55) / 0.45);
    }

    vec3 faceColor = baseColor;
    float faceAlpha = v_alpha;

    if (v_face > 0.5 && v_face < 1.5) {
        faceColor *= vec3(0.42, 0.52, 0.72);
        faceAlpha *= 0.44;
    } else if (v_face >= 1.5) {
        faceColor *= vec3(0.58, 0.72, 1.0);
        faceAlpha *= 0.64;
    }

    float heightGlow = clamp(v_height / 30.0, 0.0, 1.0);
    faceColor += vec3(0.06, 0.08, 0.10) * heightGlow;

    outColor = vec4(faceColor, faceAlpha);
}
""".trimStart()
