package Main;

import Datatypes.Shader;
import Datatypes.Vec;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Controller {
    Vec cameraPos;
    Vec cameraRot;

    float moveSpeed = 1f;
    float sensitivity = 5f;

    double deltaTime;
    long window;

    boolean mouseMode = true;
    boolean firstMouse = true;
    boolean escapeWasDown = false; boolean minusWasDown = false; boolean equalsWasDown = false; boolean f1WasDown = false; boolean f2WasDown = false; boolean f3WasDown = false; boolean f4WasDown = false; boolean f11WasDown = false;
    double lastMouseX = 0;
    double lastMouseY = 0;

    long lastTime = 0;


    public Controller(Vec startPos, Vec startRot, long window) {
        cameraPos = startPos;
        cameraRot = startRot;
        this.window = window;
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        mouseMode = false;
    }

    public void doControls(long time) {
        deltaTime = (time - lastTime) / 1_000_000_000.0;
        lastTime = time;

        float speed = moveSpeed * (float) deltaTime;

        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
            if (!escapeWasDown) {
                escapeWasDown = true;
                //action
                if (mouseMode) {
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                    mouseMode = false;
                    firstMouse = true;
                } else {
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    mouseMode = true;
                }
            }
        } else {escapeWasDown = false;}

        doMouse();
        cameraRot.x = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, cameraRot.x)); // Clamp pitch

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            cameraPos.x -= (float) (speed * Math.cos(cameraRot.y + Math.PI / 2));
            cameraPos.z += (float) (speed * Math.sin(cameraRot.y + Math.PI / 2));
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            cameraPos.x += (float) (speed * Math.cos(cameraRot.y));
            cameraPos.z -= (float) (speed * Math.sin(cameraRot.y));
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            cameraPos.x += (float) (speed * Math.cos(cameraRot.y + Math.PI / 2));
            cameraPos.z -= (float) (speed * Math.sin(cameraRot.y + Math.PI / 2));
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            cameraPos.x -= (float) (speed * Math.cos(cameraRot.y));
            cameraPos.z += (float) (speed * Math.sin(cameraRot.y));
        }
        if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) cameraPos.y -= speed;
        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) cameraPos.y += speed;

        if (glfwGetKey(window, GLFW_KEY_MINUS) == GLFW_PRESS) {
            if (!minusWasDown) {
                minusWasDown = true;
                moveSpeed /= 3;
            }
        } else {minusWasDown = false;}
        if (glfwGetKey(window, GLFW_KEY_EQUAL) == GLFW_PRESS) {
            if (!equalsWasDown) {
                equalsWasDown = true;
                moveSpeed *= 3;
            }
        } else {equalsWasDown = false;}

        if (glfwGetKey(window, GLFW_KEY_LEFT_BRACKET) == GLFW_PRESS) Run.EXPOSURE -= 0.1F;
        if (glfwGetKey(window, GLFW_KEY_RIGHT_BRACKET) == GLFW_PRESS) Run.EXPOSURE += 0.1F;
        Run.EXPOSURE = Math.max(Run.EXPOSURE, 0);
        if (glfwGetKey(window, GLFW_KEY_SEMICOLON) == GLFW_PRESS) Run.GAMMA -= 0.05F;
        if (glfwGetKey(window, GLFW_KEY_APOSTROPHE) == GLFW_PRESS) Run.GAMMA += 0.05F;
        Run.GAMMA = Math.max(Run.GAMMA, 0);
        if (glfwGetKey(window, GLFW_KEY_F7) == GLFW_PRESS) Run.SSAOradius -= 0.005F;
        if (glfwGetKey(window, GLFW_KEY_F8) == GLFW_PRESS) Run.SSAOradius += 0.005F;
        Run.SSAOradius = Math.max(Run.SSAOradius, 0);

        if (glfwGetKey(window, GLFW_KEY_F1) == GLFW_PRESS) {
            if (!f1WasDown) {
                f1WasDown = true;
                Run.compileShaders();
            }
        } else {f1WasDown = false;}
        if (glfwGetKey(window, GLFW_KEY_F2) == GLFW_PRESS) {
            if (!f2WasDown) {
                f2WasDown = true;
                screenshot(Run.postProcessingTex);
            }
        } else {f2WasDown = false;}
        if (glfwGetKey(window, GLFW_KEY_F3) == GLFW_PRESS) {
            if (!f3WasDown) {
                f3WasDown = true;
                Run.doSSAO = !Run.doSSAO;
            }
        } else {f3WasDown = false;}
        if (glfwGetKey(window, GLFW_KEY_F4) == GLFW_PRESS) {
            if (!f4WasDown) {
                f4WasDown = true;
                Run.loadSave();
            }
        } else {f4WasDown = false;}
        if (glfwGetKey(window, GLFW_KEY_F11) == GLFW_PRESS) {
            if (!f11WasDown) {
                f11WasDown = true;
                Run.toggleFullscreen();
            }
        } else {f11WasDown = false;}
    }

    private void doMouse() {
        if (mouseMode) {
            float sense = sensitivity*0.001f;
            double[] mouseX = new double[1];
            double[] mouseY = new double[1];
            glfwGetCursorPos(window, mouseX, mouseY);

            if (firstMouse) {
                lastMouseX = mouseX[0];
                lastMouseY = mouseY[0];
                firstMouse = false;
            }

            double xOffset = mouseX[0] - lastMouseX;
            double yOffset = lastMouseY - mouseY[0]; // Inverted Y-axis
            lastMouseX = mouseX[0];
            lastMouseY = mouseY[0];

            cameraRot.y -= (float) (xOffset * sense);
            cameraRot.x -= (float) (yOffset * sense);
        } else {
            float sense = sensitivity * (float) deltaTime * 0.2f;

            if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS) cameraRot.x -= sense;
            if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS) cameraRot.x += sense;
            if (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS) cameraRot.y += sense;
            if (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS) cameraRot.y -= sense;
        }
    }

    public void screenshot(int texture) {
        glBindTexture(GL_TEXTURE_2D, texture);
        int width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
        int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);

        ByteBuffer imageBuffer = MemoryUtil.memAlloc(width * height * 4);
        glGetTexImage(GL_TEXTURE_2D, 0 ,GL_RGBA, GL_UNSIGNED_BYTE, imageBuffer);
        glBindTexture(GL_TEXTURE_2D, 0);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (x + (height - y - 1) * width) * 4;
                int r = imageBuffer.get(i) & 0xFF;
                int g = imageBuffer.get(i + 1) & 0xFF;
                int b = imageBuffer.get(i + 2) & 0xFF;
                int a = imageBuffer.get(i + 3) & 0xFF;
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, y, argb);
            }
        }

        String timestamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        String folderPath = "screenshots/";
        String filePath = folderPath + "screenshot_" + timestamp + ".png";
        File file = new File(filePath);
        try {
            ImageIO.write(image, "PNG", file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
