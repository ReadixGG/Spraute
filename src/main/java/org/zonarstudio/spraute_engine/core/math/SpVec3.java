package org.zonarstudio.spraute_engine.core.math;

/**
 * Immutable-style 3D vector. All mutating methods return {@code this} for chaining.
 * No Minecraft dependencies.
 */
public final class SpVec3 {
    public float x, y, z;

    public SpVec3() {}

    public SpVec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public SpVec3(SpVec3 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    public SpVec3 set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public SpVec3 set(SpVec3 o) {
        this.x = o.x;
        this.y = o.y;
        this.z = o.z;
        return this;
    }

    public SpVec3 add(SpVec3 o) {
        x += o.x;
        y += o.y;
        z += o.z;
        return this;
    }

    public SpVec3 add(float ax, float ay, float az) {
        x += ax;
        y += ay;
        z += az;
        return this;
    }

    public SpVec3 sub(SpVec3 o) {
        x -= o.x;
        y -= o.y;
        z -= o.z;
        return this;
    }

    public SpVec3 scale(float s) {
        x *= s;
        y *= s;
        z *= s;
        return this;
    }

    public SpVec3 negate() {
        x = -x;
        y = -y;
        z = -z;
        return this;
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public float lengthSq() {
        return x * x + y * y + z * z;
    }

    public SpVec3 normalize() {
        float len = length();
        if (len > 1e-8f) {
            float inv = 1f / len;
            x *= inv;
            y *= inv;
            z *= inv;
        }
        return this;
    }

    public float dot(SpVec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public SpVec3 cross(SpVec3 o) {
        float cx = y * o.z - z * o.y;
        float cy = z * o.x - x * o.z;
        float cz = x * o.y - y * o.x;
        x = cx;
        y = cy;
        z = cz;
        return this;
    }

    public SpVec3 lerp(SpVec3 target, float t) {
        x += (target.x - x) * t;
        y += (target.y - y) * t;
        z += (target.z - z) * t;
        return this;
    }

    public SpVec3 copy() {
        return new SpVec3(x, y, z);
    }

    @Override
    public String toString() {
        return String.format("SpVec3(%.4f, %.4f, %.4f)", x, y, z);
    }
}
