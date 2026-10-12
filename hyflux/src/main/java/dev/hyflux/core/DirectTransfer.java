package dev.hyflux.core;

import dev.hyflux.api.energy.EnergyUtil;
import dev.hyflux.api.energy.IEnergyStorage;
import dev.hyflux.api.math.Direction;
import dev.hyflux.api.node.EnergyRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Energy exchanged between blocks that touch, without a cable, Redstone Flux style: a provider
 * pushes into the receivers on its faces. Only one hop is ever made, so energy never travels
 * through a block. Consumers only receive, so two machines that touch never share energy.
 *
 * <p>Each tick runs three passes, mirroring the network rules (machines before batteries):
 * generators feed touching machines, batteries cover what touching machines still need, then
 * generators charge touching batteries with what is left.
 */
final class DirectTransfer {

    enum Pass {
        PRODUCERS_TO_CONSUMERS(EnergyRole.PRODUCER, EnergyRole.CONSUMER),
        STORAGE_TO_CONSUMERS(EnergyRole.STORAGE, EnergyRole.CONSUMER),
        PRODUCERS_TO_STORAGE(EnergyRole.PRODUCER, EnergyRole.STORAGE);

        final EnergyRole from;
        final EnergyRole to;

        Pass(EnergyRole from, EnergyRole to) {
            this.from = from;
            this.to = to;
        }
    }

    private DirectTransfer() {
    }

    /** Runs one pass over {@code entries}. Returns the energy moved. */
    static long run(Pass pass, NodeEntry[] entries, Map<?, NodeEntry> nodes, ConnectionTest connects) {
        long moved = 0;
        List<NetworkBalancer.Slot> receivers = new ArrayList<>(6);
        for (NodeEntry source : entries) {
            if (source.role != pass.from || !source.attached || !source.active) {
                continue;
            }
            IEnergyStorage out = source.node.getEnergyStorage();
            if (out == null || !out.canExtract()) {
                continue;
            }
            receivers.clear();
            long wanted = 0;
            for (Direction d : Direction.all()) {
                NodeEntry target = nodes.get(source.pos.offset(d));
                if (target == null || target.role != pass.to || !target.attached || !target.active
                    || !connects.test(source, target, d)) {
                    continue;
                }
                IEnergyStorage in = target.node.getEnergyStorage();
                if (in == null || !in.canReceive()) {
                    continue;
                }
                long want = target.wantIn(in);
                if (want > 0) {
                    receivers.add(new NetworkBalancer.Slot(in, target.node.getPriority(), want, target));
                    wanted = EnergyUtil.saturatedAdd(wanted, want);
                }
            }
            if (receivers.isEmpty()) {
                continue;
            }
            long offered = Math.min(out.extractEnergy(wanted, true), source.offerOut(out));
            if (offered <= 0) {
                continue;
            }
            long taken = Math.max(0, out.extractEnergy(offered, false));
            receivers.sort(NetworkBalancer.BY_PRIORITY_DESC);
            long given = NetworkBalancer.distribute(receivers, taken, false);
            if (given < taken) {
                // A receiver accepted less than it promised; try to hand the rest back.
                out.receiveEnergy(taken - given, false);
            }
            source.sent += given;
            moved = EnergyUtil.saturatedAdd(moved, given);
        }
        return moved;
    }

    /** Whether {@code a} and its neighbour {@code b} in direction {@code d} accept each other. */
    @FunctionalInterface
    interface ConnectionTest {
        boolean test(NodeEntry a, NodeEntry b, Direction d);
    }
}
