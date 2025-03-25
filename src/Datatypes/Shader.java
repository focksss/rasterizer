package Datatypes;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;

public class Shader {
    public int vertex_shader;
    public int fragment_shader;
    public String vertex_shader_file;
    public String fragment_shader_file;
    public int program;

    public Shader(String fragPath, String vertPath) {
        try {
            this.vertex_shader_file = getString(vertPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            this.fragment_shader_file = getString(fragPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.vertex_shader = createShader(GL_VERTEX_SHADER, vertex_shader_file);
        this.fragment_shader = createShader(GL_FRAGMENT_SHADER, fragment_shader_file);
        glFinish();

        program = glCreateProgram();
        glAttachShader(program, vertex_shader);
        glAttachShader(program, fragment_shader);
        glLinkProgram(program);
        checkProgramError(program);
    }
    public void useProgram() {
        glUseProgram(program);
    }
    public void setUniform(String name, Object value) {
        useProgram();
        if (value instanceof Integer ) {
            glUniform1i(glGetUniformLocation(program, name), (Integer) value);
        } else if (value instanceof Float ) {
            glUniform1f(glGetUniformLocation(program, name), (Float) value);
        } else if (value instanceof Matrix4f) {
            Matrix4f matrix = (Matrix4f) value;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                glUniformMatrix4fv(GL20.glGetUniformLocation(program, name), false, matrix.get(stack.mallocFloat(16)));
            }
        } else if (value instanceof Vector3f) {
            Vector3f vector = (Vector3f) value;
            glUniform3f(glGetUniformLocation(program, name), vector.x, vector.y, vector.z);
        } else if (value instanceof Vec) {
            Vec vec = (Vec) value;
            vec.updateFloats();
            glUniform3f(glGetUniformLocation(program, name), vec.xF, vec.yF, vec.zF);
        } else if (value instanceof Boolean) {
            boolean bool = (Boolean) value;
            glUniform1i(glGetUniformLocation(program, name), bool ? 1 : 0);
        }
    }
    public void setUniformArray(String name, int[] values) {
        useProgram();
        IntBuffer buffer = MemoryUtil.memAllocInt(10);
        for (int i = 0; i < values.length; i++) buffer.put(values[i]);
        glUniform1iv(glGetUniformLocation(program, name), buffer);
    }
    public void setUniformTexture(String name, int texture, int id) {
        glActiveTexture(GL_TEXTURE0 + id);
        glBindTexture(GL_TEXTURE_2D, texture);
        this.setUniform(name, id);
    }
    public void setUniformCubemap(String name, int texture, int id) {
        glActiveTexture(GL_TEXTURE0 + id);
        glBindTexture(GL_TEXTURE_CUBE_MAP, texture);
        this.setUniform(name, id);
    }

    private static int createShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        checkShaderError(shader, type == GL_COMPUTE_SHADER ? "compute" :
                type == GL_VERTEX_SHADER ? "vertex" : "fragment");
        return shader;
    }
    private static void checkShaderError(int shader, String type) {
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            throw new RuntimeException(type + " shader compilation failed: " + log);
        }
    }
    private static void checkProgramError(int program) {
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            throw new RuntimeException("Program linking failed: " + log);
        }
    }
    private static String getString(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }
}
