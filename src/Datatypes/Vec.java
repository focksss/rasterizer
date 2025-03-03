package Datatypes;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class Vec {
    public double x, y, z, w;
    public static float xf;
    public static float yf;
    public static float zf;
    public static float wf;
    public float xF;
    public float yF;
    public float zF;
    public float wF;

    public Vec() {
        x = y = z = w = 0;
    }
    public Vec(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public Vec(double x, double y) {
        this.x = x;
        this.y = y;
    }
    public Vec(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }
    public Vec(Vec vector, double w) {
        this.x = vector.x;
        this.y = vector.y;
        this.z = vector.z;
        this.w = w;
    }
    public Vec(Vec vector) {
        x = vector.x;
        y = vector.y;
        z = vector.z;
    }
    public Vec(double v) {
        this.x = v;
        this.y = v;
        this.z = v;
    }
    public Vec(String x, String y, String z) {
        this.x = Double.parseDouble(x);
        this.y = Double.parseDouble(y);
        this.z = Double.parseDouble(z);
    }
    public Vec(String u, String v) {
        this.x = Double.parseDouble(u);
        this.y = Double.parseDouble(v);
    }

    public void updateFloats() {
        xf = (float) x; yf = (float) y; zf = (float) z; wf = (float) w;
        xF = (float) x; yF = (float) y; zF = (float) z; wF = (float) w;
    }

    public Vector3f toVec3f() {
        updateFloats();
        return new Vector3f(xF, yF, zF);
    }

    public void set(int c, double val) {
        switch(c) {
            case 0: x = val; break;
            case 1: y = val; break;
            case 2: z = val; break;
        }
    }
    public void set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec add(Vec vector) {
        return new Vec(x + vector.x, y + vector.y, z + vector.z);
    }
    public Vec sub(Vec vector) {
        return new Vec(x - vector.x, y - vector.y, z - vector.z);
    }
    public Vec mult(Vec vector) {
        return new Vec(x * vector.x, y * vector.y, z * vector.z);
    }
    public Vec mult(double c) {
        return new Vec(x * c, y * c, z * c);
    }
    public Vec mult(int c) {
        return new Vec(x * c, y * c, z * c);
    }
    public Vec mult(double x, double y, double z) {
        return new Vec(this.x * x, this.y * y, this.z * z);
    }
    public Vec div(Vec vector) {
        return new Vec(x / vector.x, y / vector.y, z / vector.z);
    }
    public Vec div(double c) {
        return new Vec(x / c, y / c, z / c);
    }

    public static Vec mean(List<Vec> list) {
        Vec sum = new Vec(0, 0, 0);
        for (int i = 0; i < list.size(); i++) {
            sum.add(list.get(i));
        }
        return sum.div(list.size());
    }

    public double[] toArray() {
        return new double[]{x, y, z};
    }

    public float[] toFloatArray() {
        updateFloats();
        return new float[]{xF, yF, zF};
    }

    public float[] toUVfloatArray() {
        updateFloats();
        return new float[]{xF, yF};
    }

    public List<Float> toFloatList() {
        List<Float> out = new ArrayList<>();
        out.add((float) x);
        out.add((float) y);
        out.add((float) z);
        return out;
    }
    public List<Float> toUVfloatList() {
        List<Float> out = new ArrayList<>();
        out.add((float) x);
        out.add((float) y);
        return out;
    }

    public Vec rotate(Vec rot) {
        double rx = rot.x;
        double ry = rot.y;
        double rz = rot.z;
        // Convert angles from degrees to radians
        double radX = rx;
        double radY = ry;
        double radZ = rz;

        // Rotation around the X-axis
        double cosX = Math.cos(radX);
        double sinX = Math.sin(radX);
        double newY = cosX * y - sinX * z;
        double newZ = sinX * y + cosX * z;
        y = newY;
        z = newZ;

        // Rotation around the Y-axis
        double cosY = Math.cos(radY);
        double sinY = Math.sin(radY);
        double newX = cosY * x + sinY * z;
        newZ = -sinY * x + cosY * z;
        x = newX;
        z = newZ;

        // Rotation around the Z-axis
        double cosZ = Math.cos(radZ);
        double sinZ = Math.sin(radZ);
        newX = cosZ * x - sinZ * y;
        newY = sinZ * x + cosZ * y;
        x = newX;
        y = newY;

        return new Vec(newX, newY, newZ);
    }

    public double dot(Vec vector) {
        return x * vector.x + y * vector.y + z * vector.z;
    }

    public Vec cross(Vec vector) {
        return new Vec(((y * vector.z) - (z * vector.y)), ((z * vector.x) - (x * vector.z)), ((x * vector.y) - (y * vector.x)));
    }

    public double magnitude() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    public Vec normalize() {
        double mag = this.magnitude();
        return new Vec(this.x / mag, this.y / mag, this.z / mag);
    }

    public double dist(Vec vector) {
        return Math.sqrt(Math.pow(x - vector.x, 2) + Math.pow(y - vector.y, 2) + Math.pow(z - vector.z, 2));
    }

    public void println() {
        System.out.print("(" + (x) + "," + (y) + "," + (z) + ")\n");
    }
}