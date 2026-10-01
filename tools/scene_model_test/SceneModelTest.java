import org.zonarstudio.spraute_engine.scene.*;

import java.nio.file.Files;
import java.nio.file.Path;

/** Standalone smoke test for the scene data model (no Minecraft required). */
public class SceneModelTest {
    static int failures = 0;

    public static void main(String[] args) throws Exception {
        testTransformRoundTrip();
        testJsonRoundTrip();
        testReparentPreservesWorld();
        testCyclePrevention();
        testCapabilities();
        testAtomicSave();
        if (failures > 0) {
            System.out.println("FAILED: " + failures + " assertion(s)");
            System.exit(1);
        }
        System.out.println("All scene model tests passed.");
    }

    static void check(boolean cond, String message) {
        if (!cond) {
            failures++;
            System.out.println("  FAIL: " + message);
        }
    }

    static void checkClose(double a, double b, String message) {
        check(Math.abs(a - b) < 1e-6, message + " (" + a + " vs " + b + ")");
    }

    static void testTransformRoundTrip() {
        SceneTransform parent = new SceneTransform();
        parent.px = 10; parent.py = 64; parent.pz = -3;
        parent.ry = 37; parent.sx = 2; parent.sy = 2; parent.sz = 2;

        SceneTransform local = new SceneTransform();
        local.px = 1.5; local.py = 0.25; local.pz = -4;
        local.rx = 5; local.ry = 90; local.sx = 0.5; local.sy = 1; local.sz = 3;

        SceneTransform world = SceneTransform.composeWorld(parent, local);
        SceneTransform back = SceneTransform.toLocal(parent, world);
        checkClose(back.px, local.px, "toLocal px");
        checkClose(back.py, local.py, "toLocal py");
        checkClose(back.pz, local.pz, "toLocal pz");
        checkClose(back.ry, local.ry, "toLocal ry");
        checkClose(back.sx, local.sx, "toLocal sx");
        checkClose(back.sz, local.sz, "toLocal sz");
    }

    static SceneDocument sampleDoc() {
        SceneDocument doc = SceneDocument.create("test_scene");
        SceneNode a = SceneNode.npc("A");
        a.transform.px = 5; a.transform.ry = 45; a.transform.sx = 2; a.transform.sy = 2; a.transform.sz = 2;
        a.model = "geo/defender.geo.json";
        SceneNode b = SceneNode.npc("B");
        b.transform.px = 3; b.transform.py = 1;
        a.children.add(b);
        doc.root.children.add(a);
        SceneNode c = SceneNode.npc("C");
        c.transform.px = -8;
        doc.root.children.add(c);
        return doc;
    }

    static void testJsonRoundTrip() {
        SceneDocument doc = sampleDoc();
        String json = SceneRepository.toJson(doc);
        SceneDocument copy = SceneRepository.fromJson(json);
        check(copy.id.equals(doc.id), "json id");
        check(copy.root.children.size() == 2, "json children count");
        SceneNode a = copy.root.children.get(0);
        check(a.name.equals("A") && a.children.size() == 1, "json nesting");
        checkClose(a.transform.ry, 45, "json transform");
        check(a.model.equals("geo/defender.geo.json"), "json npc payload");
    }

    static void testReparentPreservesWorld() {
        SceneDocument doc = sampleDoc();
        SceneNode b = doc.root.children.get(0).children.get(0);
        SceneNode c = doc.root.children.get(1);
        SceneTransform worldBefore = doc.worldTransform(b.id);

        check(doc.reparent(b.id, c.id), "reparent B under C succeeds");
        check(doc.findParent(b.id) == c, "B now child of C");
        SceneTransform worldAfter = doc.worldTransform(b.id);
        checkClose(worldAfter.px, worldBefore.px, "world px preserved");
        checkClose(worldAfter.py, worldBefore.py, "world py preserved");
        checkClose(worldAfter.pz, worldBefore.pz, "world pz preserved");
        checkClose(worldAfter.ry, worldBefore.ry, "world ry preserved");
        checkClose(worldAfter.sx, worldBefore.sx, "world sx preserved");
    }

    static void testCyclePrevention() {
        SceneDocument doc = sampleDoc();
        SceneNode a = doc.root.children.get(0);
        SceneNode b = a.children.get(0);
        check(!doc.reparent(a.id, b.id), "cycle rejected (parent into its child)");
        check(!doc.reparent(a.id, a.id), "self-parent rejected");
    }

    static void testCapabilities() {
        SceneDocument doc = sampleDoc();
        SceneNode c = doc.root.children.get(1);
        check(!doc.reparent(doc.root.id, c.id), "scene root reparent rejected");
        check(!SceneNodeType.SCENE.canDelete(), "scene root delete rejected");
        check(SceneNodeType.NPC.canReparent(), "npc reparent allowed");
        check(SceneNodeType.SCENE.allowedChildTypes().contains(SceneNodeType.NPC), "scene accepts npc");
    }

    static void testAtomicSave() throws Exception {
        Path dir = Files.createTempDirectory("spraute_scene_test");
        Path file = dir.resolve("test_scene.scene.json");
        SceneDocument doc = sampleDoc();
        SceneRepository.saveAtomic(doc, file);
        check(Files.isRegularFile(file), "scene file written");
        check(!Files.exists(dir.resolve("test_scene.scene.json.tmp")), "temp file cleaned up");
        SceneDocument loaded = SceneRepository.load(file);
        check(loaded.root.children.size() == 2, "load after atomic save");
        // Overwrite must not corrupt.
        loaded.root.children.get(1).name = "C-renamed";
        SceneRepository.saveAtomic(loaded, file);
        check(SceneRepository.load(file).root.children.get(1).name.equals("C-renamed"), "atomic overwrite");
    }
}
