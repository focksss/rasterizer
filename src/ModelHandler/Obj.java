package ModelHandler;

import Datatypes.Triangle;
import Datatypes.Vec;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Obj {
    public List<Triangle> triangles;
    public List<Material> mtllib;
    public List<String> texturePaths = new ArrayList<>();

    public Obj(String filePath, Vec scale, Vec translation, Vec rotation) {
        Path path = Paths.get(filePath);
        if (Files.isDirectory(path)) {
            File folder = path.toFile();
            File[] mtls = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".mtl"));
            mtllib = new ArrayList<>();
            for (File mtl : mtls) {
                List<Material> mats = Material.parseMtl(mtl.getAbsolutePath());
                mtllib.addAll(mats);
                for (Material mat : mats) {
                    //add all mapped textures to texturePaths
                    for (Field field : mat.getClass().getDeclaredFields()) {
                        if (field.getName().contains("map") && field.getType() == String.class) {
                            field.setAccessible(true);
                            try {
                                String textureName = (String) field.get(mat);
                                if (!texturePaths.contains(textureName) && !(textureName == null) && !textureName.isEmpty()) {
                                    texturePaths.add(textureName);
                                }
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }
            File[] objs = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".obj"));
            triangles = new ArrayList<>();
            for (File obj : objs) {
                triangles.addAll(parseObj(obj, mtllib, scale, translation, rotation));
            }
        }
    }

    private List<Triangle> parseObj(File obj, List<Material> mtllib, Vec scale, Vec translation, Vec rotation) {
        List<Triangle> out = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(obj))) {
            String line;
            List<Vec> vertices = new ArrayList<>();
            List<Vec> normals = new ArrayList<>();
            List<Vec> texCoords = new ArrayList<>();
            List<Vec> vertexIndices = new ArrayList<>();
            List<Vec> normalIndices = new ArrayList<>();
            List<Vec> texCoordIndices = new ArrayList<>();
            List<Integer> materials = new ArrayList<>();
            int activeMtlIndex = -1;
            int lineCounter = 0;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v": vertices.add(new Vec(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3])).mult(scale).rotate(rotation).add(translation)); activeMtlIndex = -1; break;
                    case "vn": normals.add(new Vec(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3])).rotate(rotation)); activeMtlIndex = -1; break;
                    case "vt": texCoords.add(new Vec(Double.parseDouble(parts[1]), 1-Double.parseDouble(parts[2]))); activeMtlIndex = -1; break;
                    case "usemtl": activeMtlIndex = (mtllib.stream().map(Material::getName).toList()).indexOf(parts[1]); break;
                    case "f":
                        Vec vIndices = new Vec();
                        Vec nIndices = new Vec();
                        Vec tIndices = new Vec();
                        materials.add(activeMtlIndex);
                        for (int i = 1; i < 4; i++) {
                            String[] components = parts[i].split("/");
                            vIndices.set(i - 1, Double.parseDouble(components[0]));
                            if (components.length == 3) {
                                nIndices.set(i - 1, Double.parseDouble(components[2]));
                                if (!components[1].isEmpty()) {
                                    tIndices.set(i - 1, Double.parseDouble(components[1]));
                                } else {
                                    tIndices.set(i - 1, -1);
                                }
                            } else {
                                tIndices.set(i - 1, Double.parseDouble(components[1]));
                                nIndices.set(i - 1, -1);
                            }
                        }
                        vertexIndices.add(vIndices);
                        normalIndices.add(nIndices);
                        texCoordIndices.add(tIndices);
                        break;
                }
                lineCounter++;
                if (lineCounter % 1000 == 0)
                    System.out.print("\rParsed " + lineCounter + " lines of " + obj.getName());
            }
            out.addAll(Triangle.dataToTris(vertices, normals, texCoords, vertexIndices, normalIndices, texCoordIndices, materials));
            System.out.println();
            return out;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
