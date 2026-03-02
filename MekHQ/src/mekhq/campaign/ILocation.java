/*
 * Copyright (c) 2011 Jay Lawson (jaylawson39 at yahoo.com). All rights reserved.
 * Copyright (C) 2013-2026 The MegaMek Team. All Rights Reserved.
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
package mekhq.campaign;

import megamek.logging.MMLogger;
import mekhq.MekHQ;
import mekhq.campaign.parts.Part;
import mekhq.campaign.personnel.Person;
import mekhq.campaign.unit.Unit;
import mekhq.campaign.universe.Planet;
import mekhq.campaign.universe.PlanetarySystem;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 */
public interface ILocation {

    MMLogger LOGGER = MMLogger.create(ILocation.class);

    default void writeLocationToXML(final PrintWriter pw, int indent) {
        if (hasLocation()) {
            getLocation().writeLocationToXML(pw, indent);
        }
    }

    ILocation getLocation();

    default boolean hasLocation() {
        return getLocation() != null;
    }

    /**
     * Get the current location of this location.
     * @return {@link CurrentLocation}, or {@code null} if it doesn't have a {@code CurrentLocation}
     */
    default CurrentLocation getCurrentLocation() {
        if (hasLocation()) {
            ILocation parent = getLocation();
            if (parent.hasCurrentLocation()) {
                return parent.getCurrentLocation();
            }
        }
        return null;
    }

    /**
     * Check if this location has an actual location {@link CurrentLocation}.
     * @return {@code true} if this location has a location, otherwise {@code false}
     */
    default boolean hasCurrentLocation() {
        return getCurrentLocation() != null;
    }

    /**
     * Set the transit time for this location. If {@link #hasCurrentLocation()} is {@code false},
     * this method does nothing.
     * @param transitTime the transit time to set
     */
    default void setTransitTime(double transitTime) {
        if (hasCurrentLocation()) {
            getCurrentLocation().setTransitTime(transitTime);
        }
    }

    /**
     * Check if this location is currently on a planet. If {@link #hasCurrentLocation()} is
     * {@code false}, this method returns {@code false}.
     * @return {@code true} if this location is on a planet, otherwise {@code false}
     */
    default boolean isOnPlanet() {
        if (!hasCurrentLocation()) {
            return false;
        }

        return getCurrentLocation().isOnPlanet();
    }

    /**
     * Check if this location is currently at a jump point. If {@link #hasCurrentLocation()} is
     * {@code false}, this method returns {@code false}.
     * @return {@code true} if this location is at a jump point, otherwise {@code false}
     */
    default boolean isAtJumpPoint() {
        if (!hasCurrentLocation()) {
            return false;
        }

        return getCurrentLocation().isAtJumpPoint();
    }

    /**
     * Get the percentage of transit completed for this location. If {@link #hasCurrentLocation()}
     * is {@code false}, this method returns {@code 0.0}.
     * @return the percentage of transit completed as a {@code double}
     */
    default double getPercentageTransit() {
        if (!hasCurrentLocation()) {
            return 0.0;
        }

        return getCurrentLocation().getPercentageTransit();
    }

    /**
     * Check if this location is currently in transit. If {@link #hasCurrentLocation()} is
     * {@code false}, this method returns {@code false}.
     * @return {@code true} if this location is in transit, otherwise {@code false}
     */
    default boolean isInTransit() {
        if (!hasCurrentLocation()) {
            return false;
        }

        return getCurrentLocation().isInTransit();
    }

    /**
     * Get the current {@link PlanetarySystem} for this location. If {@link #hasCurrentLocation()}
     * is {@code false}, this method returns {@code null}.
     * @return the current {@link PlanetarySystem}, or {@code null} if no location exists
     */
    default PlanetarySystem getCurrentSystem() {
        if (!hasCurrentLocation()) {
            return null;
        }

        return getCurrentLocation().getCurrentSystem();
    }

    /**
     * Get the current {@link Planet} for this location. If {@link #hasCurrentLocation()} is
     * {@code false}, this method returns {@code null}.
     * @return the current {@link Planet}, or {@code null} if no location exists
     */
    default Planet getPlanet() {
        if (!hasCurrentLocation()) {
            return null;
        }

        return getCurrentLocation().getPlanet();
    }

    /**
     * Get the remaining transit time for this location. If {@link #hasCurrentLocation()} is
     * {@code false}, this method returns {@code 0.0}.
     * @return the remaining transit time as a {@code double}
     */
    default double getTransitTime() {
        if (!hasCurrentLocation()) {
            return 0.0;
        }

        return getCurrentLocation().getTransitTime();
    }

    /**
     * Check if the JumpShip is at the zenith jump point. If {@link #hasCurrentLocation()} is
     * {@code false}, this method returns {@code false}.
     * @return {@code true} if the JumpShip is at the zenith point, otherwise {@code false}
     */
    default boolean isJumpZenith() {
        if (!hasCurrentLocation()) {
            return false;
        }

        return getCurrentLocation().isJumpZenith();
    }

    /**
     * Get the current {@link JumpPath} for this location. If {@link #hasCurrentLocation()} is
     * {@code false}, this method returns {@code null}.
     * @return the current {@link JumpPath}, or {@code null} if no location exists
     */
    default JumpPath getJumpPath() {
        if (!hasCurrentLocation()) {
            return null;
        }

        return getCurrentLocation().getJumpPath();
    }

    /**
     * Set the {@link JumpPath} for this location. If {@link #hasCurrentLocation()} is
     * {@code false}, this method does nothing.
     * @param jumpPath the {@link JumpPath} to set
     */
    default void setJumpPath(JumpPath jumpPath) {
        if (hasCurrentLocation()) {
            getCurrentLocation().setJumpPath(jumpPath);
        }
    }

    Set<ILocation> getLocations();

    default Set<ILocation> getLocationsUnmodifiable() {
        return new HashSet<>(getLocations());
    }

    default void addLocation(ILocation locatable) {
        if (this.equals(locatable)) {
            LOGGER.warn("Cannot add location to itself. Parent: {}, Child: {}", this, locatable);
            return;
        }

        getLocations().add(locatable);
    }

    default void removeLocation(ILocation locatable) {
        getLocations().remove(locatable);

        // If nothing is located at this location anymore, let's unregister from MekHQ
        if (getLocations().isEmpty()) {
            MekHQ.unregisterHandler(this);
        }
    }

    default Set<Person> getPersonnelAtLocation() {
        return getLocations().stream()
            .flatMap(loc -> loc.getPersonnelAtLocation().stream())
            .collect(Collectors.toSet());
    }

    default Set<Unit> getUnitsAtLocation() {
        return getLocations().stream()
            .flatMap(loc -> loc.getUnitsAtLocation().stream())
            .collect(Collectors.toSet());
    }

    default Set<Part> getPartsAtLocation() {
        return getLocations().stream()
            .flatMap(loc -> loc.getPartsAtLocation().stream())
            .collect(Collectors.toSet());
    }
}
