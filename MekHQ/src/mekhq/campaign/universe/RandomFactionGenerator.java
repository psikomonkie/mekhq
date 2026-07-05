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
import static mekhq.campaign.universe.Faction.REPUBLIC_OF_THE_SPHERE_FACTION_CODE;

import java.time.LocalDate;
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
 *                                                                                                                                                                                                                                     TODO : Account for the de facto alliance of the invading Clans and the
 *                                                                                                                                                                                                                                     TODO : Fortress Republic in a way that doesn't involve hard-coding them here.
 */
public class RandomFactionGenerator {
    private static final MMLogger LOGGER = MMLogger.create(RandomFactionGenerator.class);

    /** Inner Sphere factions that fought the Clans during the invasion; see {@link #adjustEnemyWeight}. */
    private static final List<String> INNER_SPHERE_CLAN_WAR_COMBATANTS = List.of("FC", "FRR", "DC");

    private static RandomFactionGenerator randomFactionGenerator = null;

    private FactionBorderTracker borderTracker;
    private FactionHints factionHints;
    private MissionTargetFinder missionTargetFinder;

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
        missionTargetFinder = new MissionTargetFinder(this.borderTracker, this.factionHints);
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

            if (faction.getShortName().equals(REPUBLIC_OF_THE_SPHERE_FACTION_CODE) &&
                      currentDate.isAfter(MHQConstants.FORTRESS_REPUBLIC_START)) {
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
     * @return {@code true} if the faction is ROS, and the date is after Fortress Republic begins, but before it ends
     */
    private static boolean isDuringFortressRepublic(String factionShortName, LocalDate currentDate) {
        return factionShortName.equals(REPUBLIC_OF_THE_SPHERE_FACTION_CODE) &&
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
    @Deprecated(since = "0.51.01", forRemoval = true)
    public @Nullable Faction getEmployerFaction(ILocation location, LocalDate date) {
        return getRandomEmployerFaction(location, date, null, true);
    }

    /**
     * Returns a randomly selected faction that can act as an employer based on the provided location, date, employer
     * type, and campaign type.
     *
     * <p>The method calculates potential employer factions by considering nearby planetary systems and
     * filtering them based on the given criteria. It then assigns weights to the factions and performs a weighted
     * random selection.</p>
     *
     * @param location            The current location used to determine the system context for employer selection.
     * @param date                The date to use for filtering factions by temporal availability.
     * @param employerType        The type of employer being considered, which may further filter faction selection. Can
     *                            be null if no specific type is required.
     * @param isMercenaryCampaign A flag indicating if the selection is being made in the context of a mercenary
     *                            campaign. May alter filtering and weighting logic.
     *
     * @return A randomly selected {@code Faction} that can act as an employer under the provided criteria, or
     *       {@code INDEPENDENT} if no suitable faction is found.
     */
    public Faction getRandomEmployerFaction(ILocation location, LocalDate date,
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

        return Factions.getInstance().getFaction(INDEPENDENT_FACTION_CODE); // unreachable in practice, safety fallback
    }

    /**
     * Selects a random enemy faction for {@code employer} within the area around {@code location}, weighted by regional
     * presence and diplomatic stance (see {@link #buildEnemyMap}).
     *
     * @param isCovert whether this is a covert operation, where allies become rare, low-chance targets instead of
     *                 always being excluded
     * @param location the location to center the search on
     * @param date     the date to check faction control and diplomatic relations against
     * @param employer the employer faction, or {@code null} to skip straight to the INDEPENDENT fallback
     *
     * @return a randomly selected enemy faction, or the INDEPENDENT faction if none could be found
     */
    public Faction getRandomEnemy(boolean isCovert, ILocation location, LocalDate date, @Nullable Faction employer) {
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
     * Logs the given warning and returns the INDEPENDENT faction, used as {@link #getRandomEnemy}'s fallback when no
     * employer is supplied or no valid enemy candidate could be found.
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

    /**
     * Builds a weighted pool of enemy candidates for {@code employer} within the search area around {@code location}.
     * Each faction's weight starts at the number of systems it controls in range; factions at war with the employer are
     * added regardless of area presence, as long as they control territory anywhere; allies (direct or through a shared
     * ally, see {@link #performIsAllyCheck}) are excluded; and remaining weights are adjusted for diplomatic stance
     * (see {@link #adjustEnemyWeight}). A pirate employer bypasses all diplomatic checks.
     *
     * @param isCovert whether allies should be rare, low-chance targets instead of always excluded
     * @param location the location to center the search on
     * @param date     the date to check faction control and diplomatic relations against
     * @param employer the employer faction
     *
     * @return a weighted map of enemy candidates, empty if {@code location} has no current system
     */
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

            // Factions that don't have a direct alliance record but share a common ally (e.g. two member states of
            // the same superpower, each individually allied with it but not with each other) should still be treated
            // as allies, unless factionHints directly contradicts that by recording them as at war with each other.
            boolean isAlly = performIsAllyCheck(date, employer, enemy);

            // During covert operations, allies may sometimes attack each other. However, this is a very low chance
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
     * Checks whether two factions should be treated as allies: directly, or through a shared ally (e.g. two member
     * states of the same superpower), unless a direct war record between them says otherwise.
     *
     * @param date     the date to check diplomatic relations against
     * @param employer one faction
     * @param enemy    the other faction
     *
     * @return {@code true} if the two factions should be treated as allied
     */
    private boolean performIsAllyCheck(LocalDate date, Faction employer, Faction enemy) {
        return factionHints.isAlliedWith(employer, enemy, date) ||
                     (!factionHints.isAtWarWith(employer, enemy, date) &&
                            factionHints.isAlliedThroughSharedAlly(employer, enemy, date));
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
     * @param location the location to center the search on, scoped by this generator's configured search radius (see
     *                 {@link #getMissionTargetList(Faction, Faction, ILocation)})
     *
     * @return The planetId of the chosen planet, or null if there are no target candidates
     */
    @Nullable
    public String getMissionTarget(String attacker, String defender, ILocation location) {
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
        List<PlanetarySystem> planetList = getMissionTargetList(faction0, faction1, location);
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
     * @param location    the location to center the search on, scoped by this generator's configured search radius (see
     *                    {@link #getMissionTargetList(Faction, Faction, ILocation)})
     *
     * @return A list of potential mission targets
     */
    public List<PlanetarySystem> getMissionTargetList(String attackerKey, String defenderKey, ILocation location) {
        Faction attacker = Factions.getInstance().getFaction(attackerKey);
        Faction defender = Factions.getInstance().getFaction(defenderKey);
        if (null == attacker) {
            LOGGER.error("Non-existent faction key (attacker): {}", attackerKey);
        }
        if (null == defender) {
            LOGGER.error("Non-existent faction key (defender): {}", defenderKey);
        }
        if ((null != attacker) && (null != defender)) {
            return getMissionTargetList(attacker, defender, location);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Builds a list of potential mission-target planets near {@code location}, generally on the shared border between
     * attacker and defender, with dedicated tiers for factions whose territory doesn't work like a normal nation's
     * (pirates, ComStar, rebels). See {@link MissionTargetFinder} for the full selection logic.
     *
     * @param attacker the attacking faction
     * @param defender the defending faction
     * @param location the location to center the search on
     *
     * @return a list of potential mission targets
     */
    public List<PlanetarySystem> getMissionTargetList(Faction attacker, Faction defender, ILocation location) {
        return missionTargetFinder.find(attacker, defender, location, getCurrentDate());
    }
}
