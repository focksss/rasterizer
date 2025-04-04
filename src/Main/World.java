package Main;

import java.io.IOException;
import java.lang.Math;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import Datatypes.Triangle;
import Datatypes.Vec;
import ModelHandler.Light;
import ModelHandler.Material;
import ModelHandler.Obj;
import ModelHandler.gLTF;
import io.github.mudbill.dds.DDSFile;
import org.joml.*;
import org.lwjgl.system.MemoryUtil;

import static Util.MathUtil.clamp;
import static org.lwjgl.opengl.ARBBindlessTexture.*;
import static org.lwjgl.opengl.ARBUniformBufferObject.glBindBufferBase;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL45.*;
import static org.lwjgl.opengl.GL46C.GL_MAX_TEXTURE_MAX_ANISOTROPY;
import static org.lwjgl.opengl.GL46C.GL_TEXTURE_MAX_ANISOTROPY;
import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_load;
import static org.lwjgl.system.MemoryUtil.*;

public class World {
    public static List<gLTF> worldGLTFs = new ArrayList<>();
    public List<worldObject> worldObjects = new ArrayList<>();
    public List<worldLight> worldLights = new ArrayList<>();
    private List<Material> worldMaterials = new ArrayList<>();
    private List<String> texturePaths = new ArrayList<>();
    private List<Long> worldTextureHandles = new ArrayList<>();
    public static gLTF.Node selectedNode;
    public static Vec selectedL = new Vec(); // location
    public static Vec selectedR = new Vec(); // rotation
    public static Vec selectedS = new Vec(); // scale

    public int materialSSBO, textureHandleSSBO, lightDataSSBO, shadowmapHandleSSBO;

    public int triCount = 0;

    public World() {}

    public void updateWorld() {
        float numMatParams = getMaterialFieldCount();
        FloatBuffer materialData = memAllocFloat((int) numMatParams * worldMaterials.size() + 1);
        materialData.put(numMatParams);
        for (Material material : worldMaterials) {
            for (Field field : material.getClass().getDeclaredFields()) {
                if (!(field.getName().equals("name") || field.getName().equals("texturesDirectory"))) {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(material);
                        if (value instanceof Number) {
                            materialData.put(((Number) value).floatValue());
                        } else if (value instanceof Vec vecValue) {
                            vecValue.updateFloats();
                            materialData.put(Vec.xf);
                            materialData.put(Vec.yf);
                            materialData.put(Vec.zf);
                        } else if (value instanceof String) {
                            materialData.put((float) texturePaths.indexOf(value));
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        materialData.flip();
        materialSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, materialSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, materialData, GL_STATIC_DRAW);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, materialSSBO);
        memFree(materialData);

        LongBuffer textureHandles = memAllocLong(texturePaths.size());
        for (Long handle : worldTextureHandles) {
            textureHandles.put(handle);
            if (!glIsTextureHandleResidentARB(handle)) {
                System.err.println("texture handle is not resident: " + handle);
            }
        }
        textureHandles.flip();
        textureHandleSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, textureHandleSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, textureHandles, GL_STATIC_DRAW);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, textureHandleSSBO);
        memFree(textureHandles);

        FloatBuffer lightData = MemoryUtil.memAllocFloat(worldLights.size() * 22);
        for (worldLight wlight : worldLights) {
            Light light = wlight.light;
            //worldObjects.get(0).newInstance(new Vec(0.1), light.position, new Vec(0));
            for (Field field : light.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    Object value = field.get(light);
                    if (value instanceof Number) {
                        lightData.put(((Number) value).floatValue());
                    } else if (value instanceof Vec) {
                        Vec vecValue = (Vec) value;
                        vecValue.updateFloats();
                        lightData.put(vecValue.xf);
                        lightData.put(vecValue.yf);
                        lightData.put(vecValue.zf);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        lightData.flip();
        lightDataSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, lightDataSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, lightData, GL_STATIC_DRAW);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, lightDataSSBO);
        memFree(lightData);

        LongBuffer shadowmapHandles = MemoryUtil.memAllocLong(worldLights.size());
        for (worldLight wlight : worldLights) {
            shadowmapHandles.put(wlight.shadowmapTexHandle);
        }
        shadowmapHandles.flip();
        shadowmapHandleSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, shadowmapHandleSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, shadowmapHandles, GL_STATIC_DRAW);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, shadowmapHandleSSBO);
        memFree(shadowmapHandles);

        FloatBuffer lightSpaceMatrixBuffer = MemoryUtil.memAllocFloat(worldLights.size() * 16);
        for (int i = worldLights.size()-1; i >= 0; i--) {
            Light light = worldLights.get(i).light;
            light.lightSpaceMatrix.get(lightSpaceMatrixBuffer);
        }
        //lightSpaceMatrixBuffer.flip();
        int lightSpaceMatrixSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, lightSpaceMatrixSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, lightSpaceMatrixBuffer, GL_STATIC_DRAW);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, lightSpaceMatrixSSBO);
        memFree(lightSpaceMatrixBuffer);
    }

    public static int loadTexture(String path) {
        int textureID = 0;
        if (path.endsWith(".dds")) {
            DDSFile ddsFile;
            try {
                ddsFile = new DDSFile(path);
                textureID = glGenTextures();
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, textureID);
                for (int level = 0; level < ddsFile.getMipMapCount(); level++)
                    glCompressedTexImage2D(
                            GL_TEXTURE_2D,
                            level,
                            ddsFile.getFormat(),
                            ddsFile.getWidth(level),
                            ddsFile.getHeight(level),
                            0,
                            ddsFile.getBuffer(level)
                    );
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, ddsFile.getMipMapCount() - 1);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            textureID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureID);
            IntBuffer width = MemoryUtil.memAllocInt(1);
            IntBuffer height = MemoryUtil.memAllocInt(1);
            IntBuffer channels = MemoryUtil.memAllocInt(1);
            ByteBuffer image;
            image = stbi_load(path, width, height, channels, 4);
            if (image == null) {
                System.err.println("could not load image " + path);
                image = stbi_load("local resources\\null.png", width, height, channels, 4);
                assert image != null;
            }
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(0), height.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
            glTextureParameteri(textureID, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glGenerateMipmap(GL_TEXTURE_2D);

            float maxAniso = glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY, maxAniso);

            stbi_image_free(image);
            MemoryUtil.memFree(width);
            MemoryUtil.memFree(height);
            MemoryUtil.memFree(channels);
        }
        return textureID;
    }

    public void addObject(String filePath, Vec scale, Vec translation, Vec rotation, String identifier) {
        worldObject newWorldObject = new worldObject();
        newWorldObject.identifer = identifier;
        Obj newObj = new Obj(filePath, scale, translation, rotation);
        System.out.println("Parsed object: " + newWorldObject.identifer);
        newWorldObject.object = newObj;
        int lastNumMats = worldMaterials.size();
        worldMaterials.addAll(newObj.mtllib);
        for (String texPath : newObj.texturePaths) {
            if(!texturePaths.contains(texPath)) {
                texturePaths.add(texPath);
            }
        }
        FloatBuffer verticesBuffer = MemoryUtil.memAllocFloat(45*newObj.triangles.size());

        int totalTriangles = newObj.triangles.size();
        int progressInterval = Math.max(totalTriangles / 10, 1); // Update every 10% or at least every 1 triangle
        for (int i = 0; i < totalTriangles; i++) {
            Triangle triangle = newObj.triangles.get(i);
            newWorldObject.triCount++;
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
        verticesBuffer.flip();
        newWorldObject.VAO = glGenVertexArrays();
        glBindVertexArray(newWorldObject.VAO);
        newWorldObject.VBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, newWorldObject.VBO);
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
        worldObjects.add(newWorldObject);
    }
    private void selectNode(gLTF.Node node) {
        if (!(selectedNode == null)) selectedNode.toggleOutline();
        selectedNode = node;
        selectedNode.toggleOutline();
        Matrix4f matrix = selectedNode.transform.get(0);

// Step 1: Extract translation
        selectedL = new Vec(matrix.m30(), matrix.m31(), matrix.m32());

// Step 2: Extract scale FIRST
        Vec scaleX = new Vec(matrix.m00(), matrix.m01(), matrix.m02());
        Vec scaleY = new Vec(matrix.m10(), matrix.m11(), matrix.m12());
        Vec scaleZ = new Vec(matrix.m20(), matrix.m21(), matrix.m22());

        selectedS = new Vec(scaleX.magnitude(), scaleY.magnitude(), scaleZ.magnitude());
        selectedS.updateFloats();

// Step 3: Extract rotation AFTER scaling is removed
        Matrix3f rotMat = new Matrix3f(
                matrix.m00() / selectedS.xF, matrix.m01() / selectedS.xF, matrix.m02() / selectedS.xF,
                matrix.m10() / selectedS.yF, matrix.m11() / selectedS.yF, matrix.m12() / selectedS.yF,
                matrix.m20() / selectedS.zF, matrix.m21() / selectedS.zF, matrix.m22() / selectedS.zF
        );

// Step 4: Convert to quaternion and extract Euler angles
        Quaternionf rotationQuat = new Quaternionf();
        rotationQuat.setFromNormalized(rotMat);

        Vector3f eulerRad = new Vector3f();
        rotationQuat.getEulerAnglesXYZ(eulerRad);
        selectedR = new Vec(
                (float) Math.toDegrees(eulerRad.x),
                (float) Math.toDegrees(eulerRad.y),
                (float) Math.toDegrees(eulerRad.z)
        );
        selectedR.updateFloats();

        selectedL.updateFloats();
        selectedS.updateFloats();
        ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(0)).value = selectedL.xF;
        ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(1)).value = selectedL.yF;
        ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(2)).value = selectedL.zF;
        ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(3)).value = selectedR.xF;
        ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(4)).value = selectedR.yF;
        ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(5)).value = selectedR.zF;
        ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(6)).value = selectedS.xF;
        ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(7)).value = selectedS.yF;
        ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(8)).value = selectedS.zF;


    }
    public void deselect() {
        selectedNode.toggleOutline();
        selectedNode = null;
    }
    private GUI.GUIObject addNodeGUI(gLTF.Node node, float startHeight, Vec lastCol, float h) {
        GUI.GUIObject nodeObject = new GUI.GUIObject(new Vec(0, startHeight), new Vec(1), new Vec(0, -50), new Vec(1, 100));
        nodeObject.addElement(new GUI.GUIQuad(new Vec(lastCol.mult(0.95))));
        nodeObject.addElement(new GUI.GUILabel(new Vec(0.05, 0.35), node.name, 0.5f, new Vec(1).sub(lastCol.mult(0.95))));

        List<Runnable> nodesToggle = new ArrayList<>(); nodesToggle.add(() -> changeScroller(h -(float) (0.1 * node.children.size()))); nodesToggle.add(nodeObject::toggleChildren);
        nodeObject.addElement(new GUI.GUIButton(new Vec(0.3, 0.1), new Vec(0.3, 0.8), new GUI.GUILabel(new Vec(0.05, 0.35), "expand/collapse", 0.3f, new Vec(1).sub(lastCol.mult(0.95))), new GUI.GUIQuad(new Vec(lastCol.mult(1-0.95))), nodesToggle, false, false));
        nodeObject.addElement(new GUI.GUIButton(new Vec(0.65, 0.1), new Vec(0.3, 0.8), new GUI.GUILabel(new Vec(0.05, 0.35), "Select", 0.5f, new Vec(1).sub(lastCol.mult(0.95))), new GUI.GUIQuad(new Vec(lastCol.mult(1-0.95))), () -> selectNode(node), false, false));

        GUI.GUIObject nodeContainer = new GUI.GUIObject(new Vec(0), new Vec(1), new Vec(-100), new Vec(200));
        nodeObject.addChild(nodeContainer);
        nodeObject.toggleChildren();

        int cn = 1;
        for (gLTF.Node child : node.children) {
            nodeContainer.addChild(addNodeGUI(child, -cn, new Vec((Math.sin(cn*0.1)+1)*0.5), h));
            cn++;
        }
        return nodeObject;
    }
    private void changeScroller(float newBottom) {
        GUI.GUIObject sceneElements = GUI.objects.get(1).children.get(0).children.get(0);
        GUI.GUIScroller scroller = ((GUI.GUIScroller) GUI.objects.get(1).elements.get(4));
        float defaultH = (float) (0.85 - (0.15 * sceneElements.children.size()));
        float defaultTotal = (1f - defaultH) + 0.05f;
        if (scroller.totalGUI + 1.05f == newBottom) {
            scroller.setTotalGUI(defaultTotal);
            //System.out.println("tried to change back");
        } else {
            scroller.setTotalGUI((1f - newBottom*0.2f) + 0.05f);
            //System.out.println("didnt try to change back" + scroller.barSize);
        }
    }
    public void addGLTF(gLTF object) {
        if (!object.Scenes.isEmpty()) {
            GUI.GUIObject sceneElements = GUI.objects.get(1).children.get(0).children.get(0);
            float h = (float) (0.85 - (0.15 * sceneElements.children.size()));
            GUI.GUIObject gLTFobj = new GUI.GUIObject(new Vec(0.05, h), new Vec(0.9, 0.1), new Vec(0, -10), new Vec(1, 20));
            ((GUI.GUIScroller) GUI.objects.get(1).elements.get(4)).setTotalGUI((1f - h) + 0.05f); // absTop - bottom + buffer space
            sceneElements.addChild(gLTFobj);
            GUI.GUIQuad quad = new GUI.GUIQuad(new Vec(0.2));
            GUI.GUIQuad quad1 = new GUI.GUIQuad(new Vec(0.1));
            GUI.GUILabel text = new GUI.GUILabel(new Vec(0.05, 0.35), object.name, 0.5f, new Vec(1));
            GUI.GUILabel showText = new GUI.GUILabel(new Vec(0.05, 0.25), "Toggle hidden", 0.4f, new Vec(1));
            GUI.GUILabel expandText = new GUI.GUILabel(new Vec(0.05, 0.25), "Expand/Collapse", 0.4f, new Vec(1));
            GUI.GUIButton show = new GUI.GUIButton(new Vec(0.3, 0.1), new Vec(0.3, 0.8), showText, quad1, object::toggleHidden, false, false);
            gLTFobj.addElement(quad);
            gLTFobj.addElement(text);
            gLTFobj.addElement(show);

            List<Runnable> scenesToggle = new ArrayList<>();
            scenesToggle.add(() -> changeScroller(h - (float) (0.1 * object.Scenes.size())));
            int c = 1;
            for (gLTF.Scene scene : object.Scenes) {
                GUI.GUIObject sceneObject = new GUI.GUIObject(new Vec(0, -c), new Vec(1, 1), new Vec(0, -50), new Vec(1, 100));
                sceneObject.addElement(quad1);
                sceneObject.addElement(new GUI.GUILabel(new Vec(0.05, 0.35), scene.name, 0.5f, new Vec(1)));
                gLTFobj.addChild(sceneObject);
                sceneObject.toggleShow();
                scenesToggle.add(sceneObject::toggleShow);
                List<Runnable> nodesToggle = new ArrayList<>(); nodesToggle.add(() -> changeScroller(h -(float) (0.1 * scene.nodes.size()))); nodesToggle.add(sceneObject::toggleChildren);
                sceneObject.addElement(new GUI.GUIButton(new Vec(0.3, 0.1), new Vec(0.3, 0.8), expandText, quad, nodesToggle, false, false));

                GUI.GUIObject nodeContainer = new GUI.GUIObject(new Vec(0), new Vec(1), new Vec(-100), new Vec(200));
                sceneObject.addChild(nodeContainer);
                sceneObject.toggleChildren();

                int cn = 1;
                for (gLTF.Node node : scene.nodes) {
                    nodeContainer.addChild(addNodeGUI(node, -cn, new Vec((Math.sin(cn*0.1)+1)*0.5), h));
                    cn++;
                }
                c++;
            }
            GUI.GUIButton expand = new GUI.GUIButton(new Vec(0.65, 0.1), new Vec(0.3, 0.8), expandText, quad1, scenesToggle, false, false);
            gLTFobj.addElement(expand);


            worldGLTFs.add(object);
            worldMaterials.addAll(object.mtllib);
            int lastNumMats = worldMaterials.size();
            worldMaterials.addAll(object.mtllib);
            int counter = 0;
            int numTextures = object.texturePaths.size();
            for (String texPath : object.texturePaths) {
                System.out.print("\rParsing texture " + counter + "/" + numTextures + " " + texPath);
                if (!texturePaths.contains(texPath)) {
                    texturePaths.add(texPath);
                    int texture = loadTexture(texPath);
                    long handle = glGetTextureHandleARB(texture);
                    glMakeTextureHandleResidentARB(handle);
                    worldTextureHandles.add(handle);
                }
                counter++;
            }
            for (gLTF.Mesh mesh : object.Meshes) {
                mesh.initialize(lastNumMats);
            }
            addLightsForScene(object, 0, 0.5f);
            System.out.print("\rloaded object");
            System.out.println();
        }
    }
    public void addLightsForObject(worldObject obj, float minDist) {
        Obj object = obj.object;
        List<Material> mats = object.mtllib;
        List<Light> newLights = new ArrayList<>();
        addLightsForTriangleSet(minDist, mats, newLights, object.triangles, new Matrix4f().identity());
        for (Light l : newLights) {
            addLight(l);
        }
    }
    public void addLightsForScene(gLTF object, int scene, float minDist) {
        gLTF.Scene operatingScene = object.Scenes.get(scene);
        List<Light> newLights = new ArrayList<>();
        for (gLTF.Node node : operatingScene.nodes) {
            addLightsForNode(node, minDist, object.mtllib, newLights, new Matrix4f().identity());
        }
        for (Light l : newLights) {
            addLight(l);
        }
    }
    private void addLightsForNode(gLTF.Node node, float minDist, List<Material> mats, List<Light> newLights, Matrix4f parentTransform) {
        for (Matrix4f relativeTransform : node.transform) {
            Matrix4f worldTransform = parentTransform.mul(relativeTransform, new Matrix4f());
        if (node.mesh == null) {
            for (gLTF.Node child : node.children) {
                addLightsForNode(child, minDist, mats, newLights, worldTransform);
            }
        } else {
            addLightsForTriangleSet(minDist, mats, newLights, node.mesh.triangles, worldTransform);
        }
        }
    }
    private void addLightsForTriangleSet(float minDist, List<Material> mats, List<Light> newLights, List<Triangle> tris, Matrix4f parentTransform) {
        List<Vec> emissiveVertices = new ArrayList<>();
        List<Triangle> emissiveTriangles = new ArrayList<>();
        List<Vec> emissionColors = new ArrayList<>();
        List<Double> emissiveStrengths = new ArrayList<>();
        for (Triangle tri : tris) {
            Material thisMtl = mats.get(tri.material);
            if (thisMtl.Ke.magnitude() > 0) {
                Vector4f pos4f = parentTransform.transform(new Vector4f(tri.v1.toVec3f(), 1));
                Vec pos = new Vec(pos4f.x, pos4f.y, pos4f.z);
                boolean willAdd = true;
                for (Light l : newLights) {
                    if (l.position.dist(pos) < minDist) {
                        willAdd = false;
                        break;
                    }
                }
                if (willAdd) {
                    double E = thisMtl.emissiveStrength;
                    double[] atten = computeAttenuation(E);
                    Light newLight1 = new Light(0);
                    newLight1.setProperty("position", pos);
                    newLight1.setProperty("constantAttenuation", 1);
                    newLight1.setProperty("linearAttenuation", 0.5);
                    newLight1.setProperty("quadraticAttenuation", 0.3/((E*E)/100));
                    newLight1.setProperty("ambient", new Vec(0.1, 0.1, 0.1));
                    newLight1.setProperty("diffuse", new Vec(thisMtl.Ke).mult(E*0.1));
                    newLight1.setProperty("specular", new Vec(1, 1, 1));

                    newLights.add(newLight1);
                }
            }
        }
    }
    public static double[] computeAttenuation(double range) {
        double intensityThreshold = 0.01; // Light is nearly gone at max range

        // Solve for K_l and K_q such that attenuation is ~0.01 at max range
        double K_q = intensityThreshold / (range * range);  // Quadratic term
        double K_l = (1.0 - K_q * range * range) / range;   // Linear term

        return new double[]{K_l, K_q};
    }

    public void addLight(Light light) {
        worldLight newLight = new worldLight();
        newLight.light = light;
        newLight.transform = new Matrix4f().identity();

        if (light.type == 1) {
            newLight.shadowmapFramebuffer = glGenFramebuffers();
            newLight.shadowmapTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, newLight.shadowmapTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, Run.SHADOW_RES, Run.SHADOW_RES, 0, GL_DEPTH_COMPONENT, GL_FLOAT, MemoryUtil.NULL);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
            float[] borderColor = {1.0f, 1.0f, 1.0f, 1.0f};
            glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, borderColor);

            glBindFramebuffer(GL_FRAMEBUFFER, newLight.shadowmapFramebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, newLight.shadowmapTexture, 0);
            glDrawBuffer(GL_NONE);
            glReadBuffer(GL_NONE);
            newLight.shadowmapTexHandle = glGetTextureHandleARB(newLight.shadowmapTexture);
            glMakeTextureHandleResidentARB(newLight.shadowmapTexture);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }

        worldLights.add(newLight);
    }

    public void modifyLight() {

    }

    public static class worldObject {
        String identifer;
        Obj object;
        int triCount = 0;
        int VBO;
        int VAO;
        List<Matrix4f> transforms = new ArrayList<>();
        int numInstances = 0;
        List<Boolean> outlined = new ArrayList<>();

        public void newInstance(Vec scale, Vec translation, Vec rotation) {
            rotation.updateFloats();
            transforms.add(new Matrix4f().identity()
                    .translate(translation.toVec3f())
                    .rotateX(rotation.xF)
                    .rotateY(rotation.yF)
                    .rotateZ(rotation.zF)
                    .scale(scale.toVec3f()));
            outlined.add(false);
            numInstances++;
        }
        public void newInstance() {
            transforms.add(new Matrix4f().identity());
            outlined.add(false);
            numInstances++;
        }

        public void removeInstance(int instanceID) {
            transforms.remove(instanceID);
            outlined.remove(instanceID);
            numInstances--;
        }

        public void stepInstance(int instanceID, Vec scale, Vec translation, Vec rotation) {
            rotation.updateFloats();
            transforms.get(instanceID).mul(new Matrix4f().identity()
                    .translate(translation.toVec3f())
                    .rotateX(rotation.xF)
                    .rotateY(rotation.yF)
                    .rotateZ(rotation.zF)
                    .scale(scale.toVec3f()));
        }
        public void setInstance(int instanceID, Vec scale, Vec translation, Vec rotation) {
            rotation.updateFloats();
            transforms.set(instanceID, new Matrix4f().identity()
                    .translate(translation.toVec3f())
                    .rotateX(rotation.xF)
                    .rotateY(rotation.yF)
                    .rotateZ(rotation.zF)
                    .scale(scale.toVec3f()));
        }
        public void toggleInstanceOutline(int instanceID) {
            outlined.set(instanceID, !outlined.get(instanceID));
        }
    }
    public static class worldLight {
        String identifer;
        int shadowmapFramebuffer;
        int shadowmapTexture;
        long shadowmapTexHandle;
        Matrix4f transform;
        Light light;

        public worldLight() {
            identifer = "";
            shadowmapFramebuffer = 0;
            shadowmapTexture = 0;
            shadowmapTexHandle = 0;
            transform = new Matrix4f().identity();
        }
    }

    private float getMaterialFieldCount() {
        float materialPropertyCount = 0;
        for (Field field : Material.class.getDeclaredFields()) {
            if (!(field.getName().equals("name") || field.getName().equals("texturesDirectory"))) {
                field.setAccessible(true);
                try {
                    Class<?> type = field.getType();
                    if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
                        materialPropertyCount++;
                    } else if (type == Vec.class) {
                        materialPropertyCount+=3;
                    } else if (type == String.class) {
                        materialPropertyCount++;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return materialPropertyCount;
    }
}
