package ModelHandler;

import Datatypes.Vec;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Field;

public class Light {
    public int type = 0;

    public Vec ambient = new Vec();
    public Vec diffuse = new Vec();
    public Vec specular = new Vec();

    public Vec position = new Vec();
    public Vec direction = new Vec();

    public float constantAttenuation = 0.0f;
    public float linearAttenuation = 0.0f;
    public float quadraticAttenuation = 0.0f;

    public float cutoff = 0.0f;
    public float innerCutoff = 0.0f;
    public float outerCutoff = 0.0f;

    public Matrix4f lightSpaceMatrix;

    public Light(int type) {
        this.type = type;
        this.lightSpaceMatrix = new Matrix4f().identity();
    }

    public void setProperty(String name, Object value) {
        try {
            Field field = this.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(this, castToFieldType(field, value));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("invalid property: " + name);
        }

        Matrix4f lightProjMatrix = new Matrix4f();
        if (this.type == 1) {
            this.position = this.direction.mult(-100);
            lightProjMatrix = new Matrix4f();
            lightProjMatrix.identity()
                    .ortho(-50f,50f,-50f,50f,0.01f, 1000f);
            Matrix4f lightView = new Matrix4f();
            lightView.identity()
                    .lookAt(this.position.toVec3f(), this.direction.toVec3f(), new Vector3f(0,1,0));
            this.lightSpaceMatrix = lightProjMatrix.mul(lightView);
        } else if (this.type == 0) {
            lightProjMatrix.identity()
                    .ortho(-30f,30f,-30f,30f,0.01f, 1000f);
            Matrix4f lightView = new Matrix4f();
            lightView.identity()
                    .lookAt(this.direction.mult(-50).toVec3f(), this.direction.toVec3f(), new Vector3f(0,1,0));
            this.lightSpaceMatrix = lightProjMatrix.mul(lightView);
        }
    }

    private static Object castToFieldType(Field field, Object value) {
        Class<?> fieldType = field.getType();

        if (fieldType.isInstance(value)) {
            return value; // Already of correct type
        }

        // Handle primitive types and common conversions
        if (fieldType == int.class || fieldType == Integer.class) {
            return Integer.parseInt(value.toString());
        } else if (fieldType == double.class || fieldType == Double.class) {
            return Double.parseDouble(value.toString());
        } else if (fieldType == float.class || fieldType == Float.class) {
            return Float.parseFloat(value.toString());
        } else if (fieldType == long.class || fieldType == Long.class) {
            return Long.parseLong(value.toString());
        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
            return Boolean.parseBoolean(value.toString());
        } else if (fieldType == char.class || fieldType == Character.class && value.toString().length() == 1) {
            return value.toString().charAt(0);
        } else if (fieldType == short.class || fieldType == Short.class) {
            return Short.parseShort(value.toString());
        } else if (fieldType == byte.class || fieldType == Byte.class) {
            return Byte.parseByte(value.toString());
        }

        throw new IllegalArgumentException("Unsupported type: " + fieldType);
    }
}
