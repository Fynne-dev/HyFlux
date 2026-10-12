package dev.hyflux.api.energy;

/**
 * Something that holds energy. This is the HyFlux equivalent of the Redstone Flux
 * {@code IEnergyStorage}: every producer, consumer and battery exposes one, and all
 * transfers go through {@link #receiveEnergy} and {@link #extractEnergy}.
 *
 * <p>Amounts are in HF (HyFlux units) and use {@code long}. The network trusts
 * {@code simulate = true} results, so an implementation must not accept or provide
 * more in a real call than it reported for the same call simulated.
 */
public interface IEnergyStorage {

    /**
     * Adds energy to this storage.
     *
     * @param maxReceive the most energy to add
     * @param simulate   if true, only report how much would be accepted
     * @return the energy accepted (or that would be accepted)
     */
    long receiveEnergy(long maxReceive, boolean simulate);

    /**
     * Removes energy from this storage.
     *
     * @param maxExtract the most energy to remove
     * @param simulate   if true, only report how much would be removed
     * @return the energy removed (or that would be removed)
     */
    long extractEnergy(long maxExtract, boolean simulate);

    /** The energy currently stored. */
    long getEnergyStored();

    /** The most energy this storage can hold. */
    long getMaxEnergyStored();

    /** Whether {@link #receiveEnergy} can ever return more than zero. */
    default boolean canReceive() {
        return true;
    }

    /** Whether {@link #extractEnergy} can ever return more than zero. */
    default boolean canExtract() {
        return true;
    }

    /** Free space left, never negative. */
    default long getFreeSpace() {
        return Math.max(0L, getMaxEnergyStored() - getEnergyStored());
    }

    /** Fill level from 0.0 to 1.0. */
    default double getFillRatio() {
        long max = getMaxEnergyStored();
        return max <= 0 ? 0.0 : Math.min(1.0, (double) getEnergyStored() / (double) max);
    }

    /** A storage that holds nothing and accepts nothing. */
    IEnergyStorage EMPTY = new IEnergyStorage() {
        @Override
        public long receiveEnergy(long maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public long extractEnergy(long maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public long getEnergyStored() {
            return 0;
        }

        @Override
        public long getMaxEnergyStored() {
            return 0;
        }

        @Override
        public boolean canReceive() {
            return false;
        }

        @Override
        public boolean canExtract() {
            return false;
        }
    };
}
