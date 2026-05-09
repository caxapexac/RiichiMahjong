package com.riichimahjong.cuterenderer.editor;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * One named, editable layout anchor. Holds defaults + optional overrides for the
 * three core fields (position, yaw, scale) plus a flat namespace of named
 * float/int extras (e.g. {@code "spacing"}, {@code "count"}).
 *
 * <p>Read methods return the override if set, otherwise the default — so consumer
 * code never branches on "is the editor active". Mutators are package-private:
 * the editor screen owns the override map.
 *
 * <p>Pose units are master-local (1.0 = one block). Yaw is degrees. Scale is
 * uniform unless extended with explicit per-axis fields.
 */
public final class LayoutEntry {

    final String name;
    final Vector3f defPos;
    final float defScale;
    final Map<String, Float> floatDefs = new LinkedHashMap<>();
    final Map<String, float[]> floatRanges = new LinkedHashMap<>();
    final Map<String, Integer> intDefs = new LinkedHashMap<>();
    final Map<String, int[]> intRanges = new LinkedHashMap<>();

    @Nullable Vector3f posOverride;
    @Nullable Float scaleOverride;
    final Map<String, Float> floatOverrides = new LinkedHashMap<>();
    final Map<String, Integer> intOverrides = new LinkedHashMap<>();

    LayoutEntry(String name, Vector3f defPos, float defScale) {
        this.name = name;
        this.defPos = new Vector3f(defPos);
        this.defScale = defScale;
    }

    public String name() { return name; }

    public Vector3f pos() { return posOverride != null ? posOverride : defPos; }
    public float scale()  { return scaleOverride != null ? scaleOverride : defScale; }

    /**
     * Register an extra float field with a default and a slider range. Idempotent —
     * re-registering keeps the existing default and range. Out-of-range values can
     * still be typed in the EditBox (slider clamps).
     */
    public LayoutEntry floatField(String key, float def, float min, float max) {
        floatDefs.putIfAbsent(key, def);
        floatRanges.putIfAbsent(key, new float[] {min, max});
        return this;
    }

    /** Convenience — defaults to a [0, 1] slider range. */
    public LayoutEntry floatField(String key, float def) {
        return floatField(key, def, 0f, 1f);
    }

    /** Register an extra int field with a default and a slider range. Idempotent. */
    public LayoutEntry intField(String key, int def, int min, int max) {
        intDefs.putIfAbsent(key, def);
        intRanges.putIfAbsent(key, new int[] {min, max});
        return this;
    }

    /** Convenience — defaults to a [0, 32] slider range. */
    public LayoutEntry intField(String key, int def) {
        return intField(key, def, 0, 32);
    }

    public float[] floatRange(String key) {
        float[] r = floatRanges.get(key);
        return r != null ? r : new float[] {0f, 1f};
    }

    public int[] intRange(String key) {
        int[] r = intRanges.get(key);
        return r != null ? r : new int[] {0, 32};
    }

    /** Read an extra float field by name. Returns 0 if the key was never registered. */
    public float f(String key) {
        Float ov = floatOverrides.get(key);
        if (ov != null) return ov;
        Float dv = floatDefs.get(key);
        return dv != null ? dv : 0f;
    }

    /** Read an extra int field by name. Returns 0 if the key was never registered. */
    public int i(String key) {
        Integer ov = intOverrides.get(key);
        if (ov != null) return ov;
        Integer dv = intDefs.get(key);
        return dv != null ? dv : 0;
    }

    /** True if any override (core or extra) is set. */
    public boolean hasOverrides() {
        return posOverride != null || scaleOverride != null
                || !floatOverrides.isEmpty() || !intOverrides.isEmpty();
    }

    void clearOverrides() {
        posOverride = null;
        scaleOverride = null;
        floatOverrides.clear();
        intOverrides.clear();
    }
}
