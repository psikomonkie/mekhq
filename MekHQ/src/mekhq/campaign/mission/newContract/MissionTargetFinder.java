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
package mekhq.campaign.mission.newContract;

import static mekhq.MHQConstants.FORTRESS_REPUBLIC_END;
import static mekhq.MHQConstants.FORTRESS_REPUBLIC_START;
import static mekhq.MHQConstants.FORTRESS_REPUBLIC_TERRA_ONLY_END;
import static mekhq.campaign.universe.Faction.PIRATE_FACTION_CODE;
import static mekhq.campaign.universe.Faction.REPUBLIC_OF_THE_SPHERE_FACTION_CODE;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import megamek.common.annotations.Nullable;
import mekhq.campaign.location.ILocation;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.FactionBorderTracker;
import mekhq.campaign.universe.FactionBorders;
import mekhq.campaign.universe.Factions;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.RandomFactionGenerator;
import mekhq.campaign.universe.factionHints.FactionHints;

/**
 * Finds potential mission-target planets for a given attacker/defender pair, generally on their shared border, with
 * dedicated tiers for factions whose territory doesn't work like a normal nation's (pirates, ComStar, rebels). Used by
 * {@link RandomFactionGenerator#getMissionTargetList(Faction, Faction, ILocation)}.
 */
public class MissionTargetFinder {
    private final FactionBorderTracker borderTracker;
    private final FactionHints factionHints;
    private final PirateMissionTargetFinder pirateFinder;
    private final ComStarMissionTargetFinder comStarFinder;

    public MissionTargetFinder(FactionBorderTracker borderTracker, FactionHints factionHints) {
        this.borderTracker = borderTracker;
        this.factionHints = factionHints;
        this.pirateFinder = new PirateMissionTargetFinder(borderTracker);
        this.comStarFinder = new ComStarMissionTargetFinder(borderTracker);
    }

    /**
     * Builds a list of potential mission-target planets near {@code location}, generally on the shared border between
     * attacker and defender (see {@link #resolveTerritorialHost}, {@link #isSpecialAttacker}, and the closest-system
     * fallback for the special cases). Pirates favor border worlds over the interior on either side of a conflict (see
     * {@link PirateMissionTargetFinder}). A ComStar defender is targeted by HPG network presence instead of borders
     * (see {@link ComStarMissionTargetFinder}).
     * <p>
     * Outside of those special cases, every returned system is one the defender actually owns: a mission is a raid on
     * (or defense of) a specific enemy world, never a neighbor's or the attacker's own territory.
     * <p>
     * Computed fresh around {@code location} on every call rather than from the tracker's single cached region, so
     * campaigns with multiple simultaneous locations get a target scoped to whichever force needs it.
     *
     * @param attacker    the attacking faction
     * @param defender    the defending faction
     * @param location    the location to center the search on
     * @param currentDate the date to check faction control and diplomatic relations against
     *
     * @return a list of potential mission targets
     */
    public List<PlanetarySystem> find(Faction attacker, Faction defender, ILocation location, LocalDate currentDate) {
        double radius = borderTracker.getRadius();
        attacker = resolveTerritorialHost(attacker, currentDate);
        defender = resolveTerritorialHost(defender, currentDate);
        if (attacker == null || defender == null) {
            return Collections.emptyList();
        }

        // A pirate defender is more likely holed up in lawless space near the frontier than on a system some real
        // faction officially claims; a pirate attacker raids from the frontier rather than striking anywhere the
        // defender holds. Only once both come up empty do we fall back to the usual border-based logic below.
        if (defender.getShortName().equals(PIRATE_FACTION_CODE)) {
            List<PlanetarySystem> targets = pirateFinder.findDefenderTargets(attacker, defender, location, radius,
                  currentDate);
            if (!targets.isEmpty()) {
                return targets;
            }
        }

        if (attacker.getShortName().equals(PIRATE_FACTION_CODE)) {
            List<PlanetarySystem> targets = pirateFinder.findAttackerTargets(attacker, defender, location, radius);
            if (!targets.isEmpty()) {
                return targets;
            }
        }

        // ComStar's HPG network matters more than its (minimal) sovereign territory: only its own systems and
        // A/B-rated HPG stations anywhere are valid targets, regardless of borders.
        if (defender.isComStar()) {
            return comStarFinder.findValidSystems(defender, location, radius, currentDate);
        }

        // Certain attackers (pirates, mercenaries, ComStar/WoB) can strike anywhere the defender holds.
        if (isSpecialAttacker(attacker)) {
            FactionBorders borders = borderTracker.getBorders(defender, location, radius);
            return systemsOf(borders, currentDate);
        }

        // A rebel uprising happens somewhere within the attacking government's own territory, not on a border.
        if (defender.isRebel()) {
            FactionBorders borders = borderTracker.getBorders(attacker, location, radius);
            return systemsOf(borders, currentDate);
        }

        // Both sides hold territory: target the shared border between them. getBorderSystems(self, other) returns
        // OTHER's systems near SELF, so this is always defender-owned.
        Set<PlanetarySystem> planetSet = new HashSet<>(borderTracker.getBorderSystems(attacker, defender, location,
              radius));

        // If neither side directly borders the other, fall back to whichever regional faction hosts a "contained"
        // opponent relationship with the attacker, using that host's own border with the defender as a proxy for the
        // attacker's otherwise poorly-defined local presence. Still always defender-owned, since it's the defender's
        // systems near the host that get returned, not the host's own.
        if (planetSet.isEmpty()) {
            for (Faction regionalFaction : borderTracker.getFactionsInRegion(location, radius)) {
                for (Faction hintFaction : factionHints.getContainedFactions(regionalFaction, currentDate)) {
                    if (hintFaction.equals(attacker) &&
                              factionHints.isContainedFactionOpponent(regionalFaction, hintFaction, defender,
                                    currentDate)) {
                        planetSet.addAll(borderTracker.getBorderSystems(regionalFaction, defender, location, radius));
                    }
                }
            }
        }

        // Last resort: neither a direct border nor a contained-faction proxy border exists. Target whichever of
        // the defender's systems is physically closest to any of the attacker's, rather than giving up entirely.
        if (planetSet.isEmpty()) {
            PlanetarySystem closestSystem = findClosestDefenderSystem(attacker, defender, location, radius);
            if (closestSystem != null) {
                planetSet.add(closestSystem);
            }
        }

        return new ArrayList<>(planetSet);
    }

    /**
     * Finds the system controlled by the defender that is physically closest to any system controlled by the attacker.
     * Used as the final fallback in {@link #find} when neither a direct border nor a contained-faction proxy border
     * could be found.
     * <p>
     * The defender's search is unrestricted (whole map) rather than limited to {@code radius}: a defender can end up
     * here specifically because it was guaranteed a valid target regardless of local presence (e.g. a faction at war
     * with the attacker, per {@code RandomFactionGenerator#buildEnemyMap}), so it may have no systems anywhere near
     * {@code location} at all. Without this, such a match would find no target and fail contract generation entirely.
     * The attacker's search stays scoped to {@code radius}, since the mission should still originate from somewhere
     * near the force generating it.
     *
     * @param attacker the attacking faction
     * @param defender the defending faction
     * @param location the location to center the search on
     * @param radius   the search radius in light years from {@code location}'s current system, applied to the attacker
     *                 only
     *
     * @return the closest defender-controlled system, or {@code null} if either faction controls no systems in range
     */
    private @Nullable PlanetarySystem findClosestDefenderSystem(Faction attacker, Faction defender,
          ILocation location, double radius) {
        FactionBorders attackerBorders = borderTracker.getBorders(attacker, location, radius);
        FactionBorders defenderBorders = borderTracker.getBorders(defender, location, -1);
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
     * Resolves a faction with no directly-controlled systems anywhere to its contained-faction host, if one is
     * configured, so mission targeting has something with real territory to work with. Inherently landless factions
     * (pirates, mercenaries, ComStar/WoB) and rebels are never contained by anyone, so this is a no-op for them;
     * {@link #find} handles their lack of territory separately.
     * <p>
     * A faction with territory only outside the local search area (e.g. a war partner guaranteed valid by
     * {@code RandomFactionGenerator#buildEnemyMap} regardless of local presence) is still returned unchanged, not
     * redirected to a host: it has real territory to work with, just not nearby. The cached, region-limited
     * {@link FactionBorderTracker#getFactionsInRegion()} is checked first as a cheap common-case shortcut; the full,
     * uncached {@link #controlsAnySystem} scan only runs for the rare faction with no local presence.
     *
     * @param faction the faction to resolve
     * @param date    the date to check faction control and the contained-faction relationship against
     *
     * @return the resolved faction (its host if one was found and needed, otherwise the faction unchanged), or
     *       {@code null} if the faction has no systems anywhere and no host could be found
     */
    private @Nullable Faction resolveTerritorialHost(Faction faction, LocalDate date) {
        if (borderTracker.getFactionsInRegion().contains(faction) ||
                  isSpecialAttacker(faction) ||
                  faction.isRebel() ||
                  controlsAnySystem(faction, date)) {
            return faction;
        }
        return factionHints.getContainedFactionHost(faction, date);
    }

    /**
     * Checks whether the given faction controls at least one system anywhere the border tracker knows about, stopping
     * at the first match. Only called for the rare faction with no presence in the tracker's cached region, so this
     * deliberately isn't cached &mdash; most calls never reach it.
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
     * @param borders     a faction's borders, or {@code null} if it controls no systems in the region
     * @param currentDate the current campaign {@link LocalDate}
     *
     * @return the border's systems as a list, or an empty list if {@code borders} is {@code null}
     */
    private static List<PlanetarySystem> systemsOf(@Nullable FactionBorders borders, final LocalDate currentDate) {
        List<PlanetarySystem> systems = borders == null ?
                                              new ArrayList<>() :
                                              new ArrayList<>(borders.getSystems());

        Faction republicOfTheSphere = Factions.getInstance().getFaction(REPUBLIC_OF_THE_SPHERE_FACTION_CODE);
        boolean isDuringFortressRepublic = isDuringFortressRepublic(REPUBLIC_OF_THE_SPHERE_FACTION_CODE, currentDate);
        boolean isDuringFortressRepublicTerraOnly = isDuringFortressRepublicTerraOnly(currentDate);

        if (!isDuringFortressRepublic && !isDuringFortressRepublicTerraOnly) {
            return systems;
        }

        List<PlanetarySystem> filteredSystems = new ArrayList<>();
        for (PlanetarySystem system : systems) {
            Set<Faction> factionSet = system.getFactionSet(currentDate);
            boolean isRepublicOwned = factionSet.contains(republicOfTheSphere);
            boolean isContested = factionSet.size() > 1;

            if (!isRepublicOwned || isContested) {
                filteredSystems.add(system);
                continue;
            }

            boolean isTerra = system.getId().equalsIgnoreCase("Terra");
            if (isDuringFortressRepublic && !isDuringFortressRepublicTerraOnly) {
                continue;
            }

            if (isTerra) {
                filteredSystems.add(system);
            }
        }

        return filteredSystems;
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
     * @param currentDate the date to check
     *
     * @return {@code true} if the date is after Fortress Republic (Terra only) begins, but before it ends
     */
    private static boolean isDuringFortressRepublicTerraOnly(LocalDate currentDate) {
        return currentDate.isBefore(FORTRESS_REPUBLIC_TERRA_ONLY_END) &&
                     currentDate.isAfter(FORTRESS_REPUBLIC_START);
    }
}
