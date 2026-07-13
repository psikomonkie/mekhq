/*
 * Copyright (C) 2025-2026 The MegaMek Team. All Rights Reserved.
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
package mekhq.campaign.digitalGM.strategy;

/**
 * Strategy for placing player forces into scenarios &mdash; direct deployment to coordinates, assignment to a scenario,
 * and committing primary forces. This is the seam that Mapless play diverges on most: it bypasses the map/coordinate
 * model entirely.
 *
 * <p><b>Extension shell.</b> This interface is intentionally empty for now. It marks force deployment as a distinct
 * capability of the digital GM, but the relevant logic still lives as static methods on
 * {@link mekhq.campaign.digitalGM.stratCon.StratConRulesManager StratConRulesManager} (for example
 * {@code deployForceToCoords}, {@code assignForceToScenario}, {@code processForceDeployment} and
 * {@code commitPrimaryForces}). Those signatures should be lifted here &mdash; and an accessor added to
 * {@link mekhq.campaign.digitalGM.AbstractDigitalGM AbstractDigitalGM} &mdash; when this capability is extracted.</p>
 *
 * @author Illiani
 * @since 0.50.10
 */
public interface ForceDeploymentStrategy {
    // TODO Extraction target. Lift the force-placement entry points from StratConRulesManager into this strategy:
    //   - deployForceToCoords(...)
    //   - assignForceToScenario(...)
    //   - processForceDeployment(...)
    //   - commitPrimaryForces(...)
}
