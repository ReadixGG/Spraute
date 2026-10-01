package org.zonarstudio.spraute_engine.entity.client;

import java.util.concurrent.atomic.LongAdder;

/** Low-overhead counters for validating geo renderer budgets. */
public final class GeoRenderStats {
    private static final LongAdder rendered=new LongAdder(), culled=new LongAdder(), fallback=new LongAdder();
    private static final LongAdder vertices=new LongAdder(), faces=new LongAdder(), draws=new LongAdder();
    private static final LongAdder transformNs=new LongAdder(), drawNs=new LongAdder();
    private static final LongAdder[] lod={new LongAdder(),new LongAdder(),new LongAdder(),new LongAdder(),new LongAdder()};
    private static volatile Snapshot last = new Snapshot(0,0,0,0,0,0,0,0,0,0,0,0);
    private static long nextPublishNs;

    private GeoRenderStats() {}

    public static long begin() { return System.nanoTime(); }
    public static void transformed(long started) { transformNs.add(System.nanoTime()-started); }
    public static void drawn(long started) { drawNs.add(System.nanoTime()-started); }
    public static void rendered(int vertexCount, int faceCount, int drawCalls, boolean usedFallback) {
        rendered(vertexCount,faceCount,drawCalls,usedFallback,0);
    }
    public static void rendered(int vertexCount, int faceCount, int drawCalls, boolean usedFallback, int level) {
        rendered.increment(); vertices.add(vertexCount); faces.add(faceCount); draws.add(drawCalls);
        if (usedFallback) fallback.increment();
        lod[Math.max(0,Math.min(4,level))].increment();
        publishIfDue();
    }
    public static void culled() { culled.increment(); publishIfDue(); }

    public static Snapshot snapshot() { publishIfDue(); return last; }
    public static void reset() {
        rendered.reset(); culled.reset(); fallback.reset(); vertices.reset(); faces.reset(); draws.reset();
        transformNs.reset(); drawNs.reset(); nextPublishNs=0;
        for(LongAdder counter:lod) counter.reset();
        last=new Snapshot(0,0,0,0,0,0,0,0,0,0,0,0);
    }

    private static synchronized void publishIfDue() {
        long now=System.nanoTime();
        if (now < nextPublishNs) return;
        last=new Snapshot(rendered.sumThenReset(),culled.sumThenReset(),fallback.sumThenReset(),
                vertices.sumThenReset(),faces.sumThenReset(),draws.sumThenReset(),
                transformNs.sumThenReset()/1_000_000.0,drawNs.sumThenReset()/1_000_000.0,
                lod[0].sumThenReset(),lod[1].sumThenReset(),lod[2].sumThenReset(),lod[3].sumThenReset());
        nextPublishNs=now+1_000_000_000L;
    }

    public record Snapshot(long rendered, long culled, long fallback, long vertices,
                           long faces, long drawCalls, double transformMs, double drawMs,
                           long lod0,long lod1,long lod2,long lod3) {}
}
