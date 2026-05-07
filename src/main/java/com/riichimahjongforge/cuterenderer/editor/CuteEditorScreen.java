package com.riichimahjongforge.cuterenderer.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

/**
 * Editor UI: anchor list on the left, editable fields for the selected anchor
 * on the right, and a row of action buttons at the bottom. Each numeric field
 * is a slider (covering a sensible default range) paired with an EditBox for
 * exact typing or out-of-range values.
 *
 * <p>Renders without a backdrop — the world stays fully visible so the user
 * iterates the scene live.
 */
public final class CuteEditorScreen extends Screen {

    private static final int LIST_X        = 10;
    private static final int LIST_TOP      = 30;
    private static final int LIST_WIDTH    = 130;
    private static final int LIST_ROW_H    = 16;

    private static final int FIELD_X        = 160;
    private static final int FIELD_TOP      = 30;
    private static final int FIELD_LABEL_W  = 60;
    private static final int FIELD_SLIDER_W = 140;
    private static final int FIELD_BOX_W    = 60;
    private static final int FIELD_H        = 18;
    private static final int FIELD_GAP      = 4;

    /** Default per-field-kind slider ranges. Out-of-range values may still be typed in the EditBox. */
    private static final float POS_X_MIN  = -2f, POS_X_MAX  = 2f;
    private static final float POS_Y_MIN  = -0.5f, POS_Y_MAX = 1.5f;
    private static final float POS_Z_MIN  = -2f, POS_Z_MAX  = 2f;
    private static final float SCALE_MIN  = 0f,  SCALE_MAX  = 0.05f;

    /** Refreshers re-read the current value into the matching widgets when the model changes externally. */
    private final List<Runnable> fieldRefreshers = new ArrayList<>();

    public CuteEditorScreen() {
        super(Component.literal("Cute Layout Editor (F8)"));
    }

    @Override
    protected void init() {
        rebuildLeftPane();
        rebuildRightPane();
        rebuildBottomBar();
    }

    private void rebuildLeftPane() {
        int y = LIST_TOP;
        for (LayoutEntry e : CuteEditor.entries()) {
            String prefix = e.name.equals(CuteEditor.selectedName()) ? "> " : "  ";
            String suffix = e.hasOverrides() ? " *" : "";
            addRenderableWidget(Button.builder(
                            Component.literal(prefix + e.name + suffix),
                            b -> {
                                CuteEditor.select(e.name);
                                refresh();
                            })
                    .bounds(LIST_X, y, LIST_WIDTH, LIST_ROW_H - 2)
                    .build());
            y += LIST_ROW_H;
        }
    }

    private void rebuildRightPane() {
        LayoutEntry e = CuteEditor.selectedEntry();
        if (e == null) return;
        int y = FIELD_TOP;

        y = addFloatRow("pos.x", y,
                () -> e.pos().x,
                v -> { Vector3f p = e.pos(); CuteEditor.setPos(e, v, p.y, p.z); },
                POS_X_MIN, POS_X_MAX, "%.4f");
        y = addFloatRow("pos.y", y,
                () -> e.pos().y,
                v -> { Vector3f p = e.pos(); CuteEditor.setPos(e, p.x, v, p.z); },
                POS_Y_MIN, POS_Y_MAX, "%.4f");
        y = addFloatRow("pos.z", y,
                () -> e.pos().z,
                v -> { Vector3f p = e.pos(); CuteEditor.setPos(e, p.x, p.y, v); },
                POS_Z_MIN, POS_Z_MAX, "%.4f");
        y = addFloatRow("scale", y, e::scale, v -> CuteEditor.setScale(e, v), SCALE_MIN, SCALE_MAX, "%.4f");

        for (String key : e.floatDefs.keySet()) {
            float[] r = e.floatRange(key);
            y = addFloatRow(key, y,
                    () -> e.f(key),
                    v -> CuteEditor.setFloat(e, key, v),
                    r[0], r[1], "%.4f");
        }
        for (String key : e.intDefs.keySet()) {
            int[] r = e.intRange(key);
            y = addIntRow(key, y,
                    () -> e.i(key),
                    v -> CuteEditor.setInt(e, key, v),
                    r[0], r[1]);
        }
    }

    private void rebuildBottomBar() {
        int y = (height - 30);
        int x = LIST_X;
        addRenderableWidget(Button.builder(Component.literal("Reset selected"),
                        b -> { CuteEditor.pushUndo(); CuteEditor.resetSelected(); refresh(); })
                .bounds(x, y, 100, 20).build());
        x += 105;
        addRenderableWidget(Button.builder(Component.literal("Reset all"),
                        b -> { CuteEditor.pushUndo(); CuteEditor.resetAll(); refresh(); })
                .bounds(x, y, 80, 20).build());
        x += 85;
        addRenderableWidget(Button.builder(Component.literal("Copy Java"),
                        b -> Minecraft.getInstance().keyboardHandler.setClipboard(CuteEditor.exportToJava()))
                .bounds(x, y, 80, 20).build());
        x += 85;
        addRenderableWidget(Button.builder(Component.literal("Close"),
                        b -> CuteEditor.toggle())
                .bounds(x, y, 60, 20).build());
    }

    private int addFloatRow(String label, int y,
                            Supplier<Float> read, Consumer<Float> write,
                            float min, float max, String fmt) {
        int sliderX = FIELD_X + FIELD_LABEL_W;
        int boxX = sliderX + FIELD_SLIDER_W + 4;

        FloatSlider slider = new FloatSlider(sliderX, y, FIELD_SLIDER_W, FIELD_H,
                label, min, max, read.get(), fmt);
        UndoEditBox box = new UndoEditBox(font, boxX, y, FIELD_BOX_W, FIELD_H, Component.literal(label));
        box.setValue(String.format(Locale.ROOT, fmt, read.get()));

        slider.onChange = v -> {
            write.accept(v);
            box.setValue(String.format(Locale.ROOT, fmt, v));
        };
        box.setResponder(s -> {
            try {
                float v = Float.parseFloat(s.trim());
                write.accept(v);
                slider.setRealValue(v);
            } catch (NumberFormatException ignored) {
                // Mid-typing — leave override unchanged.
            }
        });

        addRenderableWidget(slider);
        addRenderableWidget(box);
        fieldRefreshers.add(() -> {
            float v = read.get();
            slider.setRealValue(v);
            box.setValue(String.format(Locale.ROOT, fmt, v));
        });

        return y + FIELD_H + FIELD_GAP;
    }

    private int addIntRow(String label, int y,
                          Supplier<Integer> read, Consumer<Integer> write,
                          int min, int max) {
        int sliderX = FIELD_X + FIELD_LABEL_W;
        int boxX = sliderX + FIELD_SLIDER_W + 4;

        IntSlider slider = new IntSlider(sliderX, y, FIELD_SLIDER_W, FIELD_H,
                label, min, max, read.get());
        UndoEditBox box = new UndoEditBox(font, boxX, y, FIELD_BOX_W, FIELD_H, Component.literal(label));
        box.setValue(String.valueOf(read.get()));

        slider.onChange = v -> {
            write.accept(v);
            box.setValue(String.valueOf(v));
        };
        box.setResponder(s -> {
            try {
                int v = Integer.parseInt(s.trim());
                write.accept(v);
                slider.setRealValue(v);
            } catch (NumberFormatException ignored) {}
        });

        addRenderableWidget(slider);
        addRenderableWidget(box);
        fieldRefreshers.add(() -> {
            int v = read.get();
            slider.setRealValue(v);
            box.setValue(String.valueOf(v));
        });

        return y + FIELD_H + FIELD_GAP;
    }

    private void refresh() {
        clearWidgets();
        fieldRefreshers.clear();
        rebuildLeftPane();
        rebuildRightPane();
        rebuildBottomBar();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No backdrop — world stays fully visible so live edits feed back at full opacity.
        g.drawString(font, "Anchors", LIST_X, 16, 0xFFFFFF, true);
        LayoutEntry sel = CuteEditor.selectedEntry();
        if (sel != null) {
            g.drawString(font, "Editing: " + sel.name, FIELD_X, 16, 0xFFFFFF, true);
            int y = FIELD_TOP + 5;
            String[] coreLabels = {"pos.x", "pos.y", "pos.z", "scale"};
            for (String lbl : coreLabels) {
                g.drawString(font, lbl, FIELD_X, y, 0xCCCCCC, true);
                y += FIELD_H + FIELD_GAP;
            }
            for (String key : sel.floatDefs.keySet()) {
                g.drawString(font, key, FIELD_X, y, 0xCCCCCC, true);
                y += FIELD_H + FIELD_GAP;
            }
            for (String key : sel.intDefs.keySet()) {
                g.drawString(font, key, FIELD_X, y, 0xCCCCCC, true);
                y += FIELD_H + FIELD_GAP;
            }
        }
        g.drawCenteredString(font, getTitle(), width / 2, 4, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+Z / Cmd+Z — undo last edit. Match by key, allow either Ctrl or Super
        // (Cmd on macOS) since hasControlDown() already covers both.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z && hasControlDown() && !hasShiftDown() && !hasAltDown()) {
            if (CuteEditor.undo()) refresh();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- slider widgets ----------------------------------------------------

    /**
     * Vanilla {@link AbstractSliderButton} wraps a 0..1 normalised value; this
     * subclass exposes a real-valued [min, max] range on top, syncs the
     * displayed message via the supplied format, and notifies an external
     * callback on change. Out-of-range writes via {@link #setRealValue(float)}
     * are clamped (the slider is bound to its range; the paired EditBox keeps
     * the precise value if the user typed something outside).
     */
    /**
     * EditBox that snapshots the override state into the undo stack the first
     * time it gains focus in a session. Refocusing without typing snapshots
     * again only if state changed in between (CuteEditor coalesces duplicate
     * snapshots).
     */
    private static final class UndoEditBox extends EditBox {
        UndoEditBox(net.minecraft.client.gui.Font font, int x, int y, int w, int h, Component msg) {
            super(font, x, y, w, h, msg);
        }

        @Override
        public void setFocused(boolean focused) {
            boolean was = isFocused();
            super.setFocused(focused);
            if (focused && !was) {
                CuteEditor.pushUndo();
            }
        }
    }

    private static final class FloatSlider extends AbstractSliderButton {
        private final float min, max;
        private final String fmt;
        private final String label;
        Consumer<Float> onChange = v -> {};

        FloatSlider(int x, int y, int w, int h, String label,
                    float min, float max, float init, String fmt) {
            super(x, y, w, h, Component.empty(), normalise(init, min, max));
            this.min = min;
            this.max = max;
            this.fmt = fmt;
            this.label = label;
            updateMessage();
        }

        float realValue() {
            return (float) (min + value * (max - min));
        }

        void setRealValue(float v) {
            this.value = normalise(v, min, max);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + String.format(Locale.ROOT, fmt, realValue())));
        }

        @Override
        protected void applyValue() {
            onChange.accept(realValue());
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            // Snapshot before the click changes the value — clicks on the track
            // and at the start of a drag both flow through here.
            CuteEditor.pushUndo();
            super.onClick(mouseX, mouseY);
        }

        private static double normalise(float v, float min, float max) {
            if (max <= min) return 0;
            return Math.max(0, Math.min(1, (v - min) / (max - min)));
        }
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final int min, max;
        private final String label;
        Consumer<Integer> onChange = v -> {};

        IntSlider(int x, int y, int w, int h, String label, int min, int max, int init) {
            super(x, y, w, h, Component.empty(), normalise(init, min, max));
            this.min = min;
            this.max = max;
            this.label = label;
            updateMessage();
        }

        int realValue() {
            return (int) Math.round(min + value * (max - min));
        }

        void setRealValue(int v) {
            this.value = normalise(v, min, max);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + realValue()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(realValue());
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            // Snapshot before the click changes the value — clicks on the track
            // and at the start of a drag both flow through here.
            CuteEditor.pushUndo();
            super.onClick(mouseX, mouseY);
        }

        private static double normalise(int v, int min, int max) {
            if (max <= min) return 0;
            return Math.max(0, Math.min(1, (v - min) / (double) (max - min)));
        }
    }
}
