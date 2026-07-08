package org.zonarstudio.spraute_engine.script;

import org.zonarstudio.spraute_engine.config.ScriptTriggersConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Runtime registry for automatic script triggers (replaces {@code triggers.json} when set from scripts).
 */
public final class ScriptTriggerRegistry {

    private static final ScriptTriggerRegistry INSTANCE = new ScriptTriggerRegistry();

    private final Set<String> autorunScripts = new LinkedHashSet<>();
    private String onJoinScript = "";
    private String onFirstJoinScript = "";
    private final Map<String, String> runAfter = new LinkedHashMap<>();

    public static ScriptTriggerRegistry get() {
        return INSTANCE;
    }

    public static void reset() {
        INSTANCE.autorunScripts.clear();
        INSTANCE.onJoinScript = "";
        INSTANCE.onFirstJoinScript = "";
        INSTANCE.runAfter.clear();
    }

    /** Legacy config → registry (only fills empty slots). */
    public void migrateFromConfig(ScriptTriggersConfig cfg) {
        if (cfg == null) return;
        if ((onJoinScript == null || onJoinScript.isEmpty()) && cfg.on_join != null && !cfg.on_join.isEmpty()) {
            onJoinScript = cfg.on_join.trim();
        }
        if ((onFirstJoinScript == null || onFirstJoinScript.isEmpty()) && cfg.on_first_join != null && !cfg.on_first_join.isEmpty()) {
            onFirstJoinScript = cfg.on_first_join.trim();
        }
        if (cfg.after != null) {
            for (Map.Entry<String, String> e : cfg.after.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                String k = e.getKey().trim();
                String v = e.getValue().trim();
                if (!k.isEmpty() && !v.isEmpty() && !runAfter.containsKey(k)) {
                    runAfter.put(k, v);
                }
            }
        }
    }

    public void addAutorun(String scriptName) {
        if (scriptName == null) return;
        String n = scriptName.trim();
        if (!n.isEmpty()) autorunScripts.add(n);
    }

    public Set<String> getAutorunScripts() {
        return Collections.unmodifiableSet(autorunScripts);
    }

    public void setOnJoin(String scriptName) {
        onJoinScript = scriptName != null ? scriptName.trim() : "";
    }

    public String getOnJoin() {
        return onJoinScript != null ? onJoinScript : "";
    }

    public void setOnFirstJoin(String scriptName) {
        onFirstJoinScript = scriptName != null ? scriptName.trim() : "";
    }

    public String getOnFirstJoin() {
        return onFirstJoinScript != null ? onFirstJoinScript : "";
    }

    public void setRunAfter(String finishedScript, String nextScript) {
        if (finishedScript == null || nextScript == null) return;
        String a = finishedScript.trim();
        String b = nextScript.trim();
        if (!a.isEmpty() && !b.isEmpty()) runAfter.put(a, b);
    }

    public String getRunAfter(String finishedScript) {
        if (finishedScript == null) return "";
        return runAfter.getOrDefault(finishedScript.trim(), "");
    }
}
