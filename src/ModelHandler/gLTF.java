package ModelHandler;
import Datatypes.Vec;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class gLTF {
    List<Material> mtllib = new ArrayList<>();
    String[] gltfMatPropertiesMap = new String[]{"name", "baseColorFactor", "baseColorTexture", "metallicFactor", "roughnessFactor", "metallicTexture", "roughnessTexture"};
    String[] mtlPropertiesMap = new String[]{"name", "Kd", "map_Kd", "Pm", "Pr", "map_Pm", "map_Pr"};

    public gLTF(String gltfPath) {
        List<String> gltfMatMap = List.of("name", "baseColorFactor", "baseColorTexture", "metallicFactor", "roughnessFactor", "metallicTexture", "roughnessTexture");
        List<String> mtlMatMap = List.of("name", "Kd", "map_Kd", "Pm", "Pr", "map_Pm", "map_Pr");
        try {
        //setup
            Path path = Paths.get(gltfPath);
            File gltfFile = null;
            File binFile = null;
            JSONObject gltf;
        //check if glb or gltf
            if (Files.isDirectory(path)) {
                File gltfDir = path.toFile();

                File[] gltfs = gltfDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".gltf"));
                gltfFile = gltfs[0];
                File[] bins = gltfDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".bin"));
                binFile = bins[0];
            }

        //get gltf and bin content
            String gltfContent = new String(Files.readAllBytes(gltfFile.toPath()));
            //System.out.println(gltfContent);

            gltf = new JSONObject(gltfContent);
            byte[] binData = Files.readAllBytes(binFile.toPath());
            ByteBuffer buffer = ByteBuffer.wrap(binData).order(ByteOrder.LITTLE_ENDIAN);

        //get images
            List<String> imagePaths = new ArrayList<>();
            JSONArray images = gltf.getJSONArray("images");
            for (int i = 0; i < images.length(); i++) {
                JSONObject image = images.getJSONObject(i);
                imagePaths.add(gltfPath.replace("\\", "/") + "/" + image.getString("uri"));
            }

        //get materials
            List<Material> materialList = new ArrayList<>();
            JSONArray materials = gltf.getJSONArray("materials");
            for (int i = 0; i < materials.length(); i++) {
                JSONObject matJson = materials.getJSONObject(i);
                Material material = new Material();
                for (String key : matJson.keySet()) {
                    Object value = matJson.get(key);
                    if (value instanceof String) {
                        material.setProperty(mtlMatMap.get(gltfMatMap.indexOf(key)), value);
                    } else if (value instanceof JSONObject) {
                        if (key.equals("pbrMetallicRoughness")) {
                            JSONObject pbrMat = (JSONObject) value;
                            for (String PBRkey : pbrMat.keySet()) {
                                Object PBRvalue = pbrMat.get(PBRkey);
                                System.out.println(PBRkey + ": " + PBRvalue);
                                int mapKey = gltfMatMap.indexOf(PBRkey);
                                if (mapKey != -1) {
                                    int texIdx = -1;
                                    if (PBRvalue instanceof JSONObject) {
                                        JSONObject textureMap = (JSONObject) PBRvalue;
                                        texIdx = textureMap.getInt("index");
                                    } else if (PBRvalue instanceof Number) {
                                        PBRvalue = ((Number) PBRvalue).floatValue();
                                    }
                                    material.setProperty(mtlMatMap.get(mapKey), !PBRkey.contains("Texture") ? PBRvalue : imagePaths.get(texIdx));
                                }
                            }
                        }
                    }

                }
                System.out.println();
                material.print();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
