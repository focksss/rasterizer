package ModelHandler;

import Datatypes.Vec;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Material {
    public String name;
    public String texturesDirectory;
    Vec Ka = new Vec();
    Vec Kd = new Vec();
    Vec Ks = new Vec();
    double Ns = 0; // specular exponent
    double d= 1; // dissolved (transparency 1-0, 1 is opaque)
    double Tr= 0; // occasionally used, opposite of d (0 is opaque)
    Vec Tf = new Vec(); // transmission filter
    double Ni = 0; // refractive index
    Vec Ke = new Vec(); // emission color
    int illum = 0; // shading model (0-10, each has diff properties)
    String map_Ka = "";
    String map_Kd = "";
    String map_Ks = "";
    //PBR extension types
    double Pm = 0; // metallicity (0-1, dielectric to metallic)
    double Pr = 0; // roughness (0-1, perfectly smooth to "extremely" rough)
    double Ps = 0; // sheen (0-1, no sheen effect to maximum sheen)
    double Pc = 0; // clearcoat thickness (0-1, smooth clearcoat to rough clearcoat (blurry reflections))
    double Pcr = 0;
    double aniso = 0; // anisotropy (0-1, isotropic surface to fully anisotropic) (uniform-directional reflections)
    double anisor = 0; // rotational anisotropy (0-1, but essentially 0-2pi, rotates pattern of anisotropy)
    String map_Pm = "";
    String map_Pr = "";
    String map_Ps = "";
    String map_Pc = "";
    String map_Pcr = "";
    String map_Bump = "";
    String map_d = "";
    String map_Tr = "";
    String map_Ns = "";
    String map_Ke = "";
    String map_Disp = "";
    //custom
    double Density = 0;
    double subsurface = 0;
    Vec subsurfaceColor = new Vec();
    Vec subsurfaceRadius = new Vec();

    private static final Set<String> vecProperties = Set.of("Ka", "Kd", "Ks", "Tf", "Ke", "SubsurfaceColor", "SubsurfaceRadius");
    private static final Set<String> doubleProperties = Set.of("Ns", "d", "Tr", "Ni", "Pm", "Pr", "Ps", "Pc", "Pcr", "aniso", "anisor", "Density", "subsurface");
    private static final Set<String> intProperties = Set.of("illum");

    public Material() {
        this.texturesDirectory = "NULL";
        this.Ka = new Vec(0);
        this.Kd = new Vec(0.8);
        this.Ks = new Vec(0.5);
        this.Ns = 10;
        this.d = 0;
        this.Tr = 0;
        this.Tf = new Vec(0);
        this.Ni = 1;
        this.Ke = new Vec(0);
        this.Density = 1;
        this.illum = 0;
        this.Pm = 0;
        this.Pr = 0.5;
        this.Ps = 0;
        this.Pc = 0;
        this.Pcr = 0;
        this.aniso = 0;
        this.anisor = 0;
        //custom
        this.subsurface = 0;
        this.subsurfaceColor = new Vec(0);
        this.subsurfaceRadius = new Vec(0);
    }

    public void setProperty(String name, Object value) {
        try {
            Field[] fields = this.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.getName().equalsIgnoreCase(name)) {
                    field.setAccessible(true);
                    field.set(this, value);
                    return;
                }
            }
            throw new RuntimeException("Invalid property: " + name);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error setting property: " + name, e);
        }
    }

    public static List<Material> parseMtl(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            List<Material> materials = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("newmtl ")) {
                    Material newmtl = new Material();
                    newmtl.name = line.split(" ")[1];
                    while ((line = br.readLine()) != null && !line.isBlank()) {
                        line = line.replace("/", "\\");
                        String[] parts = line.split(" ");
                        String tex;
                        if (parts[0].startsWith("map_")) {
                            if (parts.length > 2) {
                                tex = Paths.get(parts[3]).getFileName().toString();
                                newmtl.checkTextureDir(tex, new File(filePath).getParent());
                                newmtl.setProperty(parts[0].toLowerCase(), newmtl.texturesDirectory + "\\" + tex);
                            } else {
                                tex = Paths.get(parts[1]).getFileName().toString();
                                newmtl.checkTextureDir(tex, new File(filePath).getParent());
                                newmtl.setProperty(parts[0].toLowerCase(), newmtl.texturesDirectory + "\\" + tex);
                            }
                        } else if (doubleProperties.contains(parts[0])) {
                            newmtl.setProperty(parts[0], Double.parseDouble(parts[1]));
                        } else if (intProperties.contains(parts[0])) {
                            newmtl.setProperty(parts[0], Integer.parseInt(parts[1]));
                        } else if (vecProperties.contains(parts[0])) {
                            newmtl.setProperty(parts[0], new Vec(parts[1], parts[2], parts[3]));
                        }
                    }
                    if (newmtl.texturesDirectory.equals("NULL")) System.out.println("material didnt find textures: " + newmtl.name + ", " + newmtl.map_Kd);
                    materials.add(newmtl);
                }
            }
            return materials;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName() {
        return name;
    }

    private void checkTextureDir(String textureName, String parentDirectory) {
        if (texturesDirectory.equals("NULL")) {
            File dir = new File(parentDirectory);
            if (!dir.isDirectory()) {System.out.println("given directory" + parentDirectory + " is not a directory"); return;}

            File result = searchFile(dir, textureName);
            if (result != null) {
                texturesDirectory = result.getParent();
            };
        }
    }

    private static File searchFile(File dir, String fileName) {
        File[] files = dir.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (file.isDirectory()) {
                File found = searchFile(file, fileName);
                if (found != null) return found;
            } else if (file.getName().equals(fileName)) {
                return file;
            }
        }
        return null;
    }

    public String getPropertyValue(String property) {
        try {
            Field field = this.getClass().getDeclaredField(property);
            field.setAccessible(true);
            Object value = field.get(this);
            return value != null ? value.toString() : "null";
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
