/*
 * Copyright (C) 2020-2026 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version,
 * as published by the Free Software Foundation.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * A copy of the GPL should have been included with this project;
 * if not, see <https://www.gnu.org/licenses/>.
 *
 * NOTICE: The MegaMek organization is a non-profit group of volunteers
 * creating free software for the BattleTech community.
 *
 * MechWarrior, BattleMech, `Mech and AeroTech are registered trademarks
 * of The Topps Company, Inc. All Rights Reserved.
 *
 * Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of
 * InMediaRes Productions, LLC.
 *
 * MechWarrior Copyright Microsoft Corporation. MekHQ was created under
 * Microsoft's "Game Content Usage Rules"
 * <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
 * affiliated with Microsoft.
 */
package mekhq.campaign.unit.actions;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import megamek.common.units.Entity;
import megamek.common.units.EntityMovementMode;
import megamek.common.units.Jumpship;
import megamek.common.units.LandAirMek;
import megamek.common.units.Mek;
import megamek.common.units.SmallCraft;
import megamek.common.units.SupportTank;
import megamek.common.units.Tank;
import mekhq.campaign.Campaign;
import mekhq.campaign.campaignOptions.CampaignOptions;
import mekhq.campaign.personnel.Person;
import mekhq.campaign.unit.Unit;
import org.junit.jupiter.api.Test;

public class HirePersonnelUnitActionTest {
    @Test
    public void fullUnitTakesNoAction() {
        Campaign mockCampaign = mock(Campaign.class);
        CampaignOptions mockOptions = mock(CampaignOptions.class);
        doReturn(mockOptions).when(mockCampaign).getCampaignOptions();
        doReturn(false).when(mockOptions).isUseArtillery();
        Entity mockEntity = mock(Mek.class);
        Unit unit = spy(new Unit(mockEntity, mockCampaign));

        doReturn(false).when(unit).canTakeMoreDrivers();
        doReturn(false).when(unit).canTakeMoreGunners();
        doReturn(false).when(unit).canTakeMoreVesselCrew();
        doReturn(false).when(unit).canTakeNavigator();
        doReturn(false).when(unit).canTakeTechOfficer();
        doNothing().when(unit).resetPilotAndEntity();
        doNothing().when(unit).runDiagnostic(false);

        HirePersonnelUnitAction action = new HirePersonnelUnitAction(false);
        action.execute(mockCampaign, unit);

        mekhq.campaign.Campaign campaign = Mockito.verify(mockCampaign, Mockito.times(0));
        campaign.getPlayerForce()
              .getHumanResources()
              .newPerson(campaign, mekhq.campaign.personnel.enums.PersonnelRole.MEKWARRIOR);
        Campaign campaign1 = Mockito.verify(mockCampaign, Mockito.times(0));
        campaign1.getPlayerForce()
              .getHumanResources()
              .recruitPerson(campaign1,
                    ArgumentMatchers.any(Person.class),
                    ArgumentMatchers.eq(false),
                    ArgumentMatchers.eq(true));
    }

    @Test
    public void actionPassesAlongGMSetting() {
        Campaign mockCampaign = mock(Campaign.class);
        CampaignOptions mockOptions = mock(CampaignOptions.class);
        doReturn(mockOptions).when(mockCampaign).getCampaignOptions();
        doReturn(false).when(mockOptions).isUseArtillery();
        Entity mockEntity = mock(Mek.class);
        Unit unit = spy(new Unit(mockEntity, mockCampaign));

        doReturn(true).doReturn(false).when(unit).canTakeMoreDrivers();
        doReturn(true).when(unit).usesSoloPilot();
        doReturn(false).when(unit).canTakeMoreGunners();
        doReturn(false).when(unit).canTakeMoreVesselCrew();
        doReturn(false).when(unit).canTakeNavigator();
        doReturn(false).when(unit).canTakeTechOfficer();
        doNothing().when(unit).resetPilotAndEntity();
        doNothing().when(unit).runDiagnostic(false);

        Person mockDriver = mock(Person.class);
        doNothing().when(unit).addPilotOrSoldier(eq(mockDriver));
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .newPerson(mockCampaign, mekhq.campaign.personnel.enums.PersonnelRole.MEKWARRIOR)).thenReturn(
              mockDriver);
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .recruitPerson(mockCampaign,
                         ArgumentMatchers.eq(mockDriver),
                         ArgumentMatchers.eq(true),
                         ArgumentMatchers.eq(true))).thenReturn(true);

        HirePersonnelUnitAction action = new HirePersonnelUnitAction(true);
        action.execute(mockCampaign, unit);

        mekhq.campaign.Campaign campaign = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign.getPlayerForce()
              .getHumanResources()
              .newPerson(campaign, mekhq.campaign.personnel.enums.PersonnelRole.MEKWARRIOR);
        Campaign campaign1 = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign1.getPlayerForce()
              .getHumanResources()
              .recruitPerson(campaign1,
                    ArgumentMatchers.any(Person.class),
                    ArgumentMatchers.eq(true),
                    ArgumentMatchers.eq(true));
        verify(unit, times(1)).addPilotOrSoldier(eq(mockDriver));
    }

    @Test
    public void mekNeedingDriverAddsDriver() {
        Campaign mockCampaign = mock(Campaign.class);
        CampaignOptions mockOptions = mock(CampaignOptions.class);
        doReturn(mockOptions).when(mockCampaign).getCampaignOptions();
        doReturn(false).when(mockOptions).isUseArtillery();
        Entity mockEntity = mock(Mek.class);
        Unit unit = spy(new Unit(mockEntity, mockCampaign));

        doReturn(true).doReturn(false).when(unit).canTakeMoreDrivers();
        doReturn(true).when(unit).usesSoloPilot();
        doReturn(false).when(unit).canTakeMoreGunners();
        doReturn(false).when(unit).canTakeMoreVesselCrew();
        doReturn(false).when(unit).canTakeNavigator();
        doReturn(false).when(unit).canTakeTechOfficer();
        doNothing().when(unit).resetPilotAndEntity();
        doNothing().when(unit).runDiagnostic(false);

        Person mockDriver = mock(Person.class);
        doNothing().when(unit).addPilotOrSoldier(eq(mockDriver));
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .newPerson(mockCampaign, mekhq.campaign.personnel.enums.PersonnelRole.MEKWARRIOR)).thenReturn(
              mockDriver);
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .recruitPerson(mockCampaign,
                         ArgumentMatchers.eq(mockDriver),
                         ArgumentMatchers.anyBoolean(),
                         ArgumentMatchers.eq(true))).thenReturn(true);

        HirePersonnelUnitAction action = new HirePersonnelUnitAction(false);
        action.execute(mockCampaign, unit);

        mekhq.campaign.Campaign campaign = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign.getPlayerForce()
              .getHumanResources()
              .newPerson(campaign, mekhq.campaign.personnel.enums.PersonnelRole.MEKWARRIOR);
        Campaign campaign1 = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign1.getPlayerForce()
              .getHumanResources()
              .recruitPerson(campaign1,
                    ArgumentMatchers.any(Person.class),
                    ArgumentMatchers.eq(false),
                    ArgumentMatchers.eq(true));
        verify(unit, times(1)).addPilotOrSoldier(eq(mockDriver));
    }

    @Test
    public void mekDoesntAddDriverIfRecruitFails() {
        Campaign mockCampaign = mock(Campaign.class);
        Entity mockEntity = mock(Mek.class);
        Unit unit = spy(new Unit(mockEntity, mockCampaign));

        doReturn(true).doReturn(false).when(unit).canTakeMoreDrivers();
        doReturn(true).when(unit).usesSoloPilot();
        doReturn(false).when(unit).canTakeMoreGunners();
        doReturn(false).when(unit).canTakeMoreVesselCrew();
        doReturn(false).when(unit).canTakeNavigator();
        doReturn(false).when(unit).canTakeTechOfficer();
        doNothing().when(unit).resetPilotAndEntity();
        doNothing().when(unit).runDiagnostic(false);

        Person mockDriver = mock(Person.class);
        doNothing().when(unit).addPilotOrSoldier(eq(mockDriver));
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .newPerson(mockCampaign, mekhq.campaign.personnel.enums.PersonnelRole.MEKWARRIOR)).thenReturn(
              mockDriver);
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .recruitPerson(mockCampaign,
                         ArgumentMatchers.eq(mockDriver),
                         ArgumentMatchers.anyBoolean(),
                         ArgumentMatchers.eq(true))).thenReturn(false);

        HirePersonnelUnitAction action = new HirePersonnelUnitAction(false);
        action.execute(mockCampaign, unit);

        mekhq.campaign.Campaign campaign = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign.getPlayerForce()
              .getHumanResources()
              .newPerson(campaign, mekhq.campaign.personnel.enums.PersonnelRole.MEKWARRIOR);
        Campaign campaign1 = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign1.getPlayerForce()
              .getHumanResources()
              .recruitPerson(campaign1,
                    ArgumentMatchers.any(Person.class),
                    ArgumentMatchers.eq(false),
                    ArgumentMatchers.eq(true));
        verify(unit, times(0)).addPilotOrSoldier(eq(mockDriver));
    }

    @Test
    public void lamNeedingDriverAddsDriver() {
        Campaign mockCampaign = mock(Campaign.class);
        CampaignOptions mockOptions = mock(CampaignOptions.class);
        doReturn(mockOptions).when(mockCampaign).getCampaignOptions();
        doReturn(false).when(mockOptions).isUseArtillery();
        Entity mockEntity = mock(LandAirMek.class);
        Unit unit = spy(new Unit(mockEntity, mockCampaign));

        doReturn(true).doReturn(false).when(unit).canTakeMoreDrivers();
        doReturn(true).when(unit).usesSoloPilot();
        doReturn(false).when(unit).canTakeMoreGunners();
        doReturn(false).when(unit).canTakeMoreVesselCrew();
        doReturn(false).when(unit).canTakeNavigator();
        doReturn(false).when(unit).canTakeTechOfficer();
        doNothing().when(unit).resetPilotAndEntity();
        doNothing().when(unit).runDiagnostic(false);

        Person mockDriver = mock(Person.class);
        doNothing().when(unit).addPilotOrSoldier(eq(mockDriver));
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .newPerson(mockCampaign, mekhq.campaign.personnel.enums.PersonnelRole.LAM_PILOT)).thenReturn(
              mockDriver);
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .recruitPerson(mockCampaign,
                         ArgumentMatchers.eq(mockDriver),
                         ArgumentMatchers.anyBoolean(),
                         ArgumentMatchers.eq(true))).thenReturn(true);

        HirePersonnelUnitAction action = new HirePersonnelUnitAction(false);
        action.execute(mockCampaign, unit);

        mekhq.campaign.Campaign campaign = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign.getPlayerForce()
              .getHumanResources()
              .newPerson(campaign, mekhq.campaign.personnel.enums.PersonnelRole.LAM_PILOT);
        Campaign campaign1 = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign1.getPlayerForce()
              .getHumanResources()
              .recruitPerson(campaign1,
                    ArgumentMatchers.any(Person.class),
                    ArgumentMatchers.eq(false),
                    ArgumentMatchers.eq(true));
        verify(unit, times(1)).addPilotOrSoldier(eq(mockDriver));
    }

    @Test
    public void mekNeedingGunnerAddsGunner() {
        Campaign mockCampaign = mock(Campaign.class);
        CampaignOptions mockOptions = mock(CampaignOptions.class);
        doReturn(mockOptions).when(mockCampaign).getCampaignOptions();
        doReturn(false).when(mockOptions).isUseArtillery();
        Entity mockEntity = mock(Mek.class);
        Unit unit = spy(new Unit(mockEntity, mockCampaign));

        doReturn(false).when(unit).canTakeMoreDrivers();
        doReturn(true).doReturn(false).when(unit).canTakeMoreGunners();
        doReturn(false).when(unit).canTakeMoreVesselCrew();
        doReturn(false).when(unit).canTakeNavigator();
        doReturn(false).when(unit).canTakeTechOfficer();
        doNothing().when(unit).resetPilotAndEntity();
        doNothing().when(unit).runDiagnostic(false);

        Person mockGunner = mock(Person.class);
        doNothing().when(unit).addGunner(eq(mockGunner));
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .newPerson(mockCampaign, mekhq.campaign.personnel.enums.PersonnelRole.MEKWARRIOR)).thenReturn(
              mockGunner);
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .recruitPerson(mockCampaign,
                         ArgumentMatchers.eq(mockGunner),
                         ArgumentMatchers.anyBoolean(),
                         ArgumentMatchers.eq(true))).thenReturn(true);

        HirePersonnelUnitAction action = new HirePersonnelUnitAction(false);
        action.execute(mockCampaign, unit);

        mekhq.campaign.Campaign campaign = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign.getPlayerForce()
              .getHumanResources()
              .newPerson(campaign, mekhq.campaign.personnel.enums.PersonnelRole.MEKWARRIOR);
        Campaign campaign1 = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign1.getPlayerForce()
              .getHumanResources()
              .recruitPerson(campaign1,
                    ArgumentMatchers.any(Person.class),
                    ArgumentMatchers.eq(false),
                    ArgumentMatchers.eq(true));
        verify(unit, times(1)).addGunner(eq(mockGunner));
    }

    @Test
    public void tankNeedingGunnerAddsGunner() {
        Campaign mockCampaign = mock(Campaign.class);
        CampaignOptions mockOptions = mock(CampaignOptions.class);
        doReturn(mockOptions).when(mockCampaign).getCampaignOptions();
        doReturn(false).when(mockOptions).isUseArtillery();
        Entity mockEntity = mock(Tank.class);
        when(mockEntity.getMovementMode()).thenReturn(EntityMovementMode.TRACKED);
        Unit unit = spy(new Unit(mockEntity, mockCampaign));

        doReturn(false).when(unit).canTakeMoreDrivers();
        doReturn(true).doReturn(false).when(unit).canTakeMoreGunners();
        doReturn(false).when(unit).canTakeMoreVesselCrew();
        doReturn(false).when(unit).canTakeNavigator();
        doReturn(false).when(unit).canTakeTechOfficer();
        doNothing().when(unit).resetPilotAndEntity();
        doNothing().when(unit).runDiagnostic(false);

        Person mockGunner = mock(Person.class);
        doNothing().when(unit).addGunner(eq(mockGunner));
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .newPerson(mockCampaign,
                         mekhq.campaign.personnel.enums.PersonnelRole.VEHICLE_CREW_GROUND)).thenReturn(mockGunner);
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .recruitPerson(mockCampaign,
                         ArgumentMatchers.eq(mockGunner),
                         ArgumentMatchers.anyBoolean(),
                         ArgumentMatchers.eq(true))).thenReturn(true);

        HirePersonnelUnitAction action = new HirePersonnelUnitAction(false);
        action.execute(mockCampaign, unit);

        mekhq.campaign.Campaign campaign = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign.getPlayerForce()
              .getHumanResources()
              .newPerson(campaign, mekhq.campaign.personnel.enums.PersonnelRole.VEHICLE_CREW_GROUND);
        Campaign campaign1 = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign1.getPlayerForce()
              .getHumanResources()
              .recruitPerson(campaign1,
                    ArgumentMatchers.any(Person.class),
                    ArgumentMatchers.eq(false),
                    ArgumentMatchers.eq(true));
        verify(unit, times(1)).addGunner(eq(mockGunner));
    }

    @Test
    public void spaceShipNeedingGunnerAddsGunner() {
        Campaign mockCampaign = mock(Campaign.class);
        CampaignOptions mockOptions = mock(CampaignOptions.class);
        doReturn(mockOptions).when(mockCampaign).getCampaignOptions();
        doReturn(false).when(mockOptions).isUseArtillery();
        Entity mockEntity = mock(Jumpship.class);
        Unit unit = spy(new Unit(mockEntity, mockCampaign));

        doReturn(false).when(unit).canTakeMoreDrivers();
        doReturn(true).doReturn(false).when(unit).canTakeMoreGunners();
        doReturn(false).when(unit).canTakeMoreVesselCrew();
        doReturn(false).when(unit).canTakeNavigator();
        doReturn(false).when(unit).canTakeTechOfficer();
        doNothing().when(unit).resetPilotAndEntity();
        doNothing().when(unit).runDiagnostic(false);

        Person mockGunner = mock(Person.class);
        doNothing().when(unit).addGunner(eq(mockGunner));
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .newPerson(mockCampaign, mekhq.campaign.personnel.enums.PersonnelRole.VESSEL_GUNNER)).thenReturn(
              mockGunner);
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .recruitPerson(mockCampaign,
                         ArgumentMatchers.eq(mockGunner),
                         ArgumentMatchers.anyBoolean(),
                         ArgumentMatchers.eq(true))).thenReturn(true);

        HirePersonnelUnitAction action = new HirePersonnelUnitAction(false);
        action.execute(mockCampaign, unit);

        mekhq.campaign.Campaign campaign = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign.getPlayerForce()
              .getHumanResources()
              .newPerson(campaign, mekhq.campaign.personnel.enums.PersonnelRole.VESSEL_GUNNER);
        Campaign campaign1 = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign1.getPlayerForce()
              .getHumanResources()
              .recruitPerson(campaign1,
                    ArgumentMatchers.any(Person.class),
                    ArgumentMatchers.eq(false),
                    ArgumentMatchers.eq(true));
        verify(unit, times(1)).addGunner(eq(mockGunner));
    }

    @Test
    public void smallCraftNeedingGunnerAddsGunner() {
        Campaign mockCampaign = mock(Campaign.class);
        CampaignOptions mockOptions = mock(CampaignOptions.class);
        doReturn(mockOptions).when(mockCampaign).getCampaignOptions();
        doReturn(false).when(mockOptions).isUseArtillery();
        Entity mockEntity = mock(SmallCraft.class);
        Unit unit = spy(new Unit(mockEntity, mockCampaign));

        doReturn(false).when(unit).canTakeMoreDrivers();
        doReturn(true).doReturn(false).when(unit).canTakeMoreGunners();
        doReturn(false).when(unit).canTakeMoreVesselCrew();
        doReturn(false).when(unit).canTakeNavigator();
        doReturn(false).when(unit).canTakeTechOfficer();
        doNothing().when(unit).resetPilotAndEntity();
        doNothing().when(unit).runDiagnostic(false);

        Person mockGunner = mock(Person.class);
        doNothing().when(unit).addGunner(eq(mockGunner));
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .newPerson(mockCampaign, mekhq.campaign.personnel.enums.PersonnelRole.VESSEL_GUNNER)).thenReturn(
              mockGunner);
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .recruitPerson(mockCampaign,
                         ArgumentMatchers.eq(mockGunner),
                         ArgumentMatchers.anyBoolean(),
                         ArgumentMatchers.eq(true))).thenReturn(true);

        HirePersonnelUnitAction action = new HirePersonnelUnitAction(false);
        action.execute(mockCampaign, unit);

        mekhq.campaign.Campaign campaign = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign.getPlayerForce()
              .getHumanResources()
              .newPerson(campaign, mekhq.campaign.personnel.enums.PersonnelRole.VESSEL_GUNNER);
        Campaign campaign1 = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign1.getPlayerForce()
              .getHumanResources()
              .recruitPerson(campaign1,
                    ArgumentMatchers.any(Person.class),
                    ArgumentMatchers.eq(false),
                    ArgumentMatchers.eq(true));
        verify(unit, times(1)).addGunner(eq(mockGunner));
    }

    @Test
    public void spaceShipNeedingCrewAddsCrew() {
        Campaign mockCampaign = mock(Campaign.class);
        CampaignOptions mockOptions = mock(CampaignOptions.class);
        doReturn(mockOptions).when(mockCampaign).getCampaignOptions();
        doReturn(false).when(mockOptions).isUseArtillery();
        Entity mockEntity = mock(Jumpship.class);
        when(mockEntity.isSupportVehicle()).thenReturn(false);
        when(mockEntity.isLargeCraft()).thenReturn(true);
        Unit unit = spy(new Unit(mockEntity, mockCampaign));

        doReturn(false).when(unit).canTakeMoreDrivers();
        doReturn(false).when(unit).canTakeMoreGunners();
        doReturn(true).doReturn(false).when(unit).canTakeMoreVesselCrew();
        doReturn(false).when(unit).canTakeNavigator();
        doReturn(false).when(unit).canTakeTechOfficer();
        doNothing().when(unit).resetPilotAndEntity();
        doNothing().when(unit).runDiagnostic(false);

        Person mockCrew = mock(Person.class);
        doNothing().when(unit).addVesselCrew(eq(mockCrew));
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .newPerson(mockCampaign, mekhq.campaign.personnel.enums.PersonnelRole.VESSEL_CREW)).thenReturn(
              mockCrew);
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .recruitPerson(mockCampaign,
                         ArgumentMatchers.eq(mockCrew),
                         ArgumentMatchers.anyBoolean(),
                         ArgumentMatchers.eq(true))).thenReturn(true);

        HirePersonnelUnitAction action = new HirePersonnelUnitAction(false);
        action.execute(mockCampaign, unit);

        mekhq.campaign.Campaign campaign = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign.getPlayerForce()
              .getHumanResources()
              .newPerson(campaign, mekhq.campaign.personnel.enums.PersonnelRole.VESSEL_CREW);
        Campaign campaign1 = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign1.getPlayerForce()
              .getHumanResources()
              .recruitPerson(campaign1,
                    ArgumentMatchers.any(Person.class),
                    ArgumentMatchers.eq(false),
                    ArgumentMatchers.eq(true));
        verify(unit, times(1)).addVesselCrew(eq(mockCrew));
    }

    @Test
    public void supportVehicleNeedingCrewAddsCrew() {
        Campaign mockCampaign = mock(Campaign.class);
        CampaignOptions mockOptions = mock(CampaignOptions.class);
        doReturn(mockOptions).when(mockCampaign).getCampaignOptions();
        doReturn(false).when(mockOptions).isUseArtillery();
        Entity mockEntity = mock(SupportTank.class);
        when(mockEntity.isSupportVehicle()).thenReturn(true);
        when(mockEntity.getMovementMode()).thenReturn(EntityMovementMode.HOVER);
        Unit unit = spy(new Unit(mockEntity, mockCampaign));

        doReturn(false).when(unit).canTakeMoreDrivers();
        doReturn(false).when(unit).canTakeMoreGunners();
        doReturn(true).doReturn(false).when(unit).canTakeMoreVesselCrew();
        doReturn(false).when(unit).canTakeNavigator();
        doReturn(false).when(unit).canTakeTechOfficer();
        doNothing().when(unit).resetPilotAndEntity();
        doNothing().when(unit).runDiagnostic(false);

        Person mockCrew = mock(Person.class);
        doNothing().when(unit).addVesselCrew(eq(mockCrew));
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .newPerson(mockCampaign,
                         ArgumentMatchers.any(mekhq.campaign.personnel.enums.PersonnelRole.class))).thenReturn(
              mockCrew);
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .recruitPerson(mockCampaign,
                         ArgumentMatchers.eq(mockCrew),
                         ArgumentMatchers.anyBoolean(),
                         ArgumentMatchers.eq(true))).thenReturn(true);

        HirePersonnelUnitAction action = new HirePersonnelUnitAction(false);
        action.execute(mockCampaign, unit);

        mekhq.campaign.Campaign campaign = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign.getPlayerForce()
              .getHumanResources()
              .newPerson(campaign, mekhq.campaign.personnel.enums.PersonnelRole.VEHICLE_CREW_GROUND);
        Campaign campaign1 = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign1.getPlayerForce()
              .getHumanResources()
              .recruitPerson(campaign1,
                    ArgumentMatchers.any(Person.class),
                    ArgumentMatchers.eq(false),
                    ArgumentMatchers.eq(true));
        verify(unit, times(1)).addVesselCrew(eq(mockCrew));
    }

    @Test
    public void spaceShipNeedingNavigatorAddsNavigator() {
        Campaign mockCampaign = mock(Campaign.class);
        CampaignOptions mockOptions = mock(CampaignOptions.class);
        doReturn(mockOptions).when(mockCampaign).getCampaignOptions();
        doReturn(false).when(mockOptions).isUseArtillery();
        Entity mockEntity = mock(Jumpship.class);
        when(mockEntity.isSupportVehicle()).thenReturn(false);
        Unit unit = spy(new Unit(mockEntity, mockCampaign));

        doReturn(false).when(unit).canTakeMoreDrivers();
        doReturn(false).when(unit).canTakeMoreGunners();
        doReturn(false).when(unit).canTakeMoreVesselCrew();
        doReturn(true).when(unit).canTakeNavigator();
        doReturn(false).when(unit).canTakeTechOfficer();
        doNothing().when(unit).resetPilotAndEntity();
        doNothing().when(unit).runDiagnostic(false);

        Person mockNavigator = mock(Person.class);
        doNothing().when(unit).setNavigator(eq(mockNavigator));
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .newPerson(mockCampaign,
                         mekhq.campaign.personnel.enums.PersonnelRole.VESSEL_NAVIGATOR)).thenReturn(mockNavigator);
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .recruitPerson(mockCampaign,
                         ArgumentMatchers.eq(mockNavigator),
                         ArgumentMatchers.anyBoolean(),
                         ArgumentMatchers.eq(true))).thenReturn(true);

        HirePersonnelUnitAction action = new HirePersonnelUnitAction(false);
        action.execute(mockCampaign, unit);

        mekhq.campaign.Campaign campaign = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign.getPlayerForce()
              .getHumanResources()
              .newPerson(campaign, mekhq.campaign.personnel.enums.PersonnelRole.VESSEL_NAVIGATOR);
        Campaign campaign1 = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign1.getPlayerForce()
              .getHumanResources()
              .recruitPerson(campaign1,
                    ArgumentMatchers.any(Person.class),
                    ArgumentMatchers.eq(false),
                    ArgumentMatchers.eq(true));
        verify(unit, times(1)).setNavigator(eq(mockNavigator));
    }

    @Test
    public void mekNeedingTechOfficerAddsTechOfficer() {
        Campaign mockCampaign = mock(Campaign.class);
        CampaignOptions mockOptions = mock(CampaignOptions.class);
        doReturn(mockOptions).when(mockCampaign).getCampaignOptions();
        doReturn(false).when(mockOptions).isUseArtillery();
        Entity mockEntity = mock(Mek.class);
        Unit unit = spy(new Unit(mockEntity, mockCampaign));

        doReturn(false).when(unit).canTakeMoreDrivers();
        doReturn(false).when(unit).canTakeMoreGunners();
        doReturn(false).when(unit).canTakeMoreVesselCrew();
        doReturn(false).when(unit).canTakeNavigator();
        doReturn(true).when(unit).canTakeTechOfficer();
        doNothing().when(unit).resetPilotAndEntity();
        doNothing().when(unit).runDiagnostic(false);

        Person mockTechOfficer = mock(Person.class);
        doNothing().when(unit).setTechOfficer(eq(mockTechOfficer));
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .newPerson(mockCampaign, mekhq.campaign.personnel.enums.PersonnelRole.MEKWARRIOR)).thenReturn(
              mockTechOfficer);
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .recruitPerson(mockCampaign,
                         ArgumentMatchers.eq(mockTechOfficer),
                         ArgumentMatchers.anyBoolean(),
                         ArgumentMatchers.eq(true))).thenReturn(true);

        HirePersonnelUnitAction action = new HirePersonnelUnitAction(false);
        action.execute(mockCampaign, unit);

        mekhq.campaign.Campaign campaign = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign.getPlayerForce()
              .getHumanResources()
              .newPerson(campaign, mekhq.campaign.personnel.enums.PersonnelRole.MEKWARRIOR);
        Campaign campaign1 = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign1.getPlayerForce()
              .getHumanResources()
              .recruitPerson(campaign1,
                    ArgumentMatchers.any(Person.class),
                    ArgumentMatchers.eq(false),
                    ArgumentMatchers.eq(true));
        verify(unit, times(1)).setTechOfficer(eq(mockTechOfficer));
    }

    @Test
    public void tankNeedingTechOfficerAddsTechOfficer() {
        Campaign mockCampaign = mock(Campaign.class);
        CampaignOptions mockOptions = mock(CampaignOptions.class);
        doReturn(mockOptions).when(mockCampaign).getCampaignOptions();
        doReturn(false).when(mockOptions).isUseArtillery();
        Entity mockEntity = mock(Tank.class);
        when(mockEntity.getMovementMode()).thenReturn(EntityMovementMode.TRACKED);
        Unit unit = spy(new Unit(mockEntity, mockCampaign));

        doReturn(false).when(unit).canTakeMoreDrivers();
        doReturn(false).when(unit).canTakeMoreGunners();
        doReturn(false).when(unit).canTakeMoreVesselCrew();
        doReturn(false).when(unit).canTakeNavigator();
        doReturn(true).when(unit).canTakeTechOfficer();
        doNothing().when(unit).resetPilotAndEntity();
        doNothing().when(unit).runDiagnostic(false);

        Person mockTechOfficer = mock(Person.class);
        doNothing().when(unit).setTechOfficer(eq(mockTechOfficer));
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .newPerson(mockCampaign,
                         mekhq.campaign.personnel.enums.PersonnelRole.VEHICLE_CREW_GROUND)).thenReturn(
              mockTechOfficer);
        when(mockCampaign.getPlayerForce()
                   .getHumanResources()
                   .recruitPerson(mockCampaign,
                         ArgumentMatchers.eq(mockTechOfficer),
                         ArgumentMatchers.anyBoolean(),
                         ArgumentMatchers.eq(true))).thenReturn(true);

        HirePersonnelUnitAction action = new HirePersonnelUnitAction(false);
        action.execute(mockCampaign, unit);

        mekhq.campaign.Campaign campaign = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign.getPlayerForce()
              .getHumanResources()
              .newPerson(campaign, mekhq.campaign.personnel.enums.PersonnelRole.VEHICLE_CREW_GROUND);
        Campaign campaign1 = Mockito.verify(mockCampaign, Mockito.times(1));
        campaign1.getPlayerForce()
              .getHumanResources()
              .recruitPerson(campaign1,
                    ArgumentMatchers.any(Person.class),
                    ArgumentMatchers.eq(false),
                    ArgumentMatchers.eq(true));
        verify(unit, times(1)).setTechOfficer(eq(mockTechOfficer));
    }
}
