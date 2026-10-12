package dev.hyflux.core;

import dev.hyflux.api.energy.EnergyUtil;
import dev.hyflux.api.energy.IEnergyStorage;
import dev.hyflux.api.network.NetworkStats;
import dev.hyflux.api.node.EnergyNode;
import dev.hyflux.api.node.EnergyRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Moves energy inside one network for one tick.
 *
 * <ol>
 *   <li>Consumers are served from producers, then from batteries if producers fall short.</li>
 *   <li>Leftover production charges batteries.</li>
 *   <li>Everything that moves counts against the network transfer limit (its slowest conduit).</li>
 * </ol>
 * Within a role, higher priority is served first and equal priorities share evenly
 * (water filling), so small requests are met in full before large ones are capped.
 */
final class NetworkBalancer {

    static final Comparator<Slot> BY_PRIORITY_DESC = (a, b) -> Integer.compare(b.priority, a.priority);
    private static final Comparator<Slot> BY_AMOUNT_ASC = Comparator.comparingLong(s -> s.amount);

    private NetworkBalancer() {
    }

    /** One storage taking part in a transfer, with how much it can still give or take. */
    static final class Slot {
        final IEnergyStorage storage;
        final int priority;
        /** Node whose flow counters are updated, or null. */
        final NodeEntry owner;
        long amount;

        Slot(IEnergyStorage storage, int priority, long amount, NodeEntry owner) {
            this.storage = storage;
            this.priority = priority;
            this.amount = amount;
            this.owner = owner;
        }
    }

    static NetworkStats balance(long tick, Iterable<NodeEntry> members) {
        List<Slot> producers = new ArrayList<>();
        List<Slot> consumers = new ArrayList<>();
        List<Slot> batteryOut = new ArrayList<>();
        List<Slot> batteryIn = new ArrayList<>();
        long limit = Long.MAX_VALUE;

        for (NodeEntry entry : members) {
            EnergyNode node = entry.node;
            if (entry.role == EnergyRole.CONDUIT) {
                // The limit is a property of the cabling, so frozen cables still count.
                limit = Math.min(limit, Math.max(0, node.getTransferRate()));
                continue;
            }
            if (!entry.active) {
                continue;
            }
            IEnergyStorage s = node.getEnergyStorage();
            switch (entry.role) {
                case CONDUIT -> {
                }
                case PRODUCER -> {
                    if (s != null && s.canExtract()) {
                        addSlot(producers, s, node.getPriority(), entry.offerOut(s), entry);
                    }
                }
                case CONSUMER -> {
                    if (s != null && s.canReceive()) {
                        addSlot(consumers, s, node.getPriority(), entry.wantIn(s), entry);
                    }
                }
                case STORAGE -> {
                    if (s != null) {
                        int priority = node.getPriority();
                        if (s.canExtract()) {
                            addSlot(batteryOut, s, priority, entry.offerOut(s), entry);
                        }
                        if (s.canReceive()) {
                            addSlot(batteryIn, s, priority, entry.wantIn(s), entry);
                        }
                    }
                }
            }
        }

        long available = sum(producers);
        long demand = sum(consumers);
        long storageIn = sum(batteryIn);

        producers.sort(BY_PRIORITY_DESC);
        consumers.sort(BY_PRIORITY_DESC);
        batteryOut.sort(BY_PRIORITY_DESC);
        batteryIn.sort(BY_PRIORITY_DESC);

        long budget = limit;
        long pool = 0;

        // 1. Serve consumers: producers first, batteries cover the shortfall.
        long need = Math.min(demand, budget);
        long produced = distribute(producers, Math.min(need, available), true);
        pool += produced;
        long discharged = 0;
        if (pool < need) {
            discharged = distribute(batteryOut, need - pool, true);
            pool += discharged;
        }
        long consumed = distribute(consumers, pool, false);
        pool -= consumed;
        budget = Math.max(0, budget - consumed);

        // 2. Store surplus production.
        long charged = 0;
        long room = Math.min(storageIn, budget);
        if (room > pool) {
            long extra = distribute(producers, room - pool, true);
            produced += extra;
            pool += extra;
        }
        if (pool > 0) {
            charged = distribute(batteryIn, Math.min(pool, room), false);
            pool -= charged;
        }

        // 3. Energy already pulled out must not vanish: push any remainder into batteries regardless of the limit.
        if (pool > 0) {
            long back = distribute(batteryIn, pool, false);
            charged += back;
            pool -= back;
        }
        long voided = Math.max(0, pool);

        boolean limited = limit != Long.MAX_VALUE
            && consumed + charged >= limit
            && (consumed < demand || (produced < available && charged < storageIn));

        return new NetworkStats(tick, available, demand, produced, consumed, charged, discharged, voided, limit, limited);
    }

    private static void addSlot(List<Slot> list, IEnergyStorage storage, int priority, long amount, NodeEntry owner) {
        if (amount > 0) {
            list.add(new Slot(storage, priority, amount, owner));
        }
    }

    private static long sum(List<Slot> slots) {
        long total = 0;
        for (Slot s : slots) {
            total = EnergyUtil.saturatedAdd(total, s.amount);
        }
        return total;
    }

    /**
     * Extracts from (or inserts into) the slots up to {@code target} in total, highest priority
     * group first. Slots must already be sorted by descending priority. Returns what actually moved.
     */
    static long distribute(List<Slot> slots, long target, boolean extract) {
        if (target <= 0 || slots.isEmpty()) {
            return 0;
        }
        long moved = 0;
        int n = slots.size();
        int i = 0;
        while (i < n && moved < target) {
            int priority = slots.get(i).priority;
            int j = i;
            while (j < n && slots.get(j).priority == priority) {
                j++;
            }
            moved = EnergyUtil.saturatedAdd(moved, fillGroup(slots.subList(i, j), target - moved, extract));
            i = j;
        }
        return moved;
    }

    /** Water filling within one priority group: smallest requests first, each capped at an even share. */
    private static long fillGroup(List<Slot> group, long budget, boolean extract) {
        if (group.size() > 1) {
            group.sort(BY_AMOUNT_ASC);
        }
        long moved = 0;
        int count = group.size();
        for (int k = 0; k < count && moved < budget; k++) {
            Slot slot = group.get(k);
            if (slot.amount <= 0) {
                continue;
            }
            long share = (budget - moved) / (count - k);
            long give = Math.min(slot.amount, share);
            if (give <= 0) {
                continue;
            }
            long actual;
            if (extract) {
                // Energy handed over is real even if the storage gave more than asked.
                actual = Math.max(0, slot.storage.extractEnergy(give, false));
            } else {
                // Never count more than was offered, whatever the storage claims.
                actual = Math.max(0, Math.min(give, slot.storage.receiveEnergy(give, false)));
            }
            slot.amount = Math.max(0, slot.amount - actual);
            moved = EnergyUtil.saturatedAdd(moved, actual);
            if (slot.owner != null) {
                if (extract) {
                    slot.owner.sent += actual;
                } else {
                    slot.owner.received += actual;
                }
            }
        }
        return moved;
    }
}
