package dev.hyflux.api.node;

import dev.hyflux.api.energy.EnergyStorage;
import dev.hyflux.api.energy.IEnergyStorage;
import dev.hyflux.api.math.Direction;

import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Common base for the ready-made node classes. It owns an {@link EnergyStorage},
 * remembers its {@link NodeContext} while attached, supports per-side connection
 * toggles and a priority, and saves all of that automatically.
 *
 * <p>Override {@link #onAdded}, {@link #onRemoved}, {@link #writeData} and
 * {@link #readData} for your own logic and state.
 */
public abstract class AbstractEnergyNode implements EnergyNode {

    private static final String KEY_PRIORITY = "Priority";
    private static final String KEY_DISABLED_SIDES = "DisabledSides";

    private final EnergyRole role;
    protected final EnergyStorage storage;
    private final Set<Direction> disabledSides = EnumSet.noneOf(Direction.class);
    private int priority;
    private NodeContext context;
    private String activeState;
    private Boolean shownActive;

    /**
     * @param role    the node role
     * @param storage the internal buffer, or null for nodes that hold no energy (conduits)
     */
    protected AbstractEnergyNode(EnergyRole role, EnergyStorage storage) {
        this.role = role;
        this.storage = storage;
    }

    @Override
    public final EnergyRole getRole() {
        return role;
    }

    @Override
    public IEnergyStorage getEnergyStorage() {
        return storage == null ? IEnergyStorage.EMPTY : storage;
    }

    /** The internal buffer, typed. Null for nodes created without one. */
    public EnergyStorage getStorage() {
        return storage;
    }

    @Override
    public boolean canConnect(Direction side) {
        return !disabledSides.contains(side);
    }

    /** Enables or disables connections on one side and updates the network right away. */
    public void setSideEnabled(Direction side, boolean enabled) {
        boolean changed = enabled ? disabledSides.remove(side) : disabledSides.add(side);
        if (changed && context != null) {
            context.world().refreshConnections(context.pos());
            context.markDirty();
        }
    }

    public boolean isSideEnabled(Direction side) {
        return !disabledSides.contains(side);
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        if (this.priority != priority) {
            this.priority = priority;
            markDirty();
        }
    }

    /**
     * Names the block state to show while this generator or machine is running, for example
     * {@code "On"} for a state defined under {@code BlockType.State.Definitions} in the block JSON.
     * The block switches back to its normal look when it stops. Null (the default) disables this.
     */
    public void setActiveState(String state) {
        this.activeState = state == null || state.isBlank() ? null : state;
        this.shownActive = null;
    }

    public String getActiveState() {
        return activeState;
    }

    /**
     * Shows the active state while {@code active} is true and the normal look otherwise. Called
     * every tick by {@link ProducerNode} and {@link ConsumerNode}; re-applied every 2 seconds in
     * case something else changed the block.
     */
    protected void showActive(NodeContext context, boolean active) {
        if (activeState == null) {
            return;
        }
        if (shownActive == null || shownActive != active || context.currentTick() % 60 == 0) {
            shownActive = active;
            context.setBlockState(active ? activeState : "default");
        }
    }

    /** The context while attached, otherwise null. */
    public NodeContext getContext() {
        return context;
    }

    public boolean isAttached() {
        return context != null;
    }

    protected void markDirty() {
        if (context != null) {
            context.markDirty();
        }
    }

    @Override
    public final void onAttached(NodeContext context) {
        this.context = context;
        onAdded(context);
    }

    @Override
    public final void onDetached(NodeContext context, DetachReason reason) {
        try {
            onRemoved(context, reason);
        } finally {
            this.context = null;
        }
    }

    /** Called after the node joins a world. */
    protected void onAdded(NodeContext context) {
    }

    /** Called when the node leaves a world, before its context is cleared. */
    protected void onRemoved(NodeContext context, DetachReason reason) {
    }

    @Override
    public final void save(NodeData data) {
        if (storage != null) {
            storage.save(data);
        }
        data.putInt(KEY_PRIORITY, priority);
        if (!disabledSides.isEmpty()) {
            StringJoiner joiner = new StringJoiner(",");
            disabledSides.forEach(d -> joiner.add(d.name()));
            data.putString(KEY_DISABLED_SIDES, joiner.toString());
        }
        writeData(data);
    }

    @Override
    public final void load(NodeData data) {
        if (storage != null) {
            storage.load(data);
        }
        priority = data.getInt(KEY_PRIORITY, priority); // keep the factory default when absent
        disabledSides.clear();
        String sides = data.getString(KEY_DISABLED_SIDES, "");
        for (String name : sides.split(",")) {
            if (!name.isBlank()) {
                try {
                    disabledSides.add(Direction.valueOf(name.trim()));
                } catch (IllegalArgumentException ignored) {
                    // Unknown side name in saved data; skip it.
                }
            }
        }
        readData(data);
    }

    /** Write your own state here. Energy, priority and sides are already saved. */
    protected void writeData(NodeData data) {
    }

    /** Read the state you wrote in {@link #writeData}. */
    protected void readData(NodeData data) {
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + role + ", " + getEnergyStorage().getEnergyStored() + "/"
            + getEnergyStorage().getMaxEnergyStored() + " HF]";
    }
}
