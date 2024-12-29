/*
 * TransportAssignment.java
 *
 * Copyright (c) 2020 The Megamek Team. All rights reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MekHQ. If not, see <http://www.gnu.org/licenses/>.
 */

package mekhq.campaign.unit;

import java.util.Objects;

/**
 * Represents an assignment to a specific bay on a transport.
 */
public class TransportAssignment {
    private final Unit transport;
    private final int bayNumber;

    /**
     * Initializes a new instance of the TransportAssignment class.
     * @param transport The transport.
     * @param bayNumber The bay number on the transport.
     */
    public TransportAssignment(Unit transport, int bayNumber) {
        this.transport = Objects.requireNonNull(transport);
        this.bayNumber = bayNumber;
    }

    /**
     * Gets the transport for this assignment.
     */
    public Unit getTransport() {
        return transport;
    }

    /**
     * Gets the bay number for the transport.
     */
    public int getBayNumber() {
        return bayNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        } else if (getClass() != o.getClass()) {
            return false;
        } else {
            TransportAssignment other = (TransportAssignment) o;
            return Objects.equals(getTransport(), other.getTransport())
                    && (getBayNumber() == other.getBayNumber());
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTransport(), getBayNumber());
    }
}
