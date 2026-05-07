package com.riichimahjongforge.cuterenderer;

import net.minecraft.core.Direction;
import org.joml.Vector3f;

/**
 * Pure-data layout description. {@link com.riichimahjongforge.cuterenderer.LayoutNode}
 * applies it to position child slots; changing the layout retargets children so they
 * glide to new positions instead of teleporting.
 */
public sealed interface Layout {

    /** Number of slot anchors this layout produces. */
    int slotCount();

    /** Local-space position of slot {@code i} (relative to the LayoutNode's own origin). */
    Vector3f slotPos(int i, Vector3f out);

    /** Linear strip: {@code count} slots starting at origin, growing along {@code dir}. */
    record Line(int count, Direction dir, float spacing) implements Layout {
        @Override public int slotCount() { return count; }
        @Override public Vector3f slotPos(int i, Vector3f out) {
            float d = i * spacing;
            return out.set(dir.getStepX() * d, dir.getStepY() * d, dir.getStepZ() * d);
        }
    }

    /**
     * 2-D grid laid in two arbitrary directions (must be linearly independent).
     * Slots are indexed row-major: {@code i = row * cols + col}.
     */
    record Grid(int cols, int rows, Direction colDir, Direction rowDir,
                float colSpacing, float rowSpacing) implements Layout {
        @Override public int slotCount() { return cols * rows; }
        @Override public Vector3f slotPos(int i, Vector3f out) {
            int col = i % cols;
            int row = i / cols;
            float x = col * colSpacing;
            float y = row * rowSpacing;
            return out.set(
                    colDir.getStepX() * x + rowDir.getStepX() * y,
                    colDir.getStepY() * x + rowDir.getStepY() * y,
                    colDir.getStepZ() * x + rowDir.getStepZ() * y);
        }
    }

    /**
     * Fan / arc — slots arranged on an arc of {@code radius} centred on the LayoutNode,
     * spanning {@code totalSweepDegrees}, lying in the XZ plane.
     */
    record Fan(int count, float radius, float totalSweepDegrees) implements Layout {
        @Override public int slotCount() { return count; }
        @Override public Vector3f slotPos(int i, Vector3f out) {
            if (count <= 1) {
                return out.set(0f, 0f, radius);
            }
            float t = (i / (float) (count - 1)) - 0.5f;
            float a = (float) Math.toRadians(t * totalSweepDegrees);
            return out.set((float) (Math.sin(a) * radius), 0f, (float) (Math.cos(a) * radius));
        }
    }
}
