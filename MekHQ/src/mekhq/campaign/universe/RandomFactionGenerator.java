/*
 * Copyright (c) 2014 Carl Spain. All rights reserved.
 * Copyright (C) 2014-2026 The MegaMek Team. All Rights Reserved.
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
package mekhq.campaign.universe;

import static mekhq.MHQConstants.FORTRESS_REPUBLIC_END;
import static mekhq.MHQConstants.FORTRESS_REPUBLIC_START;
import static mekhq.campaign.universe.Faction.CLAN_FACTION_CODE;
import static mekhq.campaign.universe.Faction.INDEPENDENT_FACTION_CODE;
import static mekhq.campaign.universe.Faction.MERCENARY_FACTION_CODE;
import static mekhq.campaign.universe.Faction.PIRATE_FACTION_CODE;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import megamek.codeUtilities.ObjectUtility;
import megamek.common.annotations.Nullable;
import megamek.common.compute.Compute;
import megamek.common.util.weightedMaps.WeightedIntMap;
import megamek.logging.MMLogger;
import mekhq.MHQConstants;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.location.ILocation;
import mekhq.campaign.mission.mission.contractGeneration.GlobalEmployerTableValue;
import mekhq.campaign.universe.factionHints.FactionHints;

/**
 * @author Neoancient
 *       <p>
 *       Uses Factions and Planets to weighted lists of potential employers and enemies for contract generation. Also
 *       finds a suitable planet for the action.
 *                                                                                                                                                                   TODO : Account for the de facto alliance of the invading Clans and the
 *                                                                                                                                                                   TODO : Fortress Republic in a way that doesn't involve hard-coding them here.
 */
public class RandomFactionGenerator {
    private static final MMLogger LOGGER = MMLogger.create(RandomFactionGenerator.class);

    /** Inner Sphere factions that fought the Clans during the invasion; see {@link #adjustEnemyWeight}. */
    private static final List<String> INNER_SPHERE_CLAN_WAR_COMBATANTS = List.of("FC", "FRR", "DC");

    private static RandomFactionGenerator randomFactionGenerator = null;

    private FactionBorderTracker borderTracker;
    private FactionHints factionHints;

    /**
     * Constructs a generator with a default {@link FactionBorderTracker} and the shared {@link FactionHints} instance.
     */
    public RandomFactionGenerator() {
        this(null, null);
    }

    /**
     * Constructs a generator with the given border tracker and faction hints, falling back to a default border tracker
     * and the shared {@link FactionHints} instance when either argument is {@code null}.
     *
     * @param borderTracker the border tracker to use, or {@code null} to create a default one
     * @param factionHints  the faction hints to use, or {@code null} to use {@link FactionHints#getInstance()}
     */
    public RandomFactionGenerator(FactionBorderTracker borderTracker, FactionHints factionHints) {
        this.borderTracker = borderTracker;
        this.factionHints = factionHints;
        if (null == borderTracker) {
            initDefaultBorderTracker();
        }
        if (null == factionHints) {
            this.factionHints = FactionHints.getInstance();
        }
    }

    /**
     * Builds the default {@link FactionBorderTracker} used when no tracker is supplied, configured with this
     * generator's standard day/distance thresholds and border sizes.
     */
    private void initDefaultBorderTracker() {
        borderTracker = new FactionBorderTracker();
        borderTracker.setDayThreshold(30);
        borderTracker.setDistanceThreshold(100);
        borderTracker.setDefaultBorderSize(MHQConstants.FACTION_GENERATOR_BORDER_RANGE_IS,
              MHQConstants.FACTION_GENERATOR_BORDER_RANGE_NEAR_PERIPHERY,
              MHQConstants.FACTION_GENERATOR_BORDER_RANGE_CLAN);
    }

    /**
     * @return the shared {@link RandomFactionGenerator} instance, creating a default one on first access
     */
    public static RandomFactionGenerator getInstance() {
        if (randomFactionGenerator == null) {
            randomFactionGenerator = new RandomFactionGenerator();
        }
        return randomFactionGenerator;
    }

    /**
     * Replaces the shared {@link RandomFactionGenerator} instance, e.g. for testing.
     *
     * @param instance the instance to use as the new shared instance
     */
    public static void setInstance(RandomFactionGenerator instance) {
        randomFactionGenerator = instance;
    }

    /**
     * Initializes the border tracker for the given campaign: sets its date to the campaign's current date, centers its
     * search region on the campaign's current system with a radius from the campaign options, registers it as an event
     * handler, and widens the border size for deep periphery factions.
     *
     * @param campaign the campaign to initialize the border tracker for
     */
    public void startup(Campaign campaign) {
        borderTracker.setDate(campaign.getLocalDate());
        final PlanetarySystem location = campaign.getCurrentLocation().getCurrentSystem();
        borderTracker.setRegionCenter(location.getX(), location.getY());
        borderTracker.setRegionRadius(campaign.getCampaignOptions().getContractSearchRadius());
        MekHQ.registerHandler(borderTracker);
        for (final Faction faction : Factions.getInstance().getFactions()) {
            if (faction.isDeepPeriphery()) {
                borderTracker.setBorderSize(faction, MHQConstants.FACTION_GENERATOR_BORDER_RANGE_DEEP_PERIPHERY);
            }
        }
    }

    /**
     * Updates the border tracker's current date.
     *
     * @param date the new current date
     */
    public void setDate(LocalDate date) {
        borderTracker.setDate(date);
    }

    /**
     * @return the {@link FactionHints} used by this generator
     */
    public FactionHints getFactionHints() {
        return factionHints;
    }

    /**
     * Unregisters the border tracker as an event handler.
     */
    public void dispose() {
        MekHQ.unregisterHandler(borderTracker);
    }

    /**
     * @return the border tracker's current date
     */
    private LocalDate getCurrentDate() {
        return borderTracker.getLastUpdated();
    }

    /**
     * @return A set of faction keys for all factions that have a presence within the search area.
     */
    @Deprecated(since = "0.51.01")
    public Set<String> getCurrentFactions() {
        Set<String> retVal = new TreeSet<>();
        LocalDate currentDate = getCurrentDate();
        for (Faction faction : borderTracker.getFactionsInRegion()) {
            if (FactionHints.isEmptyFaction(faction) ||
                      faction.getShortName().equals(CLAN_FACTION_CODE)) {
                continue;
            }

            if (faction.getShortName().equals("ROS") && currentDate.isAfter(MHQConstants.FORTRESS_REPUBLIC_START)) {
                continue;
            }
            // Skip factions whose stated active years don't include the current date.
            // Stale planet ownership data can otherwise leak extinct factions (e.g. ARC after 3028) into the pool.
            if (!faction.validIn(currentDate)) {
                continue;
            }

            retVal.add(faction.getShortName());
            /* Add factions which do not control any planets to the employer list */
            for (Faction containedFaction : factionHints.getContainedFactions(faction, currentDate)) {
                if (containedFaction != null && containedFaction.validIn(currentDate)) {
                    retVal.add(containedFaction.getShortName());
                }
            }
        }
        // Add rebels and pirates
        retVal.add("REB");
        retVal.add(PIRATE_FACTION_CODE);
        return retVal;
    }

    /**
     * Determines whether the given faction should be excluded from employer selection: {@code null} or empty factions,
     * factions not valid on the given date, factions filtered out by the employer-type power tier, the mercenary
     * faction itself, Clans (aside from the CW/CSF mercenary-use exceptions), and ROS once Fortress Republic begins are
     * all excluded.
     *
     * @param faction             the candidate faction to check
     * @param currentDate         the date to check faction eligibility against
     * @param currentYear         the year of {@code currentDate}
     * @param employerType        the type of employer to return, or {@code null} for no power-tier filtering
     * @param isMercenaryCampaign if the player campaign is considered part of the 'mercenary' faction
     *
     * @return {@code true} if the faction should be excluded from employer selection
     */
    private static boolean checkForEarlyExit(@Nullable Faction faction, LocalDate currentDate, int currentYear,
          @Nullable GlobalEmployerTableValue employerType, boolean isMercenaryCampaign) {
        if (faction == null) {
            return true;
        }

        if (FactionHints.isEmptyFaction(faction)) {
            return true;
        }

        // Skip factions whose stated active years don't include the current date. Stale planet ownership data
        // can otherwise leak extinct factions (e.g. ARC after 3028) into the pool.
        if (!faction.validIn(currentDate)) {
            return true;
        }

        if (applyFactionFilter(faction, employerType)) {
            return true;
        }

        if (isMercenaryCampaign && !faction.isUsesMercenaries(currentYear)) {
            return true;
        }

        // We don't add mercenary employers here, they're handled explicitly elsewhere
        String factionShortName = faction.getShortName();
        if (factionShortName.equals(MERCENARY_FACTION_CODE)) {
            return true;
        }

        return isDuringFortressRepublic(factionShortName, currentDate);
    }

    /**
     * Checks whether the given faction should be filtered out based on the requested employer power tier.
     *
     * @param faction      the candidate faction to check
     * @param employerType the type of employer to return, or {@code null} for no power-tier filtering
     *
     * @return {@code true} if the faction does not match the requested employer type and should be filtered out
     */
    private static boolean applyFactionFilter(Faction faction, @Nullable GlobalEmployerTableValue employerType) {
        if (employerType == null) {
            return false;
        }

        return !GlobalEmployerTableValue.getFactionTableType(faction).equals(employerType);
    }

    /**
     * @param factionShortName the faction's short name
     * @param currentDate      the date to check
     *
     * @return {@code true} if the faction is ROS and the date is after Fortress Republic begins
     */
    private static boolean isDuringFortressRepublic(String factionShortName, LocalDate currentDate) {
        return factionShortName.equals("ROS") &&
                     currentDate.isBefore(FORTRESS_REPUBLIC_END) &&
                     currentDate.isAfter(FORTRESS_REPUBLIC_START);
    }

    /**
     * Selects a random employer faction from those controlling (or hosted by a controller of) systems within the search
     * area around the given location, with no employer-type filtering. Equivalent to calling
     * {@link #getRandomEmployerFaction(ILocation, LocalDate, GlobalEmployerTableValue, boolean)} with a {@code null}
     * employer type.
     *
     * @param location the location to check
     * @param date     the date to check faction control and eligibility against
     *
     * @return a random eligible employer faction for the area, or {@code null} if the location has no system, or no
     *       eligible faction currently controls anything in the area
     */
    @Deprecated(since = "0.51.01")
    public @Nullable Faction getEmployerFaction(ILocation location, LocalDate date) {
        return getRandomEmployerFaction(location, date, null, true);
    }

    /**
     * Selects a random employer faction from those controlling (or hosted by a controller of) systems within the search
     * area around the given location, the same way {@link #getCurrentFactions()} surveys the area around the border
     * tracker's configured region: every system within {@link FactionBorderTracker#getRadius()} of the location's
     * system (a negative radius means the entire map) is checked, and the controlling factions of all of them are
     * pooled together as candidates, each weighted by the number of nearby systems it controls (a faction controlling
     * five systems is five times as likely to be selected as one controlling a single system). Candidates are filtered
     * using the same eligibility rules as {@link #getEmployerFaction(ILocation, LocalDate)}: the mercenary faction
     * itself is excluded, Clans are excluded (aside from the CW/CSF mercenary-use exceptions), ROS is excluded once
     * Fortress Republic begins, and factions not valid on the given date are excluded. Factions hosted by an eligible
     * controlling faction (per {@link FactionHints#getContainedFactions}) are added as additional candidates,
     * inheriting their hosting faction's weight; if a faction is hosted by more than one eligible controlling faction,
     * its weights from each are summed.
     *
     * @param location            the location whose surrounding area should be searched
     * @param date                the date to check faction control and eligibility against
     * @param employerType        the type of employer to return, or {@code null} for no power-tier filtering
     * @param isMercenaryCampaign if the player campaign is considered part of the 'mercenary' faction
     *
     * @return a random eligible employer faction for the area, weighted by regional presence, or {@code null} if the
     *       location has no system, or no eligible faction currently controls anything in the area
     */
    public @Nullable Faction getRandomEmployerFaction(ILocation location, LocalDate date,
          @Nullable GlobalEmployerTableValue employerType, boolean isMercenaryCampaign) {
        PlanetarySystem system = location.getCurrentSystem();
        if (system == null) {
            return null;
        }

        double radius = borderTracker.getRadius();
        Map<Faction, Integer> counts = new HashMap<>();
        for (PlanetarySystem nearbySystem : borderTracker.getSystemList()) {
            if ((radius < 0) || (nearbySystem.getDistanceTo(system) <= radius)) {
                for (Faction faction : nearbySystem.getFactionSet(date)) {
                    counts.merge(faction, 1, Integer::sum);
                }
            }
        }

        int currentYear = date.getYear();
        Map<Faction, Integer> finalWeights = new LinkedHashMap<>();
        for (Map.Entry<Faction, Integer> entry : counts.entrySet()) {
            Faction faction = entry.getKey();
            int weight = entry.getValue();

            if (checkForEarlyExit(faction, date, currentYear, employerType, isMercenaryCampaign)) {
                continue;
            }

            finalWeights.merge(faction, weight, Integer::sum);

            for (Faction containedFaction : factionHints.getContainedFactions(faction, date)) {
                if (!checkForEarlyExit(containedFaction, date, currentYear, employerType, isMercenaryCampaign)) {
                    finalWeights.merge(containedFaction, weight, Integer::sum);
                }
            }
        }

        if (finalWeights.isEmpty()) {
            return null;
        }

        int totalWeight = finalWeights.values().stream().mapToInt(Integer::intValue).sum();
        int roll = Compute.randomInt(totalWeight);
        int cumulative = 0;
        for (Map.Entry<Faction, Integer> entry : finalWeights.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return entry.getKey();
            }
        }

        return null; // unreachable in practice, safety fallback
    }

    public Faction getEnemy(boolean isCovert, ILocation location, LocalDate date, @Nullable Faction employer) {
        if (employer == null) {
            return independentFallback("No employer supplied or faction does not exist. Returning INDEPENDENT");
        }

        WeightedIntMap<Faction> enemyMap = buildEnemyMap(isCovert, location, date, employer);
        Faction enemy = enemyMap.randomItem();
        if (null != enemy) {
            return enemy;
        }

        return independentFallback("Could not find enemy for employerName {}. Returning INDEPENDENT",
              employer.getShortName());
    }

    /**
     * Logs the given warning and returns the INDEPENDENT faction, used as {@link #getEnemy}'s fallback when no employer
     * is supplied or no valid enemy candidate could be found.
     *
     * @param message the warning message (may contain {@code {}} placeholders)
     * @param args    arguments for the message's placeholders
     *
     * @return the INDEPENDENT faction
     */
    private Faction independentFallback(String message, Object... args) {
        LOGGER.warn(message, args);
        return Factions.getInstance().getFaction(INDEPENDENT_FACTION_CODE);
    }

    protected WeightedIntMap<Faction> buildEnemyMap(boolean isCovert, ILocation location, LocalDate date,
          Faction employer) {
        WeightedIntMap<Faction> enemyMap = new WeightedIntMap<>();
        PlanetarySystem system = location.getCurrentSystem();
        if (system == null) {
            return enemyMap;
        }

        double radius = borderTracker.getRadius();
        Map<Faction, Integer> counts = new HashMap<>();
        for (PlanetarySystem nearbySystem : borderTracker.getSystemList()) {
            if ((radius < 0) || (nearbySystem.getDistanceTo(system) <= radius)) {
                for (Faction faction : nearbySystem.getFactionSet(date)) {
                    counts.merge(faction, 1, Integer::sum);
                }
            }
        }

        String employerShortName = employer.getShortName();
        boolean isPirateEmployer = employerShortName.equals(PIRATE_FACTION_CODE);

        // A faction at war with the employer is always a valid target, even one with no systems in the search
        // area, as long as it controls territory somewhere on the map. War relationships are sparse, so check who
        // the employer is at war with first (cheap, over the whole faction roster) before scanning for territory
        // (only for the handful of actual war partners, not every faction on the map).
        Set<Faction> candidates = new HashSet<>(counts.keySet());
        if (!isPirateEmployer) {
            for (Faction faction : Factions.getInstance().getFactions()) {
                if (!candidates.contains(faction) &&
                          factionHints.isAtWarWith(employer, faction, date) &&
                          controlsAnySystem(faction, date)) {
                    candidates.add(faction);
                }
            }
        }

        // These only depend on the date, not on any individual candidate, so compute them once per call rather
        // than once per candidate in adjustEnemyWeight.
        boolean isDuringClanInvasionHeight = date.isAfter(MHQConstants.CLAN_INVASION_FIRST_WAVE_BEGINS) &&
                                                   date.isBefore(MHQConstants.BATTLE_OF_TUKAYYID);
        boolean isDuringJihad = date.isAfter(MHQConstants.JIHAD_START) && date.isBefore(MHQConstants.NOMINAL_JIHAD_END);

        for (Faction enemy : candidates) {
            if (FactionHints.isEmptyFaction(enemy)) {
                continue;
            }

            if (isDuringFortressRepublic(enemy.getShortName(), date) || !enemy.validIn(date)) {
                continue;
            }

            if (isPirateEmployer) {
                enemyMap.add(1, enemy);
                continue;
            }

            if (enemy.equals(employer)) {
                continue;
            }

            // During covert operations, allies may sometimes attack each other. However, this is a very low chance
            boolean isAlly = factionHints.isAlliedWith(employer, enemy, date);
            if (isCovert && isAlly) {
                enemyMap.add(1, enemy);
                continue;
            }

            if (isAlly) {
                continue;
            }

            double weight = adjustEnemyWeight(counts.getOrDefault(enemy, 0),
                  employer,
                  enemy,
                  date,
                  isDuringClanInvasionHeight,
                  isDuringJihad);
            if (weight > 0) {
                enemyMap.add((int) Math.round(weight), enemy);
            }
        }

        return enemyMap;
    }

    /**
     * Checks whether the given faction controls at least one system anywhere the border tracker knows about, stopping
     * at the first match. Only called for the rare case of a war partner with no presence in the immediate search area,
     * so this deliberately isn't cached alongside {@code counts} in
     * {@link #buildEnemyMap(boolean, ILocation, LocalDate, Faction)} &mdash; most calls never reach it.
     *
     * @param faction the faction to check
     * @param date    the date to check faction control against
     *
     * @return {@code true} if the faction controls at least one known system on the given date
     */
    private boolean controlsAnySystem(Faction faction, LocalDate date) {
        for (PlanetarySystem system : borderTracker.getSystemList()) {
            if (system.getFactionSet(date).contains(faction)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return A set of keys for all current factions in the space that are potential employers.
     */
    @Deprecated(since = "0.51.01")
    public Set<String> getEmployerSet() {
        Set<String> set = new HashSet<>();
        LocalDate currentDate = getCurrentDate();
        for (Faction faction : borderTracker.getFactionsInRegion()) {
            // Skip factions whose stated active years don't include the current date.
            // Stale planet ownership data can otherwise leak extinct factions (e.g. ARC after 3028) into the pool.
            if (!faction.validIn(currentDate)) {
                continue;
            }
            if (isDuringFortressRepublic(faction.getShortName(), currentDate)) {
                continue;
            }
            if (!faction.isClan() && !FactionHints.isEmptyFaction(faction)) {
                set.add(faction.getShortName());
            }
            /* Add factions which do not control any planets to the employer list */
            for (Faction containedFaction : factionHints.getContainedFactions(faction, currentDate)) {
                if (!containedFaction.isClan() && containedFaction.validIn(currentDate)) {
                    set.add(containedFaction.getShortName());
                }
            }
        }
        return set;
    }

    /**
     * Constructs a list of a faction's potential enemies based on common borders.
     *
     * @param employerName The shortName of the employer faction
     *
     * @return A list of faction that share a border
     */
    @Deprecated(since = "0.51.01")
    public List<String> getEnemyList(String employerName) {
        Faction employer = Factions.getInstance().getFaction(employerName);
        if (null == employer) {
            LOGGER.warn("Unknown faction key: {}", employerName);
            return Collections.emptyList();
        }
        return getEnemyList(Factions.getInstance().getFaction(employerName));
    }

    /**
     * Constructs a list of a faction's potential enemies based on common borders.
     *
     * @param employer The employer faction
     *
     * @return A list of faction that share a border
     */
    @Deprecated(since = "0.51.01")
    public List<String> getEnemyList(Faction employer) {
        Set<Faction> list = new HashSet<>();
        LocalDate currentDate = getCurrentDate();
        Faction outer = factionHints.getContainedFactionHost(employer, currentDate);
        for (Faction enemy : borderTracker.getFactionsInRegion()) {
            if (FactionHints.isEmptyFaction(enemy)) {
                continue;
            }

            if (enemy.isAggregate()) {
                if (!enemy.isMercenary() && !enemy.isPirate()) {
                    continue;
                }
            }

            // Skip extinct factions; stale planet ownership data can otherwise leak them into the enemy list.
            if (!enemy.validIn(currentDate)) {
                continue;
            }
            if (enemy.equals(employer) && !factionHints.isAtWarWith(enemy, enemy, currentDate)) {
                continue;
            }
            if (factionHints.isAlliedWith(employer, enemy, currentDate) && !employer.isClan() && !enemy.isClan()) {
                continue;
            }
            if (factionHints.isNeutral(employer, enemy, currentDate) ||
                      factionHints.isNeutral(enemy, employer, currentDate)) {
                continue;
            }
            Faction useBorder = employer;
            if (null != outer) {
                if (!factionHints.isContainedFactionOpponent(outer, employer, enemy, currentDate)) {
                    continue;
                }
                useBorder = outer;
            }

            if (!borderTracker.getBorderSystems(useBorder, enemy).isEmpty()) {
                list.add(enemy);
                for (Faction containedFaction : factionHints.getContainedFactions(enemy, currentDate)) {
                    if ((null != containedFaction) && containedFaction.validIn(currentDate) &&
                              factionHints.isContainedFactionOpponent(enemy, containedFaction, employer, currentDate)) {
                        list.add(containedFaction);
                    }
                }
            }
        }
        return list.stream().map(Faction::getShortName).collect(Collectors.toList());
    }

    /**
     * Applies diplomatic-stance multipliers to an enemy candidate's base area-presence weight (see
     * {@link #buildEnemyMap(boolean, ILocation, LocalDate, Faction)}). Factions at war with the employer are floored to
     * a weight of at least 1 before doubling, so a belligerent with no systems in the search area is still a valid,
     * pickable target.
     *
     * @param count                      The candidate's base weight (number of systems it controls in the search area)
     * @param employer                   The attacking faction
     * @param enemy                      The defending faction
     * @param date                       The date to check diplomatic relations against
     * @param isDuringClanInvasionHeight Whether {@code date} falls between the Clan invasion's first wave and the
     *                                   Battle of Tukayyid
     * @param isDuringJihad              Whether {@code date} falls within the Jihad
     *
     * @return The adjusted weight
     */
    protected double adjustEnemyWeight(int count, Faction employer, Faction enemy, LocalDate date,
          boolean isDuringClanInvasionHeight, boolean isDuringJihad) {
        double weight = count;
        if (factionHints.isAtWarWith(employer, enemy, date)) {
            weight = Math.max(weight, 1.0);
            weight *= 2.0;
        }

        if (factionHints.isRivalOf(employer, enemy, date)) {
            weight *= 2.0;
        }

        if (INNER_SPHERE_CLAN_WAR_COMBATANTS.contains(employer.getShortName()) &&
                  enemy.isClan() &&
                  isDuringClanInvasionHeight) {
            weight *= 2.0;
        }

        if (isDuringJihad && enemy.isWoB()) {
            weight *= 2.0;
        }

        /*
         * This is pretty hacky, but ComStar does not have many targets
         * and tends to fight the Clans too much between Tukayyid and
         * the Jihad.
         */
        if (employer.getShortName().equals("CS") && enemy.isClan()) {
            weight /= 12.0;
        }

        return weight;
    }

    /**
     * Selects a random planet from a list of potential targets based on the attacking and defending factions.
     *
     * @param attacker The faction key of the attacker
     * @param defender The faction key of the defender
     *
     * @return The planetId of the chosen planet, or null if there are no target candidates
     */
    @Nullable
    public String getMissionTarget(String attacker, String defender) {
        Faction faction0 = Factions.getInstance().getFaction(attacker);
        Faction faction1 = Factions.getInstance().getFaction(defender);
        if (null == faction0) {
            LOGGER.error("Non-existent faction key: {}", attacker);
            return null;
        }
        if (null == faction1) {
            LOGGER.error("Non-existent faction key: {}", attacker);
            return null;
        }
        List<PlanetarySystem> planetList = getMissionTargetList(faction0, faction1);
        if (!planetList.isEmpty()) {
            return ObjectUtility.getRandomItem(planetList).getId();
        }
        return null;
    }

    /**
     * Builds a list of planets controlled by the defender that are near one or more of the attacker's planets.
     *
     * @param attackerKey The attacking faction's shortName
     * @param defenderKey The defending faction's shortName
     *
     * @return A list of potential mission targets
     */
    public List<PlanetarySystem> getMissionTargetList(String attackerKey, String defenderKey) {
        Faction attacker = Factions.getInstance().getFaction(attackerKey);
        Faction defender = Factions.getInstance().getFaction(defenderKey);
        if (null == attacker) {
            LOGGER.error("Non-existent faction key (attacker): {}", attackerKey);
        }
        if (null == defender) {
            LOGGER.error("Non-existent faction key (defender): {}", defenderKey);
        }
        if ((null != attacker) && (null != defender)) {
            return getMissionTargetList(attacker, defender);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Builds a list of planets controlled by the defender that are near one or more of the attacker's planets.
     *
     * @param attacker The attacking faction
     * @param defender The defending faction
     *
     * @return A list of potential mission targets
     */
    public List<PlanetarySystem> getMissionTargetList(Faction attacker, Faction defender) {
        LocalDate currentDate = getCurrentDate();
        attacker = resolveTerritorialHost(attacker, currentDate);
        defender = resolveTerritorialHost(defender, currentDate);
        if (attacker == null || defender == null) {
            return Collections.emptyList();
        }

        // Certain attackers (pirates, mercenaries, ComStar/WoB) can strike anywhere the defender holds.
        if (isSpecialAttacker(attacker)) {
            return systemsOf(borderTracker.getBorders(defender));
        }

        // A rebel uprising happens somewhere within the attacking government's own territory, not on a border.
        if (defender.isRebel()) {
            return systemsOf(borderTracker.getBorders(attacker));
        }

        // Both sides hold territory: target the shared border between them.
        Set<PlanetarySystem> planetSet = new HashSet<>(borderTracker.getBorderSystems(attacker, defender));

        // If the defender has no systems, widen the search to the attacker's frontier with every neighboring faction.
        boolean widenForLandlessDefender = !borderTracker.getFactionsInRegion().contains(defender);

        // If neither side directly borders the other, fall back to whichever regional faction hosts a "contained"
        // opponent relationship between the two sides. Collected alongside the frontier-widening above in a single
        // pass over the region instead of a second one, since both need the same regional-faction list; only
        // merged in below if nothing else found a target.
        Set<PlanetarySystem> containedFactionFallback = new HashSet<>();
        for (Faction regionalFaction : borderTracker.getFactionsInRegion()) {
            if (widenForLandlessDefender) {
                planetSet.addAll(borderTracker.getBorderSystems(regionalFaction, attacker));
                planetSet.addAll(borderTracker.getBorderSystems(attacker, regionalFaction));
            }

            for (Faction hintFaction : factionHints.getContainedFactions(regionalFaction, currentDate)) {
                if (hintFaction.equals(attacker) &&
                          factionHints.isContainedFactionOpponent(regionalFaction, hintFaction, defender,
                                currentDate)) {
                    containedFactionFallback.addAll(borderTracker.getBorderSystems(regionalFaction, defender));
                } else if (hintFaction.equals(defender) &&
                                 factionHints.isContainedFactionOpponent(regionalFaction, hintFaction, attacker,
                                       currentDate)) {
                    containedFactionFallback.addAll(borderTracker.getBorderSystems(attacker, regionalFaction));
                }
            }
        }

        if (planetSet.isEmpty()) {
            planetSet.addAll(containedFactionFallback);
        }

        // Last resort: neither a direct border nor a contained-faction proxy border exists. Target whichever of
        // the defender's systems is physically closest to any of the attacker's, rather than giving up entirely.
        if (planetSet.isEmpty()) {
            PlanetarySystem closestSystem = findClosestDefenderSystem(attacker, defender);
            if (closestSystem != null) {
                planetSet.add(closestSystem);
            }
        }

        return new ArrayList<>(planetSet);
    }

    /**
     * Finds the system controlled by the defender that is physically closest to any system controlled by the
     * attacker. Used as the final fallback in {@link #getMissionTargetList(Faction, Faction)} when neither a
     * direct border nor a contained-faction proxy border could be found.
     *
     * @param attacker the attacking faction
     * @param defender the defending faction
     *
     * @return the closest defender-controlled system, or {@code null} if either faction controls no systems
     */
    private @Nullable PlanetarySystem findClosestDefenderSystem(Faction attacker, Faction defender) {
        FactionBorders attackerBorders = borderTracker.getBorders(attacker);
        FactionBorders defenderBorders = borderTracker.getBorders(defender);
        if (attackerBorders == null || defenderBorders == null) {
            return null;
        }

        PlanetarySystem closestSystem = null;
        double closestDistance = Double.MAX_VALUE;
        for (PlanetarySystem defenderSystem : defenderBorders.getSystems()) {
            for (PlanetarySystem attackerSystem : attackerBorders.getSystems()) {
                double distance = defenderSystem.getDistanceTo(attackerSystem);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestSystem = defenderSystem;
                }
            }
        }
        return closestSystem;
    }

    /**
     * @param faction the faction to check
     *
     * @return {@code true} if the faction is inherently landless: pirates, mercenaries, and ComStar/WoB don't hold
     *       fixed territory of their own
     */
    private static boolean isSpecialAttacker(Faction faction) {
        return faction.isPirate() || faction.isMercenary() || faction.isComStarOrWoB();
    }

    /**
     * Resolves a faction with no directly-controlled systems in the region to its contained-faction host, if one is
     * configured, so mission targeting has something with real territory to work with. Inherently landless factions
     * (pirates, mercenaries, ComStar/WoB) and rebels are never contained by anyone, so this is a no-op for them;
     * {@link #getMissionTargetList(Faction, Faction)} handles their lack of territory separately.
     *
     * @param faction the faction to resolve
     * @param date    the date to check the contained-faction relationship against
     *
     * @return the resolved faction (its host if one was found and needed, otherwise the faction unchanged), or
     *       {@code null} if the faction has no systems of its own and no host could be found
     */
    private @Nullable Faction resolveTerritorialHost(Faction faction, LocalDate date) {
        if (borderTracker.getFactionsInRegion().contains(faction) ||
                  isSpecialAttacker(faction) ||
                  faction.isRebel()) {
            return faction;
        }
        return factionHints.getContainedFactionHost(faction, date);
    }

    /**
     * @param borders a faction's borders, or {@code null} if it controls no systems in the region
     *
     * @return the border's systems as a list, or an empty list if {@code borders} is {@code null}
     */
    private static List<PlanetarySystem> systemsOf(@Nullable FactionBorders borders) {
        return (borders == null) ? Collections.emptyList() : new ArrayList<>(borders.getSystems());
    }
}
