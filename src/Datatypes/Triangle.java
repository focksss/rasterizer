package Datatypes;

import java.util.ArrayList;
import java.util.List;

public class Triangle {
    public static int NEXT_TRI_ID = 0;
    public Vec v1;
    public Vec v2;
    public Vec v3;
    public Vec n1;
    public Vec n2;
    public Vec n3;
    public Vec t1;
    public Vec t2;
    public Vec t3;
    public Vec bt1;
    public Vec bt2;
    public Vec bt3;
    public Vec vt1;
    public Vec vt2;
    public Vec vt3;
    public int material;
    public int ID;

    public Triangle(Vec v1, Vec v2, Vec v3) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.ID = NEXT_TRI_ID++;
    }

    public Triangle(Vec v1, Vec v2, Vec v3, Vec n1, Vec n2, Vec n3, Vec vt1, Vec vt2, Vec vt3, int material) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.n1 = n1.normalize();
        this.n2 = n2.normalize();
        this.n3 = n3.normalize();
        this.vt1 = vt1;
        this.vt2 = vt2;
        this.vt3 = vt3;
        this.material = material;
        this.ID = NEXT_TRI_ID++;
        Vec e1 = v2.sub(v1);
        Vec e2 = v3.sub(v1);
        Vec deltaUV1 = vt2.sub(vt1);
        Vec deltaUV2 = vt3.sub(vt1);
        double f = 1/(deltaUV1.x * deltaUV2.y - deltaUV2.x * deltaUV1.y);
        Vec tangent = new Vec();
        Vec bitangent = new Vec();
        tangent.x = f * (deltaUV2.y * e1.x - deltaUV1.y * e2.x);
        tangent.y = f * (deltaUV2.y * e1.y - deltaUV1.y * e2.y);
        tangent.z = f * (deltaUV2.y * e1.z - deltaUV1.y * e2.z);

        bitangent.x = f * (-deltaUV2.x * e1.x + deltaUV1.x * e2.x);
        bitangent.y = f * (-deltaUV2.x * e1.y + deltaUV1.x * e2.y);
        bitangent.z = f * (-deltaUV2.x * e1.z + deltaUV1.x * e2.z);

        tangent = tangent.normalize();
        bitangent = bitangent.normalize();
        if ((tangent.cross(bitangent)).dot(n1) < 0.2) {
            tangent = tangent.mult(-1);
        }
        this.t1 = tangent;
        this.t2 = tangent;
        this.t3 = tangent;
        this.bt1 = bitangent;
        this.bt2 = bitangent;
        this.bt3 = bitangent;
    }

    public double area() {
        Vec AB = v2.sub(v1);
        Vec AC = v3.sub(v1);
        Vec crossProduct = AB.cross(AC);
        return Math.abs(0.5 * crossProduct.magnitude());
    }

    public static List<Triangle> dataToTris(List<Vec> vertices, List<Vec> normals, List<Vec> texCoords, List<Vec> vertexIndices, List<Vec> normalIndices, List<Vec> texCoordIndices, List<Integer> materialIndices) {
        List<Triangle> triangles = new ArrayList<Triangle>();
        for (int counter = 0; counter < vertexIndices.size(); counter++) {
            Vec vIndices = vertexIndices.get(counter);
            Vec nIndices = normalIndices.get(counter);
            Vec tIndices = texCoordIndices.get(counter);
            if ((nIndices.x > -1) && (tIndices.x > -1)) {
                triangles.add(new Triangle(
                        vertices.get((int) vIndices.x - 1), vertices.get((int) vIndices.y - 1), vertices.get((int) vIndices.z - 1),
                        normals.get((int) nIndices.x - 1), normals.get((int) nIndices.y - 1), normals.get((int) nIndices.z - 1),
                        texCoords.get((int) tIndices.x - 1), texCoords.get((int) tIndices.y - 1), texCoords.get((int) tIndices.z - 1),
                        materialIndices.get(counter)));
            } else if (nIndices.x > -1) {
                triangles.add(new Triangle(
                        vertices.get((int) vIndices.x - 1), vertices.get((int) vIndices.y - 1), vertices.get((int) vIndices.z - 1),
                        normals.get((int) nIndices.x - 1), normals.get((int) nIndices.y - 1), normals.get((int) nIndices.z - 1),
                        new Vec(0), new Vec(0), new Vec(0),
                        materialIndices.get(counter)));
            } else {
                Vec norm = new Vec((vertices.get((int) vIndices.z - 1).sub(vertices.get((int) vIndices.x - 1))).cross(vertices.get((int) vIndices.z - 1).sub(vertices.get((int) vIndices.y - 1))));
                triangles.add(new Triangle(
                        vertices.get((int) vIndices.x - 1), vertices.get((int) vIndices.y - 1), vertices.get((int) vIndices.z - 1),
                        norm, norm, norm,
                        texCoords.get((int) tIndices.x - 1), texCoords.get((int) tIndices.y - 1), texCoords.get((int) tIndices.z - 1),
                        materialIndices.get(counter)));
            }
        }
        return triangles;
    }

    public int ID() {
        return this.ID;
    }
}