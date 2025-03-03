package Main;

import Datatypes.Shader;
import Datatypes.Vec;

import static org.lwjgl.glfw.GLFW.*;

public class Controller {
    Vec cameraPos;
    Vec cameraRot;

    float moveSpeed = 1f;
    float sensitivity = 5f;

    double deltaTime;
    long window;

    boolean mouseMode = true;
    boolean firstMouse = true;
    boolean escapeWasDown = false; boolean minusWasDown = false; boolean equalsWasDown = false; boolean f1WasDown = false;
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
        if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) cameraPos.y -= speed;
        if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) cameraPos.y += speed;

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

        if (glfwGetKey(window, GLFW_KEY_LEFT_BRACKET) == GLFW_PRESS) Run.EXPOSURE -= 0.01F;
        if (glfwGetKey(window, GLFW_KEY_RIGHT_BRACKET) == GLFW_PRESS) Run.EXPOSURE += 0.01F;
        Run.EXPOSURE = Math.max(Run.EXPOSURE, 0);
        if (glfwGetKey(window, GLFW_KEY_SEMICOLON) == GLFW_PRESS) Run.GAMMA -= 0.01F;
        if (glfwGetKey(window, GLFW_KEY_APOSTROPHE) == GLFW_PRESS) Run.GAMMA += 0.01F;
        Run.GAMMA = Math.max(Run.GAMMA, 0);

        //if (glfwGetKey(window, GLFW_KEY_F1) == GLFW_PRESS) Run.world.worldObjects.get(1).newInstance(new Vec(1), cameraPos.mult(new Vec(-1,1,-1)), new Vec(0));
        if (glfwGetKey(window, GLFW_KEY_F1) == GLFW_PRESS) Run.compileShaders();
        /*
        if (glfwGetKey(window, GLFW_KEY_F1) == GLFW_PRESS) {
            if (!f1WasDown) {
                f1WasDown = true;
                Run.world.modifyLight();
                Run.world.updateWorld();
            }
        } else {f1WasDown = false;}
*/
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
}
