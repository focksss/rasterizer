package Main;

import Datatypes.Shader;
import ModelHandler.gLTF;
import Datatypes.Vec;
import ModelHandler.Light;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import java.io.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import static Main.World.*;
import static Util.MathUtil.*;
import static Util.textureUtil.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.ARBFramebufferSRGB.GL_FRAMEBUFFER_SRGB;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL45.*;
import static org.lwjgl.system.MemoryUtil.*;

import javax.swing.*;

public class Run {
    public static int
            lightingFBO, lightingTex, lightingRBO,
            gFBO, gPosition, gNormal, gMaterial, gTexCoord, gViewPosition, gViewNormal, gDepth,
            SSAOfbo, SSAOblurFBO, SSAOtex, SSAOblurTex, SSAOnoiseTex,
            PBbloomFBO, SSRfbo,
            postProcessingFBO, postProcessingTex, SSRtex,
            skyboxTex, skyboxCubemap, skyboxIrradiance, skyboxPrefiltered, BRDFintegrationMap,
            gaussianBlurTexOneHalf, gaussianBlurTex;
    private static int[] gaussianBlurFBO;
    private static int[] bloomMipTextures;
    private static Vec[] SSAOkernal;
    public static Shader
            geometryShader, screenShader, skyboxShader, shadowShader, lightingShader, SSAOshader, blurShader, postProcessingShader, gaussianBlurShader, debugShader, upsampleShader, downsampleShader, outlineShader, SSRshader;
    public static long window, FPS = 240;
    static boolean isFullscreen = false; static int[] windowX = new int[1]; static int[] windowY = new int[1]; static int windowedWidth = 1920; static int windowedHeight = 1080;
    static GLFWVidMode vidMode;
    public static Vec camPos, camRot;
    public static float
            EXPOSURE, GAMMA, SSAOradius, SSAObias, bloomRadius, bloomIntensity, bloomThreshold,
            FOV;
    public static boolean doSSAO = true; static boolean CAP_FPS = true; public static boolean borderless_fullscreen = true; public static boolean doBloom = true; public static boolean doShadows = true; public static boolean doSSR = false;

    public static long startTime = System.nanoTime();
    static float currFPS = 0;
    public static long time = 0;

    public static int SHADOW_RES = 8192;
    public static int WIDTH = 1920;
    public static int HEIGHT = 1080;
    public static float aspectRatio = (float) WIDTH / HEIGHT;
    public static float nearPlane = 0.001f, farPlane = 10000.0f;
    public static Matrix4f projectionMatrix;
    public static Matrix4f viewMatrix;
    public static String skyboxPath = "local_resources\\symmetrical_garden_4k.hdr";
    public static String BRDFintegrationMapPath = "local_resources\\brdf.png";
    private static final String shaderPath = "C:\\Graphics\\rasterizer\\src\\shaders\\";

    public static World world;
    public static Controller controller;
    public static GUI gui;

    private static final List<String> lines = new ArrayList<>();
    private static JTextArea textArea;

    static List<Plane> frustumPlanes;
    static boolean doRescale = false;

    public static void main(String[] args) throws IOException {
        loadSave();
        init();
        world = new World();
        gui = new GUI();
        compileShaders();
        controller = new Controller(camPos, camRot, window);
        runEngine();
        //test();
        updateSave();

        //Util.PBRtextureSeparator.splitPrPm_GB("C:/Graphics/assets/bistro2/textures");
        //Util.PBRtextureSeparator.processMaterialFile("C:/Graphics/assets/bistro2/bistro.mtl");
    }
    private static void test() {
        long frameTime = 1000000000 / FPS; // Nanoseconds per frame
        System.out.println("Initiation complete");
        int frames = 0;
        long lastTime = System.nanoTime();

        while (!glfwWindowShouldClose(window)) {
            long startTime = System.nanoTime();

            glfwPollEvents();
            checkWindowSize();
            controller.doControls(time);
            if (doRescale) {scaleToWindow(); doRescale = false;}
            update();
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            doGUI();
            //System.out.println(Controller.scrollY);
            glfwSwapBuffers(window);

            frames++;
            double thisFrameTime = (startTime - lastTime) / 1_000_000_000.0;
            if (thisFrameTime >= 0.5) {
                currFPS = (float) (frames / thisFrameTime);
                frames = 0;
                lastTime = startTime;
            }
            long elapsedTime = System.nanoTime() - startTime;
            long sleepTime = frameTime - elapsedTime;
            if (sleepTime > 0 && CAP_FPS) {
                try {
                    Thread.sleep(sleepTime / 1000000, (int) (sleepTime % 1000000)); // Convert to milliseconds & nanoseconds
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        glfwTerminate();
    }
    public static void runEngine() {
        glActiveTexture(GL_TEXTURE0);
        skyboxTex = World.loadTexture(skyboxPath);
        BRDFintegrationMap = World.loadTexture(BRDFintegrationMapPath);
        skyboxCubemap = equirectangularToCubemap(skyboxTex);
        skyboxIrradiance = convoluteCubemap(skyboxCubemap);
        skyboxPrefiltered = preFilterCubemap(skyboxCubemap, 5);
        System.out.println("Initiation complete");

        createWorld();

        int frames = 0;
        long lastTime = System.nanoTime();

        while (!glfwWindowShouldClose(window)) {
            long frameTime = 1000000000 / FPS; // Nanoseconds per frame
            long startTime = System.nanoTime();

            doFrame();

            frames++;
            long thisFrameTime = startTime - lastTime; // Time taken for this frame
            if (thisFrameTime >= 500000000) { // Update FPS every 0.5 seconds
                currFPS = (float) (frames / (thisFrameTime / 1000000000.0));
                frames = 0;
                lastTime = startTime;
            }

            long elapsedTime = System.nanoTime() - startTime;
            long sleepTime = frameTime - elapsedTime;
            if (sleepTime > 0 && CAP_FPS) {
                try {
                    Thread.sleep(sleepTime / 1000000, (int) (sleepTime % 1000000)); // Convert to milliseconds & nanoseconds
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                // If the frame took longer than the target, skip sleep
                // to avoid dropping too many frames and maintaining the cap
            }
        }
        glfwTerminate();
    }
    private static void doFrame() {
        glfwPollEvents();

        checkWindowSize();
        controller.doControls(time);
        if (doRescale) {scaleToWindow(); doRescale = false;}
        update();
        updateWorldObjectInstances();
        render();
        doOutlines();
        //equirectangularToCubemapDemo(skyboxTex);
        doGUI();

        glfwSwapBuffers(window);
    }
    public static void doGUI() {
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        ((GUI.GUILabel) GUI.objects.get(0).children.get(1).elements.get(1))
                .setText("FPS: " + (int) currFPS);
        ((GUI.GUILabel) GUI.objects.get(0).children.get(2).elements.get(1))
                .setText("Position: " + String.format("%.2f %.2f %.2f", controller.cameraPos.x, controller.cameraPos.y, controller.cameraPos.z));
        ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(3)).label
                .setText("Exposure: " + EXPOSURE);
        ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(4)).label
                .setText("Gamma: " + GAMMA);
        ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(5)).label
                .setText("FOV: " + FOV);
        ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(6)).label
                .setText("SSAO radius: " + SSAOradius);
        ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(7)).label
                .setText("SSAO bias: " + SSAObias);
        ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(8)).label
                .setText("Bloom radius: " + bloomRadius);
        ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(9)).label
                .setText("Bloom intensity: " + bloomIntensity);
        ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(10)).label
                .setText("Bloom threshold: " + bloomThreshold);
        ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(11)).label
                .setText("Max FPS: " + FPS);
        //GUI.objects.get(0).children.get(0).children.get(0).position.y = ((GUI.GUIScroller) GUI.objects.get(0).children.get(0).elements.get(1)).value;
        //GUI.objects.get(0).children.get(0).children.get(0).position.y = 0.25;
        if (Controller.escaped) GUI.renderGUI();
        EXPOSURE = ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(3)).value;
        GAMMA = ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(4)).value;
        FOV = ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(5)).value;
        SSAOradius = ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(6)).value;
        SSAObias = ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(7)).value;
        bloomRadius = ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(8)).value;
        bloomIntensity = ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(9)).value;
        bloomThreshold = ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(10)).value;
        FPS = (long) ((GUI.GUISlider) GUI.objects.get(0).children.get(0).children.get(0).elements.get(11)).value;

        doSSAO = ((GUI.GUISwitch) GUI.objects.get(0).children.get(3).children.get(0).elements.get(0)).toggle;
        borderless_fullscreen = ((GUI.GUISwitch) GUI.objects.get(0).children.get(3).children.get(0).elements.get(1)).toggle;
        doBloom = ((GUI.GUISwitch) GUI.objects.get(0).children.get(3).children.get(0).elements.get(2)).toggle;
        doShadows = ((GUI.GUISwitch) GUI.objects.get(0).children.get(3).children.get(0).elements.get(3)).toggle;
        doSSR = ((GUI.GUISwitch) GUI.objects.get(0).children.get(3).children.get(0).elements.get(4)).toggle;

        if (!(selectedNode == null)) {
            selectedL.x = ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(0)).value;
            selectedL.y = ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(1)).value;
            selectedL.z = ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(2)).value;
            selectedR.x = ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(3)).value;
            selectedR.y = ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(4)).value;
            selectedR.z = ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(5)).value;
            selectedS.x = ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(6)).value;
            selectedS.y = ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(7)).value;
            selectedS.z = ((GUI.GUISlider) GUI.objects.get(1).children.get(1).children.get(0).elements.get(8)).value;

            selectedL.updateFloats();
            selectedR.updateFloats();
            selectedS.updateFloats();
            Matrix4f reconstructedMatrix = new Matrix4f();
            Quaternionf userRotQuat = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(selectedR.xF),
                    (float) Math.toRadians(selectedR.yF),
                    (float) Math.toRadians(selectedR.zF)
            );
            reconstructedMatrix.setTranslation(selectedL.toVec3f());
            reconstructedMatrix.rotate(userRotQuat);
            reconstructedMatrix.scale(selectedS.toVec3f());

            World.selectedNode.transform.set(0, reconstructedMatrix);
        }

    }
    public static void doOutlines() {
        outlineShader.setUniform("viewMatrix", viewMatrix);
        outlineShader.setUniform("projectionMatrix", projectionMatrix);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glViewport(0, 0, WIDTH, HEIGHT);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        drawScene(outlineShader, false, true, false);
    }

    public static void createWorld() {
        Light newLight = new Light(1);
        newLight.setProperty("direction", new Vec(.15, -.75, -.5));
        newLight.setProperty("position", new Vec(0.1, 1, 0.05).mult(50));
        newLight.setProperty("cutoff", 0.8);
        newLight.setProperty("innerCutoff", 0.9);
        newLight.setProperty("constantAttenuation", 1);
        newLight.setProperty("linearAttenuation", 0.09);
        newLight.setProperty("quadraticAttenuation", 0.032);
        newLight.setProperty("ambient", new Vec(0.1, 0.1, 0.1));
        newLight.setProperty("diffuse", new Vec(0.98, 0.84, 0.64).mult(5));
        newLight.setProperty("specular", new Vec(1, 1, 1));
        world.addLight(newLight);

        world.updateWorld();
    }
    public static void render() {
        if (doShadows) generateShadowMaps();
        //geometry pass
        glBindFramebuffer(GL_FRAMEBUFFER, gFBO);
        glViewport(0, 0, WIDTH, HEIGHT);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        glClearColor(0.0f, 0.0f, 0.0f, -1);
        glClearTexImage(gPosition, 0, GL_RGBA, GL_FLOAT, new float[]{Float.POSITIVE_INFINITY,0,0,0});
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        drawScene(geometryShader, false, false, false);
        glDisable(GL_CULL_FACE);
        if (doSSAO) {
            //ssao generation pass
            glBindFramebuffer(GL_FRAMEBUFFER, SSAOfbo);
            glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
            for (int i = 0; i < 64; i++) {
                SSAOshader.setUniform("samples[" + i + "]", SSAOkernal[i]);
            }
            SSAOshader.setUniform("projection", projectionMatrix);
            SSAOshader.setUniformTexture("gViewPosition", gViewPosition, 0);
            SSAOshader.setUniformTexture("gNormal", gViewNormal, 1);
            SSAOshader.setUniformTexture("texNoise", SSAOnoiseTex, 2);
            glDrawArrays(GL_TRIANGLES, 0, 6);

            //ssao blur pass
            glBindFramebuffer(GL_FRAMEBUFFER, SSAOblurFBO);
            glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
            blurShader.setUniformTexture("blurInput", SSAOtex, 0);
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }
        if (doSSR) {
            //SSR pass
            glBindFramebuffer(GL_FRAMEBUFFER, SSRfbo);
            glViewport(0, 0, WIDTH, HEIGHT);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            SSRshader.setUniformTexture("gViewPosition", gViewPosition, 0);
            SSRshader.setUniformTexture("gViewNormal", gViewNormal, 1);
            SSRshader.setUniform("projectionMatrix", projectionMatrix);
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }
        //lighting pass
        glBindFramebuffer(GL_FRAMEBUFFER, lightingFBO);
        glViewport(0, 0, WIDTH, HEIGHT);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        skyboxShader.setUniform("projection", projectionMatrix);
        skyboxShader.setUniform("view", viewMatrix);
        skyboxShader.setUniformCubemap("environmentMap", skyboxCubemap, 1);
        renderCube();
        lightingShader.setUniform("shadows", doShadows);
        lightingShader.setUniform("SSR", doSSR);
        lightingShader.setUniformTexture("gPosition", gPosition, 0);
        lightingShader.setUniformTexture("gNormal", gNormal, 1);
        lightingShader.setUniformTexture("gMaterial", gMaterial, 2);
        lightingShader.setUniformTexture("gTexCoord", gTexCoord, 3);
        lightingShader.setUniformTexture("shadowmaps", world.worldLights.get(0).shadowmapTexture, 4);
        lightingShader.setUniformTexture("SSAOtex", SSAOblurTex, 5);
        lightingShader.setUniformTexture("gViewPostion", gViewPosition, 6);
        lightingShader.setUniformCubemap("irradianceMap", skyboxIrradiance, 7);
        lightingShader.setUniformCubemap("prefilterMap", skyboxPrefiltered, 8);
        lightingShader.setUniformTexture("BRDFintegrationMap", BRDFintegrationMap, 9);
        lightingShader.setUniformTexture("SSRtex", SSRtex, 10);
        lightingShader.setUniform("FOV", FOV);
        glEnable(GL_FRAMEBUFFER_SRGB);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glDisable(GL_FRAMEBUFFER_SRGB);

        if (doBloom) {
            //bloom pass
            glBindFramebuffer(GL_FRAMEBUFFER, PBbloomFBO);
            glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
            //set init write
            downsampleShader.setUniformTexture("srcTexture", lightingTex, 0);
            for (int i = 0; i < 6; i++) {
                int resDiv = (int) Math.pow(2, i);
                glViewport(0, 0, WIDTH / resDiv, HEIGHT / resDiv);
                downsampleShader.setUniform("srcResolution", new Vec(WIDTH / resDiv, HEIGHT / resDiv));
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bloomMipTextures[i], 0);
                glDrawArrays(GL_TRIANGLES, 0, 6);
                //next write
                downsampleShader.setUniformTexture("srcTexture", bloomMipTextures[i], 0);
            }
            upsampleShader.setUniform("filterRadius", bloomRadius);
            for (int i = 5; i > 0; i--) {
                int resDiv = (int) Math.pow(2, i - 1);
                glViewport(0, 0, WIDTH / resDiv, HEIGHT / resDiv);
                //set read
                upsampleShader.setUniformTexture("srcTexture", bloomMipTextures[i], 0);
                //set write
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bloomMipTextures[i - 1], 0);
                glDrawArrays(GL_TRIANGLES, 0, 6);
            }
        }

        //post process
        glBindFramebuffer(GL_FRAMEBUFFER, postProcessingFBO);
        glViewport(0, 0, WIDTH, HEIGHT);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        postProcessingShader.setUniformTexture("postProcessingBuffer", lightingTex, 0);
        postProcessingShader.setUniformTexture("bloomTex", bloomMipTextures[0], 1);
        postProcessingShader.setUniform("bloomIntensity", bloomIntensity);
        postProcessingShader.setUniform("doBloom", doBloom);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        renderToQuad(postProcessingTex);
    }

    public static void updateWorldObjectInstances() {
/*
        world.worldObjects.get(0).setInstance(0,
                new Vec(1 + (Math.sin(time*1e-9) * 0.25), 1 + (Math.cos(1.3*time*1e-9 + 1) * 0.25), 1 + (Math.sin(0.8 *time*1e-9 + 13) * 0.25)),
                new Vec(0,6,0),
                new Vec((Math.sin(time*1e-9) * 0.25), (Math.cos(1.3*time*1e-9 + 1) * 0.25),  (Math.sin(0.8 *time*1e-9 + 13) * 0.25)));
*/
    }
    public static void update() {
        time = (System.nanoTime() - startTime);

        viewMatrix = new Matrix4f();
        viewMatrix.identity()
                .rotateX(controller.cameraRot.xF).rotateY(-controller.cameraRot.yF).rotateZ(-controller.cameraRot.zF)
                .translate(new Vector3f(controller.cameraPos.xF, -controller.cameraPos.yF, controller.cameraPos.zF));
        geometryShader.setUniform("viewMatrix", viewMatrix);

        projectionMatrix = new Matrix4f();
        projectionMatrix.identity()
                .setPerspective((float) Math.toRadians(FOV), aspectRatio, nearPlane, farPlane, false);
        geometryShader.setUniform("projectionMatrix", projectionMatrix);

        frustumPlanes = getFrustumPlanes();

        lightingShader.setUniform("camPos", controller.cameraPos);
        lightingShader.setUniform("camRot", controller.cameraRot);

        postProcessingShader.setUniform("exposure", EXPOSURE);
        postProcessingShader.setUniform("gamma", GAMMA);
        lightingShader.setUniform("width", WIDTH);
        lightingShader.setUniform("height", HEIGHT);
        lightingShader.setUniform("SSAO", doSSAO);
        SSAOshader.setUniform("width", WIDTH);
        SSAOshader.setUniform("height", HEIGHT);
        SSAOshader.setUniform("radius", SSAOradius);
        SSAOshader.setUniform("bias", SSAObias);
    }

    public static void init() {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_SAMPLES, 4);
        long primaryMonitor = glfwGetPrimaryMonitor();
        vidMode = glfwGetVideoMode(primaryMonitor);
        window = glfwCreateWindow(WIDTH, HEIGHT, "rasterizer", 0, NULL);
        glfwMakeContextCurrent(window);
        GL.createCapabilities();

        IntBuffer width = MemoryUtil.memAllocInt(1);
        IntBuffer height = MemoryUtil.memAllocInt(1);
        glfwGetWindowSize(window, width, height);
        WIDTH = width.get(0);
        HEIGHT = height.get(0);
        MemoryUtil.memFree(width);
        MemoryUtil.memFree(height);

        scaleToWindow();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        //glEnable(GL_BLEND);
        glEnable(GL_MULTISAMPLE);
        glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }
    public static void compileShaders() {
        skyboxShader = new Shader(shaderPath + "\\skyboxShader\\skyboxFrag.glsl", shaderPath + "\\skyboxShader\\skybox.vert");
        geometryShader = new Shader(shaderPath + "\\geometryShader\\geometryPassFrag.glsl", shaderPath + "\\geometryShader\\geometryPassVert.glsl");
        shadowShader = new Shader(shaderPath + "\\ShadowMapping\\shadowmapFrag.glsl", shaderPath + "\\ShadowMapping\\shadowmapVert.glsl");
        screenShader = new Shader(shaderPath + "\\quadShader\\quadFrag.glsl", shaderPath + "\\quadVertex.glsl");
        lightingShader = new Shader(shaderPath + "\\lightingShader\\lightingPassFrag.glsl", shaderPath + "\\quadVertex.glsl");
        postProcessingShader = new Shader(shaderPath + "\\postProcessingShader\\postProcessingShaderFrag.glsl", shaderPath + "\\quadVertex.glsl");
        blurShader = new Shader(shaderPath + "\\blurShader\\blurFrag.glsl", shaderPath + "\\quadVertex.glsl");
        SSAOshader = new Shader(shaderPath + "\\SSAOshader\\SSAOfrag.glsl", shaderPath + "\\quadVertex.glsl");
        gaussianBlurShader = new Shader(shaderPath + "\\gaussianBlurShader\\gaussianBlurFrag.glsl", shaderPath + "\\quadVertex.glsl");
        debugShader = new Shader(shaderPath + "\\debugShader\\debugShader.frag", shaderPath + "\\debugShader\\debugShader.frag");
        downsampleShader = new Shader(shaderPath + "\\up_down_sampling\\downsampleFrag.glsl", shaderPath + "\\quadVertex.glsl");
        upsampleShader = new Shader(shaderPath + "\\up_down_sampling\\upsampleFrag.glsl", shaderPath + "\\quadVertex.glsl");
        GUI.textShader = new Shader("src\\shaders\\text_shader\\text_shader.frag", "src\\shaders\\text_shader\\text_shader.vert");
        GUI.backgroundShader = new Shader("src\\shaders\\GUIBackground\\GUIBackground.frag", "src\\shaders\\GUIBackground\\GUIBackground.vert");
        GUI.pointShader = new Shader("src\\shaders\\pointShader\\pointShader.frag", "src\\shaders\\pointShader\\pointShader.vert");
        GUI.lineShader = new Shader("src\\shaders\\lineShader\\line.frag", "src\\shaders\\lineShader\\line.vert");
        GUI.lineShader = new Shader("src\\shaders\\lineShader\\line.frag", "src\\shaders\\lineShader\\line.vert");
        outlineShader = new Shader("src\\shaders\\outlineShader\\outline.frag", "src\\shaders\\outlineShader\\outline.vert");
        SSRshader = new Shader("src\\shaders\\SSRshader\\SSR.frag", "src\\shaders\\quadVertex.glsl");
    }
    static void checkWindowSize() {
        IntBuffer width = MemoryUtil.memAllocInt(1);
        IntBuffer height = MemoryUtil.memAllocInt(1);
        glfwGetWindowSize(window, width, height);
        glBindTexture(GL_TEXTURE_2D, postProcessingTex);
        int widthTex = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
        int heightTex = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
//        System.out.println(width.get(0) + " " + height.get(0) + ", " + widthTex + " " + heightTex);
        if (width.get(0) != widthTex || height.get(0) != heightTex) {
            WIDTH = width.get(0);
            HEIGHT = height.get(0);
            aspectRatio = (float) WIDTH / HEIGHT;
            doRescale = true;
        }
        MemoryUtil.memFree(width);
        MemoryUtil.memFree(height);
    }
    public static void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode vidMode = glfwGetVideoMode(monitor);

        if (isFullscreen) {
            // Store window position and size
            glfwGetWindowPos(window, windowX, windowY);
            int[] width = new int[1], height = new int[1];
            glfwGetWindowSize(window, width, height);
            windowedWidth = width[0];
            windowedHeight = height[0];

            if (borderless_fullscreen) {
                glfwSetWindowAttrib(window, GLFW_DECORATED, GLFW_FALSE);
                glfwSetWindowAttrib(window, GLFW_RESIZABLE, GLFW_FALSE);
            }

            WIDTH = vidMode.width();
            HEIGHT = vidMode.height();
            glfwSetWindowSize(window, WIDTH, HEIGHT);
            glfwSetWindowPos(window, 0, 0);

        } else {
            if (borderless_fullscreen) {
                glfwSetWindowAttrib(window, GLFW_DECORATED, GLFW_TRUE);
                glfwSetWindowAttrib(window, GLFW_RESIZABLE, GLFW_TRUE);
            }

            WIDTH = windowedWidth;
            HEIGHT = windowedHeight;
            glfwSetWindowSize(window, WIDTH, HEIGHT);
            glfwSetWindowPos(window, windowX[0], windowY[0]);
        }
        glfwSwapBuffers(window);
        glfwPollEvents();
        doRescale = true;
    }
    public static void scaleToWindow() {
        lightingFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, lightingFBO);
        lightingTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, lightingTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, MemoryUtil.NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, lightingTex, 0);
        glDrawBuffers(GL_COLOR_ATTACHMENT0);
        lightingRBO = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, lightingRBO);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, WIDTH, HEIGHT);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, lightingRBO);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        gFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, gFBO);
        gPosition = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gPosition);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, gPosition, 0);
        gNormal = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gNormal);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, gNormal, 0);
        gMaterial = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gMaterial);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT2, GL_TEXTURE_2D, gMaterial, 0);
        gTexCoord = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gTexCoord);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RG32F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT3, GL_TEXTURE_2D, gTexCoord, 0);
        gViewPosition = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gViewPosition);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT4, GL_TEXTURE_2D, gViewPosition, 0);
        gViewNormal = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gViewNormal);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT5, GL_TEXTURE_2D, gViewNormal, 0);
        gDepth = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gDepth);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, WIDTH, HEIGHT, 0, GL_DEPTH_COMPONENT, GL_FLOAT, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, gDepth, 0);
        int[] gAttachments = new int[]{GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1, GL_COLOR_ATTACHMENT2, GL_COLOR_ATTACHMENT3, GL_COLOR_ATTACHMENT4, GL_COLOR_ATTACHMENT5};
        glDrawBuffers(gAttachments);
        glReadBuffer(GL_NONE);

        postProcessingFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, postProcessingFBO);
        postProcessingTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, postProcessingTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemoryUtil.NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, postProcessingTex, 0);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);

        SSAOfbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, SSAOfbo);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        SSAOtex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, SSAOtex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemoryUtil.NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, SSAOtex, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        SSAOblurFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, SSAOblurFBO);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        SSAOblurTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, SSAOblurTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemoryUtil.NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, SSAOblurTex, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        generateSSAOsampling();

        gaussianBlurFBO = new int[2];
        for (int i = 0; i < 2; i++)
        {
            gaussianBlurFBO[i] = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, gaussianBlurFBO[i]);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            if (i == 0) {gaussianBlurTexOneHalf = glGenTextures();} else {gaussianBlurTex = glGenTextures();}
            glBindTexture(GL_TEXTURE_2D, (i == 0) ? gaussianBlurTexOneHalf : gaussianBlurTex);
            glTexImage2D(
                    GL_TEXTURE_2D, 0, GL_RGBA16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, NULL
            );
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, (i == 0) ? gaussianBlurTexOneHalf : gaussianBlurTex, 0);
        }

        PBbloomFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, PBbloomFBO);
        bloomMipTextures = new int[6];
        for (int i = 0; i < 6; i++) {
            int resDiv = (int) Math.pow(2, i);
            bloomMipTextures[i] = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, bloomMipTextures[i]);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, WIDTH/resDiv, HEIGHT/resDiv, 0, GL_RGBA, GL_FLOAT, MemoryUtil.NULL);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }
        glDrawBuffers(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_NONE);

        SSRfbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, SSRfbo);
        SSRtex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, SSRtex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RG32F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, MemoryUtil.NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, SSRtex, 0);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public static void Quit() {
        glfwSetWindowShouldClose(window, true);
    }

    private static void gaussianBlur(int texture) {
        for (int i = 0; i < 2; i++) {
            glBindFramebuffer(GL_FRAMEBUFFER, gaussianBlurFBO[i]);
            glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, (i == 0) ? texture : gaussianBlurTexOneHalf);
            gaussianBlurShader.setUniform("blurInput", 0);
            gaussianBlurShader.setUniform("horizontal", i);
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }
    }

    private static void generateSSAOsampling() {
        SSAOkernal = new Vec[64];
        for (int i = 0; i < 64; i++) {
            double scale = (double) i / 64;
            scale = lerp(scale * scale, 0.1, 1);
            SSAOkernal[i] = new Vec(
                    Math.random() * 2 - 1,
                    Math.random() * 2 - 1,
                    Math.random())
                    .normalize().mult(Math.random()).mult(scale);
        }

        FloatBuffer noiseBuffer = MemoryUtil.memAllocFloat(16 * 3);
        for (int i = 0; i < 16; i++) {
            noiseBuffer.put((float) (Math.random() * 2 - 1));
            noiseBuffer.put((float) (Math.random() * 2 - 1));
            noiseBuffer.put(0f);
        }

        SSAOnoiseTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, SSAOnoiseTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 4, 4, 0, GL_RGB, GL_FLOAT, noiseBuffer);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        memFree(noiseBuffer);
    }

    private static void renderToQuad(int texture) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        screenShader.useProgram();
        glDisable(GL_DEPTH_TEST);
        glViewport(0, 0, WIDTH, HEIGHT);
        glActiveTexture(GL_TEXTURE0);

        glBindTexture(GL_TEXTURE_2D, texture);
        screenShader.setUniform("lightingTexture", 0);

        /*
        glBindTexture(GL_TEXTURE_2D, world.shadowmapArray);
        screenShader.setUniform("shadowmapArray", 0);
        */
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    private static void drawScene(Shader shader, boolean doFrustumCull, boolean onlyOutlined, boolean showBounds) {
        for (World.worldObject obj : world.worldObjects) {
            int i = 0;
            for (int j = 0; j < obj.numInstances; j++) {
                if (!onlyOutlined || obj.outlined.get(j)) {
                    shader.setUniform("objectMatrix[" + i + "]", obj.transforms.get(j));
                    i++;
                }
            }
            glBindVertexArray(obj.VAO);
            glEnableVertexAttribArray(0);
            glDrawArraysInstanced(GL_TRIANGLES, 0, obj.triCount * 3, i);
        }
        for (gLTF gltf : world.worldGLTFs) {
            if (!(gltf.Nodes.isEmpty())) {
                gLTF.Scene scene = gltf.activeScene;
                if (gltf.show) {
                    for (gLTF.Node node : scene.nodes) {
                        renderNode(node, new Matrix4f().identity(), shader, doFrustumCull, onlyOutlined, showBounds, node.outline);
                    }
                }
            }
        }
    }
    private static void renderNode(gLTF.Node node, Matrix4f parentTransform, Shader shader, boolean doFrustumCull, boolean onlyOutlined, boolean showBounds, boolean outlined) {
        if (node.show) {
            if ((!onlyOutlined) || node.outline || outlined) {
                if (!(node.mesh == null)) {
                    int i = 0;
                    for (Matrix4f relativeTransform : node.transform) {
                        Matrix4f worldTransform = parentTransform.mul(relativeTransform, new Matrix4f());
                        shader.setUniform("objectMatrix[" + i + "]", worldTransform);
                        if (showBounds) {
                            glBindVertexArray(node.mesh.boundVAO);
                        } else {
                            glBindVertexArray(node.mesh.VAO);
                        }
                        glEnableVertexAttribArray(0);
                        i++;
                    }
                    glDrawArraysInstanced(GL_TRIANGLES, 0, node.mesh.triCount * 3, node.transform.size());
                }
            }
            for (Matrix4f relativeTransform : node.transform) {
                Matrix4f worldTransform = parentTransform.mul(relativeTransform, new Matrix4f());
                for (gLTF.Node childNode : node.children) {
                    renderNode(childNode, worldTransform, shader, doFrustumCull, onlyOutlined, showBounds, node.outline);
                }
            }
        }
    }
    private static class Plane {
        public Vec normal;
        public Vec point;
        public Plane(Vec normal, Vec point) {
            this.normal = normal;
            this.point = point;
        }
        public boolean pointContained(Vec point) {
            double signedDistance = normal.dot(point.sub(this.point));
            return signedDistance >= 0;
        }
    }
    private static List<Plane> getFrustumPlanes() {
        List<Plane> planes = new ArrayList<>();
        double vSide = Math.abs(Math.tan((FOV*0.5)*Math.PI/180)*farPlane);
        double hSide = aspectRatio*vSide;

        double a = Math.sqrt((hSide * hSide)*0.25);
        double b = Math.sqrt((vSide * vSide)*0.25);

        Vec lNorm = new Vec(farPlane / a, 0 , (hSide*0.5)/a);
        Vec rNorm = (lNorm.mult(new Vec(-1,1,1))).rotate(controller.cameraRot);
        lNorm = lNorm.rotate(controller.cameraRot);

        Vec bNorm = new Vec(0, farPlane/b, vSide/(2*b));
        Vec tNorm = (bNorm.mult(new Vec(1,-1,1))).rotate(controller.cameraRot);
        bNorm = bNorm.rotate(controller.cameraRot);

        Vec nNorm = new Vec(0,0,1).rotate(controller.cameraRot);
        Vec fNorm = nNorm.mult(-1);

        planes.add(new Plane(nNorm, controller.cameraPos));
        planes.add(new Plane(fNorm, controller.cameraPos.add(nNorm.mult(farPlane))));
        planes.add(new Plane(lNorm, controller.cameraPos));
        planes.add(new Plane(rNorm, controller.cameraPos));
        planes.add(new Plane(bNorm, controller.cameraPos));
        planes.add(new Plane(tNorm, controller.cameraPos));
        return planes;
    }
    private static boolean boxInFrustum(Vec min, Vec max, Matrix4f worldTransform) {
        List<Vec> boxPoints = new ArrayList<>();
        boxPoints.add(min);
        boxPoints.add(max);
        boxPoints.add(new Vec(max.x, min.y, min.z));
        boxPoints.add(new Vec(max.x, min.y, max.z));
        boxPoints.add(new Vec(min.x, min.y, max.z));
        boxPoints.add(new Vec(min.x, max.y, min.z));
        boxPoints.add(new Vec(max.x, max.y, min.z));
        boxPoints.add(new Vec(min.x, max.y, max.z));
        for (int i = 0; i < 8; i++) {
            Vector4f newPoint = worldTransform.transform(new Vector4f(boxPoints.get(i).toVec3f(), 1));
            boxPoints.set(i, new Vec(newPoint.x, newPoint.y, newPoint.z));
        }
//        System.out.println("...");
//        System.out.println(frustumPlanes.get(0).pointContained(new Vec(0)));
//        System.out.println(frustumPlanes.get(1).pointContained(new Vec(0)));
//        System.out.println(frustumPlanes.get(2).pointContained(new Vec(0)));
//        System.out.println(frustumPlanes.get(3).pointContained(new Vec(0)));
//        System.out.println(frustumPlanes.get(4).pointContained(new Vec(0)));
//        System.out.println(frustumPlanes.get(5).pointContained(new Vec(0)));
        for (int i = 0; i < 0; i++) {
            boolean inside = false;
            for (Vec point : boxPoints) {
                if (frustumPlanes.get(i).pointContained(point)) {
                    inside = true;
                    break;
                }
            }
            if (!inside) return false;
        }
        return true;
    }

    private static void generateShadowMaps() {
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT_FACE);
        for (World.worldLight wLight : world.worldLights) {
            if (wLight.light.type == 1) {
                glBindFramebuffer(GL_FRAMEBUFFER, wLight.shadowmapFramebuffer);
                glViewport(0, 0, SHADOW_RES, SHADOW_RES);
                glClear(GL_DEPTH_BUFFER_BIT);
                shadowShader.useProgram();
                Light light = wLight.light;

                shadowShader.setUniform("lightSpaceMatrix", light.lightSpaceMatrix);

                glEnable(GL_DEPTH_TEST);
                drawScene(shadowShader, false, false, false);
            }
        }
        glCullFace(GL_BACK);
    }

    public static void updateSave() throws IOException {
        System.out.println("Parameters saved to save.txt");
        File saveFile = new File("save.txt");
        if (!saveFile.exists()) {
            System.err.println("Save file not found, a new one has been created");
            return;
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(saveFile));
        writer.write("Position: " + controller.cameraPos.toString());
        writer.newLine();
        writer.write("Rotation: " + controller.cameraRot.toString());
        writer.newLine();
        writer.write("Max_FPS: " + FPS);
        writer.newLine();
        writer.write("FOV: " + FOV);
        writer.newLine();
        writer.write("Exposure: " + EXPOSURE);
        writer.newLine();
        writer.write("Gamma: " + GAMMA);
        writer.newLine();
        writer.write("Do_SSAO: " + doSSAO);
        writer.newLine();
        writer.write("SSAO_Radius: " + SSAOradius);
        writer.newLine();
        writer.write("SSAO_Bias: " + SSAObias);
        writer.newLine();
        writer.write("Bloom_radius: " + bloomRadius);
        writer.newLine();
        writer.write("Bloom_intensity: " + bloomIntensity);
        writer.newLine();
        writer.write("Bloom_threshold: " + bloomThreshold);
        writer.newLine();
        writer.write("Cap_FPS: " + CAP_FPS);
        writer.newLine();
        writer.write("Borderless_fullscreen: " + borderless_fullscreen);
        writer.newLine();
        writer.write("Do_Shadows: " + doShadows);
        writer.newLine();
        writer.write("Do_Bloom: " + doBloom);
        writer.newLine();
        writer.write("Do_SSR: " + doSSR);
        writer.newLine();
        writer.close();
    }
    public static void loadSave() {
        File saveFile = new File("save.txt");
        if (!saveFile.exists()) {
            System.err.println("Save file not found, a new one will be created");
        } else {
            try (BufferedReader reader = new BufferedReader(new FileReader("save.txt"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] l = line.split(" ");
                    switch (l[0]) {
                        case ("Position:"):
                            camPos = new Vec(l[1], l[2], l[3]);
                            break;
                        case ("Rotation:"):
                            camRot = new Vec(l[1], l[2], l[3]);
                            break;
                        case ("Max_FPS:"):
                            FPS = Integer.parseInt(l[1]);
                            break;
                        case ("FOV:"):
                            FOV = Float.parseFloat(l[1]);
                            break;
                        case ("Exposure:"):
                            EXPOSURE = Float.parseFloat(l[1]);
                            break;
                        case ("Gamma:"):
                            GAMMA = Float.parseFloat(l[1]);
                            break;
                        case ("Do_SSAO:"):
                            doSSAO = Boolean.parseBoolean(l[1]);
                            break;
                        case ("SSAO_Radius:"):
                            SSAOradius = Float.parseFloat(l[1]);
                            break;
                        case ("SSAO_Bias:"):
                            SSAObias = Float.parseFloat(l[1]);
                            break;
                        case ("Bloom_radius:"):
                            bloomRadius = Float.parseFloat(l[1]);
                            break;
                        case ("Bloom_intensity:"):
                            bloomIntensity = Float.parseFloat(l[1]);
                            break;
                        case ("Bloom_threshold:"):
                            bloomThreshold = Float.parseFloat(l[1]);
                            break;
                        case ("Cap_FPS:"):
                            CAP_FPS = Boolean.parseBoolean(l[1]);
                            break;
                        case ("Borderless_fullscreen:"):
                            borderless_fullscreen = Boolean.parseBoolean(l[1]);
                            break;
                        case ("Do_Shadows:"):
                            doShadows = Boolean.parseBoolean(l[1]);
                            break;
                        case ("Do_Bloom:"):
                            doBloom = Boolean.parseBoolean(l[1]);
                            break;
                        case ("Do_SSR:"):
                            doSSR = Boolean.parseBoolean(l[1]);
                            break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}