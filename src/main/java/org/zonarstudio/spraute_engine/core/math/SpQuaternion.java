package org.zonarstudio.spraute_engine.core.math;

/**
 * Quaternion for rotation. Stored as (x, y, z, w) where w is the scalar part.
 * No Minecraft dependencies.
 */
public final class SpQuaternion {
    public float x, y, z, w;

    public SpQuaternion() {
        w = 1f;
    }

    public SpQuaternion(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public SpQuaternion identity() {
        x = 0f;
        y = 0f;
        z = 0f;
        w = 1f;
        return this;
    }

    /** Set from Euler angles in degrees (Bedrock order: ZYX intrinsic = XYZ extrinsic). */
    public SpQuaternion setEulerDeg(float degX, float degY, float degZ) {
        float rx = (float) Math.toRadians(degX) * 0.5f;
        float ry = (float) Math.toRadians(degY) * 0.5f;
        float rz = (float) Math.toRadians(degZ) * 0.5f;
        float sx = (float) Math.sin(rx), cx = (float) Math.cos(rx);
        float sy = (float) Math.sin(ry), cy = (float) Math.cos(ry);
        float sz = (float) Math.sin(rz), cz = (float) Math.cos(rz);
        // ZYX intrinsic
        this.w = cx * cy * cz + sx * sy * sz;
        this.x = sx * cy * cz - cx * sy * sz;
        this.y = cx * sy * cz + sx * cy * sz;
        this.z = cx * cy * sz - sx * sy * cz;
        return this;
    }

    /** Set from axis-angle (axis must be normalized). */
    public SpQuaternion setAxisAngle(float ax, float ay, float az, float angleDeg) {
        float halfRad = (float) Math.toRadians(angleDeg) * 0.5f;
        float s = (float) Math.sin(halfRad);
        this.x = ax * s;
        this.y = ay * s;
        this.z = az * s;
        this.w = (float) Math.cos(halfRad);
        return this;
    }

    /** Multiply: this = this * other */
    public SpQuaternion mul(SpQuaternion o) {
        float nw = w * o.w - x * o.x - y * o.y - z * o.z;
        float nx = w * o.x + x * o.w + y * o.z - z * o.y;
        float ny = w * o.y - x * o.z + y * o.w + z * o.x;
        float nz = w * o.z + x * o.y - y * o.x + z * o.w;
        this.x = nx;
        this.y = ny;
        this.z = nz;
        this.w = nw;
        return this;
    }

    /** Rotate a vector by this quaternion: v' = q * v * q^-1 */
    public SpVec3 rotate(SpVec3 v) {
        float vx = v.x, vy = v.y, vz = v.z;
        float tx = 2f * (y * vz - z * vy);
        float ty = 2f * (z * vx - x * vz);
        float tz = 2f * (x * vy - y * vx);
        v.x = vx + w * tx + (y * tz - z * ty);
        v.y = vy + w * ty + (z * tx - x * tz);
        v.z = vz + w * tz + (x * ty - y * tx);
        return v;
    }

    public SpQuaternion normalize() {
        float len = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (len > 1e-8f) {
            float inv = 1f / len;
            x *= inv;
            y *= inv;
            z *= inv;
            w *= inv;
        }
        return this;
    }

    public SpQuaternion copy() {
        return new SpQuaternion(x, y, z, w);
    }

    public SpQuaternion set(SpQuaternion o) {
        this.x = o.x;
        this.y = o.y;
        this.z = o.z;
        this.w = o.w;
        return this;
    }

    @Override
    public String toString() {
        return String.format("SpQuat(%.4f, %.4f, %.4f, %.4f)", x, y, z, w);
    }
}
