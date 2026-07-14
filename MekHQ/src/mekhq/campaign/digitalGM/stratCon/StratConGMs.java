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

import mekhq.campaign.Campaign;
import mekhq.campaign.digitalGM.DigitalGM;
import mekhq.campaign.digitalGM.DigitalGMRegistry;
import mekhq.campaign.digitalGM.strategy.ForceDeploymentStrategy;
import mekhq.campaign.digitalGM.strategy.MapGenerationStrategy;
import mekhq.campaign.digitalGM.strategy.OpForDeploymentStrategy;
import mekhq.campaign.digitalGM.strategy.OpForGenerationStrategy;

/**
 * Entry point for non-GM StratCon code (mainly the deployment UI) to reach the active digital GM's strategies for a
 * campaign, rather than calling the static rules directly. This is what makes the strategy seams live: routing a call
 * through here means a GM that overrides the strategy actually changes behaviour at the call site.
 *
 * <p>When no digital GM governs the campaign &mdash; or the active GM is not StratCon-based &mdash; the lookup falls
 * back to the default StratCon strategy, which delegates to {@link StratConRulesManager}. That keeps behaviour
 * identical to the pre-registry code path.</p>
 *
 * @author Illiani
 * @since 0.50.10
 */
public final class StratConGMs {
    private static final ForceDeploymentStrategy DEFAULT_FORCE_DEPLOYMENT = new StratConForceDeploymentStrategy();
    private static final OpForGenerationStrategy DEFAULT_OPFOR_GENERATION = new StratConOpForGenerationStrategy();
    private static final OpForDeploymentStrategy DEFAULT_OPFOR_DEPLOYMENT = new StratConOpForDeploymentStrategy();
    private static final MapGenerationStrategy DEFAULT_MAP_GENERATION = new StratConMapGenerationStrategy();

    private StratConGMs() {
    }

    /**
     * Resolves the force-deployment strategy of the digital GM governing the given campaign.
     *
     * @param campaign the campaign whose active GM is consulted
     *
     * @return the active StratCon GM's {@link ForceDeploymentStrategy}, or the default StratCon strategy when no
     *       StratCon GM is active
     */
    public static ForceDeploymentStrategy forceDeployment(Campaign campaign) {
        DigitalGM activeGM = DigitalGMRegistry.getActiveGM(campaign).orElse(null);

        if (activeGM instanceof AbstractStratConGM stratConGM) {
            return stratConGM.forceDeployment();
        }

        return DEFAULT_FORCE_DEPLOYMENT;
    }

    /**
     * Resolves the OpFor-generation strategy of the digital GM governing the given campaign.
     *
     * @param campaign the campaign whose active GM is consulted
     *
     * @return the active StratCon GM's {@link OpForGenerationStrategy}, or the default StratCon strategy when no
     *       StratCon GM is active
     */
    public static OpForGenerationStrategy opForGeneration(Campaign campaign) {
        DigitalGM activeGM = DigitalGMRegistry.getActiveGM(campaign).orElse(null);

        if (activeGM instanceof AbstractStratConGM stratConGM) {
            return stratConGM.opForGeneration();
        }

        return DEFAULT_OPFOR_GENERATION;
    }

    /**
     * Resolves the OpFor-deployment strategy of the digital GM governing the given campaign.
     *
     * @param campaign the campaign whose active GM is consulted
     *
     * @return the active StratCon GM's {@link OpForDeploymentStrategy}, or the default StratCon strategy when no
     *       StratCon GM is active
     */
    public static OpForDeploymentStrategy opForDeployment(Campaign campaign) {
        DigitalGM activeGM = DigitalGMRegistry.getActiveGM(campaign).orElse(null);

        if (activeGM instanceof AbstractStratConGM stratConGM) {
            return stratConGM.opForDeployment();
        }

        return DEFAULT_OPFOR_DEPLOYMENT;
    }

    /**
     * Resolves the map-generation (terrain) strategy of the digital GM governing the given campaign.
     *
     * @param campaign the campaign whose active GM is consulted
     *
     * @return the active StratCon GM's {@link MapGenerationStrategy}, or the default StratCon strategy when no StratCon
     *       GM is active
     */
    public static MapGenerationStrategy mapGeneration(Campaign campaign) {
        DigitalGM activeGM = DigitalGMRegistry.getActiveGM(campaign).orElse(null);

        if (activeGM instanceof AbstractStratConGM stratConGM) {
            return stratConGM.mapGeneration();
        }

        return DEFAULT_MAP_GENERATION;
    }
}
