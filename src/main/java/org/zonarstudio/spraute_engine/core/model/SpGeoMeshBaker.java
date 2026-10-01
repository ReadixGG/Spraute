package org.zonarstudio.spraute_engine.core.model;

import org.zonarstudio.spraute_engine.core.math.SpMatrix4;
import org.zonarstudio.spraute_engine.core.math.SpVec3;

import java.util.ArrayList;
import java.util.List;

/** Converts parsed Bedrock cubes into compact, immutable bone-local mesh pages. */
public final class SpGeoMeshBaker {
    private static final float MIN_EXTENT = 0.025f;

    private SpGeoMeshBaker() {}

    public static SpGeoBakedModel bake(SpGeoModel model) {
        if (model == null || model.boneMap.isEmpty()) {
            return new SpGeoBakedModel(List.of(), SpGeoBounds.EMPTY, 0, 0);
        }
        SpModelInstance bind = new SpModelInstance(model);
        bind.computeTransforms();
        Bounds global = new Bounds();
        List<SpGeoBakedModel.Page> pages = new ArrayList<>();
        PageBuilder page = new PageBuilder();
        int faces = 0;
        int vertices = 0;

        for (SpBone bone : model.boneMap.values()) {
            if (page.bones.size() >= SpGeoBakedModel.PALETTE_BONES && !page.hasBone(bone.name)) {
                pages.add(page.finish());
                page = new PageBuilder();
            }
            int paletteIndex = page.bone(bone.name);
            SpMatrix4 bindMatrix = bind.getBoneMatrix(bone.name);
            for (SpCube cube : bone.cubes) {
                int before = page.faceCount();
                bakeCube(model, bone, cube, paletteIndex, page, bindMatrix, global);
                int added = page.faceCount() - before;
                faces += added;
                vertices += added * 4;
            }
        }
        if (page.faceCount() > 0) pages.add(page.finish());
        return new SpGeoBakedModel(pages, global.finish(), faces, vertices);
    }

    private static void bakeCube(SpGeoModel model, SpBone bone, SpCube cube, int boneIndex,
                                 PageBuilder out, SpMatrix4 bind, Bounds global) {
        float x0 = cube.origin.x - cube.inflate;
        float y0 = cube.origin.y - cube.inflate;
        float z0 = cube.origin.z - cube.inflate;
        float x1 = x0 + cube.size.x + cube.inflate * 2f;
        float y1 = y0 + cube.size.y + cube.inflate * 2f;
        float z1 = z0 + cube.size.z + cube.inflate * 2f;
        if (x1 - x0 < MIN_EXTENT) { float m=(x0+x1)*.5f; x0=m-MIN_EXTENT*.5f; x1=m+MIN_EXTENT*.5f; }
        if (y1 - y0 < MIN_EXTENT) { float m=(y0+y1)*.5f; y0=m-MIN_EXTENT*.5f; y1=m+MIN_EXTENT*.5f; }
        if (z1 - z0 < MIN_EXTENT) { float m=(z0+z1)*.5f; z0=m-MIN_EXTENT*.5f; z1=m+MIN_EXTENT*.5f; }
        boolean cullSafe = cube.faceUVs.size() == 6
                && cube.size.x > 2f && cube.size.y > 2f && cube.size.z > 2f;
        float faceSize = Math.max(cube.size.x, Math.max(cube.size.y, cube.size.z)) / 16f;

        for (var entry : cube.faceUVs.entrySet()) {
            SpFaceUV uv = entry.getValue();
            if (Math.abs(uv.uSize) < .001f && Math.abs(uv.vSize) < .001f) continue;
            SpCube.SpFace face = cube.geometryFace(entry.getKey(), cube.boxUv);
            float[] verts = faceVertices(face, x0, y0, z0, x1, y1, z1);
            float[] normal = faceNormal(face);
            SpVec3 n = new SpVec3(normal[0], normal[1], normal[2]);
            cube.rotateDirection(n);
            float[] rotatedUv = faceUvs(uv, model.textureWidth, model.textureHeight, cube.mirror);
            float cx=0, cy=0, cz=0;
            int first = out.vertexCount();
            for (int i=0; i<4; i++) {
                SpVec3 p = new SpVec3(verts[i*3], verts[i*3+1], verts[i*3+2]);
                cube.modelPointToBoneLocal(bone, p);
                out.vertex(p.x, p.y, p.z, n.x, n.y, n.z,
                        rotatedUv[i*2], rotatedUv[i*2+1], boneIndex);
                cx += p.x; cy += p.y; cz += p.z;
                SpVec3 world = new SpVec3(p);
                bind.transformPoint(world);
                global.add(-world.x, world.y, world.z);
            }
            out.face(first, cx*.25f, cy*.25f, cz*.25f, faceSize, cullSafe);
        }
    }

    private static float[] faceUvs(SpFaceUV uv, int texW, int texH, boolean mirror) {
        float u=uv.u/texW, v=uv.v/texH;
        float u1=(uv.u+uv.uSize)/texW, v1=(uv.v+uv.vSize)/texH;
        if (!mirror) { float t=u1; u1=u; u=t; }
        int steps = Math.floorMod(uv.uvRotation, 360) / 90;
        return switch (steps) {
            case 1 -> new float[]{u1,v, u1,v1, u,v1, u,v};
            case 2 -> new float[]{u1,v1, u,v1, u,v, u1,v};
            case 3 -> new float[]{u,v1, u,v, u1,v, u1,v1};
            default -> new float[]{u,v, u1,v, u1,v1, u,v1};
        };
    }

    private static float[] faceVertices(SpCube.SpFace face, float x0,float y0,float z0,float x1,float y1,float z1) {
        return switch (face) {
            case NORTH -> new float[]{x1,y1,z0, x0,y1,z0, x0,y0,z0, x1,y0,z0};
            case SOUTH -> new float[]{x0,y1,z1, x1,y1,z1, x1,y0,z1, x0,y0,z1};
            case EAST -> new float[]{x1,y1,z1, x1,y1,z0, x1,y0,z0, x1,y0,z1};
            case WEST -> new float[]{x0,y1,z0, x0,y1,z1, x0,y0,z1, x0,y0,z0};
            case UP -> new float[]{x1,y1,z1, x0,y1,z1, x0,y1,z0, x1,y1,z0};
            case DOWN -> new float[]{x1,y0,z0, x0,y0,z0, x0,y0,z1, x1,y0,z1};
        };
    }

    private static float[] faceNormal(SpCube.SpFace face) {
        return switch (face) {
            case NORTH -> new float[]{0,0,-1}; case SOUTH -> new float[]{0,0,1};
            case EAST -> new float[]{1,0,0}; case WEST -> new float[]{-1,0,0};
            case UP -> new float[]{0,1,0}; case DOWN -> new float[]{0,-1,0};
        };
    }

    private static final class Bounds {
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY;
        float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY,radius;
        void add(float x,float y,float z) {
            minX=Math.min(minX,x); minY=Math.min(minY,y); minZ=Math.min(minZ,z);
            maxX=Math.max(maxX,x); maxY=Math.max(maxY,y); maxZ=Math.max(maxZ,z);
            radius=Math.max(radius,(float)Math.sqrt(x*x+y*y+z*z));
        }
        SpGeoBounds finish() {
            return minX == Float.POSITIVE_INFINITY ? SpGeoBounds.EMPTY
                    : new SpGeoBounds(minX,minY,minZ,maxX,maxY,maxZ,radius);
        }
    }

    private static final class PageBuilder {
        final List<String> bones=new ArrayList<>();
        final Floats pos=new Floats(), norm=new Floats(), uv=new Floats(), centers=new Floats(), sizes=new Floats();
        final Bytes boneIds=new Bytes(); final Ints first=new Ints(); final Bools cull=new Bools();
        boolean hasBone(String name) { return bones.contains(name); }
        int bone(String name) { int i=bones.indexOf(name); if(i>=0)return i; bones.add(name); return bones.size()-1; }
        int vertexCount(){return boneIds.size;} int faceCount(){return first.size;}
        void vertex(float x,float y,float z,float nx,float ny,float nz,float u,float v,int bone) {
            pos.add(x,y,z); norm.add(nx,ny,nz); uv.add(u,v); boneIds.add((byte)bone);
        }
        void face(int f,float x,float y,float z,float size,boolean safe) {
            first.add(f); centers.add(x,y,z); sizes.add(size); cull.add(safe);
        }
        SpGeoBakedModel.Page finish(){return new SpGeoBakedModel.Page(bones,pos.array(),norm.array(),uv.array(),
                boneIds.array(),first.array(),centers.array(),sizes.array(),cull.array());}
    }
    private static final class Floats { float[] a=new float[64];int size;void add(float...v){for(float x:v){if(size==a.length)a=java.util.Arrays.copyOf(a,size*2);a[size++]=x;}}float[] array(){return java.util.Arrays.copyOf(a,size);}}
    private static final class Ints { int[] a=new int[16];int size;void add(int v){if(size==a.length)a=java.util.Arrays.copyOf(a,size*2);a[size++]=v;}int[] array(){return java.util.Arrays.copyOf(a,size);}}
    private static final class Bytes { byte[] a=new byte[32];int size;void add(byte v){if(size==a.length)a=java.util.Arrays.copyOf(a,size*2);a[size++]=v;}byte[] array(){return java.util.Arrays.copyOf(a,size);}}
    private static final class Bools { boolean[] a=new boolean[16];int size;void add(boolean v){if(size==a.length)a=java.util.Arrays.copyOf(a,size*2);a[size++]=v;}boolean[] array(){return java.util.Arrays.copyOf(a,size);}}
}
