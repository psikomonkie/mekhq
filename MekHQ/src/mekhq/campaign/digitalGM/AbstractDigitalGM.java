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

import megamek.common.event.Subscribe;
import megamek.logging.MMLogger;
import mekhq.MekHQ;
import mekhq.campaign.events.NewDayEvent;

/**
 * Common, engine-agnostic base for every {@link DigitalGM}. This class owns only the wiring that is identical across
 * <i>all</i> digital GMs, regardless of their underlying data model: event-bus registration and the enable-gated
 * dispatch of the daily lifecycle.
 *
 * <p>It deliberately knows nothing about StratCon tracks, scenarios or facilities. GMs built on the StratCon data
 * model extend {@link mekhq.campaign.digitalGM.stratCon.AbstractStratConGM AbstractStratConGM}, which supplies the
 * shared per-track daily loop and the strategy seams the play types vary. A future GM built on a different model would
 * extend this class directly and provide its own {@link #handleNewDay(NewDayEvent)}.</p>
 *
 * <p><b>Dispatch.</b> {@link #onNewDay(NewDayEvent)} is the single {@code @Subscribe} entry point and is {@code final}
 * so the enable-gate is uniform: every registered GM receives the event, but only the one whose {@link #isEnabled}
 * returns {@code true} for the campaign acts. Because the StratCon play types are mutually exclusive, at most one GM
 * runs per day.</p>
 *
 * @author Illiani
 * @since 0.50.10
 */
public abstract class AbstractDigitalGM implements DigitalGM {
    protected final MMLogger logger = MMLogger.create(getClass());

    /**
     * {@inheritDoc}
     *
     * <p>Registers this GM on the MekHQ event bus so {@link #onNewDay(NewDayEvent)} begins firing.</p>
     */
    @Override
    public void startup() {
        MekHQ.registerHandler(this);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Removes this GM from the MekHQ event bus.</p>
     */
    @Override
    public void shutdown() {
        MekHQ.unregisterHandler(this);
    }

    /**
     * Event-bus entry point. Kept {@code final} so the enable-gate is uniform across all GMs: subclasses customise
     * behaviour by overriding {@link #handleNewDay(NewDayEvent)} (and the strategy seams below it), never by
     * intercepting the raw event.
     *
     * @param event the new-day event
     */
    @Subscribe
    public final void onNewDay(NewDayEvent event) {
        if (!isEnabled(event.getCampaign())) {
            return;
        }

        handleNewDay(event);
    }
}
