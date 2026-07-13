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
package mekhq.campaign.digitalGM;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import mekhq.campaign.Campaign;

/**
 * Process-wide registry of the {@link DigitalGM}s wired up at startup. It exists so code that is not itself a GM
 * &mdash; UI actions, resolution logic &mdash; can reach the GM that governs a given campaign and route work through
 * that GM's strategies instead of calling the static rules directly.
 *
 * <p>Every registered GM is offered each lookup; {@link #getActiveGM(Campaign)} returns the one whose
 * {@link DigitalGM#isEnabled(Campaign)} accepts the campaign. Because the StratCon play types are mutually exclusive,
 * at most one GM matches (none when play is disabled). Registration is handled by {@link AbstractDigitalGM#startup()} /
 * {@link AbstractDigitalGM#shutdown()}.</p>
 *
 * @author Illiani
 * @since 0.50.10
 */
public final class DigitalGMRegistry {
    private static final List<DigitalGM> REGISTERED = new CopyOnWriteArrayList<>();

    private DigitalGMRegistry() {
    }

    /**
     * Registers a GM so it participates in {@link #getActiveGM(Campaign)} lookups. Idempotent: registering the same
     * instance twice has no effect.
     *
     * @param digitalGM the GM to register
     */
    public static void register(DigitalGM digitalGM) {
        if (!REGISTERED.contains(digitalGM)) {
            REGISTERED.add(digitalGM);
        }
    }

    /**
     * Removes a GM from the registry.
     *
     * @param digitalGM the GM to remove
     */
    public static void unregister(DigitalGM digitalGM) {
        REGISTERED.remove(digitalGM);
    }

    /**
     * Finds the GM that governs the supplied campaign.
     *
     * @param campaign the campaign to resolve a GM for
     *
     * @return the enabled GM, or empty if none is active (for example when digital-GM play is disabled)
     */
    public static Optional<DigitalGM> getActiveGM(Campaign campaign) {
        return REGISTERED.stream().filter(digitalGM -> digitalGM.isEnabled(campaign)).findFirst();
    }
}
