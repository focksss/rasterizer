package Main;

import Datatypes.Shader;
import Datatypes.Vec;
import Util.IOUtil;
import static Util.MathUtil.*;

import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.*;

import static Util.IOUtil.resizeBuffer;
import static org.lwjgl.BufferUtils.createByteBuffer;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryUtil.memSlice;

/**
 * This effectively mimics the gLTF data structure,
 * the GUI contains GUI objects,
 * each GUI object can contain children GUI objects,
 * a GUI object can contain children and GUI elements.
 * A GUI element can be a button, slider, label, or something else.
 * Children positions and scales are relative to their parent.
 * <p>
 * If a GUI element does not have a parent, then it will be rendered relative to the entire screen
 * <p>
 * Scale and position are both relative to the parent element, where 0,0 is the bottom left, and scale and position increase up and right
 */

public class GUI {
    static TextRenderer textRenderer;
    static Shader backgroundShader = new Shader("src\\shaders\\GUIBackground\\GUIBackground.frag", "src\\shaders\\GUIBackground\\GUIBackground.vert");
    static Shader pointShader = new Shader("src\\shaders\\pointShader\\pointShader.frag", "src\\shaders\\pointShader\\pointShader.vert");
    static Shader lineShader = new Shader("src\\shaders\\lineShader\\line.frag", "src\\shaders\\lineShader\\line.vert");
    static Shader textShader = new Shader("src\\shaders\\text_shader\\text_shader.frag", "src\\shaders\\text_shader\\text_shader.vert");
    static int VAO;

    private static Vec lastMouse;

    static int mouseInteractingWith = -1;
    static int interactables = 0;

    static List<GUIObject> objects = new ArrayList<>();

    public GUI() {
        textRenderer = new TextRenderer("C:\\Graphics\\rasterizer\\local_resources\\LexendDeca-VariableFont_wght.ttf");
        VAO = glGenVertexArrays();
        glBindVertexArray(VAO);
        toDefaultGUI();
    }

    public void toDefaultGUI() {
        objects.clear();
        GUIQuad quad1 = new GUIQuad(new Vec(0.3));
        GUIQuad quad2 = new GUIQuad(new Vec(0.2));
        GUIQuad quad3 = new GUIQuad(new Vec(0.8,0.2,0.2));

        GUIObject clipObject = new GUIObject(new Vec(0.1, 0.1), new Vec(0.3, 0.8), null);
        GUIObject settingsClip = new GUIObject(new Vec(0.1, 0.1), new Vec(0.3, 0.6), null);
        GUIObject mainObject = new GUIObject(new Vec(0.1, 0.1), new Vec(0.3, 0.8), clipObject);
        GUIObject settings = new GUIObject(new Vec(0,0), new Vec(1, 0.9), settingsClip);
        GUIObject subObject = new GUIObject(new Vec(0.05, 0.05), new Vec(0.25, 0.1), clipObject);
        GUIObject subObject1 = new GUIObject(new Vec(0.35, 0.05), new Vec(0.6, 0.1), clipObject);

        GUILabel emptyText = new GUILabel(new Vec(), "", 0f, new Vec());
        GUILabel fpsText = new GUILabel(new Vec(0.05, 0.4), "", 1f, new Vec(1));
        GUILabel posText = new GUILabel(new Vec(0.05, 0.4), "", 1f, new Vec(1));

        GUILabel settingsText = new GUILabel(new Vec(0.1, 0.9), "Settings", 2, new Vec(1));
        GUILabel recompileText = new GUILabel(new Vec(0.1, 0.4), "Recompile Shaders", 1, new Vec(1));
        GUILabel screenshotText = new GUILabel(new Vec(0.1, 0.4), "Take Screenshot", 1, new Vec(1));
        GUILabel exposureText = new GUILabel(new Vec(0.05, 0.65), "Exposure", 0.8f, new Vec(1));
        GUILabel gammaText = new GUILabel(new Vec(0.05, 0.65), "Gamma", 0.8f, new Vec(1));
        GUILabel exitText = new GUILabel(new Vec(0.1, 0.4), "Exit", 1f, new Vec(0));

        GUIButton recompile = new GUIButton(new Vec(0.05, 0.7), new Vec(0.9, 0.1), recompileText, quad2, Run::compileShaders, false);
        GUIButton screenshot = new GUIButton(new Vec(0.05, 0.55), new Vec(0.9, 0.1), screenshotText, quad2, Controller::screenshot, false);
        GUIButton exit = new GUIButton(new Vec(0.8, 0.9), new Vec(0.15, 0.05), exitText, quad3, Run::Quit, false);
        GUISlider exposure = new GUISlider(new Vec(0.05, 0.4), new Vec(0.9, 0.1), exposureText, quad2, 0, 10, new Vec(1), new Vec(1), Run.EXPOSURE);
        GUISlider gamma = new GUISlider(new Vec(0.05, 0.25), new Vec(0.9, 0.1), gammaText, quad2, 0, 2, new Vec(1), new Vec(1), Run.GAMMA);
        List<Runnable> moveActions = new ArrayList<>(); moveActions.add(mainObject::toMouse); moveActions.add(settingsClip::toMouse);
        GUIButton moveGUI = new GUIButton(new Vec(0, 0.975), new Vec(1, 0.025), emptyText, quad2, moveActions, true);
        GUIScroller scroll = new GUIScroller(new Vec(0.95, 0.05), new Vec(0.025, 0.9), emptyText, quad1, 0, 1, new Vec(0.1), new Vec(0.2), 0);
        scroll.setTotalGUI(0.8f - 0.25f); // top button h - bottom button h

        mainObject.addElement(quad1);
        mainObject.addElement(moveGUI);
        mainObject.addElement(settingsText);
        mainObject.addElement(exit);
        mainObject.addElement(scroll);
        settings.addElement(recompile);
        settings.addElement(screenshot);
        settings.addElement(exposure);
        settings.addElement(gamma);

        mainObject.addChild(settings);
        mainObject.addChild(subObject);
        mainObject.addChild(subObject1);

        subObject.addElement(quad2);
        subObject.addElement(fpsText);
        subObject1.addElement(quad2);
        subObject1.addElement(posText);

        objects.add(mainObject);
        objects.add(clipObject);
    }

    public static void renderGUI() {
        glDisable(GL_CULL_FACE);
        for (GUIObject object : objects) {
            renderObject(object, new Vec(0), new Vec(1));
        }
        lastMouse = Controller.mousePos;
    }
    private static void renderObject(GUIObject object, Vec parentPosition, Vec parentScale) {
        Vec localPos = parentPosition.add(parentScale.mult(object.position));
        Vec localSize = parentScale.mult(object.size);
        if (object.clipTo != null) {
            Vec clipMin = object.clipTo.position.mult(new Vec(Run.WIDTH, Run.HEIGHT));
            Vec clipMax = (object.clipTo.position.add(object.clipTo.size)).mult(new Vec(Run.WIDTH, Run.HEIGHT));
            setClipBounds(clipMin, clipMax);
        } else {
            Vec clipMin = new Vec(0);
            Vec clipMax = new Vec(Run.WIDTH, Run.HEIGHT);
            setClipBounds(clipMin, clipMax);
        }
        for (Object element : object.elements) {
            renderElement(element, localPos, localSize);
        }
        for (GUIObject child : object.children) {
            renderObject(child, localPos, localSize);
        }
    }
    private static void setClipBounds(Vec clipMin, Vec clipMax) {
        pointShader.setUniform("clipMin", clipMin);
        pointShader.setUniform("clipMax", clipMax);
        lineShader.setUniform("clipMin", clipMin);
        lineShader.setUniform("clipMax", clipMax);
        backgroundShader.setUniform("clipMin", clipMin);
        backgroundShader.setUniform("clipMax", clipMax);
        textShader.setUniform("clipMin", clipMin);
        textShader.setUniform("clipMax", clipMax);
    }

    private static void renderElement(Object element, Vec localPos, Vec localSize) {
        if (element instanceof GUILabel label) {
            renderLabel(label, localPos, localSize);
        } else if (element instanceof GUIQuad quad) {
            renderQuad(quad, localPos, localSize);
        } else if (element instanceof GUIButton) {
            GUIButton button = (GUIButton) element;
            Vec pos = localPos.add(localSize.mult(button.position));
            Vec size = localSize.mult(button.size);
            pos.updateFloats();
            size.updateFloats();
            renderQuad(pos, size, button.quad.color.add(new Vec(0.05).mult(button.hovered)));
            renderLabel(button.label, pos, size);
            button.doButton(Controller.mousePos, pos, pos.add(size));
        } else if (element instanceof GUISlider) {
            GUISlider slider = (GUISlider) element;
            Vec pos = localPos.add(localSize.mult(slider.position));
            Vec size = localSize.mult(slider.size);
            renderQuad(pos, size, slider.quad.color);
            renderLabel(slider.label, pos, size);
            Vec p1 = pos.add(size.mult(new Vec(0.05,0.5)));
            Vec p2 = pos.add(size.mult(new Vec(0.95, 0.5)));
            renderLine(p1, p2, 5, slider.lineColor);
            Vec pointPos = p1.add((p2.sub(p1)).mult((slider.value - slider.Lbound) / (slider.Rbound - slider.Lbound)));
            renderPoint(pointPos, 20, slider.pointColor);
            slider.doSlider(Controller.mousePos, pointPos, p1, p2);
        } else if (element instanceof GUIScroller) {
            GUIScroller scroller = (GUIScroller) element;
            Vec pos = localPos.add(localSize.mult(scroller.position));
            Vec size = localSize.mult(scroller.size);
            renderQuad(pos, size, scroller.quad.color);
            renderLabel(scroller.label, pos, size);
            Vec p1 = pos.add(size.mult(new Vec(0.5,0.05)));
            Vec p2 = pos.add(size.mult(new Vec(0.5, 0.95)));
            renderLine(p1, p2, 5, scroller.lineColor);
            double loc = (scroller.value - scroller.Lbound) / (scroller.Rbound - scroller.Lbound);
            Vec pointPos = p2.add((p1.sub(p2)).mult(loc));
            Vec pointPos1 = p2.add((p1.sub(p2)).mult(loc-(double)(1.0/scroller.totalGUI)));
            renderPoint(pointPos, 20, scroller.pointColor);
            scroller.doScroller(Controller.mousePos, pointPos, p2, p1);
        }
    }
    private static void renderQuad(GUIQuad quad, Vec localPos, Vec localSize) {
        Vec pos = localPos.add(localSize.mult(quad.position));
        Vec size = localSize.mult(quad.size);
        pos.updateFloats();
        size.updateFloats();
        renderQuad(pos, size, quad.color);
    }
    private static void renderLabel(GUILabel label, Vec localPos, Vec localSize) {
        Vec pos = localPos.add(localSize.mult(label.position));
        pos.updateFloats();
        textRenderer.renderText(label.text, pos.xF, pos.yF, label.scale, label.color);
    }
    private static void renderPoint(Vec localPos, float size, Vec color) {
        glBindVertexArray(VAO);
        pointShader.setUniform("color", color);
        pointShader.setUniform("pointPosition", localPos);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glPointSize(size);
        glDrawArrays(GL_POINTS, 0, 1);
    }
    private static void renderLine(Vec localPos1, Vec localPos2, float width, Vec color) {
        glBindVertexArray(VAO);
        lineShader.setUniform("color", color);
        lineShader.setUniform("p1", localPos1);
        lineShader.setUniform("p2", localPos2);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glLineWidth(width);
        glDrawArrays(GL_LINES, 0, 2);
        renderPoint(localPos1, width, color);
        renderPoint(localPos2, width, color);
    }

    public static class GUIObject {
        Vec position;
        Vec size;
        List<GUIObject> children = new ArrayList<>();
        List<Object> elements = new ArrayList<>();
        GUIObject clipTo;

        public GUIObject(Vec position, Vec size, GUIObject clipTo) {
            this.position = position;
            this.size = size;
            this.clipTo = clipTo;
        }
        public void addChild(GUIObject child) {
            children.add(child);
        }
        public void addElement(Object element) {
            elements.add(element);
        }
        public void toMouse() {
            Vec offset = ((Controller.mousePos.sub(lastMouse))).div(new Vec(Run.WIDTH, Run.HEIGHT));
            position = position.add(new Vec(offset.x, -offset.y));
            if (!(clipTo == null)) clipTo.position = clipTo.position.add(new Vec(offset.x, -offset.y));
        }
    }
    public static class GUILabel {
        Vec position;
        String text;
        float scale;
        Vec color;

        public GUILabel(Vec position, String text, float scale, Vec color) {
            this.position = position;
            this.text = text;
            this.scale = scale;
            this.color = color;
        }
        public void setText(String text) {
            this.text = text;
        }
    }
    public static class GUIButton {
        Vec position;
        Vec size;
        GUILabel label;
        GUIQuad quad;
        List<Runnable> actions;
        boolean holdable;
        boolean hovered;
        private boolean interacted = false;
        private boolean wasLMBdown = false;
        private boolean mousePressedInside = false;
        int ID;

        public GUIButton(Vec position, Vec size, GUILabel label, GUIQuad quad, List<Runnable> actions, boolean holdable) {
            this.position = position;
            this.size = size;
            this.label = label;
            this.quad = quad;
            this.actions = actions;
            this.holdable = holdable;
            ID = interactables++;
        }
        public GUIButton(Vec position, Vec size, GUILabel label, GUIQuad quad, Runnable action, boolean holdable) {
            this.position = position;
            this.size = size;
            this.label = label;
            this.quad = quad;
            this.actions = new ArrayList<>();
            actions.add(action);
            this.holdable = holdable;
            ID = interactables++;
        }

        public void doButton(Vec mousePos, Vec buttonMin, Vec buttonMax) {
            // Map normalized bottom left 0,0 with up right size to pixel coordinates with top left 0,0
            Vec screenSpaceMin = new Vec(buttonMin.x * Run.WIDTH, (1 - buttonMax.y) * Run.HEIGHT);
            Vec screenSpaceMax = new Vec(buttonMax.x * Run.WIDTH, (1 - buttonMin.y) * Run.HEIGHT);
            hovered = false;
            boolean insideButton = (mousePos.x > screenSpaceMin.x && mousePos.x < screenSpaceMax.x &&
                    mousePos.y > screenSpaceMin.y && mousePos.y < screenSpaceMax.y);
            if (insideButton) {
                hovered = true;
                if (Controller.LMBdown && !wasLMBdown) {
                    mousePressedInside = true;
                }
                if (Controller.LMBdown && mousePressedInside) {
                    if (!holdable) {
                        if (mouseInteractingWith == -1 && !interacted) {
                            runAllActions();
                            interacted = true;
                        }
                    } else {
                        if (mouseInteractingWith == -1 || mouseInteractingWith == ID) {
                            mouseInteractingWith = ID;
                            if (!interacted) {
                                lastMouse = new Vec(Controller.mousePos.x, Controller.mousePos.y);
                            }
                            interacted = true;
                            runAllActions();
                        }
                    }
                }
            }
            if (!Controller.LMBdown) {
                interacted = false;
                mousePressedInside = false;
                if (mouseInteractingWith == ID) mouseInteractingWith = -1;
            }
            wasLMBdown = Controller.LMBdown;
        }
        private void runAllActions() {
            for (Runnable action : actions) {
                action.run();
            }
        }
    }
    public class GUIQuad {
        Vec position;
        Vec size;
        Vec color;

        public GUIQuad(Vec position, Vec size, Vec color) {
            this.position = position;
            this.size = size;
            this.color = color;
        }
        public GUIQuad(Vec color) {
            position = new Vec(0);
            size = new Vec(1);
            this.color = color;
        }
    }
    public class GUISlider {
        Vec position;
        Vec size;
        GUILabel label;
        GUIQuad quad;
        float Lbound;
        float Rbound; 
        public float value;
        Vec lineColor;
        Vec pointColor;
        private boolean held = false;
        int ID;

        public GUISlider(Vec position, Vec size, GUILabel label, GUIQuad quad, float Lbound, float Rbound, Vec lineColor, Vec pointColor, float startValue) {
            this.position = position;
            this.size = size;
            this.label = label;
            this.quad = quad;
            this.Lbound = Lbound;
            this.Rbound = Rbound;
            this.lineColor = lineColor;
            this.pointColor = pointColor;
            this.value = startValue;
            ID = interactables++;
        }

        public void doSlider(Vec mousePos, Vec pointPos, Vec p1, Vec p2) {
            // Map normalized bottom left 0,0 with up right size to pixel coordinates with top left 0,0
            float screenSpaceP1x = (float) (p1.x*Run.WIDTH);
            float screenSpaceP2x = (float) (p2.x*Run.WIDTH);
            Vec screenSpacePos = new Vec(pointPos.x*Run.WIDTH, (1-pointPos.y)*Run.HEIGHT);
            Vec screenSpaceMin = screenSpacePos.sub(new Vec(20));
            Vec screenSpaceMax = screenSpacePos.add(new Vec(20));
            //check if pressed
            boolean hovered = false;
            if (mousePos.x > screenSpaceMin.x && mousePos.x < screenSpaceMax.x) {
                if (mousePos.y > screenSpaceMin.y && mousePos.y < screenSpaceMax.y) {
                    hovered = true;
                    renderPoint(pointPos, 30, pointColor.mult(0.5));
                    renderPoint(pointPos, 20, pointColor);
                }
            }
            if (Controller.LMBdown) {
                if (hovered && mouseInteractingWith == -1) {
                    float percent = (float) ((mousePos.x - screenSpaceP1x) / (screenSpaceP2x - screenSpaceP1x));
                    value = clamp(Lbound + percent*(Rbound-Lbound), Lbound, Rbound);
                    held = true;
                }
                if (held) {
                    if (mouseInteractingWith == -1 || mouseInteractingWith == ID) {
                        mouseInteractingWith = ID;
                        float percent = (float) ((mousePos.x - screenSpaceP1x) / (screenSpaceP2x - screenSpaceP1x));
                        value = clamp(Lbound + percent * (Rbound - Lbound), Lbound, Rbound);
                    }
                } else {
                    held = false;
                    if (mouseInteractingWith == ID) mouseInteractingWith = -1;
                }
            } else {
                held = false;
            }
        }
    }
    public class GUIScroller {
        Vec position;
        Vec size;
        GUILabel label;
        GUIQuad quad;
        float Lbound;
        float Rbound;
        public float value;
        Vec lineColor;
        Vec pointColor;
        float totalGUI;
        //subline should be length visibleGUI(1)/totalGUI 
        private boolean held = false;
        int ID;

        public GUIScroller(Vec position, Vec size, GUILabel label, GUIQuad quad, float Lbound, float Rbound, Vec lineColor, Vec pointColor, float startValue) {
            this.position = position;
            this.size = size;
            this.label = label;
            this.quad = quad;
            this.Lbound = Lbound;
            this.Rbound = Rbound;
            this.lineColor = lineColor;
            this.pointColor = pointColor;
            this.value = startValue;
            ID = interactables++;
        }
        void setTotalGUI(float totalGUI) {
            this.totalGUI = totalGUI;
        }

        public void doScroller(Vec mousePos, Vec pointPos, Vec p1, Vec p2) {
            // Map normalized bottom left 0,0 with up right size to pixel coordinates with top left 0,0
            float screenSpaceP1y = (float) (p1.y*Run.HEIGHT);
            float screenSpaceP2y = (float) (p2.y*Run.HEIGHT);
            Vec screenSpacePos = new Vec(pointPos.x*Run.WIDTH, (1-pointPos.y)*Run.HEIGHT);
            Vec screenSpaceMin = screenSpacePos.sub(new Vec(20));
            Vec screenSpaceMax = screenSpacePos.add(new Vec(20));
            //check if pressed
            boolean hovered = false;
            if (mousePos.x > screenSpaceMin.x && mousePos.x < screenSpaceMax.x) {
                if (mousePos.y > screenSpaceMin.y && mousePos.y < screenSpaceMax.y) {
                    hovered = true;
                    renderPoint(pointPos, 30, pointColor.mult(0.5));
                    renderPoint(pointPos, 20, pointColor);
                }
            }
            if (Controller.LMBdown) {
                if (hovered && mouseInteractingWith == -1) {
                    float percent = (float) ((Run.HEIGHT - mousePos.y - screenSpaceP1y) / (screenSpaceP2y - screenSpaceP1y));
                    value = clamp(Lbound + percent*(Rbound-Lbound), Lbound, Rbound);
                    held = true;
                }
                if (held) {
                    if (mouseInteractingWith == -1 || mouseInteractingWith == ID) {
                        mouseInteractingWith = ID;
                        float percent = (float) ((Run.HEIGHT - mousePos.y - screenSpaceP1y) / (screenSpaceP2y - screenSpaceP1y));
                        value = clamp(Lbound + percent*(Rbound-Lbound), Lbound, Rbound);
                    }
                } else {
                    held = false;
                    if (mouseInteractingWith == ID) mouseInteractingWith = -1;
                }
            } else {
                held = false;
            }
            value = clamp(value, 0, 1-(clamp(clipTo.size.y / totalGUI, 0, 1)));
        }
    }

    public static void renderQuad(Vec position, Vec scale, Vec color) {
        glBindVertexArray(VAO);
        backgroundShader.setUniform("color", color);
        backgroundShader.setUniform("position", position);
        backgroundShader.setUniform("scale", scale);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }
    public static class TextRenderer {
        private int VAO, VBO;
        public static int fontTexture;
        private STBTTBakedChar.Buffer charData;
        private final int BITMAP_WIDTH = 512;
        private final int BITMAP_HEIGHT = 512;

        public TextRenderer(String fontPath) {
            // Compile and link the shader program

            // Create VAO and VBO for text rendering
            VAO = glGenVertexArrays();
            VBO = glGenBuffers();

            glBindVertexArray(VAO);
            glBindBuffer(GL_ARRAY_BUFFER, VBO);

            // Allocate buffer (we'll update it when rendering each character)
            glBufferData(GL_ARRAY_BUFFER, 6 * 4 * Float.BYTES, GL_DYNAMIC_DRAW);

            // Position attribute
            glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);

            // Texture coordinates attribute
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
            glEnableVertexAttribArray(1);

            glBindVertexArray(0);

            // Load the font texture
            fontTexture = loadFontTexture(fontPath);
        }

        public void renderText(String text, float x, float y, float scale, Vec color) {
            //x and y are screen percent where 0 0 is center, 0.5 0.5 bring right top bound, - is left bottom bound
            textShader.useProgram();
            textShader.setUniform("textTexture", 0);
            textShader.setUniform("textTexture", 0);
            textShader.setUniform("width", Run.WIDTH);
            textShader.setUniform("height", Run.HEIGHT);
            textShader.setUniform("textColor", color);
            textShader.setUniform("pos", new Vec(x,y));

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, fontTexture);

            // Enable blending for font rendering
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            glBindVertexArray(VAO);
            glBindBuffer(GL_ARRAY_BUFFER, VBO);

            // Render each character
            float xpos = 0;
            y = 0;
            //float xpos = x*Run.WIDTH;
            //y *= -Run.HEIGHT;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 32 || c > 127) continue; // Skip characters outside the range

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    FloatBuffer xposBuffer = stack.floats(xpos);
                    FloatBuffer yposBuffer = stack.floats(y);

                    // Get character data
                    STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
                    stbtt_GetBakedQuad(charData, BITMAP_WIDTH, BITMAP_HEIGHT, c - 32, xposBuffer, yposBuffer, q, true);
                    // Update xpos for next character
                    xpos = xposBuffer.get(0);

                    // Calculate vertex positions and texture coordinates
                    float[] vertices = {
                            // Position         // Texture Coordinates
                            q.x0() * scale, q.y0() * scale, q.s0(), q.t0(), // Bottom Left
                            q.x1() * scale, q.y0() * scale, q.s1(), q.t0(), // Bottom Right
                            q.x1() * scale, q.y1() * scale, q.s1(), q.t1(), // Top Right

                            q.x0() * scale, q.y0() * scale, q.s0(), q.t0(), // Bottom Left
                            q.x1() * scale, q.y1() * scale, q.s1(), q.t1(), // Top Right
                            q.x0() * scale, q.y1() * scale, q.s0(), q.t1()  // Top Left
                    };
                    // Update VBO with new vertex data
                    glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
                    // Draw character
                    glBindFramebuffer(GL_FRAMEBUFFER, 0);
                    glDrawArrays(GL_TRIANGLES, 0, 6);
                }
            }

            glBindVertexArray(0);
            glDisable(GL_BLEND);
        }

        private int loadFontTexture(String fontPath) {
            // Load font file
            try {
                ByteBuffer fontBuffer = ioResourceToByteBuffer(fontPath, 512 * 1024);

                // Initialize character data buffer (ASCII 32-127)
                charData = STBTTBakedChar.malloc(96);

                // Create bitmap for font
                ByteBuffer bitmap = createByteBuffer(BITMAP_WIDTH * BITMAP_HEIGHT);

                // Bake font to bitmap
                stbtt_BakeFontBitmap(fontBuffer, 32.0f, bitmap, BITMAP_WIDTH, BITMAP_HEIGHT, 32, charData);

                // Generate and configure texture
                int texture = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, BITMAP_WIDTH, BITMAP_HEIGHT, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);

                // Set texture parameters
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

                return texture;
            } catch (RuntimeException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // Utility method to load resources
        public static ByteBuffer ioResourceToByteBuffer(String resource, int bufferSize) throws IOException {
            ByteBuffer buffer;

            Path path = resource.startsWith("http") ? null : Paths.get(resource);
            if (path != null && Files.isReadable(path)) {
                try (SeekableByteChannel fc = Files.newByteChannel(path)) {
                    buffer = createByteBuffer((int)fc.size() + 1);
                    while (fc.read(buffer) != -1) {
                        ;
                    }
                }
            } else {
                try (
                        InputStream source = resource.startsWith("http")
                                ? new URL(resource).openStream()
                                : IOUtil.class.getClassLoader().getResourceAsStream(resource);
                        ReadableByteChannel rbc = Channels.newChannel(source)
                ) {
                    buffer = createByteBuffer(bufferSize);

                    while (true) {
                        int bytes = rbc.read(buffer);
                        if (bytes == -1) {
                            break;
                        }
                        if (buffer.remaining() == 0) {
                            buffer = resizeBuffer(buffer, buffer.capacity() * 3 / 2); // 50%
                        }
                    }
                }
            }

            buffer.flip();
            return memSlice(buffer);
        }
    }
}
