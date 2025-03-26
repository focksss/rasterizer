package Util;

import Datatypes.*;
import Main.Run;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_CUBE_MAP_SEAMLESS;

public class textureUtil {
    public static int VAO;
    static float[] vertices = {
            // back face
            -1.0f, -1.0f, -1.0f,  0.0f,  0.0f, -1.0f, 0.0f, 0.0f, // bottom-left
            1.0f,  1.0f, -1.0f,  0.0f,  0.0f, -1.0f, 1.0f, 1.0f, // top-right
            1.0f, -1.0f, -1.0f,  0.0f,  0.0f, -1.0f, 1.0f, 0.0f, // bottom-right
            1.0f,  1.0f, -1.0f,  0.0f,  0.0f, -1.0f, 1.0f, 1.0f, // top-right
            -1.0f, -1.0f, -1.0f,  0.0f,  0.0f, -1.0f, 0.0f, 0.0f, // bottom-left
            -1.0f,  1.0f, -1.0f,  0.0f,  0.0f, -1.0f, 0.0f, 1.0f, // top-left
            // front face
            -1.0f, -1.0f,  1.0f,  0.0f,  0.0f,  1.0f, 0.0f, 0.0f, // bottom-left
            1.0f, -1.0f,  1.0f,  0.0f,  0.0f,  1.0f, 1.0f, 0.0f, // bottom-right
            1.0f,  1.0f,  1.0f,  0.0f,  0.0f,  1.0f, 1.0f, 1.0f, // top-right
            1.0f,  1.0f,  1.0f,  0.0f,  0.0f,  1.0f, 1.0f, 1.0f, // top-right
            -1.0f,  1.0f,  1.0f,  0.0f,  0.0f,  1.0f, 0.0f, 1.0f, // top-left
            -1.0f, -1.0f,  1.0f,  0.0f,  0.0f,  1.0f, 0.0f, 0.0f, // bottom-left
            // left face
            -1.0f,  1.0f,  1.0f, -1.0f,  0.0f,  0.0f, 1.0f, 0.0f, // top-right
            -1.0f,  1.0f, -1.0f, -1.0f,  0.0f,  0.0f, 1.0f, 1.0f, // top-left
            -1.0f, -1.0f, -1.0f, -1.0f,  0.0f,  0.0f, 0.0f, 1.0f, // bottom-left
            -1.0f, -1.0f, -1.0f, -1.0f,  0.0f,  0.0f, 0.0f, 1.0f, // bottom-left
            -1.0f, -1.0f,  1.0f, -1.0f,  0.0f,  0.0f, 0.0f, 0.0f, // bottom-right
            -1.0f,  1.0f,  1.0f, -1.0f,  0.0f,  0.0f, 1.0f, 0.0f, // top-right
            // right face
            1.0f,  1.0f,  1.0f,  1.0f,  0.0f,  0.0f, 1.0f, 0.0f, // top-left
            1.0f, -1.0f, -1.0f,  1.0f,  0.0f,  0.0f, 0.0f, 1.0f, // bottom-right
            1.0f,  1.0f, -1.0f,  1.0f,  0.0f,  0.0f, 1.0f, 1.0f, // top-right
            1.0f, -1.0f, -1.0f,  1.0f,  0.0f,  0.0f, 0.0f, 1.0f, // bottom-right
            1.0f,  1.0f,  1.0f,  1.0f,  0.0f,  0.0f, 1.0f, 0.0f, // top-left
            1.0f, -1.0f,  1.0f,  1.0f,  0.0f,  0.0f, 0.0f, 0.0f, // bottom-left
            // bottom face
            -1.0f, -1.0f, -1.0f,  0.0f, -1.0f,  0.0f, 0.0f, 1.0f, // top-right
            1.0f, -1.0f, -1.0f,  0.0f, -1.0f,  0.0f, 1.0f, 1.0f, // top-left
            1.0f, -1.0f,  1.0f,  0.0f, -1.0f,  0.0f, 1.0f, 0.0f, // bottom-left
            1.0f, -1.0f,  1.0f,  0.0f, -1.0f,  0.0f, 1.0f, 0.0f, // bottom-left
            -1.0f, -1.0f,  1.0f,  0.0f, -1.0f,  0.0f, 0.0f, 0.0f, // bottom-right
            -1.0f, -1.0f, -1.0f,  0.0f, -1.0f,  0.0f, 0.0f, 1.0f, // top-right
            // top face
            -1.0f,  1.0f, -1.0f,  0.0f,  1.0f,  0.0f, 0.0f, 1.0f, // top-left
            1.0f,  1.0f , 1.0f,  0.0f,  1.0f,  0.0f, 1.0f, 0.0f, // bottom-right
            1.0f,  1.0f, -1.0f,  0.0f,  1.0f,  0.0f, 1.0f, 1.0f, // top-right
            1.0f,  1.0f,  1.0f,  0.0f,  1.0f,  0.0f, 1.0f, 0.0f, // bottom-right
            -1.0f,  1.0f, -1.0f,  0.0f,  1.0f,  0.0f, 0.0f, 1.0f, // top-left
            -1.0f,  1.0f,  1.0f,  0.0f,  1.0f,  0.0f, 0.0f, 0.0f  // bottom-left
    };
    public static int VBO;
    public static Matrix4f proj = (new Matrix4f().identity()).perspective((float)Math.toRadians(90.0f), 1.0f, 0.1f, 10.0f);
    public static Matrix4f[] views = new Matrix4f[]{
            (new Matrix4f().identity()).lookAt(new Vector3f(0,0,0), new Vector3f(1,0,0), new Vector3f(0,-1,0)),
            (new Matrix4f().identity()).lookAt(new Vector3f(0,0,0), new Vector3f(-1,0,0), new Vector3f(0,-1,0)),
            (new Matrix4f().identity()).lookAt(new Vector3f(0,0,0), new Vector3f(0,1,0), new Vector3f(0,0,1)),
            (new Matrix4f().identity()).lookAt(new Vector3f(0,0,0), new Vector3f(0,-1,0), new Vector3f(0,0,-1)),
            (new Matrix4f().identity()).lookAt(new Vector3f(0,0,0), new Vector3f(0,0,1), new Vector3f(0,-1,0)),
            (new Matrix4f().identity()).lookAt(new Vector3f(0,0,0), new Vector3f(0,0,-1), new Vector3f(0,-1,0))
    };

    public static void equirectangularToCubemapDemo(int texture) {
        Shader equirectangularToCubemapShader = new Shader("src\\shaders\\equirecToCubeShader\\equirecMap.frag", "src\\shaders\\equirecToCubeShader\\cube.vert");

        int VAO = glGenVertexArrays();
        glBindVertexArray(VAO);
        int VBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, VBO);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 8*Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        equirectangularToCubemapShader.setUniformTexture("equirectangularMap", texture, 0);
        equirectangularToCubemapShader.setUniform("projection", Run.projectionMatrix);
        equirectangularToCubemapShader.setUniform("view", Run.viewMatrix);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDisable(GL_CULL_FACE);
        glDrawArrays(GL_TRIANGLES, 0, 36);
        //return new int[]{};
    }

    public static int equirectangularToCubemap(int texture) {
        Shader equirectangularToCubemapShader = new Shader("src\\shaders\\equirecToCubeShader\\equirecMap.frag", "src\\shaders\\equirecToCubeShader\\cube.vert");

        VAO = glGenVertexArrays();
        glBindVertexArray(VAO);
        VBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, VBO);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 8*Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        int cubemap = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, cubemap);
        for (int i = 0; i < 6; i++) {
            glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL_RGB16F, 512, 512, 0, GL_RGB, GL_FLOAT, MemoryUtil.NULL);
        }
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        int FBO = glGenFramebuffers();
        int RBO = glGenRenderbuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, FBO);
        glBindRenderbuffer(GL_RENDERBUFFER, RBO);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, 512, 512);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, RBO);

        equirectangularToCubemapShader.setUniformTexture("equirectangularMap", texture, 0);
        equirectangularToCubemapShader.setUniform("projection", proj);
        glViewport(0,0,512,512);
        glDisable(GL_CULL_FACE);
        for (int i = 0; i < 6; i++) {
            equirectangularToCubemapShader.setUniform("view", views[i]);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, cubemap, 0);
            renderCube();
        }
        return cubemap;
    }
    public static int convoluteCubemap(int cubemapHDRI) {
        Shader irradianceShader = new Shader("src\\shaders\\environmentMapUtil\\envMapConvolution.frag", "src\\shaders\\equirecToCubeShader\\cube.vert");

        int irradianceMap = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, irradianceMap);
        for (int i = 0; i < 6; i++) {
            glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL_RGB16F, 32, 32, 0, GL_RGB, GL_FLOAT, MemoryUtil.NULL);
        }
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        int FBO = glGenFramebuffers();
        int RBO = glGenRenderbuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, FBO);
        glBindRenderbuffer(GL_RENDERBUFFER, RBO);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, 32, 32);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, RBO);

        irradianceShader.setUniformCubemap("environmentMap", cubemapHDRI, 0);
        irradianceShader.setUniform("projection", proj);
        glViewport(0,0,32,32);
        glDisable(GL_CULL_FACE);
        for (int i = 0; i < 6; i++) {
            irradianceShader.setUniform("view", views[i]);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, irradianceMap, 0);
            renderCube();
        }
        return irradianceMap;
    }
    public static int preFilterCubemap(int cubemapHDRI, int max_reflection_LOD) {
        Shader prefilterShader = new Shader("src\\shaders\\environmentMapUtil\\envMapPrefilter.frag", "src\\shaders\\equirecToCubeShader\\cube.vert");

        int prefilterMap = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, prefilterMap);
        for (int i = 0; i < 6; i++) {
            glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL_RGB16F, 128, 128, 0, GL_RGB, GL_FLOAT, MemoryUtil.NULL);
        }
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glGenerateMipmap(GL_TEXTURE_CUBE_MAP);
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS);

        int FBO = glGenFramebuffers();
        int RBO = glGenRenderbuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, FBO);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, RBO);

        prefilterShader.setUniformCubemap("environmentMap", cubemapHDRI, 0);
        prefilterShader.setUniform("projection", proj);
        int res = 128;
        for (int mip = 0; mip < max_reflection_LOD; mip++) {
            glViewport(0, 0, res, res);
            glBindRenderbuffer(GL_RENDERBUFFER, RBO);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, res, res);

            float roughness = mip/((float)max_reflection_LOD-1);
            prefilterShader.setUniform("roughness", roughness);
            for (int i = 0; i < 6; i++) {
                prefilterShader.setUniform("view", views[i]);
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, prefilterMap, mip);
                renderCube();
            }
            res /= 2;
        }
        return prefilterMap;
    }
    public static void renderCube() {
        glBindVertexArray(VAO);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glDepthFunc(GL_LEQUAL);
        glDrawArrays(GL_TRIANGLES, 0, 36);
    }
}
