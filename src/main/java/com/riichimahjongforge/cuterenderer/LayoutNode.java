package com.riichimahjongforge.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector3f;

/**
 * Container that arranges children in fixed slot positions per its {@link Layout}.
 *
 * <p>Each call to {@link #setLayout} retargets every attached slot's transform so
 * children glide to new positions instead of teleporting. Slots are stable empty
 * group anchors — attach renderable nodes as children of {@code slot(i)}.
 */
public final class LayoutNode extends CuteNode {

    private Layout layout;
    private final List<GroupNode> slots = new ArrayList<>();
    private final Vector3f tmp = new Vector3f();

    public LayoutNode(Layout layout) {
        this.layout = layout;
        rebuildSlots();
    }

    public Layout layout() { return layout; }

    public void setLayout(Layout newLayout) {
        if (newLayout.slotCount() != slots.size()) {
            // Slot count change — destructively rebuild. Children of removed slots
            // are dropped along with their slot anchor.
            for (GroupNode g : slots) removeChild(g);
            slots.clear();
            this.layout = newLayout;
            rebuildSlots();
            return;
        }
        this.layout = newLayout;
        for (int i = 0; i < slots.size(); i++) {
            Vector3f p = layout.slotPos(i, tmp);
            slots.get(i).transform.targetPos(p.x, p.y, p.z);
        }
    }

    public GroupNode slot(int i) {
        return slots.get(i);
    }

    public int slotCount() {
        return slots.size();
    }

    private void rebuildSlots() {
        for (int i = 0; i < layout.slotCount(); i++) {
            GroupNode g = new GroupNode();
            Vector3f p = layout.slotPos(i, tmp);
            g.transform.setPos(p.x, p.y, p.z);
            slots.add(g);
            super.addChild(g);
        }
    }

    /** LayoutNodes own slot management; reject manual addChild from outside. */
    @Override
    public <T extends CuteNode> T addChild(T child) {
        throw new UnsupportedOperationException(
                "Attach to LayoutNode.slot(i) instead of addChild");
    }

    @Override
    protected void drawSelf(PoseStack pose, MultiBufferSource buffers, int packedLight,
                            int packedOverlay, float partialTick) {
        // Container only.
    }
}
