package Main;

import Datatypes.Shader;
import ModelHandler.gLTF;
import Datatypes.Vec;
import ModelHandler.Light;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.ARBFramebufferSRGB.GL_FRAMEBUFFER_SRGB;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL45.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryUtil.*;

import javax.swing.*;

public class Run {
    public static int
            lightingFBO, lightingTex, lightingRBO,
            gFBO, gPosition, gNormal, gMaterial, gTexCoord, gViewPosition, gRBO, gViewNormal,
            SSAOfbo, SSAOblurFBO, SSAOtex, SSAOblurTex, SSAOnoiseTex,
            postProcessingFBO, postProcessingTex, bloomTex,
            skyboxTex,
            gaussianBlurTexOneHalf, gaussianBlurTex;
    private static int[] gaussianBlurFBO;
    private static Vec[] SSAOkernal;
    public static Shader
            geometryShader, screenShader, skyboxShader, shadowShader, lightingShader, SSAOshader, blurShader, postProcessingShader, gaussianBlurShader, debugShader;
    public static long window, FPS = 240;

    public static float EXPOSURE = 1f;
    public static float GAMMA = 1; // 0.25
    public static boolean doSSAO = true;
    public static float SSAOradius = 0.6f;
    public static float SSAObias = 0.001f;
    public static float bloomRadius = 1f;
    public static float bloomIntensity = 0.5f;
    public static float bloomThreshold = 1f;

    public static long startTime = System.nanoTime();
    public static long time = 0;

    public static int SHADOW_RES = 8192;
    public static float FOV = 100;
    public static int WIDTH = 1920;
    public static int HEIGHT = 1080;
    public static float aspectRatio = (float) WIDTH / HEIGHT;
    public static float nearPlane = 0.001f, farPlane = 10000.0f;
    public static Matrix4f projectionMatrix;
    public static Matrix4f viewMatrix;
    public static String skyboxPath = "C:\\Graphics\\assets\\the_sky_is_on_fire_4k.hdr";
    private static final String shaderPath = "C:\\Graphics\\rasterizer\\src\\shaders\\";

    public static World world;
    public static Controller controller;
    public static GUI gui;

    private static final List<String> lines = new ArrayList<>();
    private static JTextArea textArea;

    static List<Plane> frustumPlanes;


    public static void main(String[] args) {
        init();
        runEngine();

        //Util.PBRtextureSeparator.splitPrPm_GB("C:/Graphics/assets/bistro2/textures");
        //Util.PBRtextureSeparator.processMaterialFile("C:/Graphics/assets/bistro2/bistro.mtl");
    }
    public static void runEngine() {
        controller = new Controller(new Vec(0), new Vec(0), window);
        //controller = new Controller(new Vec(11.05, 2.71, 2.47), new Vec(0.06, -1.6, 0), window);
        world = new World();
        createWorld();
        world.updateWorld();
        initSkybox(skyboxPath);

        long frameTime = 1000000000 / FPS; // Nanoseconds per frame
        System.out.println();
        int frames = 0;
        long lastTime = System.nanoTime();

        //feeble attempt to fix SSAO simply not happening sometimes...
        renderToQuad(SSAOblurTex);

        while (!glfwWindowShouldClose(window)) {
            long startTime = System.nanoTime();

            update();
            controller.doControls(time);
            updateWorldObjectInstances();
            render();
            frames++;
            double thisFrameTime = (startTime - lastTime) / 1_000_000_000.0;
            if (thisFrameTime >= 0.5) {
                updateLine(0, "FPS: " + frames / thisFrameTime);
                frames = 0;
                lastTime = startTime;
            }
            long elapsedTime = System.nanoTime() - startTime;
            long sleepTime = frameTime - elapsedTime;
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime / 1000000, (int) (sleepTime % 1000000)); // Convert to milliseconds & nanoseconds
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        glfwTerminate();
    }

    public static void createWorld() {
        world.addObject("C:\\Graphics\\assets\\sphere", new Vec(1), new Vec(0, 0, 0), new Vec(0), "bistro");
        gLTF newObject = new gLTF("C:\\Graphics\\assets\\bistro2");
        world.addGLTF(newObject);

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

        //world.addLightsForObject(world.worldObjects.get(0), 0.5f);
        world.addLightsForScene(newObject, 0, 0.5f);
    }
    public static void render() {
        generateShadowMaps();
        //geometry pass
        glBindFramebuffer(GL_FRAMEBUFFER, gFBO);
        glViewport(0, 0, WIDTH, HEIGHT);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        glClearColor(0.0f, 0.0f, 0.0f, -1);
        glClearTexImage(gPosition, 0, GL_RGBA, GL_FLOAT, new float[]{Float.POSITIVE_INFINITY,0,0,0});
        glEnable(GL_DEPTH_TEST);
        drawScene(geometryShader, false);

        //ssao generation pass
        glBindFramebuffer(GL_FRAMEBUFFER, SSAOfbo);
        glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        for (int i = 0; i < 64; i++) {
            SSAOshader.setUniform("samples[" + i + "]", SSAOkernal[i]);
        }
        SSAOshader.setUniform("projection", projectionMatrix);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, gViewPosition);
        SSAOshader.setUniform("gViewPosition", 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, gViewNormal);
        SSAOshader.setUniform("gNormal", 1);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, SSAOnoiseTex);
        SSAOshader.setUniform("texNoise", 2);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        
        //ssao blur pass
        glBindFramebuffer(GL_FRAMEBUFFER, SSAOblurFBO);
        glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, SSAOtex);
        blurShader.setUniform("blurInput", 0);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        //lighting pass
        glBindFramebuffer(GL_FRAMEBUFFER, lightingFBO);
        glViewport(0, 0, WIDTH, HEIGHT);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        lightingShader.useProgram();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, gPosition);
        lightingShader.setUniform("gPosition", 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, gNormal);
        lightingShader.setUniform("gNormal", 1);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, gMaterial);
        lightingShader.setUniform("gMaterial", 2);
        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, gTexCoord);
        lightingShader.setUniform("gTexCoord", 3);
        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_2D, world.worldLights.get(0).shadowmapTexture);
        lightingShader.setUniform("shadowmaps", 4);
        glActiveTexture(GL_TEXTURE5);
        glBindTexture(GL_TEXTURE_2D, SSAOblurTex);
        lightingShader.setUniform("SSAOtex", 5);
        glActiveTexture(GL_TEXTURE6);
        glBindTexture(GL_TEXTURE_2D, skyboxTex);
        lightingShader.setUniform("skybox", 6);
        lightingShader.setUniform("FOV", FOV);
        glEnable(GL_FRAMEBUFFER_SRGB);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glDisable(GL_FRAMEBUFFER_SRGB);
        
        //blur bloom pass (replace this with a gaussian blur fbo)
        gaussianBlur(bloomTex);
        //
        glBindFramebuffer(GL_FRAMEBUFFER, postProcessingFBO);
        glViewport(0, 0, WIDTH, HEIGHT);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, lightingTex);
        postProcessingShader.setUniform("postProcessingBuffer", 0);
        glActiveTexture(GL_TEXTURE2);
        //replace this texture with new guassian blur texture later
        glBindTexture(GL_TEXTURE_2D, gaussianBlurTex);
        postProcessingShader.setUniform("bloomTex", 2);
        postProcessingShader.setUniform("skybox", 1);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        
        renderToQuad(postProcessingTex);

        glfwSwapBuffers(window);
        glfwPollEvents();
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

        IntBuffer width = MemoryUtil.memAllocInt(1);
        IntBuffer height = MemoryUtil.memAllocInt(1);
        glfwGetWindowSize(window, width, height);
        if (width.get(0) != WIDTH || height.get(0) != HEIGHT) {
            WIDTH = width.get(0);
            HEIGHT = height.get(0);
            aspectRatio = (float) WIDTH / HEIGHT;
            scaleToWindow();
        }
        MemoryUtil.memFree(width);
        MemoryUtil.memFree(height);

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

        updateLine(1, "Position: " + String.format("%.2f %.2f %.2f", controller.cameraPos.x, controller.cameraPos.y, controller.cameraPos.z));
        updateLine(2, "Rotation: " + String.format("%.2f %.2f %.2f", controller.cameraRot.x, controller.cameraRot.y, controller.cameraRot.z));
        updateLine(3, "Exposure: " + EXPOSURE);
        updateLine(4, "Gamma: " + GAMMA);
        updateLine(5, "SSAO radius: " + SSAOradius);
    }

    public static void init() {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_SAMPLES, 4);
        window = glfwCreateWindow(WIDTH, HEIGHT, "rasterizer", NULL, NULL);
        glfwMakeContextCurrent(window);
        GL.createCapabilities();

        IntBuffer width = MemoryUtil.memAllocInt(1);
        IntBuffer height = MemoryUtil.memAllocInt(1);
        glfwGetWindowSize(window, width, height);
        WIDTH = width.get(0);
        HEIGHT = height.get(0);
        MemoryUtil.memFree(width);
        MemoryUtil.memFree(height);
        compileShaders();

        scaleToWindow();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        //glEnable(GL_BLEND);
        glEnable(GL_MULTISAMPLE);
        glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_STENCIL_TEST);
        glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);


        JFrame frame = new JFrame("Console Output");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 400);

        textArea = new JTextArea(5, 30);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        textArea.setEditable(false);

        frame.add(new JScrollPane(textArea));
        frame.setVisible(true);

        print("Position: ");
        print("FPS: ");
        print("");
        print("");
        print("");
        print("");
    }
    public static void compileShaders() {
        skyboxShader = new Shader(shaderPath + "\\skyboxShader\\skyboxFrag.glsl", shaderPath + "\\quadVertex.glsl");
        geometryShader = new Shader(shaderPath + "\\geometryShader\\geometryPassFrag.glsl", shaderPath + "\\geometryShader\\geometryPassVert.glsl");
        shadowShader = new Shader(shaderPath + "\\ShadowMapping\\shadowmapFrag.glsl", shaderPath + "\\ShadowMapping\\shadowmapVert.glsl");
        screenShader = new Shader(shaderPath + "\\quadShader\\quadFrag.glsl", shaderPath + "\\quadVertex.glsl");
        lightingShader = new Shader(shaderPath + "\\lightingShader\\lightingPassFrag.glsl", shaderPath + "\\quadVertex.glsl");
        postProcessingShader = new Shader(shaderPath + "\\postProcessingShader\\postProcessingShaderFrag.glsl", shaderPath + "\\quadVertex.glsl");
        blurShader = new Shader(shaderPath + "\\blurShader\\blurFrag.glsl", shaderPath + "\\quadVertex.glsl");
        SSAOshader = new Shader(shaderPath + "\\SSAOshader\\SSAOfrag.glsl", shaderPath + "\\quadVertex.glsl");
        gaussianBlurShader = new Shader(shaderPath + "\\gaussianBlurShader\\gaussianBlurFrag.glsl", shaderPath + "\\quadVertex.glsl");
        debugShader = new Shader(shaderPath + "\\debugShader\\debugShader.frag", shaderPath + "\\debugShader\\debugShader.frag");
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
        bloomTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, bloomTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemoryUtil.NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, bloomTex, 0);
        glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});
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
        int[] gAttachments = new int[]{GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1, GL_COLOR_ATTACHMENT2, GL_COLOR_ATTACHMENT3, GL_COLOR_ATTACHMENT4, GL_COLOR_ATTACHMENT5};
        glDrawBuffers(gAttachments);
        gRBO = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, gRBO);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, WIDTH, HEIGHT);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, gRBO);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        postProcessingFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, postProcessingFBO);
        postProcessingTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, postProcessingTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemoryUtil.NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, postProcessingTex, 0);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);


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
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
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

    public static void initSkybox(String imagePath) {
        skyboxTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, skyboxTex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);  // No mipmaps, just linear filtering
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        IntBuffer width = MemoryUtil.memAllocInt(1);
        IntBuffer height = MemoryUtil.memAllocInt(1);
        IntBuffer channels = MemoryUtil.memAllocInt(1);
        FloatBuffer image = stbi_loadf(imagePath, width, height, channels, 4);
        if (image == null) {
            System.err.println("could not load image " + imagePath);
            return;
        }
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width.get(0), height.get(0), 0, GL_RGBA, GL_FLOAT, image);
        glGenerateMipmap(GL_TEXTURE_2D);
        stbi_image_free(image);
        MemoryUtil.memFree(width);
        MemoryUtil.memFree(height);
        MemoryUtil.memFree(channels);
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

    private static void drawSkybox() {
        skyboxShader.useProgram();
        glBindTexture(GL_TEXTURE_2D, skyboxTex);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    private static void drawScene(Shader shader, boolean doFrustumCull) {
        for (World.worldObject obj : world.worldObjects) {
            for (int i = 0; i < obj.numInstances; i++) {
                shader.setUniform("objectMatrix[" + i + "]", obj.transforms.get(i));
            }
            glBindVertexArray(obj.VAO);
            glEnableVertexAttribArray(0);
            glDrawArraysInstanced(GL_TRIANGLES, 0, obj.triCount * 3, obj.numInstances);
        }
        for (gLTF ignored : world.worldGLTFs) {
            gLTF.Scene scene = gLTF.activeScene;
            for (gLTF.Node node : scene.nodes) {
                renderNode(node, new Matrix4f().identity(), shader, doFrustumCull);
            }
        }
    }
    private static void renderNode(gLTF.Node node, Matrix4f parentTransform, Shader shader, boolean doFrustumCull) {
        Matrix4f worldTransform = parentTransform.mul(node.transform, new Matrix4f());
        if (node.mesh == null) {
            for (gLTF.Node childNode : node.children) {
                renderNode(childNode, worldTransform, shader, doFrustumCull);
            }
        } else if (!doFrustumCull || boxInFrustum(node.mesh.min, node.mesh.max, worldTransform)) {
            shader.setUniform("objectMatrix[" + 0 + "]", worldTransform);
            glBindVertexArray(node.mesh.VAO);
            glEnableVertexAttribArray(0);
            glDrawArrays(GL_TRIANGLES, 0, node.mesh.triCount * 3);
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
        for (int i = 0; i < 6; i++) {
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
                drawScene(shadowShader, false);
            }
        }
        glCullFace(GL_BACK);
    }

    public static double lerp(double t, double a, double b) {
        return a + t * ((double) b - a);
    }

    public static void print(String text) {
        String[] splitText = text.split("\n");
        Collections.addAll(lines, splitText);
        refreshTextArea();
    }

    public static void updateLine(int lineNumber, String newText) {
        if (lineNumber >= 0 && lineNumber < lines.size()) {
            lines.set(lineNumber, newText);
            refreshTextArea();
        }
    }

    private static void refreshTextArea() {
        textArea.setText(String.join("\n", lines));
    }
}
