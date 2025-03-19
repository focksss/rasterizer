package Util;

public class MathUtil {
    public static double lerp(double t, double a, double b) {
        return a + t * ((double) b - a);
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
