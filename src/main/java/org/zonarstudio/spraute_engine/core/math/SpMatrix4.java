package org.zonarstudio.spraute_engine.core.math;

/**
 * 4x4 column-major matrix for combined bone transforms.
 * Stored as float[16] in column-major order (OpenGL convention):
 * [m0 m4 m8  m12]
 * [m1 m5 m9  m13]
 * [m2 m6 m10 m14]
 * [m3 m7 m11 m15]
 * No Minecraft dependencies.
 */
public final class SpMatrix4 {
    public final float[] m = new float[16];

    public SpMatrix4() {
        identity();
    }

    public SpMatrix4 identity() {
        for (int i = 0; i < 16; i++) m[i] = 0f;
        m[0] = m[5] = m[10] = m[15] = 1f;
        return this;
    }

    public SpMatrix4 set(SpMatrix4 o) {
        System.arraycopy(o.m, 0, m, 0, 16);
        return this;
    }

    /** Set from translation + quaternion rotation + uniform scale. */
    public SpMatrix4 compose(SpVec3 translate, SpQuaternion rot, float scale) {
        float xx = rot.x * rot.x, xy = rot.x * rot.y, xz = rot.x * rot.z, xw = rot.x * rot.w;
        float yy = rot.y * rot.y, yz = rot.y * rot.z, yw = rot.y * rot.w;
        float zz = rot.z * rot.z, zw = rot.z * rot.w;

        m[0]  = (1f - 2f * (yy + zz)) * scale;
        m[1]  = (2f * (xy + zw)) * scale;
        m[2]  = (2f * (xz - yw)) * scale;
        m[3]  = 0f;

        m[4]  = (2f * (xy - zw)) * scale;
        m[5]  = (1f - 2f * (xx + zz)) * scale;
        m[6]  = (2f * (yz + xw)) * scale;
        m[7]  = 0f;

        m[8]  = (2f * (xz + yw)) * scale;
        m[9]  = (2f * (yz - xw)) * scale;
        m[10] = (1f - 2f * (xx + yy)) * scale;
        m[11] = 0f;

        m[12] = translate.x;
        m[13] = translate.y;
        m[14] = translate.z;
        m[15] = 1f;
        return this;
    }

    /** this = this * other */
    public SpMatrix4 mul(SpMatrix4 o) {
        float[] r = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                r[col * 4 + row] =
                    m[0 * 4 + row] * o.m[col * 4 + 0] +
                    m[1 * 4 + row] * o.m[col * 4 + 1] +
                    m[2 * 4 + row] * o.m[col * 4 + 2] +
                    m[3 * 4 + row] * o.m[col * 4 + 3];
            }
        }
        System.arraycopy(r, 0, m, 0, 16);
        return this;
    }

    /** Transform a point (w=1). */
    public SpVec3 transformPoint(SpVec3 v) {
        float rx = m[0] * v.x + m[4] * v.y + m[8]  * v.z + m[12];
        float ry = m[1] * v.x + m[5] * v.y + m[9]  * v.z + m[13];
        float rz = m[2] * v.x + m[6] * v.y + m[10] * v.z + m[14];
        v.x = rx;
        v.y = ry;
        v.z = rz;
        return v;
    }

    /** Transform a direction (w=0), ignores translation. */
    public SpVec3 transformDirection(SpVec3 v) {
        float rx = m[0] * v.x + m[4] * v.y + m[8]  * v.z;
        float ry = m[1] * v.x + m[5] * v.y + m[9]  * v.z;
        float rz = m[2] * v.x + m[6] * v.y + m[10] * v.z;
        v.x = rx;
        v.y = ry;
        v.z = rz;
        return v;
    }

    /** Extract translation. */
    public SpVec3 getTranslation() {
        return new SpVec3(m[12], m[13], m[14]);
    }

    public SpMatrix4 copy() {
        SpMatrix4 c = new SpMatrix4();
        System.arraycopy(m, 0, c.m, 0, 16);
        return c;
    }
}
