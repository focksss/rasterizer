package ModelHandler;
import Datatypes.Triangle;
import Datatypes.Vec;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.json.JSONObject;
import org.json.JSONArray;
import org.lwjgl.system.MemoryUtil;

import javax.lang.model.element.QualifiedNameable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.system.MemoryUtil.memFree;

public class gLTF {
    public static List<Material> mtllib = new ArrayList<>();
    public static List<String> texturePaths = new ArrayList<>();
    String[] gltfMatPropertiesMap = new String[]{"name", "baseColorFactor", "baseColorTexture", "metallicFactor", "roughnessFactor", "metallicTexture", "roughnessTexture"};
    String[] mtlPropertiesMap = new String[]{"name", "Kd", "map_Kd", "Pm", "Pr", "map_Pm", "map_Pr"};
    public static Scene activeScene;
    public static List<Scene> Scenes = new ArrayList<>();
    public static List<Node> Nodes = new ArrayList<>();
    public static List<Mesh> Meshes = new ArrayList<>();
    public List<Accessor> Accessors = new ArrayList<>();
    public List<BufferView> BufferViews = new ArrayList<>();
    public List<Buffer> Buffers = new ArrayList<>();

    public gLTF(String gltfPath) {
        List<String> gltfMatMap = List.of("name", "baseColorFactor", "baseColorTexture", "metallicFactor", "roughnessFactor", "metallicTexture", "roughnessTexture", "doubleSided", "normalTexture", "metallicRoughnessTexture", "emissiveTexture", "emissiveFactor");
        List<String> mtlMatMap = List.of("name", "Kd", "map_Kd", "Pm", "Pr", "map_Pm", "map_Pr", "doubleSided", "map_Bump", "map_Pm&map_Pr", "map_Ke", "Ke");
        try {
        //setup
            Path path = Paths.get(gltfPath);
            File gltfFile = null;
            File binFile = null;
            JSONObject gltf;
        //check if glb or gltf
            if (Files.isDirectory(path)) {
                File gltfDir = path.toFile();

                File[] gltfs = gltfDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".gltf"));
                assert gltfs != null;
                gltfFile = gltfs[0];
                File[] bins = gltfDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".bin"));
                assert bins != null;
                binFile = bins[0];
            }

        //get gltf content
            assert gltfFile != null;
            Path gltfFilePath = gltfFile.toPath();
            String gltfContent = new String(Files.readAllBytes(gltfFilePath));
            //System.out.println(gltfContent);
            gltf = new JSONObject(gltfContent);

        //construct buffers
            JSONArray buffers = gltf.getJSONArray("buffers");
            for (int i = 0; i < buffers.length(); i++) {
                JSONObject buffer = buffers.getJSONObject(i);
                int byteLength = buffer.getInt("byteLength");
                String uri = buffer.getString("uri");
                Buffers.add(new Buffer(byteLength, uri, path));
            }
        //construct bufferViews
            JSONArray bufferViews = gltf.getJSONArray("bufferViews");
            for (int i = 0; i < bufferViews.length(); i++) {
                JSONObject bufferView = bufferViews.getJSONObject(i);
                Buffer buffer = Buffers.get(bufferView.getInt("buffer"));
                int byteLength = bufferView.getInt("byteLength");
                int byteOffset = bufferView.getInt("byteOffset");
                int target = bufferView.getInt("target");
                BufferViews.add(new BufferView(buffer, byteLength, byteOffset, target));
            }
        //construct accessors
            JSONArray accessors = gltf.getJSONArray("accessors");
            for (int i = 0; i < accessors.length(); i++) {
                JSONObject accessor = accessors.getJSONObject(i);
                BufferView bufferView = BufferViews.get(accessor.getInt("bufferView"));
                int componentType = accessor.getInt("componentType");
                int count = accessor.getInt("count");
                String type = accessor.getString("type");
                Accessors.add(new Accessor(bufferView, componentType, count, type));
            }
        //construct meshes
            JSONArray meshes = gltf.getJSONArray("meshes");
            for (int i = 0; i < meshes.length(); i++) {
                List<Accessor> positionAttributes = new ArrayList<>();
                List<Accessor> normalAttributes = new ArrayList<>();
                List<Accessor> texCoordAttributes = new ArrayList<>();
                List<Accessor> indices = new ArrayList<>();
                List<Integer> materialIndices = new ArrayList<>();
                JSONObject mesh = meshes.getJSONObject(i);
                String meshName = mesh.getString("name");
                JSONArray primitives = mesh.getJSONArray("primitives");
                for (int j = 0; j < primitives.length(); j++) {
                    JSONObject primitiveSet = primitives.getJSONObject(j);
                    JSONObject attributes = primitiveSet.getJSONObject("attributes");
                    for (String key : attributes.keySet()) {
                        if (key.equals("POSITION")) positionAttributes.add(Accessors.get(attributes.getInt(key)));
                        else if (key.equals("NORMAL")) normalAttributes.add(Accessors.get(attributes.getInt(key)));
                        else if (key.equals("TEXCOORD_0")) texCoordAttributes.add(Accessors.get(attributes.getInt(key)));
                    }
                    indices.add(Accessors.get(primitiveSet.getInt("indices")));
                    materialIndices.add(primitiveSet.getInt("material"));
                }
                Meshes.add(new Mesh(meshName, positionAttributes, normalAttributes, texCoordAttributes, indices, materialIndices));
            }
        //construct nodes
            JSONArray nodes = gltf.getJSONArray("nodes");
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                String name = node.getString("name");
                Matrix4f transform = new Matrix4f().identity();
                if (node.has("scale")) {
                    JSONArray scale = node.getJSONArray("scale");
                    transform.scale(scale.getNumber(0).floatValue(), scale.getNumber(1).floatValue(), scale.getNumber(2).floatValue());
                }
                if (node.has("rotation")) {
                    JSONArray rotation = node.getJSONArray("rotation");
                    Quaternionf quaternionRotation = new Quaternionf(rotation.getNumber(0).floatValue(), rotation.getNumber(1).floatValue(), rotation.getNumber(2).floatValue(), rotation.getNumber(3).floatValue());
                    transform.rotate(quaternionRotation);
                }
                if (node.has("translation")) {
                    JSONArray translation = node.getJSONArray("translation");
                    transform.translate(translation.getNumber(0).floatValue(), translation.getNumber(1).floatValue(), translation.getNumber(2).floatValue());
                }
                if (node.has("mesh")) {
                    Mesh mesh = Meshes.get(node.getInt("mesh"));
                    Nodes.add(new Node(name, mesh, transform));
                }
                if (node.has("children")) {
                    JSONArray children = node.getJSONArray("children");
                    List<Node> childrenNodes = new ArrayList<>();
                    for (int j = 0; j < children.length(); j++) {
                        childrenNodes.add(Nodes.get(children.getInt(j)));
                    }
                    Nodes.add(new Node(name, childrenNodes, transform));
                    System.out.println("added " + childrenNodes.size() + " nodes to " + name);
                }
            }
        //construct scenes
            JSONArray scenes = gltf.getJSONArray("scenes");
            for (int i = 0; i < scenes.length(); i++) {
                JSONObject scene = scenes.getJSONObject(i);
                String name = scene.getString("name");
                JSONArray sceneNodes = scene.getJSONArray("nodes");
                List<Node> nodesList = new ArrayList<>();
                for (int j = 0; j < sceneNodes.length(); j++) {
                    nodesList.add(Nodes.get(sceneNodes.getInt(j)));
                }
                Scenes.add(new Scene(name, nodesList));
            }
        //set active scene
            activeScene = Scenes.get(gltf.getInt("scene"));

        //get textures
            List<String> textureSources = new ArrayList<>();
            List<String> imagePaths = new ArrayList<>();
            JSONArray textures = gltf.getJSONArray("textures");
            JSONArray images = gltf.getJSONArray("images");
            for (int i = 0; i < images.length(); i++) {
                JSONObject image = images.getJSONObject(i);
                imagePaths.add(gltfPath.replace("\\", "/") + "/" + image.getString("uri"));
            }
            for (int i = 0; i < textures.length(); i++) {
                JSONObject texture = textures.getJSONObject(i);
                textureSources.add(imagePaths.get(texture.getInt("source")));
            }

        //get materials
            List<Material> materialList = new ArrayList<>();
            JSONArray materials = gltf.getJSONArray("materials");
            for (int i = 0; i < materials.length(); i++) {
                JSONObject matJson = materials.getJSONObject(i);
                Material material = new Material();
                for (String key : matJson.keySet()) {
                    Object value = matJson.get(key);
                    //if basic property that has a mtl equivalent
                    if ((value instanceof String || value instanceof Boolean) && gltfMatMap.contains(key)) {
                        material.setProperty(mtlMatMap.get(gltfMatMap.indexOf(key)), value);
                    } else if (value instanceof JSONObject) {
                        //either a PBR material object or a textured property
                        if (key.equals("pbrMetallicRoughness")) {
                            JSONObject pbrMat = (JSONObject) value;
                            for (String PBRkey : pbrMat.keySet()) {
                                Object PBRvalue = pbrMat.get(PBRkey);
                                int mapKey = gltfMatMap.indexOf(PBRkey);
                                String mtlProp = mtlMatMap.get(mapKey);
                                if (mapKey != -1) {
                                    int texIdx = -1;
                                    if (PBRvalue instanceof JSONObject textureMap) {
                                        texIdx = textureMap.getInt("index");
                                        texturePaths.add(textureSources.get(texIdx));
                                    } else if (PBRvalue instanceof Number) {
                                        PBRvalue = ((Number) PBRvalue).floatValue();
                                    } else if (PBRvalue instanceof JSONArray colorArray) {
                                        PBRvalue = new Vec(colorArray.getNumber(0).floatValue(), colorArray.getNumber(1).floatValue(), colorArray.getNumber(2).floatValue());
                                    }
                                    if (mtlProp.contains("&")) {
                                        String[] dualProperty = mtlProp.split("&");
                                        material.setProperty(dualProperty[0], textureSources.get(texIdx));
                                        material.setProperty(dualProperty[1], textureSources.get(texIdx));
                                    } else {
                                        material.setProperty(mtlProp, !PBRkey.contains("Texture") ? PBRvalue : textureSources.get(texIdx));
                                    }
                                }
                            }
                        } else if (key.contains("Texture")) {
                            JSONObject textureMapProperty = (JSONObject) value;
                            int texIdx = -1;
                            texIdx = textureMapProperty.getInt("index");
                            texturePaths.add(textureSources.get(texIdx));
                            int mapKey = gltfMatMap.indexOf(key);
                            String mtlProp = mtlMatMap.get(mapKey);
                            if (mtlProp.contains("&")) {
                                String[] dualProperty = mtlProp.split("&");
                                material.setProperty(dualProperty[0], textureSources.get(texIdx));
                                material.setProperty(dualProperty[1], textureSources.get(texIdx));
                            } else {
                                material.setProperty(mtlProp, textureSources.get(texIdx));
                            }
                        } else if (key.equals("extensions")) {
                            JSONObject extensions = (JSONObject) value;
                            for (String extensionKey : extensions.keySet()) {
                                if (extensionKey.equals("KHR_materials_emissive_strength")) {
                                    JSONObject extension_emissive_strength = extensions.getJSONObject(extensionKey);
                                    material.setProperty("emissiveStrength", extension_emissive_strength.getNumber("emissiveStrength").doubleValue());
                                }
                            }
                        }
                    } else if (value instanceof JSONArray vector && gltfMatMap.contains(key)) {
                        int mapKey = gltfMatMap.indexOf(key);
                        String mtlProp = mtlMatMap.get(mapKey);
                        material.setProperty(mtlProp, new Vec(vector.getNumber(0).floatValue(), vector.getNumber(1).floatValue(), vector.getNumber(2).floatValue()));
                    }
                }
                //material.print();
                materialList.add(material);
            }
            mtllib.addAll(materialList);

            constructPrimitives();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void constructPrimitives() {
        for (Mesh mesh : Meshes) {
            // Process each primitive set (position, normal, texCoord, indices, material)
            for (int i = 0; i < mesh.positionAttribute.size(); i++) {
                Accessor posAcc = mesh.positionAttribute.get(i);
                Accessor normAcc = null;
                Accessor texAcc = null;

                // Get normal accessor if available
                if (i < mesh.normalAttribute.size()) {
                    normAcc = mesh.normalAttribute.get(i);
                }

                // Get texture coordinate accessor if available
                if (i < mesh.texCoordAttribute.size()) {
                    texAcc = mesh.texCoordAttribute.get(i);
                }

                // Get indices accessor
                Accessor indAcc = mesh.indices.get(i);

                // Get material index
                int materialIndex = mesh.material.get(i);

                // Cache position data
                List<Vec> positions = new ArrayList<>();
                ByteBuffer posBuffer = posAcc.bufferView.buffer.buffer;
                posBuffer.position(posAcc.bufferView.byteOffset);

                for (int j = 0; j < posAcc.count; j++) {
                    float x = posBuffer.getFloat();
                    float y = posBuffer.getFloat();
                    float z = posBuffer.getFloat();
                    positions.add(new Vec(x, y, z));
                }

                // Cache normal data if available
                List<Vec> normals = new ArrayList<>();
                if (normAcc != null) {
                    ByteBuffer normBuffer = normAcc.bufferView.buffer.buffer;
                    normBuffer.position(normAcc.bufferView.byteOffset);

                    for (int j = 0; j < normAcc.count; j++) {
                        float x = normBuffer.getFloat();
                        float y = normBuffer.getFloat();
                        float z = normBuffer.getFloat();
                        normals.add(new Vec(x, y, z));
                    }
                }

                // Cache texture coordinate data if available
                List<Vec> texCoords = new ArrayList<>();
                if (texAcc != null) {
                    ByteBuffer texBuffer = texAcc.bufferView.buffer.buffer;
                    texBuffer.position(texAcc.bufferView.byteOffset);

                    for (int j = 0; j < texAcc.count; j++) {
                        float u = texBuffer.getFloat();
                        float v = texBuffer.getFloat();
                        texCoords.add(new Vec(u, v, 0)); // Z component set to 0 for texture coordinates
                    }
                }

                // Read indices and create triangles
                ByteBuffer indBuffer = indAcc.bufferView.buffer.buffer;
                indBuffer.position(indAcc.bufferView.byteOffset);

                int triangleCount = indAcc.count / 3;

                for (int j = 0; j < triangleCount; j++) {
                    int idx1, idx2, idx3;

                    // Read indices based on component type
                    if (indAcc.componentType == 5123) { // UNSIGNED_SHORT
                        idx1 = indBuffer.getShort() & 0xFFFF;
                        idx2 = indBuffer.getShort() & 0xFFFF;
                        idx3 = indBuffer.getShort() & 0xFFFF;
                    } else if (indAcc.componentType == 5125) { // UNSIGNED_INT
                        idx1 = indBuffer.getInt();
                        idx2 = indBuffer.getInt();
                        idx3 = indBuffer.getInt();
                    } else if (indAcc.componentType == 5121) { // UNSIGNED_BYTE
                        idx1 = indBuffer.get() & 0xFF;
                        idx2 = indBuffer.get() & 0xFF;
                        idx3 = indBuffer.get() & 0xFF;
                    } else {
                        // Unsupported index type
                        continue;
                    }

                    // Get position vectors
                    Vec v1 = positions.get(idx1);
                    Vec v2 = positions.get(idx2);
                    Vec v3 = positions.get(idx3);

                    // Get normal vectors (or default)
                    Vec n1 = normals.isEmpty() ? new Vec(0, 0, 1) : normals.get(idx1);
                    Vec n2 = normals.isEmpty() ? new Vec(0, 0, 1) : normals.get(idx2);
                    Vec n3 = normals.isEmpty() ? new Vec(0, 0, 1) : normals.get(idx3);

                    // Get texture coordinates (or default)
                    Vec tc1 = texCoords.isEmpty() ? new Vec(0, 0, 0) : texCoords.get(idx1);
                    Vec tc2 = texCoords.isEmpty() ? new Vec(0, 0, 0) : texCoords.get(idx2);
                    Vec tc3 = texCoords.isEmpty() ? new Vec(0, 0, 0) : texCoords.get(idx3);

                    // Create and add triangle
                    Triangle triangle = new Triangle(v1, v2, v3, n1, n2, n3, tc1, tc2, tc3, materialIndex);
                    mesh.addTriangle(triangle);
//                    System.out.println("new triangle");
//                    Mesh.triangles.get(j).v1.println();
//                    v2.println();
//                    v3.println();
                }
            }
        }
    }


    public static class Scene {
        String name;
        public List<Node> nodes;

        public Scene(String name, List<Node> nodes) {
            this.name = name;
            this.nodes = nodes;
        }
    }
    public static class Node {
        String name;
        public Mesh mesh = null;
        public Matrix4f transform = new Matrix4f().identity();
        public List<Node> children = new ArrayList<>();

        public Node(String name, Mesh mesh, Matrix4f transform) {
            this.name = name;
            this.mesh = mesh;
            this.transform = transform;
        }
        public Node(String name, List<Node> children, Matrix4f transform) {
            this.name = name;
            this.children = children;
            this.transform = transform;
        }
    }
    public static class Mesh {
        String name;
        List<Accessor> positionAttribute;
        List<Accessor> normalAttribute;
        List<Accessor> texCoordAttribute;
        List<Integer> material;
        List<Accessor> indices;
        public List<Triangle> triangles = new ArrayList<>();
        public int VAO;
        public int VBO;
        public int triCount = 0;

        public Mesh(String name, List<Accessor> positionAttribute, List<Accessor> normalAttribute, List<Accessor> texCoordAttribute, List<Accessor> indices, List<Integer> material) {
            this.name = name;
            this.positionAttribute = positionAttribute;
            this.normalAttribute = normalAttribute;
            this.texCoordAttribute = texCoordAttribute;
            this.material = material;
            this.indices = indices;
        }
        public void addTriangle(Triangle triangle) {
            triangles.add(triangle);
        }
        public void initialize(int lastNumMats) {
            FloatBuffer verticesBuffer = MemoryUtil.memAllocFloat(45*triangles.size());

            int totalTriangles = triangles.size();
            int progressInterval = Math.max(totalTriangles / 10, 1); // Update every 10% or at least every 1 triangle
            for (int i = 0; i < totalTriangles; i++) {
                Triangle triangle = triangles.get(i);
                triCount++;
                verticesBuffer.put(triangle.v1.toFloatArray());
                verticesBuffer.put(triangle.vt1.toUVfloatArray());
                verticesBuffer.put(triangle.n1.toFloatArray());
                verticesBuffer.put((float) triangle.material + lastNumMats);
                verticesBuffer.put(triangle.t1.toFloatArray());
                verticesBuffer.put(triangle.bt1.toFloatArray());
                verticesBuffer.put(triangle.v2.toFloatArray());
                verticesBuffer.put(triangle.vt2.toUVfloatArray());
                verticesBuffer.put(triangle.n2.toFloatArray());
                verticesBuffer.put((float) triangle.material + lastNumMats);
                verticesBuffer.put(triangle.t2.toFloatArray());
                verticesBuffer.put(triangle.bt2.toFloatArray());
                verticesBuffer.put(triangle.v3.toFloatArray());
                verticesBuffer.put(triangle.vt3.toUVfloatArray());
                verticesBuffer.put(triangle.n3.toFloatArray());
                verticesBuffer.put((float) triangle.material + lastNumMats);
                verticesBuffer.put(triangle.t3.toFloatArray());
                verticesBuffer.put(triangle.bt3.toFloatArray());
                if (i % progressInterval == 0 || i == totalTriangles - 1) {
                    System.out.print("\rProcessed " + (i + 1) + " / " + totalTriangles + " triangles (" +
                            (int) ((i + 1) / (float) totalTriangles * 100) + "%)");
                }
            }
            //System.out.println(" processed all tris of " + name);
            verticesBuffer.flip();
            VAO = glGenVertexArrays();
            glBindVertexArray(VAO);
            VBO = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, VBO);
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);

            glVertexAttribPointer(0, 3, GL_FLOAT, false, 15*Float.BYTES, 0);
            glEnableVertexAttribArray(0);
            //vertex texCoord
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 15*Float.BYTES, 3*Float.BYTES);
            glEnableVertexAttribArray(1);
            //vertex normal
            glVertexAttribPointer(2, 3, GL_FLOAT, false, 15*Float.BYTES, 5*Float.BYTES);
            glEnableVertexAttribArray(2);
            //vertex material
            glVertexAttribPointer(3, 1, GL_FLOAT, false, 15*Float.BYTES, 8*Float.BYTES);
            glEnableVertexAttribArray(3);
            //vertex tangent
            glVertexAttribPointer(4, 3, GL_FLOAT, false, 15*Float.BYTES, 9*Float.BYTES);
            glEnableVertexAttribArray(4);
            //vertex bitangent
            glVertexAttribPointer(5, 3, GL_FLOAT, false, 15*Float.BYTES, 12*Float.BYTES);
            glEnableVertexAttribArray(5);

            memFree(verticesBuffer);
        }
    }
//replaced by my material class
    public static class Texture {
        Sampler sampler;
        Image source;

        public Texture(Sampler sampler, Image source) {
            this.sampler = sampler;
            this.source = source;
        }
    }
    public static class Sampler {
        int minFilter;
        int maxFilter;

        public Sampler(int minFilter, int maxFilter) {
            this.minFilter = minFilter;
            this.maxFilter = maxFilter;
        }
    }
    public static class Image {
        String name;
        String uri;
        String mimeType;

        public Image(String name, String uri, String mimeType) {
            this.name = name;
            this.uri = uri;
            this.mimeType = mimeType;
        }
    }
//
    public static class Accessor {
        BufferView bufferView;
        int componentType;
        int count;
        float[] max;
        float[] min;
        String type;

        public Accessor(BufferView bufferView, int componentType, int count, String type) {
            this.bufferView = bufferView;
            this.componentType = componentType;
            this.count = count;
            this.type = type;
        }
        public void setMax(float[] max) {
            this.max = max;
        }
        public void setMin(float[] min) {
            this.min = min;
        }
    }
    public static class BufferView {
        Buffer buffer;
        int byteLength;
        int byteOffset;
        int target;

        public BufferView(Buffer buffer, int byteLength, int byteOffset, int target) {
            this.buffer = buffer;
            this.byteLength = byteLength;
            this.byteOffset = byteOffset;
            this.target = target;
        }
    }
    public static class Buffer {
        int byteLength;
        String uri;
        ByteBuffer buffer;

        public Buffer(int byteLength, String uri, Path gltfPath) throws IOException {
            this.byteLength = byteLength;
            this.uri = uri;

            byte[] binData = Files.readAllBytes(Paths.get((gltfPath.toString() + "\\" + Path.of(uri).toString())));
            buffer = ByteBuffer.wrap(binData).order(ByteOrder.LITTLE_ENDIAN);
        }
    }
}
