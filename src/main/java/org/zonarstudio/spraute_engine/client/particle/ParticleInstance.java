package org.zonarstudio.spraute_engine.client.particle;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

final class ParticleInstance {
    double x, y, z, oldX, oldY, oldZ;
    double vx, vy, vz;
    float age, lifetime;
    float sizeX, sizeY, rotation, rotationSpeed;
    int color;
    int frame;
    long serial;
    ParticleSystemInstance owner;
    final Deque<Vec3> trail = new ArrayDeque<>();

    void reset() {
        x = y = z = oldX = oldY = oldZ = 0;
        vx = vy = vz = 0;
        age = lifetime = sizeX = sizeY = rotation = rotationSpeed = 0;
        color = 0xFFFFFFFF;
        frame = 0;
        serial = 0;
        owner = null;
        trail.clear();
    }
}
