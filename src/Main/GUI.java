package Main;

import Datatypes.Shader;
import Datatypes.Vec;
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

import static Util.IOUtil.resizeBuffer;
import static org.lwjgl.BufferUtils.createByteBuffer;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryUtil.memSlice;

public class GUI {
    static TextRenderer textRenderer;

    public GUI() {
        textRenderer = new TextRenderer("C:\\Graphics\\rasterizer\\local_resources\\LexendDeca-VariableFont_wght.ttf");
    }

    public class GUIElement {

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
            float xpos = x*Run.WIDTH;
            y *= -Run.HEIGHT;
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