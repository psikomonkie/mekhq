/*
 * Copyright (C) 2021-2025 The MegaMek Team. All Rights Reserved.
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
 */
package mekhq.campaign.unit.cleanup;

import static org.mockito.Mockito.*;

import megamek.common.AmmoType;
import megamek.common.EquipmentType;
import megamek.common.Mounted;
import mekhq.campaign.parts.equipment.AmmoBin;
import mekhq.campaign.parts.equipment.EquipmentPart;
import org.junit.jupiter.api.Test;

public class ApproximateMatchStepTest {
    @Test
    public void notAmmoBinTest() {
        EquipmentProposal mockProposal = mock(EquipmentProposal.class);
        EquipmentPart mockPart = mock(EquipmentPart.class);

        ApproximateMatchStep step = new ApproximateMatchStep();

        step.visit(mockProposal, mockPart);

        verify(mockProposal, times(0)).proposeMapping(any(), anyInt());
    }

    @Test
    public void noMatchingEquipmentTest() {
        EquipmentProposal mockProposal = mock(EquipmentProposal.class);
        AmmoBin mockPart = mock(AmmoBin.class);

        ApproximateMatchStep step = new ApproximateMatchStep();

        step.visit(mockProposal, mockPart);

        verify(mockProposal, times(0)).proposeMapping(any(), anyInt());
    }

    @Test
    public void mountDoesNotMatchEquipmentTest() {
        EquipmentProposal mockProposal = mock(EquipmentProposal.class);
        Mounted mockMount = mock(Mounted.class);
        when(mockMount.getType()).thenReturn(mock(EquipmentType.class));
        doReturn(mockMount).when(mockProposal).getEquipment(eq(1));
        AmmoBin mockPart = mock(AmmoBin.class);
        when(mockPart.getEquipmentNum()).thenReturn(1);
        when(mockPart.getType()).thenReturn(mock(AmmoType.class));

        ApproximateMatchStep step = new ApproximateMatchStep();

        step.visit(mockProposal, mockPart);

        verify(mockProposal, times(0)).proposeMapping(any(), anyInt());
    }

    @Test
    public void mountDoesNotMatchAmmoTypeTest() {
        EquipmentProposal mockProposal = mock(EquipmentProposal.class);
        Mounted mockMount = mock(Mounted.class);
        when(mockMount.getType()).thenReturn(mock(AmmoType.class));
        doReturn(mockMount).when(mockProposal).getEquipment(eq(1));
        AmmoBin mockPart = mock(AmmoBin.class);
        when(mockPart.getEquipmentNum()).thenReturn(1);
        when(mockPart.getType()).thenReturn(mock(AmmoType.class));

        ApproximateMatchStep step = new ApproximateMatchStep();

        step.visit(mockProposal, mockPart);

        verify(mockProposal, times(0)).proposeMapping(any(), anyInt());
    }

    @Test
    public void mountMatchesEquipmentTest() {
        EquipmentProposal mockProposal = mock(EquipmentProposal.class);
        AmmoType mockType = mock(AmmoType.class);
        Mounted mockMount = mock(Mounted.class);
        when(mockMount.getType()).thenReturn(mockType);
        doReturn(mockMount).when(mockProposal).getEquipment(eq(1));
        AmmoBin mockPart = mock(AmmoBin.class);
        when(mockPart.getEquipmentNum()).thenReturn(1);
        when(mockPart.getType()).thenReturn(mock(AmmoType.class));
        doReturn(true).when(mockPart).canChangeMunitions(eq(mockType));

        ApproximateMatchStep step = new ApproximateMatchStep();

        step.visit(mockProposal, mockPart);

        verify(mockProposal, times(1)).proposeMapping(eq(mockPart), eq(1));
    }
}
