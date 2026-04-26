#include <GLES2/gl2.h>
#include <jni.h>

#include <array>

namespace mbgl {
namespace style {

struct CustomLayerRenderParameters {
    double width;
    double height;
    double latitude;
    double longitude;
    double zoom;
    double bearing;
    double pitch;
    double fieldOfView;
    std::array<double, 16> projectionMatrix;
};

class CustomLayerHost {
public:
    virtual ~CustomLayerHost() = default;
    virtual void initialize() = 0;
    virtual void render(const CustomLayerRenderParameters&) = 0;
    virtual void contextLost() = 0;
    virtual void deinitialize() = 0;
};

} // namespace style
} // namespace mbgl

namespace {

class JourneyCustomLayerHost final : public mbgl::style::CustomLayerHost {
public:
    JourneyCustomLayerHost() = default;

    void initialize() override {
        initializeProgram();
    }

    void render(const mbgl::style::CustomLayerRenderParameters&) override {
        if (program == 0) {
            initializeProgram();
        }
        if (program == 0) {
            return;
        }

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glUseProgram(program);
        glUniform4f(colorUniform, 0.10f, 0.70f, 1.00f, 0.30f);

        glEnableVertexAttribArray(positionAttribute);
        glVertexAttribPointer(positionAttribute, 2, GL_FLOAT, GL_FALSE, 0, kQuadVertices.data());
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glDisableVertexAttribArray(positionAttribute);

        glUseProgram(0);
        glDisable(GL_BLEND);
    }

    void contextLost() override {
        releaseProgram();
    }

    void deinitialize() override {
        releaseProgram();
    }

private:
    static constexpr std::array<GLfloat, 8> kQuadVertices = {
        -0.45f, -0.45f,
         0.45f, -0.45f,
        -0.45f,  0.45f,
         0.45f,  0.45f,
    };

    static constexpr const char* kVertexShader = R"(
attribute vec2 a_position;
void main() {
  gl_Position = vec4(a_position, 0.0, 1.0);
}
)";

    static constexpr const char* kFragmentShader = R"(
precision mediump float;
uniform vec4 u_color;
void main() {
  gl_FragColor = u_color;
}
)";

    GLuint program = 0;
    GLint positionAttribute = -1;
    GLint colorUniform = -1;

    void initializeProgram() {
        const GLuint vertexShader = compileShader(GL_VERTEX_SHADER, kVertexShader);
        const GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, kFragmentShader);
        if (vertexShader == 0 || fragmentShader == 0) {
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            return;
        }

        const GLuint createdProgram = glCreateProgram();
        glAttachShader(createdProgram, vertexShader);
        glAttachShader(createdProgram, fragmentShader);
        glLinkProgram(createdProgram);

        GLint linkStatus = GL_FALSE;
        glGetProgramiv(createdProgram, GL_LINK_STATUS, &linkStatus);

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        if (linkStatus != GL_TRUE) {
            glDeleteProgram(createdProgram);
            return;
        }

        program = createdProgram;
        positionAttribute = glGetAttribLocation(program, "a_position");
        colorUniform = glGetUniformLocation(program, "u_color");
    }

    void releaseProgram() {
        if (program != 0) {
            glDeleteProgram(program);
            program = 0;
        }
        positionAttribute = -1;
        colorUniform = -1;
    }

    static GLuint compileShader(GLenum type, const char* source) {
        const GLuint shader = glCreateShader(type);
        if (shader == 0) {
            return 0;
        }

        glShaderSource(shader, 1, &source, nullptr);
        glCompileShader(shader);

        GLint status = GL_FALSE;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
        if (status != GL_TRUE) {
            glDeleteShader(shader);
            return 0;
        }

        return shader;
    }
};

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeMapLibreCustomLayerFactory_nativeCreateCustomLayerHost(
    JNIEnv*,
    jobject
) {
    auto* host = new JourneyCustomLayerHost();
    return reinterpret_cast<jlong>(host);
}
