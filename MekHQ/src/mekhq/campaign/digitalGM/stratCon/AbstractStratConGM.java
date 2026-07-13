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
package mekhq.campaign.digitalGM.stratCon;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import mekhq.campaign.Campaign;
import mekhq.campaign.digitalGM.AbstractDigitalGM;
import mekhq.campaign.digitalGM.strategy.FacilityStrategy;
import mekhq.campaign.digitalGM.strategy.ScenarioGenerationStrategy;
import mekhq.campaign.digitalGM.strategy.ScenarioLifecycleStrategy;
import mekhq.campaign.events.NewDayEvent;
import mekhq.campaign.mission.AtBContract;

/**
 * Base for every digital GM built on the StratCon data model (tracks, scenarios, facilities). It owns the shared
 * per-track daily lifecycle &mdash; the loop that previously lived in {@code StratConRulesManager.handleNewDay} &mdash;
 * as a template method, and exposes the points where the StratCon play types diverge as overridable strategy accessors
 * and hooks.
 *
 * <p>The template is intentionally identical in <i>structure</i> to the legacy engine; each step now runs through a
 * strategy so a subclass changes behaviour by supplying a different strategy rather than by copying the loop:</p>
 * <ul>
 *     <li>{@link #facility()} &mdash; map-based StratCon applies facility effects; Mapless and Singles use a no-op
 *     (the old {@code if (!isUseStratConMapless)} guard).</li>
 *     <li>{@link #isSingleDropMode()} &mdash; Singles schedules at most one scenario per week (the old
 *     {@code isUseStratConSingles} flag).</li>
 *     <li>{@link #scenarioGeneration()} / {@link #scenarioLifecycle()} &mdash; generation and expiry/return/resolution,
 *     shared across the play types today but overridable per GM.</li>
 * </ul>
 *
 * <p>Leaf rules helpers remain static utilities on {@link StratConRulesManager}; the strategies delegate to them.</p>
 *
 * @author Illiani
 * @since 0.50.10
 */
public abstract class AbstractStratConGM extends AbstractDigitalGM {

    private final ScenarioGenerationStrategy scenarioGeneration = new StratConScenarioGenerationStrategy();
    private final ScenarioLifecycleStrategy scenarioLifecycle = new StratConScenarioLifecycleStrategy();
    private final FacilityStrategy facility = new StratConFacilityStrategy();

    /**
     * @return the strategy that decides when and how scenarios are generated for this GM
     */
    protected ScenarioGenerationStrategy scenarioGeneration() {
        return scenarioGeneration;
    }

    /**
     * @return the strategy that expires, resolves and returns forces from scenarios for this GM
     */
    protected ScenarioLifecycleStrategy scenarioLifecycle() {
        return scenarioLifecycle;
    }

    /**
     * @return the strategy governing periodic facility effects; map-based play returns the real StratCon strategy,
     *       Mapless/Singles override this to a {@link NoOpFacilityStrategy}
     */
    protected FacilityStrategy facility() {
        return facility;
    }

    /**
     * @return {@code true} if this GM schedules at most one scenario per week across all tracks (Singles play);
     *       {@code false} otherwise
     */
    protected boolean isSingleDropMode() {
        return false;
    }

    /**
     * The shared StratCon daily lifecycle. Runs the scenario-generation routine for every track attached to an active
     * contract: cleaning up phantom scenarios, returning forces whose deployment has ended, applying facility effects,
     * expiring ignored scenarios, scheduling the coming week's scenarios, and generating those due today.
     *
     * @param event the new-day event (already enable-gated by {@link AbstractDigitalGM#onNewDay})
     */
    @Override
    public void handleNewDay(NewDayEvent event) {
        Campaign campaign = event.getCampaign();

        LocalDate today = campaign.getLocalDate();
        boolean isMonday = today.getDayOfWeek() == DayOfWeek.MONDAY;
        boolean isStartOfMonth = today.getDayOfMonth() == 1;
        boolean singleDrop = isSingleDropMode();

        // run scenario generation routine for every track attached to an active contract
        for (AtBContract contract : campaign.getActiveAtBContracts()) {
            StratConCampaignState campaignState = contract.getStratConCampaignState();

            if (campaignState == null) {
                continue;
            }

            boolean hasAssignedSingleDropScenario = false;
            for (StratConTrackState track : campaignState.getTracks()) {
                cleanupPhantomScenarios(track);

                // check if some of the forces have finished deployment
                // please do this before generating scenarios for track
                // to avoid unintentionally cleaning out integrated force deployments on
                // 0-deployment-length tracks
                scenarioLifecycle().processForceReturnDates(track, campaign);

                // map-based play applies facility effects here; Mapless/Singles supply a no-op strategy
                facility().applyPeriodicEffects(track, campaignState, isStartOfMonth);

                // loop through scenarios - if we haven't deployed in time,
                // fail it and apply consequences
                for (StratConScenario scenario : track.getScenarios().values()) {
                    if ((scenario.getDeploymentDate() != null) &&
                              scenario.getDeploymentDate().isBefore(today) &&
                              scenario.getPrimaryForceIDs().isEmpty()) {
                        scenarioLifecycle().processExpiredScenario(scenario, track, campaignState);
                    }
                }

                // on monday, generate new scenario dates
                if (isMonday && !hasAssignedSingleDropScenario) {
                    scenarioGeneration().generateWeeklyScenarioDates(campaign, campaignState, contract, track,
                          singleDrop);
                }

                // Only one scenario/week for Single Drop
                if (singleDrop) {
                    hasAssignedSingleDropScenario = true;
                }
            }

            List<LocalDate> weeklyScenarioDates = campaignState.getWeeklyScenarios();

            if (weeklyScenarioDates.contains(today)) {
                int scenarioCount = 0;
                for (LocalDate date : weeklyScenarioDates) {
                    if (date.equals(today)) {
                        scenarioCount++;
                    }
                }
                weeklyScenarioDates.removeIf(date -> date.equals(today));

                // If the OpFor is routed, we want to just discard any scheduled scenarios, clearly they've been
                // canceled due to impending defeat
                if (!contract.getMoraleLevel().isRouted()) {
                    scenarioGeneration().generateDailyScenarios(campaign, campaignState, contract, scenarioCount);
                }
            }
        }
    }

    /**
     * Worker function that goes through a track and cleans up scenarios missing required data.
     *
     * @param track the track to clean up
     */
    private void cleanupPhantomScenarios(StratConTrackState track) {
        List<StratConScenario> cleanupList = track.getScenarios()
                                                   .values()
                                                   .stream()
                                                   .filter(scenario -> (scenario.getDeploymentDate() == null) &&
                                                                             !scenario.isStrategicObjective())
                                                   .toList();

        for (StratConScenario scenario : cleanupList) {
            track.removeScenario(scenario);
        }
    }
}
