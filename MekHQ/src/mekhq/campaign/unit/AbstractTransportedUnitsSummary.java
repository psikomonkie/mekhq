package mekhq.campaign.unit;

import megamek.common.*;
import megamek.common.annotations.Nullable;
import megamek.logging.MMLogger;
import mekhq.campaign.Campaign;

import javax.swing.*;
import java.util.*;

public abstract class AbstractTransportedUnitsSummary implements ITransportedUnitsSummary {
    protected final MMLogger logger = MMLogger.create(this.getClass());
    protected Unit transport;
    private Set<Unit> transportedUnits = new HashSet<>();
    private Map<Class<? extends Transporter>, Double> transportCapacity = new HashMap<>();

    AbstractTransportedUnitsSummary(Unit transport) {
        this.transport = transport;
        if (transport.getEntity() != null) {
            initializeTransportCapacity(transport.getEntity().getTransports());
        }
    }

    /**
     * Main method to be used for loading units onto a transport
     *
     * @param transportedUnits Units we wish to load
     * @return the old transports the transportedUnits were assigned to, or an empty set
     */
    public Set<Unit> loadTransport(Unit... transportedUnits) {
        HashSet<Unit> oldTransports = new HashSet<>();

        return oldTransports;
    }

    /**
     * Main method to be used for unloading units from a transport
     *
     * @param transportedUnits Units we wish to unload
     */
    @Override
    public void unloadTransport(Unit... transportedUnits) {
        for (Unit transportedUnit : transportedUnits) {
            unloadTransport(transportedUnit);
        }
    }

    protected void unloadTransport(Unit transportedUnit) {
        Objects.requireNonNull(transportedUnit);

        // Remove this unit from our collection of transported units.
        removeTransportedUnit(transportedUnit);
        if (transport.getEntity() != null) {
            //transport.getEntity().unload(transportedUnit.getEntity()); //TODO fix this?
            //initializeTransportCapacity(transport.getEntity().getTransports());
        }
    }

    /**
     * Recalculates transport capacity
     * @param transporters What transporters are we tracking the details of?
     */
    @Override
    public void initializeTransportCapacity(@Nullable Vector<Transporter> transporters) {
        transportCapacity.clear();
        clearTransportedEntities();
        loadTransportedEntities();
        if (transporters != null && !transporters.isEmpty()) {
            for (Transporter transporter : transporters) {
                if (transportCapacity.containsKey(transporter.getClass())) {
                    transportCapacity.replace(transporter.getClass(), transportCapacity.get(transporter.getClass()) + transporter.getUnused());
                }
                else {
                    transportCapacity.put(transporter.getClass(), transporter.getUnused());
                }
            }
        }
    }

    /**
     * If this unit is capable of transporting another unit, return true
     *
     * @return true if the unit can transport another unit
     */
    @Override
    public boolean hasTransportCapacity() {
        return !transportCapacity.isEmpty();
    }

    /**
     * Gets the different kinds of transporters the transport has
     *
     * @return Set of Transporter classes
     */
    @Override
    public Set<Class<? extends Transporter>> getTransportCapabilities() {
        return transportCapacity.keySet();
    }

    /**
     * Returns true if the unit has capacity left for a transporter type
     *
     * @param transporterType Does the unit have free capacity in this type?
     * @return True if the unit has capacity, false if not
     */
    @Override
    public boolean hasTransportCapacity(Class<? extends Transporter> transporterType) {
        return transportCapacity.containsKey(transporterType);
    }

    /**
     * Returns the current capacity of a transporter type
     *
     * @param transporterType What kind of transporter types are we checking?
     * @return The current capacity of the transporter, or 0
     */
    @Override
    public double getCurrentTransportCapacity(Class<? extends Transporter> transporterType) {
        return transportCapacity.getOrDefault(transporterType, 0.0);
    }

    /**
     * Sets the current transport capacity for the provided transport type
     *
     * @param transporterType What kind of transporter are we changing the capacity of?
     * @param capacity        What is the new capacity?
     */
    @Override
    public void setCurrentTransportCapacity(Class<? extends Transporter> transporterType, double capacity) {
        transportCapacity.replace(transporterType, capacity);
    }

    /**
     * Gets a value indicating whether or not this unit is
     * transporting units.
     *
     * @return true if the unit has any transported units
     */
    @Override
    public boolean hasTransportedUnits() {
        return !transportedUnits.isEmpty();
    }

    /**
     * @return the set of units being transported by this unit.
     */
    @Override
    public Set<Unit> getTransportedUnits() {
        return Collections.unmodifiableSet(transportedUnits);
    }

    /**
     * Adds a unit to our set of transported units.
     *
     * @param unit The unit being transported by this instance.
     */
    @Override
    public void addTransportedUnit(Unit unit) {
        transportedUnits.add(Objects.requireNonNull(unit));
    }

    /**
     * Removes a unit from our set of transported units.
     *
     * @param unit The unit to remove from our set of transported units.
     * @return True if the unit was removed, otherwise false.
     */
    @Override
    public boolean removeTransportedUnit(Unit unit) {
        return transportedUnits.remove(unit);
    }

    /**
     * Clears the set of units being transported by this unit.
     */
    @Override
    public void clearTransportedUnits() {
        if (!transportedUnits.isEmpty()) {
            transportedUnits.clear();
        }

        clearTransportedEntities();
    }

    protected Set<Entity> clearTransportedEntities() {
        Set<Entity> transportedEntities = new HashSet<>();
        if (transport.getEntity() != null) {
            for (Entity transportedEntity : transport.getEntity().getUnloadableUnits()) {
                transport.getEntity().unload(transportedEntity);
                transportedEntities.add(transportedEntity);
            }

            transport.getEntity().resetTransporter();
        }
        return transportedEntities;
    }

    protected void loadTransportedEntities() {
        if (transport.getEntity() != null) {
            for (Unit transportedUnit : getTransportedUnits()) {
                if (transportedUnit.getEntity() != null) {
                    transport.getEntity().resetBays();
                    loadEntity(transportedUnit.getEntity());
                }
            }
        }
    }

    protected void loadEntity(Entity transportedEntity) {
        if (transport.getEntity() != null && transportedEntity != null) {
            if (transport.getEntity().canLoad(transportedEntity, false)) {
                transport.getEntity().load(transportedEntity, false);
            }
            else {
                logger.error(String.format("Could not load entity %s onto unit %s", transportedEntity.getDisplayName(), transport.getName()));
            }
        }
    }

    /**
     * When fixing references we need to replace the transported units
     * @param newTransportedUnits The units that should be transported
     */
    @Override
    public void replaceTransportedUnits(Set<Unit> newTransportedUnits) {
        clearTransportedUnits();
        for (Unit newUnit : newTransportedUnits) {
            addTransportedUnit(newUnit);
        }
    }
}
