package Main;

import Datatypes.Shader;
import Datatypes.Vec;
import Util.IOUtil;

import static Main.Controller.mousePos;
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
 * <p>
 * Clipping is done relative to the parent, to clip an object to its own bounds, don't set clip parameters in the constructor or set them to be the same as the objects own position and scale
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
        GUIQuad quad4 = new GUIQuad(new Vec(0.25));
        GUIQuad quad2 = new GUIQuad(new Vec(0.2));
        GUIQuad quad3 = new GUIQuad(new Vec(0.8,0.2,0.2));

        GUIObject main = new GUIObject(new Vec(0.1, 0.1), new Vec(0.3, 0.8));
        GUIObject settings = new GUIObject(new Vec(0.05,0.2), new Vec(0.9, 0.65));
        GUIObject settingsElements = new GUIObject(new Vec(0,0), new Vec(1, 1), new Vec(0,-10), new Vec(1, 20));
        GUIObject fps = new GUIObject(new Vec(0.05, 0.05), new Vec(0.25, 0.1));
        GUIObject position = new GUIObject(new Vec(0.35, 0.05), new Vec(0.6, 0.1));

        GUILabel emptyText = new GUILabel(new Vec(), "", 0f, new Vec());
        GUILabel fpsText = new GUILabel(new Vec(0.05, 0.4), "", 1f, new Vec(1));
        GUILabel posText = new GUILabel(new Vec(0.05, 0.4), "", 1f, new Vec(1));

        GUILabel settingsText = new GUILabel(new Vec(0.1, 0.9), "Settings", 2, new Vec(1));
        GUILabel recompileText = new GUILabel(new Vec(0.1, 0.4), "Recompile Shaders", 1, new Vec(1));
        GUILabel screenshotText = new GUILabel(new Vec(0.1, 0.4), "Take Screenshot", 1, new Vec(1));
        GUILabel exposureText = new GUILabel(new Vec(0.05, 0.65), "Exposure", 0.8f, new Vec(1));
        GUILabel gammaText = new GUILabel(new Vec(0.05, 0.65), "Gamma", 0.8f, new Vec(1));
        GUILabel exitText = new GUILabel(new Vec(0.1, 0.4), "Exit", 1f, new Vec(0));
        GUILabel FOVtext = new GUILabel(new Vec(0.05, 0.65), "FOV: ", 0.8f, new Vec(1));
        GUILabel SSAOrText = new GUILabel(new Vec(0.05, 0.65), "SSAO radius: ", 0.8f, new Vec(1));
        GUILabel SSAObText = new GUILabel(new Vec(0.05, 0.65), "SSAO bias: ", 0.8f, new Vec(1));
        GUILabel bloomRText = new GUILabel(new Vec(0.05, 0.65), "Bloom radius: ", 0.8f, new Vec(1));
        GUILabel bloomIText = new GUILabel(new Vec(0.05, 0.65), "Bloom intensity: ", 0.8f, new Vec(1));
        GUILabel bloomTText = new GUILabel(new Vec(0.05, 0.65), "Bloom threshold: ", 0.8f, new Vec(1));
        GUILabel fpsCapText = new GUILabel(new Vec(0.05, 0.65), "Max FPS: ", 0.8f, new Vec(1));

        GUIButton exit = new GUIButton(new Vec(0.8, 0.9), new Vec(0.15, 0.05), exitText, quad3, Run::Quit, false, false);
        GUIButton recompile = new GUIButton(new Vec(0.05, 0.85), new Vec(0.9, 0.1), recompileText, quad2, Run::compileShaders, false, false);
        GUIButton screenshot = new GUIButton(new Vec(0.05, 0.7), new Vec(0.9, 0.1), screenshotText, quad2, Controller::screenshot, false, false);
        GUISlider exposure = new GUISlider(new Vec(0.05, 0.55), new Vec(0.9, 0.1), exposureText, quad2, 0, 30, new Vec(1), new Vec(1), Run.EXPOSURE);
        GUISlider gamma = new GUISlider(new Vec(0.05, 0.4), new Vec(0.9, 0.1), gammaText, quad2, 0, 2, new Vec(1), new Vec(1), Run.GAMMA);
        GUISlider FOV = new GUISlider(new Vec(0.05, 0.25), new Vec(0.9, 0.1), FOVtext, quad2, 0.001f, 179.999f, new Vec(1), new Vec(1), Run.FOV);
        GUISlider SSAOr = new GUISlider(new Vec(0.05, 0.1), new Vec(0.9, 0.1), SSAOrText, quad2, 0, 20, new Vec(1), new Vec(1), Run.SSAOradius);
        GUISlider SSAOb = new GUISlider(new Vec(0.05, -0.05), new Vec(0.9, 0.1), SSAObText, quad2, 0, 0.1f, new Vec(1), new Vec(1), Run.SSAObias);
        GUISlider bloomR = new GUISlider(new Vec(0.05, -0.2), new Vec(0.9, 0.1), bloomRText, quad2, 0, 1f, new Vec(1), new Vec(1), Run.bloomRadius);
        GUISlider bloomI = new GUISlider(new Vec(0.05, -0.35), new Vec(0.9, 0.1), bloomIText, quad2, 0, 1f, new Vec(1), new Vec(1), Run.bloomIntensity);
        GUISlider bloomT = new GUISlider(new Vec(0.05, -0.5), new Vec(0.9, 0.1), bloomTText, quad2, 0, 10f, new Vec(1), new Vec(1), Run.bloomThreshold);
        GUISlider maxFPS = new GUISlider(new Vec(0.05, -0.65), new Vec(0.9, 0.1), fpsCapText, quad2, 1, 240f, new Vec(1), new Vec(1), Run.FPS);
        //
        GUIButton moveGUI = new GUIButton(new Vec(0, 0.975), new Vec(1, 0.025), emptyText, quad2, main::toMouse, true, true);
        GUIScroller scroll = new GUIScroller(new Vec(0.95, 0.05), new Vec(0.05, 0.9), emptyText, quad4, 0, settings, new Vec(0.1), new Vec(0.2), 0, 8);
        scroll.setTotalGUI((1f - (-0.65f)) + 0.05f); // absTop - bottom + buffer space

        main.addElement(quad1);
        main.addElement(moveGUI);
        main.addElement(settingsText);
        main.addElement(exit);
        settings.addElement(quad4);
        settings.addElement(scroll);
        settings.addChild(settingsElements);
        settingsElements.addElement(recompile);
        settingsElements.addElement(recompile);
        settingsElements.addElement(screenshot);
        settingsElements.addElement(exposure);
        settingsElements.addElement(gamma);
        settingsElements.addElement(FOV);
        settingsElements.addElement(SSAOr);
        settingsElements.addElement(SSAOb);
        settingsElements.addElement(bloomR);
        settingsElements.addElement(bloomI);
        settingsElements.addElement(bloomT);
        settingsElements.addElement(maxFPS);

        main.addChild(settings);
        main.addChild(fps);
        main.addChild(position);

        fps.addElement(quad2);
        fps.addElement(fpsText);
        position.addElement(quad2);
        position.addElement(posText);

        objects.add(main);
    }

    public static void renderGUI() {
        glDisable(GL_CULL_FACE);
        for (GUIObject object : objects) {
            renderObject(object, new Vec(0), new Vec(1), new Vec(0), new Vec(1));
        }
        lastMouse = mousePos;
    }
    private static void renderObject(GUIObject object, Vec parentPosition, Vec parentScale, Vec parentClipMin, Vec parentClipMax) {
        Vec localPos = parentPosition.add(parentScale.mult(object.position));
        Vec localSize = parentScale.mult(object.size);

        Vec localClipMin = (parentPosition.add(parentScale.mult(object.clipPosition)));
        localClipMin.growTo(parentClipMin);
        Vec localClipSize = parentScale.mult(object.clipSize);
        Vec localClipMax = (localClipMin.add(localClipSize));
        localClipMax.shrinkTo(parentClipMax);

        object.checkHover(localPos, localPos.add(localSize));

        Vec clipMin = localClipMin.mult(new Vec(Run.WIDTH, Run.HEIGHT));
        Vec clipMax = localClipMax.mult(new Vec(Run.WIDTH, Run.HEIGHT));
        setClipBounds(clipMin, clipMax);

        for (Object element : object.elements) {
            renderElement(element, localPos, localSize);
        }
        for (GUIObject child : object.children) {
            renderObject(child, localPos, localSize, localClipMin, localClipMax);
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
            renderQuad(pos, size, button.quad.color.mult(button.hovered ? 1.1 : 1));
            renderLabel(button.label, pos, size);
            button.doButton(mousePos, pos, pos.add(size));
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
            slider.doSlider(mousePos, pointPos, p1, p2);
        } else if (element instanceof GUIScroller) {
            GUIScroller scroller = (GUIScroller) element;
            Vec pos = localPos.add(localSize.mult(scroller.position));
            Vec size = localSize.mult(scroller.size);
            renderQuad(pos, size, scroller.quad.color);
            renderLabel(scroller.label, pos, size);
            Vec l1 = pos.add(size.mult(new Vec(0.5,0.05)));
            Vec l2 = pos.add(size.mult(new Vec(0.5, 0.95)));
            renderLine(l1, l2, scroller.width, scroller.lineColor);
            double percent = (scroller.value - scroller.Lbound) / ((scroller.totalGUI - scroller.Lbound));
            Vec pointPos = l2.add((l1.sub(l2)).mult(percent));
            Vec s1 = l2.add((l1.sub(l2)).mult((percent)*(1-scroller.barSize)));
            Vec s2 = l2.add((l1.sub(l2)).mult((percent)*(1-scroller.barSize) + scroller.barSize));
            renderLine(s1, s2, scroller.width, scroller.pointColor);
            scroller.doScroller(mousePos, s1, s2, l1, l2, percent);
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
        Vec clipPosition;
        Vec clipSize;
        List<GUIObject> children = new ArrayList<>();
        List<Object> elements = new ArrayList<>();
        boolean hovered;

        public GUIObject(Vec position, Vec size, Vec clipPosition, Vec clipSize) {
            this.position = position;
            this.size = size;
            this.clipPosition = clipPosition;
            this.clipSize = clipSize;
        }
        public GUIObject(Vec position, Vec size) {
            this.position = position;
            this.size = size;
            this.clipPosition = position;
            this.clipSize = size;
        }
        public void addChild(GUIObject child) {
            children.add(child);
        }
        public void addElement(Object element) {
            elements.add(element);
        }
        public void toMouse() {
            Vec offset = ((mousePos.sub(lastMouse))).div(new Vec(Run.WIDTH, Run.HEIGHT));
            position = position.add(new Vec(offset.x, -offset.y));
            clipPosition = clipPosition.add(new Vec(offset.x, -offset.y));
        }
        public void checkHover(Vec min, Vec max) {
            Vec screenSpaceMin = new Vec(min.x * Run.WIDTH, (1 - max.y) * Run.HEIGHT);
            Vec screenSpaceMax = new Vec(max.x * Run.WIDTH, (1 - min.y) * Run.HEIGHT);
            hovered = (mousePos.x > screenSpaceMin.x && mousePos.x < screenSpaceMax.x &&
                    mousePos.y > screenSpaceMin.y && mousePos.y < screenSpaceMax.y);
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
        boolean draggable;
        private boolean interacted = false;
        private boolean wasLMBdown = false;
        private boolean mousePressedInside = false;
        int ID;

        public GUIButton(Vec position, Vec size, GUILabel label, GUIQuad quad, List<Runnable> actions, boolean holdable, boolean draggable) {
            this.position = position;
            this.size = size;
            this.label = label;
            this.quad = quad;
            this.actions = actions;
            this.holdable = holdable;
            this.draggable = draggable;
            ID = interactables++;
        }
        public GUIButton(Vec position, Vec size, GUILabel label, GUIQuad quad, Runnable action, boolean holdable, boolean draggable) {
            this.position = position;
            this.size = size;
            this.label = label;
            this.quad = quad;
            this.actions = new ArrayList<>();
            actions.add(action);
            this.holdable = holdable;
            this.draggable = draggable;
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
            } else if (mouseInteractingWith == ID && Controller.LMBdown && draggable) {
                runAllActions();
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
        GUIObject linked;
        float Lbound;
        public float value;
        float width;
        Vec lineColor;
        Vec pointColor;
        float totalGUI;
        //subline should be length visibleGUI(1)/totalGUI
        float barSize;
        private boolean held = false;
        private double initialClickPercent;
        private boolean firstInteract = true;
        private boolean linkedHovered = false;
        private double scrollRawStart;
        int ID;

        public GUIScroller(Vec position, Vec size, GUILabel label, GUIQuad quad, float Lbound, GUIObject linked, Vec lineColor, Vec pointColor, float startValue, float width) {
            this.position = position;
            this.size = size;
            this.label = label;
            this.quad = quad;
            this.Lbound = Lbound;
            this.linked = linked;
            this.lineColor = lineColor;
            this.pointColor = pointColor;
            this.value = startValue;
            this.width = width;
            ID = interactables++;
        }
        void setTotalGUI(float totalGUI) {
            this.totalGUI = (float) (totalGUI-1);
            this.barSize = (float) ((linked.clipSize.y/linked.size.y)/totalGUI);
        }

        public void doScroller(Vec mousePos, Vec s1, Vec s2, Vec l1, Vec l2, double percent) {
            // Map normalized bottom left 0,0 with up right size to pixel coordinates with top left 0,0

            //Map the usable scroll bar to account for the bars size
            Vec effectiveL1 = l1.add((l2.sub(l1)).mult((barSize)));
            float screenSpaceEffectiveL1y = (float) (effectiveL1.y * Run.HEIGHT);
            float screenSpaceL2y = (float) (l2.y * Run.HEIGHT);

            Vec screenSpaceS1 = new Vec(s1.x * Run.WIDTH, (1 - s1.y) * Run.HEIGHT);
            Vec screenSpaceS2 = new Vec(s2.x * Run.WIDTH, (1 - s2.y) * Run.HEIGHT);
            Vec screenSpaceMin = screenSpaceS1.sub(new Vec(width));
            Vec screenSpaceMax = screenSpaceS2.add(new Vec(width));

            //make scroll bar brighter when hovered
            boolean hovered = mousePos.x > screenSpaceMin.x && mousePos.x < screenSpaceMax.x &&
                    mousePos.y > screenSpaceMin.y && mousePos.y < screenSpaceMax.y;
            if (hovered) {
                renderLine(s1, s2, width, pointColor.mult(1.1));
            }
            if (Controller.LMBdown) {
                //if was not previously interacting, and now is
                if (hovered && mouseInteractingWith == -1) {
                    held = true;
                }
                //if was not previously interacting, now is, or was already interacting and still is
                if (held && (mouseInteractingWith == -1 || mouseInteractingWith == ID)) {
                    //get mouse percent relative to usable bar
                    float moPercent = (float) ((Run.HEIGHT - mousePos.y - screenSpaceL2y) / (screenSpaceEffectiveL1y - screenSpaceL2y));
                    if (firstInteract) {
                        firstInteract = false;
                        initialClickPercent = moPercent - (value / totalGUI);
                    }
                    value = (float) (totalGUI*((moPercent - initialClickPercent)));
                    mouseInteractingWith = ID;
                }
            } else {
                held = false;
                firstInteract = true;
                if (mouseInteractingWith == ID) {
                    mouseInteractingWith = -1;
                }
            }
            if (linked.hovered) {
                if (!linkedHovered) {
                    scrollRawStart = Controller.scrollY;
                }
                linkedHovered = true;
                float scrollDelta = (float) ((Controller.scrollY - scrollRawStart) * 0.1f);
                value += scrollDelta;
                scrollRawStart = Controller.scrollY;
            } else {
                linkedHovered = false;
            }
            value = clamp(value, 0, totalGUI);
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
