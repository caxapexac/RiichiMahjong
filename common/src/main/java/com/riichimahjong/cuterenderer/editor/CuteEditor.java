package com.riichimahjong.cuterenderer.editor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.riichimahjong.cuterenderer.HoverHighlightRenderer;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Layout WYSIWYG editor — global, in-memory, dev-only. Consumer renderers
 * register named {@link LayoutEntry layout anchors} and read pose/extras through
 * them. Pressing F8 (see {@code CuteEditorKeyHandler}) opens a screen that lets
 * the user tweak overrides; "Copy to clipboard" produces paste-ready Java the
 * developer can fold back into the source as new defaults.
 *
 * <p>Edits live in process memory only — they do not persist across game
 * restarts. The intended workflow is: open editor, nudge values until the scene
 * looks right, copy to clipboard, paste into source, restart.
 *
 * <p>Anchors are global by name. Every renderer that calls
 * {@link #layout(String, Vector3f, float, float)} with the same name shares
 * the same overrides — so editing one table's hand layout retunes all tables
 * simultaneously.
 *
 * <p>Will be stripped from production builds. Couplings are deliberately loose
 * (no listeners, no events) so removal is just deleting the {@code editor}
 * package and its call sites.
 */
public final class CuteEditor {

    private static final Map<String, LayoutEntry> ENTRIES = new LinkedHashMap<>();
    private static boolean overlayActive = false;
    @Nullable private static String selected;

    /** Undo stack — snapshots taken before each user edit. Capped at {@link #MAX_UNDO}. */
    private static final Deque<Snapshot> UNDO = new ArrayDeque<>();
    private static final int MAX_UNDO = 64;

    private CuteEditor() {}

    /**
     * Register-or-fetch a global layout anchor. The first call locks in the
     * defaults; subsequent calls with the same {@code name} return the same
     * entry regardless of what defaults are passed (so hot-reload of the
     * caller doesn't lose user edits). Add extra fields via
     * {@link LayoutEntry#floatField(String, float)} / {@link LayoutEntry#intField(String, int)}.
     */
    public static LayoutEntry layout(String name, Vector3f defPos, float defScale) {
        return ENTRIES.computeIfAbsent(name, n -> new LayoutEntry(n, defPos, defScale));
    }

    public static Collection<LayoutEntry> entries() {
        return ENTRIES.values();
    }

    public static boolean isActive() {
        return overlayActive;
    }

    /** Toggle and open/close the editor screen. Call from key handlers / debug commands. */
    public static void toggle() {
        overlayActive = !overlayActive;
        Minecraft mc = Minecraft.getInstance();
        if (overlayActive) {
            if (selected == null && !ENTRIES.isEmpty()) {
                selected = ENTRIES.keySet().iterator().next();
            }
            mc.setScreen(new CuteEditorScreen());
        } else if (mc.screen instanceof CuteEditorScreen) {
            mc.setScreen(null);
        }
    }

    @Nullable
    public static String selectedName() { return selected; }

    public static void select(@Nullable String name) { selected = name; }

    @Nullable
    public static LayoutEntry selectedEntry() {
        return selected == null ? null : ENTRIES.get(selected);
    }

    /** Reset every anchor's overrides — keeps the registered defaults intact. */
    public static void resetAll() {
        for (LayoutEntry e : ENTRIES.values()) e.clearOverrides();
    }

    public static void resetSelected() {
        LayoutEntry e = selectedEntry();
        if (e != null) e.clearOverrides();
    }

    // ---- undo --------------------------------------------------------------

    /**
     * Snapshot the current overrides. Call BEFORE applying a user edit so that
     * {@link #undo()} can restore the prior state. Identical-to-top snapshots
     * are skipped to keep the stack tidy under noisy callers (e.g. focus events
     * that fire without an edit). Stack is bounded at {@link #MAX_UNDO}.
     */
    public static void pushUndo() {
        Snapshot snap = Snapshot.capture(ENTRIES.values());
        if (!UNDO.isEmpty() && UNDO.peek().equals(snap)) return;
        UNDO.push(snap);
        while (UNDO.size() > MAX_UNDO) UNDO.pollLast();
    }

    /** Restore the last-pushed snapshot. No-op if the stack is empty. Returns true if undone. */
    public static boolean undo() {
        Snapshot snap = UNDO.poll();
        if (snap == null) return false;
        snap.restore(ENTRIES);
        return true;
    }

    public static int undoDepth() { return UNDO.size(); }

    /** Internal — reset the stack (e.g. when reopening the editor fresh). */
    public static void clearUndo() { UNDO.clear(); }

    /**
     * Frozen copy of every entry's override state. Equality + restore use the
     * {@link LayoutEntry#name} as the key — additions of new anchors after the
     * snapshot are tolerated (their overrides aren't touched on restore).
     */
    private record Snapshot(Map<String, EntrySnap> entries) {

        static Snapshot capture(Collection<LayoutEntry> values) {
            Map<String, EntrySnap> m = new HashMap<>();
            for (LayoutEntry e : values) m.put(e.name, EntrySnap.of(e));
            return new Snapshot(m);
        }

        void restore(Map<String, LayoutEntry> live) {
            for (var entry : entries.entrySet()) {
                LayoutEntry e = live.get(entry.getKey());
                if (e == null) continue;
                entry.getValue().applyTo(e);
            }
        }
    }

    private record EntrySnap(
            @Nullable Vector3f pos,
            @Nullable Float scale,
            Map<String, Float> floats,
            Map<String, Integer> ints) {

        static EntrySnap of(LayoutEntry e) {
            return new EntrySnap(
                    e.posOverride == null ? null : new Vector3f(e.posOverride),
                    e.scaleOverride,
                    new HashMap<>(e.floatOverrides),
                    new HashMap<>(e.intOverrides));
        }

        void applyTo(LayoutEntry e) {
            e.posOverride = pos == null ? null : new Vector3f(pos);
            e.scaleOverride = scale;
            e.floatOverrides.clear();
            e.floatOverrides.putAll(floats);
            e.intOverrides.clear();
            e.intOverrides.putAll(ints);
        }
    }

    // ---- gizmo rendering ---------------------------------------------------

    private static final float TRIAD_LEN = 1f / 8f;

    /**
     * Draw an XYZ triad at master-local {@code (x, y, z)}. Red = +X, green = +Y,
     * blue = +Z. Length {@link #TRIAD_LEN}. Selected anchor gets a 2× brighter
     * draw plus a small 4-line cross at the origin so it's distinguishable
     * across overlapping anchors.
     */
    public static void drawTriad(PoseStack pose, MultiBufferSource buffers,
                                 double x, double y, double z, boolean isSelected) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose entry = pose.last();
        float ax = (float) x, ay = (float) y, az = (float) z;
        float i = isSelected ? 1.0f : 0.7f;
        HoverHighlightRenderer.line(vc, entry, ax, ay, az, ax + TRIAD_LEN, ay, az,        i, 0f, 0f, 1f);
        HoverHighlightRenderer.line(vc, entry, ax, ay, az, ax, ay + TRIAD_LEN, az,        0f, i, 0f, 1f);
        HoverHighlightRenderer.line(vc, entry, ax, ay, az, ax, ay, az + TRIAD_LEN,        0f, 0f, i, 1f);
        if (isSelected) {
            float c = TRIAD_LEN * 0.4f;
            HoverHighlightRenderer.line(vc, entry, ax - c, ay, az, ax + c, ay, az, 1f, 1f, 0f, 1f);
            HoverHighlightRenderer.line(vc, entry, ax, ay - c, az, ax, ay + c, az, 1f, 1f, 0f, 1f);
            HoverHighlightRenderer.line(vc, entry, ax, ay, az - c, ax, ay, az + c, 1f, 1f, 0f, 1f);
        }
    }

    /** Draw triads for one or more entries at their {@link LayoutEntry#pos() pos}. */
    public static void drawGizmos(PoseStack pose, MultiBufferSource buffers, LayoutEntry... entries) {
        if (!overlayActive) return;
        for (LayoutEntry e : entries) {
            Vector3f p = e.pos();
            drawTriad(pose, buffers, p.x, p.y, p.z, e.name.equals(selected));
        }
    }

    // ---- clipboard export --------------------------------------------------

    /**
     * Build paste-ready Java that recreates the current overrides. Generated
     * lines call {@code CuteEditor.applyOverride(...)} so a quick paste into
     * an init block restores the state mid-iteration; final-numbers should be
     * folded back into the {@code layout(...)} default arguments by hand.
     */
    public static String exportToJava() {
        StringBuilder sb = new StringBuilder();
        sb.append("// CuteEditor overrides — paste into source, fold values into layout() defaults.\n");
        for (LayoutEntry e : ENTRIES.values()) {
            if (!e.hasOverrides()) continue;
            sb.append("// ").append(e.name).append('\n');
            if (e.posOverride != null || e.scaleOverride != null) {
                sb.append(String.format(java.util.Locale.ROOT,
                        "CuteEditor.layout(\"%s\", new Vector3f(%sf, %sf, %sf), %sf);%n",
                        e.name,
                        fmt(e.pos().x), fmt(e.pos().y), fmt(e.pos().z),
                        fmt(e.scale())));
            }
            for (var f : e.floatOverrides.entrySet()) {
                sb.append(String.format(java.util.Locale.ROOT,
                        "//   .floatField(\"%s\", %sf)%n", f.getKey(), fmt(f.getValue())));
            }
            for (var ie : e.intOverrides.entrySet()) {
                sb.append(String.format(java.util.Locale.ROOT,
                        "//   .intField(\"%s\", %d)%n", ie.getKey(), ie.getValue()));
            }
        }
        if (sb.length() == 0
                || sb.indexOf("//", sb.indexOf("\n") + 1) == sb.indexOf("\n") + 1
                && !ENTRIES.values().stream().anyMatch(LayoutEntry::hasOverrides)) {
            sb.append("// (no overrides set)\n");
        }
        return sb.toString();
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }

    // ---- override mutators (called by the screen) --------------------------

    public static void setPos(LayoutEntry e, float x, float y, float z) {
        if (e.posOverride == null) e.posOverride = new Vector3f();
        e.posOverride.set(x, y, z);
    }

    public static void setScale(LayoutEntry e, float scale) { e.scaleOverride = scale; }
    public static void setFloat(LayoutEntry e, String key, float v) { e.floatOverrides.put(key, v); }
    public static void setInt(LayoutEntry e, String key, int v)     { e.intOverrides.put(key, v); }
}
