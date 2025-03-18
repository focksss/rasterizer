package Main;

import Datatypes.Shader;
import Datatypes.Vec;
import ModelHandler.Obj;
import Util.IOUtil;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.system.MemoryStack;
import org.joml.Matrix4f;

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
import static org.lwjgl.glfw.GLFW.*;
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
    static int VAO;

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
        GUIObject mainObject = new GUIObject(new Vec(0.1,0.1), new Vec(0.3,0.8));
        GUIObject subObject = new GUIObject(new Vec(0.05,0.05), new Vec(0.9,0.1));
        GUILabel label1 = new GUILabel(new Vec(-0.2,-0.5), "https://github.com/focksss/rasterizer", 0.8f, new Vec(1));
        GUILabel label2 = new GUILabel(new Vec(0.1,0.9), "this is a menu", 1, new Vec(1));
        GUIButton button1 = new GUIButton(new Vec(0.05, 0.5), new Vec(0.9,0.1), "Recompile Shaders", new Vec(0.1), Run::compileShaders);

        mainObject.addElement(quad1);
        mainObject.addElement(label2);
        mainObject.addElement(button1);
        mainObject.addChild(subObject);

        subObject.addElement(quad2);
        subObject.addElement(label1);

        objects.add(mainObject);
    }

    public static void renderGUI() {
        glDisable(GL_CULL_FACE);
        for (GUIObject object : objects) {
            renderObject(object, new Vec(0), new Vec(1));
        }
    }
    private static void renderObject(GUIObject object, Vec parentPosition, Vec parentScale) {
        Vec localPos = parentPosition.add(parentScale.mult(object.position));
        Vec localSize = parentScale.mult(object.size);
        for (Object element : object.elements) {
            renderElement(element, localPos, localSize);
        }
        for (GUIObject child : object.children) {
            renderObject(child, localPos, localSize);
        }
    }
    private static void renderElement(Object element, Vec localPos, Vec localSize) {
        if (element instanceof GUILabel) {
            GUILabel label = (GUILabel) element;
            Vec pos = localPos.add(localSize.mult(label.position));
            pos.updateFloats();
            textRenderer.renderText(label.text, pos.xF, pos.yF, label.scale, label.color);
        } else if (element instanceof GUIButton) {
            GUIButton button = (GUIButton) element;
            Vec pos = localPos.add(localSize.mult(button.position));
            Vec size = localSize.mult(button.size);
            pos.updateFloats();
            size.updateFloats();
            renderQuad(pos, size, button.backgroundColor);
            textRenderer.renderText(button.text, pos.xF, pos.yF, 1, button.color);
            button.doButton(Run.controller.mousePos, pos, pos.add(size));
        } else if (element instanceof GUIQuad) {
            GUIQuad quad = (GUIQuad) element;
            Vec pos = localPos.add(localSize.mult(quad.position));
            Vec size = localSize.mult(quad.size);
            pos.updateFloats();
            size.updateFloats();
            renderQuad(pos, size, quad.color);
        }
    }

    public class GUIObject {
        Vec position;
        Vec size;
        List<GUIObject> children = new ArrayList<>();
        List<Object> elements = new ArrayList<>();

        public GUIObject(Vec position, Vec size) {
            this.position = position;
            this.size = size;
        }
        public void addChild(GUIObject child) {
            children.add(child);
        }
        public void addElement(Object element) {
            elements.add(element);
        }
    }
    public class GUILabel {
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
    public class GUIButton {
        Vec color;
        Vec position;
        Vec size;
        String text;
        Vec backgroundColor;
        private Runnable action;

        public GUIButton(Vec position, Vec size, String text, Vec color, Runnable action) {
            this.position = position;
            this.size = size;
            this.text = text;
            this.color = color;
            this.action = action;
        }

        public void doButton(Vec mousePos, Vec buttonMin, Vec buttonMax) {
            // Map normalized bottom left 0,0 with up right size to pixel coordinates with top left 0,0
            Vec screenSpaceMin = new Vec(buttonMin.x*Run.WIDTH, (1-buttonMax.y)*Run.HEIGHT);
            Vec screenSpaceMax = new Vec(buttonMax.x*Run.WIDTH, (1-buttonMin.y)*Run.HEIGHT);
            //check if pressed
            if (mousePos.x > screenSpaceMin.x && mousePos.x < screenSpaceMax.x) {
                if (mousePos.y > screenSpaceMin.y && mousePos.y < screenSpaceMax.y) {
                    if (glfwGetMouseButton(Run.window, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS) action.run();
                }
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

    public static void renderQuad(Vec position, Vec scale, Vec color) {
        glBindVertexArray(VAO);
        backgroundShader.setUniform("color", color);
        backgroundShader.setUniform("position", position);
        backgroundShader.setUniform("scale", scale);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }
    public static class TextRenderer {
        Shader shaderProgram;
        private int VAO, VBO;
        public static int fontTexture;
        private STBTTBakedChar.Buffer charData;
        private final int BITMAP_WIDTH = 512;
        private final int BITMAP_HEIGHT = 512;

        public TextRenderer(String fontPath) {
            // Compile and link the shader program
            shaderProgram = new Shader("src\\shaders\\text_shader\\text_shader.frag", "src\\shaders\\text_shader\\text_shader.vert");

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
            shaderProgram.useProgram();
            shaderProgram.setUniform("textTexture", 0);
            shaderProgram.setUniform("textTexture", 0);
            shaderProgram.setUniform("width", Run.WIDTH);
            shaderProgram.setUniform("height", Run.HEIGHT);
            shaderProgram.setUniform("textColor", color);

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, fontTexture);

            // Enable blending for font rendering
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            glBindVertexArray(VAO);
            glBindBuffer(GL_ARRAY_BUFFER, VBO);

            // Render each character
            float xpos = -Run.WIDTH*0.5f + x*Run.WIDTH;
            y = (Run.HEIGHT*0.5f - y*Run.HEIGHT);
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