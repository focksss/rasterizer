package Util;

import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryStack;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Objects;

public class PBRtextureSeparator {
    public static String writePath = "outputTextures/";

    public static void splitPrPm_GB(String directoryPath) {
        File dir = new File(directoryPath);
        if (!dir.isDirectory()) {
            System.err.println("Invalid directory: " + directoryPath);
            return;
        }

        File outputDir = new File(dir, "processed_textures");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            System.err.println("Failed to create output directory: " + outputDir.getAbsolutePath());
            return;
        }

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (!file.isFile()) continue;

            String filePath = file.getAbsolutePath();
            String fileName = file.getName();

            if (!file.getName().endsWith(".dds") && file.getName().endsWith("Specular.png")) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer width = stack.mallocInt(1);
                    IntBuffer height = stack.mallocInt(1);
                    IntBuffer channels = stack.mallocInt(1);

                    ByteBuffer image = STBImage.stbi_load(filePath, width, height, channels, 4);
                    if (image == null) {
                        System.err.println("Failed to load image: " + filePath);
                        continue;
                    }

                    int w = width.get(0);
                    int h = height.get(0);
                    int size = w * h * 4;

                    ByteBuffer metallic = ByteBuffer.allocateDirect(size);
                    ByteBuffer roughness = ByteBuffer.allocateDirect(size);

                    for (int i = 0; i < size; i += 4) {
                        byte r = 0;
                        byte g = image.get(i + 1); // Green channel
                        byte b = image.get(i + 2); // Blue channel
                        byte a = image.get(i + 3); // Preserve alpha

                        metallic.put(i, b); // R = 0
                        metallic.put(i + 1, b); // G = 0
                        metallic.put(i + 2, b); // B = Blue channel
                        metallic.put(i + 3, a); // A = Alpha

                        roughness.put(i, g); // R = 0
                        roughness.put(i + 1, g); // G = Green channel
                        roughness.put(i + 2, g); // B = 0
                        roughness.put(i + 3, a); // A = Alpha
                    }

                    STBImage.stbi_image_free(image);

                    String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                    String metallicPath = new File(outputDir, baseName + "_metallic.png").getAbsolutePath();
                    String roughnessPath = new File(outputDir, baseName + "_roughness.png").getAbsolutePath();

                    STBImageWrite.stbi_write_png(metallicPath, w, h, 4, metallic, w * 4);
                    STBImageWrite.stbi_write_png(roughnessPath, w, h, 4, roughness, w * 4);

                    System.out.println("Processed: " + fileName);
                }
            }
        }
    }
    public static void processMaterialFile(String materialFilePath) {
        File materialFile = new File(materialFilePath);
        if (!materialFile.exists()) {
            System.err.println("Material file not found: " + materialFilePath);
            return;
        }

        File outputMaterialFile = new File(materialFile.getParent(), "processed_" + materialFile.getName());

        try (BufferedReader reader = new BufferedReader(new FileReader(materialFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputMaterialFile))) {

            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();

                if (line.startsWith("map_Kd")) {
                    String[] parts = line.split(" ");
                    if (parts.length > 1) {
                        String texturePath = parts[1];
                        String basePath = texturePath.replace("BaseColor.png", "");
                        writer.write("map_Pm " + basePath + "Specular_metallic.png");
                        writer.newLine();
                        writer.write("map_Pr " + basePath + "Specular_roughness.png");
                        writer.newLine();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

