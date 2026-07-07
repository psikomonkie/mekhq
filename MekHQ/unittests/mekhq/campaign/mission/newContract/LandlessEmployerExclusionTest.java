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
package mekhq.campaign.mission.newContract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.RandomFactionGenerator;
import org.junit.jupiter.api.Test;

class LandlessEmployerExclusionTest {

    @Test
    void shouldReturnTrueWhenEmployerIsLandlessAndNotAttacker() {
        // Arrange
        Faction mockedFaction = mock(Faction.class);
        RandomFactionGenerator factionGenerator = mock(RandomFactionGenerator.class);
        when(factionGenerator.hasAnyTerritory(eq(mockedFaction), any(LocalDate.class))).thenReturn(false);
        LocalDate currentDate = LocalDate.now();

        // Act
        boolean result = LandlessEmployerExclusion.shouldRejectDefensiveObjectives(mockedFaction, false, currentDate);

        // Assert
        assertTrue(result, "Expected 'true' when employer is landless and player is not attacker");
    }

    @Test
    void shouldReturnFalseWhenEmployerIsNotLandlessAndNotAttacker() {
        // Arrange
        Faction mockedFaction = mock(Faction.class);
        RandomFactionGenerator factionGenerator = mock(RandomFactionGenerator.class);
        when(factionGenerator.hasAnyTerritory(eq(mockedFaction), any(LocalDate.class))).thenReturn(true);
        LocalDate currentDate = LocalDate.now();

        // Act
        boolean result = LandlessEmployerExclusion.shouldRejectDefensiveObjectives(mockedFaction, false, currentDate);

        // Assert
        assertFalse(result, "Expected 'false' when employer is not landless and player is not attacker");
    }

    @Test
    void shouldReturnFalseWhenPlayerIsAttackerRegardlessOfLandlessStatus() {
        // Arrange
        Faction mockedFaction = mock(Faction.class);
        RandomFactionGenerator factionGenerator = mock(RandomFactionGenerator.class);
        when(factionGenerator.hasAnyTerritory(eq(mockedFaction), any(LocalDate.class))).thenReturn(false);
        LocalDate currentDate = LocalDate.now();

        // Act
        boolean result = LandlessEmployerExclusion.shouldRejectDefensiveObjectives(mockedFaction, true, currentDate);

        // Assert
        assertFalse(result, "Expected 'false' when player is attacker, regardless of employer's landless status");
    }

    @Test
    void shouldReturnFalseWhenEmployerIsNullRegardlessOfPlayerRole() {
        // Arrange
        LocalDate currentDate = LocalDate.now();

        // Act
        boolean result = LandlessEmployerExclusion.shouldRejectDefensiveObjectives(null, false, currentDate);

        // Assert
        assertFalse(result, "Expected 'false' when employer is null, regardless of player's role");
    }
}
