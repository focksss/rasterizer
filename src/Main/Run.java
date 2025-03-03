package Main;

import Datatypes.Shader;
import Datatypes.Vec;
import ModelHandler.Light;
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
            screenFBO, screenTex, screenRBO,
            gFBO, gPosition, gNormal, gMaterial, gTexCoord, gViewPosition, gRBO,
            SSAOfbo, SSAOblurFBO, SSAOtex, SSAOblurTex, SSAOnoiseTex,
            ppFBO, ppTex,
            skyboxTex;
    private static Vec[] SSAOkernal;
    public static Shader
            geometryShader, screenShader, skyboxShader, shadowShader, lightingShader, SSAOshader, blurShader, ppShader;
    public static long window, FPS = 240;

    public static float EXPOSURE = 2.75f;
    public static float GAMMA = 1.15f;
    public static long startTime = System.nanoTime();
    public static long time = 0;

    public static int SHADOW_RES = 8192;
    public static float FOV = 100;
    public static int WIDTH = 1920;
    public static int HEIGHT = 1080;
    public static float aspectRatio = (float) WIDTH / HEIGHT;
    public static float nearPlane = 0.001f, farPlane = 10000.0f;
    public static Matrix4f projectionMatrix;
    public static String skyboxPath = "C:\\Graphics\\antiDoxxFolder\\thatch_chapel_4k.png";
    private static final String shaderPath = "C:\\Graphics\\rasterizer\\src\\shaders\\";

    public static World world;
    public static Controller controller;
    public static GUI gui;

    private static final List<String> lines = new ArrayList<>();
    private static JTextArea textArea;


    public static void main(String[] args) {
        init();
        controller = new Controller(new Vec(0), new Vec(0), window);
        world = new World();
        createWorld();
        world.updateWorld();
        initSkybox(skyboxPath);

        long frameTime = 1000000000 / FPS; // Nanoseconds per frame
        System.out.println();
        int frames = 0;
        long lastTime = System.nanoTime();

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

    public static void render() {
        generateShadowMaps();
        //geometry pass
        glBindFramebuffer(GL_FRAMEBUFFER, gFBO);
        glViewport(0, 0, WIDTH, HEIGHT);
        glClearColor(0.0f, 0.0f, 0.0f, 0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        drawScene(geometryShader);
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
        glBindTexture(GL_TEXTURE_2D, gNormal);
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
        glBindFramebuffer(GL_FRAMEBUFFER, screenFBO);
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
        glEnable(GL_FRAMEBUFFER_SRGB);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glDisable(GL_FRAMEBUFFER_SRGB);

        glBindFramebuffer(GL_FRAMEBUFFER, ppFBO);
        glViewport(0, 0, WIDTH, HEIGHT);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, screenTex);
        ppShader.setUniform("ppBuffer", 0);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        renderToQuad(ppTex);

        glfwSwapBuffers(window);
        glfwPollEvents();
    }
    public static void createWorld() {
        world.addObject("C:\\Graphics\\assets\\bistro", new Vec(1), new Vec(0, 0, 0), new Vec(0), "bistro");
        //world.addObject("C:\\Graphics\\assets\\grassblock1", new Vec(1), new Vec(0), new Vec(0), "bistro");
        //world.addObject("C:\\Graphics\\assets\\sponza", new Vec(0.01), new Vec(0), new Vec(0), "bistro");
        world.worldObjects.get(0).newInstance();

        Light newLight = new Light(1);
        newLight.setProperty("direction", new Vec(-0.15, -1, .15));
        newLight.setProperty("position", new Vec(0.1, 1, 0.05).mult(50));
        newLight.setProperty("cutoff", 0.8);
        newLight.setProperty("innerCutoff", 0.9);
        newLight.setProperty("constantAttenuation", 1);
        newLight.setProperty("linearAttenuation", 0.09);
        newLight.setProperty("quadraticAttenuation", 0.032);
        newLight.setProperty("ambient", new Vec(0.1, 0.1, 0.1));
        newLight.setProperty("diffuse", new Vec(1, 1, 1));
        newLight.setProperty("specular", new Vec(1, 1, 1));
        world.addLight(newLight);

/*
        Light newLight = new Light(3);
        newLight.setProperty("direction", new Vec(-0.5, -1, -0.15));
        newLight.setProperty("position", new Vec(0.1, 1, 0.05).mult(50));
        newLight.setProperty("cutoff", 0.8);
        newLight.setProperty("innerCutoff", 0.9);
        newLight.setProperty("constantAttenuation", 1);
        newLight.setProperty("linearAttenuation", 0.09);
        newLight.setProperty("quadraticAttenuation", 0.032);
        newLight.setProperty("ambient", new Vec(0.1, 0.1, 0.1));
        newLight.setProperty("diffuse", new Vec(1, 1, 1));
        newLight.setProperty("specular", new Vec(1, 1, 1));

        world.addLight(newLight);
*/

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

        Matrix4f viewMatrix = new Matrix4f();
        viewMatrix.identity()
                .rotateX(controller.cameraRot.xF).rotateY(-controller.cameraRot.yF).rotateZ(-controller.cameraRot.zF)
                .translate(new Vector3f(controller.cameraPos.xF, -controller.cameraPos.yF, controller.cameraPos.zF));
        geometryShader.setUniform("viewMatrix", viewMatrix);

        projectionMatrix = new Matrix4f();
        projectionMatrix.identity()
                .setPerspective((float) Math.toRadians(FOV), aspectRatio, nearPlane, farPlane, false);
        geometryShader.setUniform("projectionMatrix", projectionMatrix);

        lightingShader.setUniform("camPos", controller.cameraPos);
        lightingShader.setUniform("camRot", controller.cameraRot);

        ppShader.setUniform("exposure", EXPOSURE);
        ppShader.setUniform("gamma", GAMMA);
        lightingShader.setUniform("gamma", GAMMA);

        updateLine(1, "Position: " + String.format("%.2f %.2f %.2f", controller.cameraPos.x, controller.cameraPos.y, controller.cameraPos.z));
        updateLine(2, "Rotation: " + String.format("%.2f %.2f %.2f", controller.cameraRot.x, controller.cameraRot.y, controller.cameraRot.z));
        updateLine(3, "Exposure: " + EXPOSURE);
        updateLine(4, "Gamma: " + GAMMA);
    }

    public static void init() {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
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


        screenFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, screenFBO);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        screenTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, screenTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemoryUtil.NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, screenTex, 0);
        screenRBO = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, screenRBO);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, WIDTH, HEIGHT);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, screenRBO);
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
        int[] gAttachments = new int[]{GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1, GL_COLOR_ATTACHMENT2, GL_COLOR_ATTACHMENT3, GL_COLOR_ATTACHMENT4};
        glDrawBuffers(gAttachments);
        gRBO = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, gRBO);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, WIDTH, HEIGHT);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, gRBO);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        ppFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, ppFBO);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        ppTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, ppTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemoryUtil.NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, ppTex, 0);
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


        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        //glEnable(GL_BLEND);
        glEnable(GL_MULTISAMPLE);
        glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE);
        glfwWindowHint(GLFW_SAMPLES, 4);
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
        ppShader = new Shader(shaderPath + "\\postProcessingShader\\postProcessingShaderFrag.glsl", shaderPath + "\\quadVertex.glsl");
        blurShader = new Shader(shaderPath + "\\blurShader\\blurFrag.glsl", shaderPath + "\\quadVertex.glsl");
        SSAOshader = new Shader(shaderPath + "\\SSAOshader\\SSAOfrag.glsl", shaderPath + "\\quadVertex.glsl");
    }
    public static void scaleToWindow() {
        glBindFramebuffer(GL_FRAMEBUFFER, screenFBO);
        glDeleteTextures(screenTex);
        screenTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, screenTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemoryUtil.NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, screenTex, 0);
        glDeleteRenderbuffers(screenRBO);
        screenRBO = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, screenRBO);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, WIDTH, HEIGHT);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, screenRBO);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        glBindFramebuffer(GL_FRAMEBUFFER, gFBO);
        glDeleteTextures(gPosition);
        gPosition = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gPosition);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, gPosition, 0);
        glDeleteTextures(gNormal);
        gNormal = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gNormal);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, gNormal, 0);
        glDeleteTextures(gMaterial);
        gMaterial = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gMaterial);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT2, GL_TEXTURE_2D, gMaterial, 0);
        glDeleteTextures(gTexCoord);
        gTexCoord = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gTexCoord);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RG32F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT3, GL_TEXTURE_2D, gTexCoord, 0);
        glDeleteTextures(gViewPosition);
        gViewPosition = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gViewPosition);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT4, GL_TEXTURE_2D, gViewPosition, 0);
        glDeleteTextures(gRBO);
        gRBO = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, gRBO);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, WIDTH, HEIGHT);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, gRBO);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        glBindFramebuffer(GL_FRAMEBUFFER, ppFBO);
        glDeleteTextures(ppTex);
        ppTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, ppTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemoryUtil.NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, ppTex, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        glBindFramebuffer(GL_FRAMEBUFFER, SSAOfbo);
        glDeleteTextures(SSAOtex);
        SSAOtex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, SSAOtex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemoryUtil.NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, SSAOtex, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, SSAOblurFBO);
        glDeleteTextures(SSAOblurTex);
        SSAOblurTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, SSAOblurTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemoryUtil.NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, SSAOblurTex, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        generateSSAOsampling();
    }

    private static void generateSSAOsampling() {
        SSAOkernal = new Vec[64];
        for (int i = 0; i < 64; i++) {
            double scale = (double) i / 64;
            scale = lerp(0.1, 1, scale * scale);
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
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTextureParameteri(skyboxTex, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        IntBuffer width = MemoryUtil.memAllocInt(1);
        IntBuffer height = MemoryUtil.memAllocInt(1);
        IntBuffer channels = MemoryUtil.memAllocInt(1);
        ByteBuffer image = stbi_load(imagePath, width, height, channels, 4);
        if (image == null) {
            System.err.println("could not load image " + imagePath);
            return;
        }
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(0), height.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
        glGenerateMipmap(GL_TEXTURE_2D);
        stbi_image_free(image);
        MemoryUtil.memFree(width);
        MemoryUtil.memFree(height);
        MemoryUtil.memFree(channels);
    }

    private static void renderToQuad(int texture) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glClearColor(1, 1, 1, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        screenShader.useProgram();
        glDisable(GL_DEPTH_TEST);
        glViewport(0, 0, WIDTH, HEIGHT);
        glActiveTexture(GL_TEXTURE0);

        glBindTexture(GL_TEXTURE_2D, texture);
        screenShader.setUniform("screenTexture", 0);

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

    private static void drawScene(Shader shader) {
        for (World.worldObject obj : world.worldObjects) {
            for (int i = 0; i < obj.numInstances; i++) {
                shader.setUniform("objectMatrix[" + i + "]", obj.transforms.get(i));
            }
            glBindVertexArray(obj.VAO);
            glEnableVertexAttribArray(0);
//            glStencilFunc(GL_ALWAYS, 1, 0xFF);
//            glStencilMask(0xFF);
            glDrawArraysInstanced(GL_TRIANGLES, 0, obj.triCount * 3, obj.numInstances);
        }
    }

    private static void generateShadowMaps() {
        glEnable(GL_DEPTH_TEST);
        glCullFace(GL_FRONT_FACE);
        for (World.worldLight wLight : world.worldLights) {
            glBindFramebuffer(GL_FRAMEBUFFER, wLight.shadowmapFramebuffer);
            glViewport(0, 0, SHADOW_RES, SHADOW_RES);
            glClear(GL_DEPTH_BUFFER_BIT);
            shadowShader.useProgram();
            Light light = wLight.light;

            shadowShader.setUniform("lightSpaceMatrix", light.lightSpaceMatrix);

            glEnable(GL_DEPTH_TEST);
            drawScene(shadowShader);
            glCullFace(GL_BACK);
        }
        glCullFace(GL_BACK);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
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
