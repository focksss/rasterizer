package ModelHandler;
import Datatypes.Triangle;
import Datatypes.Vec;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.json.JSONObject;
import org.json.JSONArray;

import javax.lang.model.element.QualifiedNameable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class gLTF {
    List<Material> mtllib = new ArrayList<>();
    String[] gltfMatPropertiesMap = new String[]{"name", "baseColorFactor", "baseColorTexture", "metallicFactor", "roughnessFactor", "metallicTexture", "roughnessTexture"};
    String[] mtlPropertiesMap = new String[]{"name", "Kd", "map_Kd", "Pm", "Pr", "map_Pm", "map_Pr"};
    Scene activeScene;
    List<Scene> Scenes = new ArrayList<>();
    List<Node> Nodes = new ArrayList<>();
    List<Mesh> Meshes = new ArrayList<>();
    List<Accessor> Accessors = new ArrayList<>();
    List<BufferView> BufferViews = new ArrayList<>();
    List<Buffer> Buffers = new ArrayList<>();

    public gLTF(String gltfPath) {
        List<String> gltfMatMap = List.of("name", "baseColorFactor", "baseColorTexture", "metallicFactor", "roughnessFactor", "metallicTexture", "roughnessTexture", "doubleSided");
        List<String> mtlMatMap = List.of("name", "Kd", "map_Kd", "Pm", "Pr", "map_Pm", "map_Pr", "doubleSided");
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
            System.out.println(gltfContent);
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
                Mesh mesh = Meshes.get(node.getInt("mesh"));
                String name = node.getString("name");
                Matrix4f transform = new Matrix4f().identity();
                if (node.has("scale")) {
                    JSONArray scale = node.getJSONArray("scale");
                    transform.scale(scale.getLong(0), scale.getLong(1), scale.getLong(2));
                }
                if (node.has("rotation")) {
                    JSONArray rotation = node.getJSONArray("rotation");
                    Quaternionf quaternionRotation = new Quaternionf(rotation.getLong(0), rotation.getLong(1), rotation.getLong(2), rotation.getLong(3));
                    transform.rotate(quaternionRotation);
                }
                if (node.has("translation")) {
                    JSONArray translation = node.getJSONArray("translation");
                    transform.translate(translation.getLong(0), translation.getLong(1), translation.getLong(2));
                }
                Nodes.add(new Node(name, mesh, transform));
            }
        //construct scenes
            JSONArray scenes = gltf.getJSONArray("scenes");
            for (int i = 0; i < scenes.length(); i++) {
                JSONObject scene = scenes.getJSONObject(i);
                String name = scene.getString("name");
                JSONArray sceneNodes = scene.getJSONArray("nodes");
                List<Node> nodesList = new ArrayList<>();
                for (int j = 0; j < nodes.length(); j++) {
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
                    if (value instanceof String || value instanceof Boolean) {
                        material.setProperty(mtlMatMap.get(gltfMatMap.indexOf(key)), value);
                    } else if (value instanceof JSONObject) {
                        if (key.equals("pbrMetallicRoughness")) {
                            JSONObject pbrMat = (JSONObject) value;
                            for (String PBRkey : pbrMat.keySet()) {
                                Object PBRvalue = pbrMat.get(PBRkey);
                                int mapKey = gltfMatMap.indexOf(PBRkey);
                                if (mapKey != -1) {
                                    int texIdx = -1;
                                    if (PBRvalue instanceof JSONObject textureMap) {
                                        texIdx = textureMap.getInt("index");
                                    } else if (PBRvalue instanceof Number) {
                                        PBRvalue = ((Number) PBRvalue).floatValue();
                                    }
                                    material.setProperty(mtlMatMap.get(mapKey), !PBRkey.contains("Texture") ? PBRvalue : textureSources.get(texIdx));
                                }
                            }
                        }
                    }
                }
                //material.print();
                materialList.add(material);
            }
            mtllib.addAll(materialList);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class Scene {
        String name;
        List<Node> nodes;

        public Scene(String name, List<Node> nodes) {
            this.name = name;
            this.nodes = nodes;
        }
    }
    public static class Node {
        String name;
        Mesh mesh;
        Matrix4f transform = new Matrix4f().identity();

        public Node(String name, Mesh mesh, Matrix4f transform) {
            this.name = name;
            this.mesh = mesh;
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
        List<Triangle> triangles;

        public Mesh(String name, List<Accessor> positionAttribute, List<Accessor> normalAttribute, List<Accessor> texCoordAttribute, List<Accessor> indices, List<Integer> material) {
            this.name = name;
            this.positionAttribute = positionAttribute;
            this.normalAttribute = normalAttribute;
            this.texCoordAttribute = texCoordAttribute;
            this.material = material;
            this.indices = indices;
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
