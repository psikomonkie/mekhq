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

import mekhq.campaign.digitalGM.stratCon.StratConCampaignState;
import mekhq.campaign.digitalGM.stratCon.StratConTrackState;

/**
 * Strategy for map facilities &mdash; the periodic effects a facility applies during the daily lifecycle. This is the
 * seam Mapless (and, by extension, Singles) play skips entirely, since those play types have no facility map: the
 * legacy engine gated the call behind {@code if (!isUseStratConMapless)}, which is now expressed as a no-op
 * implementation of this strategy.
 *
 * @author Illiani
 * @since 0.50.10
 */
public interface FacilityStrategy {

    /**
     * Applies the periodic (daily, and where relevant monthly) effects of the facilities on a track.
     *
     * @param track          the track whose facilities are processed
     * @param campaignState  the StratCon state for the contract
     * @param isStartOfMonth {@code true} on the first day of the month, when monthly effects also apply
     */
    void applyPeriodicEffects(StratConTrackState track, StratConCampaignState campaignState, boolean isStartOfMonth);

    // TODO Further facility entry points on StratConRulesManager remain candidates for this strategy as the
    //      abstraction grows: updateFacilityForScenario(...) and switchFacilityOwner(...).
}
