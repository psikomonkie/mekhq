package mekhq.gui.menus;

import megamek.common.Transporter;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.event.UnitChangedEvent;
import mekhq.campaign.unit.TacticalTransportedUnitsSummary;
import mekhq.campaign.unit.Unit;

import java.awt.event.ActionEvent;
import java.util.HashSet;
import java.util.Set;

import static mekhq.campaign.enums.CampaignTransportType.TACTICAL_TRANSPORT;

public class AssignForceToTacticalTransportMenu extends AssignForceToTransportMenu {

    public AssignForceToTacticalTransportMenu(Campaign campaign, Unit... units) {
        super(TACTICAL_TRANSPORT, campaign, units);
    }

    @Override
    protected Set<Class<? extends Transporter>> filterTransporterTypeMenus(final Unit... units) {
        Set<Class<? extends Transporter>> transporterTypes = new HashSet<>(campaign.getTransports(TACTICAL_TRANSPORT).keySet());

        for (Unit unit : units) {
            Set<Class<? extends Transporter>> unitTransporterTypes = TACTICAL_TRANSPORT.mapEntityToTransporters(unit.getEntity());
            if (!unitTransporterTypes.isEmpty()) {
                transporterTypes.retainAll(unitTransporterTypes);
            } else {
                return new HashSet<>();
            }
        }
        if (transporterTypes.isEmpty()) {
            return new HashSet<>();
        }

        return transporterTypes;
    }

    @Override
    protected void transportMenuAction(ActionEvent evt, Class<? extends Transporter> transporterType, Unit transport, Unit... units) {
        for (Unit unit : units) {
            if (!transport.getEntity().canLoad(unit.getEntity(), false)) {
                return; //TODO error popup
            }

        }
        Set<Unit> oldTransports = transport.loadTacticalTransport(transporterType, units);
        if (!oldTransports.isEmpty()) {
            oldTransports.forEach(oldTransport -> campaign.updateTransportInTransports(TACTICAL_TRANSPORT, oldTransport));
            oldTransports.forEach(oldTransport -> MekHQ.triggerEvent(new UnitChangedEvent(transport)));
        }
        for (Unit unit : units) {
            MekHQ.triggerEvent(new UnitChangedEvent(unit));
        }
        campaign.updateTransportInTransports(TACTICAL_TRANSPORT, transport);
        MekHQ.triggerEvent(new UnitChangedEvent(transport));
    }
}
