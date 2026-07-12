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
package mekhq.campaign.force;

import megamek.common.annotations.Nullable;
import mekhq.campaign.Campaign;
import mekhq.campaign.camOpsReputation.ReputationController;
import mekhq.campaign.campaignOptions.CampaignOptions;
import mekhq.campaign.finances.Finances;
import mekhq.campaign.location.ILocation;
import mekhq.campaign.personnel.ranks.RankSystem;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.factionStanding.FactionStandings;
import org.w3c.dom.Node;

/**
 * The human player's active force: the single {@link AbstractForce} a {@link Campaign} is played through.
 *
 * <p>It is the {@link mekhq.campaign.location.IPlace} node representing the main force in the location tree (its
 * {@link mekhq.campaign.location.LocationNode}, owned by {@link AbstractForce}, is what
 * {@link mekhq.campaign.ForceLocationManager} anchors to a location) and the referable node that survives an XML
 * save/load round-trip. Like every force, it holds no reference back to the campaign.</p>
 *
 * <p>For now a {@link Campaign} owns exactly one {@code PlayerForce}; multiple forces per campaign is a later
 * refactor.</p>
 */
public class PlayerForce extends AbstractForce {

    /** Discriminator identifying the player's main force as a serialized {@link ILocation} reference. */
    public static final String LOCATION_REFERENCE_TYPE = "playerForce";

    /**
     * @param faction              the force's starting faction
     * @param techFaction          the resolved MegaMek tech faction
     * @param rankSystem           the force's rank system
     * @param finances             the force's finances ledger
     * @param reputationController the force's reputation controller
     * @param factionStandings     the force's standings with the wider universe
     * @param campaignOptions      the campaign options the force's {@link ForceOptions} passes through to
     */
    public PlayerForce(Faction faction, megamek.common.enums.Faction techFaction, RankSystem rankSystem,
          Finances finances, ReputationController reputationController, FactionStandings factionStandings,
          CampaignOptions campaignOptions) {
        super(new ForceOptions(campaignOptions, faction), techFaction, rankSystem, finances, reputationController,
              factionStandings);
    }

    @Override
    public String locationReferenceType() {
        return LOCATION_REFERENCE_TYPE;
    }

    /**
     * Resolves a serialized reference to the player force back to the live instance. Because a campaign has a single
     * player force, the reference carries no identity beyond its discriminator.
     */
    public static @Nullable ILocation resolveReference(Campaign campaign, Node node) {
        return campaign.getPlayerForce();
    }
}
