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
package mekhq.campaign.digitalGM.stratCon;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import mekhq.campaign.Campaign;
import mekhq.campaign.campaignOptions.CampaignOptions;
import mekhq.campaign.digitalGM.DigitalGM;
import mekhq.campaign.digitalGM.DigitalGMRegistry;
import mekhq.campaign.digitalGM.strategy.ForceDeploymentStrategy;
import mekhq.campaign.digitalGM.strategy.MapGenerationStrategy;
import mekhq.campaign.digitalGM.strategy.OpForDeploymentStrategy;
import mekhq.campaign.digitalGM.strategy.OpForGenerationStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link StratConGMs} routes to the active GM's force-deployment strategy &mdash; so that a GM overriding
 * {@code forceDeployment()} actually takes effect at the deployment call sites &mdash; and falls back to the default
 * StratCon strategy when no GM governs the campaign.
 *
 * @author Illiani
 */
class StratConGMsTest {

    private final List<DigitalGM> registered = new ArrayList<>();

    private void register(DigitalGM digitalGM) {
        DigitalGMRegistry.register(digitalGM);
        registered.add(digitalGM);
    }

    @AfterEach
    void cleanup() {
        registered.forEach(DigitalGMRegistry::unregister);
        registered.clear();
    }

    private static Campaign campaignWith(StratConPlayType playType) {
        CampaignOptions options = mock(CampaignOptions.class);
        when(options.getStratConPlayType()).thenReturn(playType);

        Campaign campaign = mock(Campaign.class);
        when(campaign.getCampaignOptions()).thenReturn(options);
        return campaign;
    }

    /** A Normal-play GM that supplies sentinel strategies so routing can be observed. */
    private static class SentinelGM extends StratConDigitalGM {
        final ForceDeploymentStrategy forceDeploymentSentinel = mock(ForceDeploymentStrategy.class);
        final OpForGenerationStrategy opForGenerationSentinel = mock(OpForGenerationStrategy.class);
        final OpForDeploymentStrategy opForDeploymentSentinel = mock(OpForDeploymentStrategy.class);
        final MapGenerationStrategy mapGenerationSentinel = mock(MapGenerationStrategy.class);

        @Override
        protected ForceDeploymentStrategy getForceDeploymentStrategy() {
            return forceDeploymentSentinel;
        }

        @Override
        protected OpForGenerationStrategy getOpForGenerationStrategy() {
            return opForGenerationSentinel;
        }

        @Override
        protected OpForDeploymentStrategy getOpForDeploymentStrategy() {
            return opForDeploymentSentinel;
        }

        @Override
        protected MapGenerationStrategy getMapGenerationStrategy() {
            return mapGenerationSentinel;
        }
    }

    @Test
    void routesToActiveGmForceDeploymentStrategy() {
        SentinelGM gm = new SentinelGM();
        register(gm);

        assertSame(gm.forceDeploymentSentinel, StratConGMs.forceDeployment(campaignWith(StratConPlayType.NORMAL)));
    }

    @Test
    void routesToActiveGmOpForGenerationStrategy() {
        SentinelGM gm = new SentinelGM();
        register(gm);

        assertSame(gm.opForGenerationSentinel, StratConGMs.opForGeneration(campaignWith(StratConPlayType.NORMAL)));
    }

    @Test
    void routesToActiveGmOpForDeploymentStrategy() {
        SentinelGM gm = new SentinelGM();
        register(gm);

        assertSame(gm.opForDeploymentSentinel, StratConGMs.opForDeployment(campaignWith(StratConPlayType.NORMAL)));
    }

    @Test
    void routesToActiveGmMapGenerationStrategy() {
        SentinelGM gm = new SentinelGM();
        register(gm);

        assertSame(gm.mapGenerationSentinel, StratConGMs.mapGeneration(campaignWith(StratConPlayType.NORMAL)));
    }

    @Test
    void fallsBackToDefaultStrategyWhenNoGmIsActive() {
        // Nothing enabled for a disabled campaign -> default StratCon strategies (delegate to the static rules)
        assertInstanceOf(StratConForceDeploymentStrategy.class,
              StratConGMs.forceDeployment(campaignWith(StratConPlayType.DISABLED)));
        assertInstanceOf(StratConOpForGenerationStrategy.class,
              StratConGMs.opForGeneration(campaignWith(StratConPlayType.DISABLED)));
        assertInstanceOf(StratConOpForDeploymentStrategy.class,
              StratConGMs.opForDeployment(campaignWith(StratConPlayType.DISABLED)));
        assertInstanceOf(StratConMapGenerationStrategy.class,
              StratConGMs.mapGeneration(campaignWith(StratConPlayType.DISABLED)));
    }
}
