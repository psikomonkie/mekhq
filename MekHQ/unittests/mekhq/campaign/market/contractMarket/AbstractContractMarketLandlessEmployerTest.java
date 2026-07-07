/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
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
package mekhq.campaign.market.contractMarket;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import mekhq.campaign.Campaign;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.enums.AtBContractType;
import mekhq.campaign.mission.newContract.MissionLocationProfile;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.RandomFactionGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link AbstractContractMarket#setSystemId}'s landless-employer rule: a contract where the player defends is a
 * defense of the employer's own territory, so an employer that controls no planets anywhere cannot generate one, while
 * contracts where the player attacks stay valid for the same employer.
 */
class AbstractContractMarketLandlessEmployerTest {

    private static final LocalDate TODAY = LocalDate.of(3025, 1, 1);

    private final AbstractContractMarket contractMarket = new AtbMonthlyContractMarket();

    @AfterEach
    void tearDown() {
        RandomFactionGenerator.setInstance(null);
    }

    private static Faction mockLandlessEmployer(RandomFactionGenerator rfg) {
        Faction employer = mock(Faction.class);
        when(rfg.hasAnyTerritory(eq(employer), any(LocalDate.class))).thenReturn(false);
        return employer;
    }

    private static AtBContract mockContract(AtBContractType contractType, Faction employer,
          boolean isPlayerAttacker) {
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(contractType);
        when(contract.getEmployerFaction()).thenReturn(employer);
        when(contract.getEmployerCode()).thenReturn("EMPLOYER");
        when(contract.getEnemyCode()).thenReturn("ENEMY");
        when(contract.isPlayerAttacker()).thenReturn(isPlayerAttacker);
        return contract;
    }

    private static Campaign mockCampaign() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getLocalDate()).thenReturn(TODAY);
        return campaign;
    }

    @Test
    void defensiveContractFailsForLandlessEmployer() {
        RandomFactionGenerator rfg = mock(RandomFactionGenerator.class);
        RandomFactionGenerator.setInstance(rfg);
        AtBContract contract = mockContract(AtBContractType.GARRISON_DUTY, mockLandlessEmployer(rfg), false);
        Campaign campaign = mockCampaign();

        assertThrows(AbstractContractMarket.NoContractLocationFoundException.class,
              () -> contractMarket.setSystemId(contract, campaign),
              "An employer with no planets has nothing to defend, so a contract with the player defending must fail");
        verify(rfg, never()).getMissionTarget(anyString(), anyString(), any(), any(MissionLocationProfile.class));
    }

    @Test
    void attackingContractStillResolvesForLandlessEmployer() {
        RandomFactionGenerator rfg = mock(RandomFactionGenerator.class);
        RandomFactionGenerator.setInstance(rfg);
        AtBContract contract = mockContract(AtBContractType.OBJECTIVE_RAID, mockLandlessEmployer(rfg), true);
        when(contract.getSystem()).thenReturn(mock(PlanetarySystem.class));
        when(rfg.getMissionTarget(anyString(), anyString(), any(), any(MissionLocationProfile.class)))
              .thenReturn("TARGET");
        Campaign campaign = mockCampaign();

        contractMarket.setSystemId(contract, campaign);

        verify(contract).setSystemId("TARGET");
    }
}
