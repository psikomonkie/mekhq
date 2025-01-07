package mekhq.campaign.unit;

import megamek.common.*;
import mekhq.Utilities;
import mekhq.campaign.Campaign;
import mekhq.campaign.enums.CampaignTransportType;

import java.util.*;

public class ShipTransportedUnitsSummary extends AbstractTransportedUnitsSummary {

    /**
     * Initialize the transport details for a transport ship
     * @param transport unit this summary is about
     */
    public ShipTransportedUnitsSummary(Unit transport) {
        super(transport);
    }

    /**
     * Main method to be used for loading units onto a transport
     *
     * @param transportedUnits Units we wish to load
     * @return the old transports the transportedUnits were assigned to, or an empty set
     */
    @Override
    public Set<Unit> loadTransport(Unit... transportedUnits) {
        return super.loadTransport(transportedUnits);
    }

    /**
     * Main method to be used for unloading units from a transport
     *
     * @param transportedUnits Units we wish to unload
     */
    @Override
    public void unloadTransport(Unit... transportedUnits) {
        super.unloadTransport(transportedUnits);

    }

    @Override
    protected void unloadTransport(Unit transportedUnit) {
        super.unloadTransport(transportedUnit);

        // And if the unit is being transported by us,
        // then update its transport ship assignment (provided the
        // assignment is actually to us!).
        if (transportedUnit.hasTransportShipAssignment()
            && transportedUnit.getTransportShipAssignment().getTransportShip().equals(transport)) {
            transportedUnit.setTransportShipAssignment(null);

        }
    }


    /**
     * Bay loading utility used when assigning units to bay-equipped transport units
     * For each passed-in unit, this will find the first available, transport bay
     * and set
     * both the target bay and the transport ship. Once in the MM lobby,
     * this data
     * will be used to actually load the unit into a bay on the transport.
     *
     * @param transportedUnits units being loaded
     * @param transporterType type (Class) of bay or Transporter
     * @return old transports; what were  the units' previous transport, if they had one?
     */
    public Set<Unit> loadTransportShip(Vector<Unit> transportedUnits, Class<? extends Transporter> transporterType) {
        Set<Unit> oldTransports = new HashSet<>();
        //Set<Entity> oldTransportedEntities = clearTransportedEntities();
        for (Unit transportedUnit : transportedUnits) {
            Unit oldTransport = loadTransport(transporterType, transportedUnit);
            if (oldTransport != null) {
                oldTransports.add(oldTransport);
            }
        }
        transport.initializeShipTransportSpace();
        return oldTransports;
    }

    private Unit loadTransport(Class<? extends Transporter> transporterType, Unit transportedUnit) {
        Unit oldTransport = null;
        int bayNumber = Utilities.selectBestBayFor(transportedUnit.getEntity(), transport.getEntity());

        Class<? extends Transporter> oldTransporterType = null;
        if(transportedUnit.hasTransportShipAssignment()) {
            oldTransport = transportedUnit.getTransportShipAssignment().getTransportShip();
            oldTransporterType = transportedUnit.getTransportShipAssignment().getTransporterType();
            if (oldTransport.getEntity() != null) {
                oldTransport.unloadFromTransportShip(transportedUnit);
            }
        }

        transportedUnit.setTransportShipAssignment(new TransportShipAssignment(transport, bayNumber));

        if ((transportedUnit.getEntity() != null)) {
            if (transport.getEntity() != null) {
                //loadEntity(transportedUnit.getEntity());
            }
            // This shouldn't happen, but it'd be really annoying to debug if it did
            if ((transportedUnit.getEntity().getBayById(bayNumber) != null && transportedUnit.getEntity().getBayById(bayNumber).getClass() != transporterType)) {
                logger.warn(String.format("Unit was assigned a bay number for a different transport type than the unit is assigned! " +
                    "Transport: %s Unit: %s Assigned Transporter: %s Assigned Bay ID: %s",
                    transport.getName(), transportedUnit.getName(), transporterType, bayNumber));
            }
        }

        addTransportedUnit(transportedUnit);
        if (!Objects.equals(oldTransport, transport)
            && (transportedUnit.getTransportShipAssignment().getTransporterType() != oldTransporterType)) {
            setCurrentTransportCapacity(transporterType,
                getCurrentTransportCapacity(transporterType) - CampaignTransportType.transportCapacityUsage(transporterType, transportedUnit.getEntity()));
        }
        return oldTransport;
    }

    /**
     * Bay unloading utility used when removing units from bay-equipped transport
     * units
     * and/or moving them to a new transport
     *
     * @param transportedUnit The unit that we wish to unload from this transport
     */
    public void unloadFromTransportShip(Unit transportedUnit) {
        unloadTransport(transportedUnit);
    }

    /**
     * Bay unloading utility used when removing a bay-equipped Transport unit
     * This removes all units assigned to the transport from it
     */
    @Override
    public void clearTransportedUnits(Campaign campaign) {
        clearTransportedUnits();

        // And now reset the Transported values for all the units we just booted
        campaign.getHangar().forEachUnit(u -> {
            if (u.hasTransportShipAssignment()
                && Objects.equals(transport, u.getTransportShipAssignment().getTransportShip())) {
                u.setTransportShipAssignment(null);
            }
        });

        initializeTransportCapacity(transport.getEntity().getTransports());
    }

    /**
     * Fixes references after loading
     *
     * @param campaign
     * @param unit
     */
    @Override
    public void fixReferences(Campaign campaign, Unit unit) {
        Set<Unit> newTransportedUnits = new HashSet<>();
        for (Unit transportedUnit : getTransportedUnits()) {
            if (transportedUnit instanceof Unit.UnitRef) {
                Unit realUnit = campaign.getHangar().getUnit(transportedUnit.getId());
                if (realUnit != null) {
                    newTransportedUnits.add(realUnit);
                } else {
                    logger.error(
                        String.format("Unit %s ('%s') references missing transported unit %s",
                            unit.getId(), unit.getName(), transportedUnit.getId()));
                }
            } else {
                newTransportedUnits.add(transportedUnit);
            }
        }
        replaceTransportedUnits(newTransportedUnits);
    }
}
