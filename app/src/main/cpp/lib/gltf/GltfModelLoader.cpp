#include "gltf/GltfModelLoader.hpp"

#include <android/log.h>
#include <cgltf.h>

#include <cstring>
#include <string>
#include <utility>
#include <vector>

#include "assets/AssetReader.hpp"

namespace {

constexpr const char* tiger_model_path = "models/animal-tiger.glb";
constexpr const char* texture_fallback_path = "models/textures/colormap.png";

using custom_map_layers::gltf::ModelVertex;

std::string resolveTexturePath(const char* uri) {
    if (uri == nullptr || std::strlen(uri) == 0) {
        return texture_fallback_path;
    }
    if (std::strcmp(uri, "Textures/colormap.png") == 0) {
        return texture_fallback_path;
    }
    return std::string("models/") + uri;
}

void transformPosition(const cgltf_float matrix[16], const float in[3], float out[3]) {
    out[0] = matrix[0] * in[0] + matrix[4] * in[1] + matrix[8] * in[2] + matrix[12];
    out[1] = matrix[1] * in[0] + matrix[5] * in[1] + matrix[9] * in[2] + matrix[13];
    out[2] = matrix[2] * in[0] + matrix[6] * in[1] + matrix[10] * in[2] + matrix[14];
}

void appendPrimitive(
        const cgltf_primitive& primitive,
        const cgltf_float matrix[16],
        std::vector<ModelVertex>& vertices,
        const char* logTag
) {
    const cgltf_accessor* positionAccessor = nullptr;
    const cgltf_accessor* texcoordAccessor = nullptr;

    for (cgltf_size attributeIndex = 0; attributeIndex < primitive.attributes_count; ++attributeIndex) {
        const cgltf_attribute& attribute = primitive.attributes[attributeIndex];
        if (attribute.type == cgltf_attribute_type_position) {
            positionAccessor = attribute.data;
        } else if (attribute.type == cgltf_attribute_type_texcoord && attribute.index == 0) {
            texcoordAccessor = attribute.data;
        }
    }

    if (positionAccessor == nullptr || texcoordAccessor == nullptr || primitive.indices == nullptr) {
        __android_log_write(ANDROID_LOG_ERROR, logTag, "Primitive missing POSITION, TEXCOORD_0, or indices");
        return;
    }

    const cgltf_size indexCount = primitive.indices->count;
    const size_t initialVertexCount = vertices.size();
    vertices.reserve(vertices.size() + static_cast<size_t>(indexCount));

    for (cgltf_size i = 0; i < indexCount; ++i) {
        const cgltf_size vertexIndex = cgltf_accessor_read_index(primitive.indices, i);

        float position[3] = {0.0f, 0.0f, 0.0f};
        float texcoord[2] = {0.0f, 0.0f};
        float transformed[3] = {0.0f, 0.0f, 0.0f};
        if (!cgltf_accessor_read_float(positionAccessor, vertexIndex, position, 3) ||
            !cgltf_accessor_read_float(texcoordAccessor, vertexIndex, texcoord, 2)) {
            __android_log_write(ANDROID_LOG_ERROR, logTag, "Failed to read primitive vertex accessors");
            vertices.resize(initialVertexCount);
            return;
        }
        transformPosition(matrix, position, transformed);

        vertices.push_back(
                ModelVertex{
                        .x = transformed[0],
                        .y = transformed[1],
                        .z = transformed[2],
                        .u = texcoord[0],
                        .v = texcoord[1],
                }
        );
    }
}

void appendNode(
        const cgltf_node& node,
        std::vector<ModelVertex>& vertices,
        std::string& texturePath,
        const char* logTag
) {
    cgltf_float matrix[16];
    cgltf_node_transform_world(&node, matrix);

    if (node.mesh != nullptr) {
        for (cgltf_size primitiveIndex = 0; primitiveIndex < node.mesh->primitives_count; ++primitiveIndex) {
            const cgltf_primitive& primitive = node.mesh->primitives[primitiveIndex];
            if (primitive.type != cgltf_primitive_type_triangles) {
                __android_log_write(ANDROID_LOG_WARN, logTag, "Skipping non-triangle primitive");
                continue;
            }
            appendPrimitive(primitive, matrix, vertices, logTag);

            if (texturePath.empty() && primitive.material != nullptr) {
                const cgltf_texture_view& baseColor = primitive.material->pbr_metallic_roughness.base_color_texture;
                if (baseColor.texture != nullptr && baseColor.texture->image != nullptr) {
                    texturePath = resolveTexturePath(baseColor.texture->image->uri);
                }
            }
        }
    }

    for (cgltf_size childIndex = 0; childIndex < node.children_count; ++childIndex) {
        appendNode(*node.children[childIndex], vertices, texturePath, logTag);
    }
}

}  // namespace

namespace custom_map_layers::gltf {

GltfModelLoader::GltfModelLoader(AAssetManager* assetManager) : assetManager_(assetManager) {}

std::optional<LoadedModel> GltfModelLoader::loadTiger(const char* logTag) const {
    const custom_map_layers::assets::AssetReader reader(assetManager_);
    const auto bytes = reader.readBytes(tiger_model_path, logTag);
    if (!bytes.has_value()) {
        return std::nullopt;
    }

    cgltf_options options = {};
    cgltf_data* data = nullptr;
    const cgltf_result parseResult = cgltf_parse(&options, bytes->data(), bytes->size(), &data);
    if (parseResult != cgltf_result_success || data == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, logTag, "cgltf_parse failed: %d", parseResult);
        return std::nullopt;
    }
    const cgltf_result loadBuffersResult = cgltf_load_buffers(&options, data, nullptr);
    if (loadBuffersResult != cgltf_result_success) {
        __android_log_print(ANDROID_LOG_ERROR, logTag, "cgltf_load_buffers failed: %d", loadBuffersResult);
        cgltf_free(data);
        return std::nullopt;
    }

    std::vector<ModelVertex> vertices;
    std::string texturePath;
    const cgltf_scene* scene =
            data->scene != nullptr ? data->scene : (data->scenes_count > 0 ? &data->scenes[0] : nullptr);
    if (scene == nullptr) {
        __android_log_write(ANDROID_LOG_ERROR, logTag, "GLB has no scene");
        cgltf_free(data);
        return std::nullopt;
    }

    for (cgltf_size nodeIndex = 0; nodeIndex < scene->nodes_count; ++nodeIndex) {
        appendNode(*scene->nodes[nodeIndex], vertices, texturePath, logTag);
    }

    cgltf_free(data);

    if (vertices.empty()) {
        __android_log_write(ANDROID_LOG_ERROR, logTag, "GLB produced no renderable vertices");
        return std::nullopt;
    }
    if (texturePath.empty()) {
        texturePath = texture_fallback_path;
    }

    return LoadedModel{
            .triangleVertices = std::move(vertices),
            .texturePath = std::move(texturePath),
    };
}

}  // namespace custom_map_layers::gltf
