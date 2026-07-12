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

import static mekhq.campaign.market.contractMarket.ContractAutomation.performAutomatedActivation;
import static mekhq.campaign.mission.RandomFactionCamouflage.pickRandomCamouflage;

import java.time.LocalDate;
import java.util.Objects;

import jakarta.annotation.Nonnull;
import megamek.client.ui.util.PlayerColour;
import megamek.common.annotations.Nullable;
import megamek.common.icons.Camouflage;
import mekhq.campaign.AbstractLocation;
import mekhq.campaign.Campaign;
import mekhq.campaign.CampaignNewDayManager;
import mekhq.campaign.Hangar;
import mekhq.campaign.HumanResources;
import mekhq.campaign.Personnel;
import mekhq.campaign.Warehouse;
import mekhq.campaign.camOpsReputation.ReputationController;
import mekhq.campaign.campaignOptions.CampaignOptions;
import mekhq.campaign.finances.Finances;
import mekhq.campaign.finances.Money;
import mekhq.campaign.finances.enums.TransactionType;
import mekhq.campaign.icons.StandardFormationIcon;
import mekhq.campaign.icons.UnitIcon;
import mekhq.campaign.location.IPlace;
import mekhq.campaign.location.LocationNode;
import mekhq.campaign.market.RequestedStockLevels;
import mekhq.campaign.market.ShoppingList;
import mekhq.campaign.personnel.ranks.RankSystem;
import mekhq.campaign.personnel.ranks.RankValidator;
import mekhq.campaign.unit.UnitTechProgression;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.factionStanding.FactionStandings;

/**
 * Base class holding the state and logic extracted from {@link mekhq.campaign.Campaign} that belongs to a player force:
 * faction identity and appearance, finances, reputation/standing, and the owned {@link Hangar}/{@link Warehouse}/
 * personnel roster.
 *
 * <p>An {@code AbstractForce} is the {@link IPlace} that owns those resources: it holds its own {@link LocationNode}
 * and
 * parents its hangar, warehouse, and personnel to itself, exactly like {@link mekhq.campaign.base.AbstractBase}. The
 * upward {@code IPlace} walk therefore terminates here.</p>
 *
 * <p>A force holds <em>no</em> reference back to its {@link mekhq.campaign.Campaign}. Campaign-level concerns — the
 * current date, and writing daily-report lines — are the campaign's responsibility: it passes values such as the date
 * in as method parameters and does its own reporting around the force's state changes.</p>
 */
public abstract class AbstractForce implements IPlace {

    private final LocationNode locationNode;

    // Owned resources — this force is the IPlace that owns them.
    private final Hangar units = new Hangar();
    private Warehouse parts = new Warehouse();
    private final Personnel mainForcePersonnel = new Personnel();
    private HumanResources humanResources = new HumanResources();
    private final RequestedStockLevels requestedStockLevels = new RequestedStockLevels();
    private ShoppingList shoppingList = new ShoppingList();

    private final ForceOptions forceOptions;

    // Identity / appearance
    private String name;
    private RankSystem rankSystem;
    private Camouflage camouflage = pickRandomCamouflage(3025, "Root");
    private PlayerColour colour = PlayerColour.BLUE;
    private StandardFormationIcon unitIcon = new UnitIcon(null, null);
    private String retainerEmployerCode = null;
    private LocalDate retainerStartDate = null;
    private megamek.common.enums.Faction techFaction;

    private Finances finances;

    // Reputation / standing / crime / initiative
    private ReputationController reputation;
    private FactionStandings factionStandings;
    private int crimeRating = 0;
    private int crimePirateModifier = 0;
    private LocalDate dateOfLastCrime = null;
    private int initiativeBonus = 0;
    private int initiativeMaxBonus = 1;

    protected AbstractForce(ForceOptions forceOptions, megamek.common.enums.Faction techFaction, RankSystem rankSystem,
          Finances finances, ReputationController reputation, FactionStandings factionStandings) {
        this.forceOptions = forceOptions;
        this.techFaction = techFaction;
        this.rankSystem = rankSystem;
        this.finances = finances;
        this.reputation = reputation;
        this.factionStandings = factionStandings;

        this.locationNode = new LocationNode(this);
        LocationNode.LocationManager.setLocation(mainForcePersonnel, this);
        LocationNode.LocationManager.setLocation(units, this);
        LocationNode.LocationManager.setLocation(parts, this);
    }

    // region IPlace resources

    @Override
    public @Nonnull LocationNode getLocationNode() {
        return locationNode;
    }

    @Override
    public Hangar getHangar() {
        return units;
    }

    @Override
    public Warehouse getWarehouse() {
        return parts;
    }

    public void setWarehouse(Warehouse warehouse) {
        parts = Objects.requireNonNull(warehouse);
    }

    @Override
    public Personnel getPersonnel() {
        return mainForcePersonnel;
    }

    public HumanResources getHumanResources() {
        return humanResources;
    }

    public void setHumanResources(HumanResources humanResources) {
        this.humanResources = humanResources;
    }

    @Override
    public RequestedStockLevels getRequestedStockLevels() {
        return requestedStockLevels;
    }

    public void setShoppingList(ShoppingList shoppingList) {
        this.shoppingList = shoppingList;
    }

    public ShoppingList getShoppingList() {
        return shoppingList;
    }


    /**
     * Runs the main force's arrival behavior when it finishes travelling to a location. The {@link Campaign} is
     * supplied as a parameter (a force holds no campaign reference) for the systems this drives: automated mothball
     * activation, disease/inoculation checks, early-arrival contract checks, and refreshing local applicants.
     */
    @Override
    public void onArrival(Campaign campaign, boolean isSilentProcessing) {
        // This should be before inoculations so that we can correctly read the TO&E.
        if (!campaign.getAutomatedMothballUnits().isEmpty()) {
            performAutomatedActivation(campaign);
        }

        CampaignOptions campaignOptions = campaign.getCampaignOptions();
        if (campaignOptions.isUseRandomDiseases() && campaignOptions.isUseAlternativeAdvancedMedical()) {
            if (getParentLocation() instanceof AbstractLocation loc) {
                loc.checkForDiseaseOrBioweaponOutbreaks(campaign, campaign.getLocalDate());
            }
        }

        // Inoculations (generic IPlace behavior)
        IPlace.super.onArrival(campaign, isSilentProcessing);

        if (getParentLocation() instanceof AbstractLocation loc) {
            loc.testForEarlyArrival(campaign);
        }

        // We've just stopped traveling, so we should see if there are any local applicants.
        if (!HumanResources.isUsingLegacyPersonnelMarket(campaign.getCampaignOptions())) {
            campaign.refreshApplicants(true);
            CampaignNewDayManager.showRarePersonnelDialog(campaign, false);
        }
    }

    // endregion IPlace resources

    // region Options / faction

    public ForceOptions getForceOptions() {
        return forceOptions;
    }

    public Faction getFaction() {
        return forceOptions.getFaction();
    }

    public void setFaction(final Faction faction) {
        setFactionDirect(faction);
        updateTechFactionCode();
    }

    public void setFactionDirect(final Faction faction) {
        forceOptions.setFaction(faction);
    }

    public megamek.common.enums.Faction getTechFaction() {
        return techFaction;
    }

    public void updateTechFactionCode() {
        if (forceOptions.isFactionIntroDate()) {
            for (megamek.common.enums.Faction f : megamek.common.enums.Faction.values()) {
                if (f.equals(megamek.common.enums.Faction.NONE)) {
                    continue;
                }
                if (f.getCodeMM().equals(getFaction().getShortName())) {
                    techFaction = f;
                    UnitTechProgression.loadFaction(techFaction);
                    return;
                }
            }
            // If the tech progression data does not include the current faction, use a generic.
            if (getFaction().isClan()) {
                techFaction = megamek.common.enums.Faction.CLAN;
            } else if (getFaction().isPeriphery()) {
                techFaction = megamek.common.enums.Faction.PER;
            } else {
                techFaction = megamek.common.enums.Faction.IS;
            }
        } else {
            techFaction = megamek.common.enums.Faction.NONE;
        }
        // Unit tech level will be calculated if the code has changed.
        UnitTechProgression.loadFaction(techFaction);
    }

    // endregion Options / faction

    // region Identity / appearance

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RankSystem getRankSystem() {
        return rankSystem;
    }

    public void setRankSystem(final @Nullable RankSystem rankSystem) {
        // If they are the same object, there hasn't been a change and thus don't need to process further
        if (Objects.equals(getRankSystem(), rankSystem)) {
            return;
        }

        // Then, we need to validate the rank system. Null isn't valid to be set but may be the result of a cancelled
        // load. However, validation will prevent that
        final RankValidator rankValidator = new RankValidator();
        if (!rankValidator.validate(rankSystem, false)) {
            return;
        }

        // We need to know the old rank system for personnel processing
        final RankSystem oldRankSystem = getRankSystem();

        // And with that, we can set the rank system
        setRankSystemDirect(rankSystem);

        // Finally, we fix all personnel ranks and ensure they are properly set
        getPersonnel().values().stream()
              .filter(person -> person.getRankSystem().equals(oldRankSystem))
              .forEach(person -> person.setRankSystem(rankValidator, rankSystem));
    }

    public void setRankSystemDirect(final RankSystem rankSystem) {
        this.rankSystem = rankSystem;
    }

    public Camouflage getCamouflage() {
        return camouflage;
    }

    public void setCamouflage(final Camouflage camouflage) {
        this.camouflage = camouflage;
    }

    public PlayerColour getColour() {
        return colour;
    }

    public void setColour(final PlayerColour colour) {
        this.colour = Objects.requireNonNull(colour, "Colour cannot be set to null");
    }

    public StandardFormationIcon getUnitIcon() {
        return unitIcon;
    }

    public void setUnitIcon(final StandardFormationIcon unitIcon) {
        this.unitIcon = unitIcon;
    }

    public String getRetainerEmployerCode() {
        return retainerEmployerCode;
    }

    public void setRetainerEmployerCode(String code) {
        retainerEmployerCode = code;
    }

    public LocalDate getRetainerStartDate() {
        return retainerStartDate;
    }

    public void setRetainerStartDate(LocalDate retainerStartDate) {
        this.retainerStartDate = retainerStartDate;
    }

    // endregion Identity / appearance

    // region Finances

    public Finances getFinances() {
        return finances;
    }

    public void setFinances(Finances finances) {
        this.finances = finances;
    }

    public Money getFunds() {
        return finances.getBalance();
    }

    /**
     * Credits {@code quantity} to this force's finances. The campaign is responsible for defaulting the description and
     * writing any daily-report line.
     */
    public void addFunds(final TransactionType type, final LocalDate date, final Money quantity,
          final String description) {
        finances.credit(type, date, quantity, description);
    }

    /**
     * Debits {@code quantity} from this force's finances. The campaign is responsible for defaulting the description
     * and writing any daily-report line.
     */
    public void removeFunds(final TransactionType type, final LocalDate date, final Money quantity,
          final String description) {
        finances.debit(type, date, quantity, description);
    }

    // endregion Finances

    // region Reputation / standing / crime / initiative

    public ReputationController getReputation() {
        return reputation;
    }

    public void setReputation(ReputationController reputation) {
        this.reputation = reputation;
    }

    public FactionStandings getFactionStandings() {
        return factionStandings;
    }

    public void setFactionStandings(FactionStandings factionStandings) {
        this.factionStandings = factionStandings;
    }

    public int getRawCrimeRating() {
        return crimeRating;
    }

    public void setCrimeRating(int crimeRating) {
        this.crimeRating = crimeRating;
    }

    public void changeCrimeRating(int change) {
        this.crimeRating = Math.min(0, crimeRating + change);
    }

    public int getCrimePirateModifier() {
        return crimePirateModifier;
    }

    public void setCrimePirateModifier(int crimePirateModifier) {
        this.crimePirateModifier = crimePirateModifier;
    }

    public void changeCrimePirateModifier(int change) {
        this.crimePirateModifier = Math.min(0, crimePirateModifier + change);
    }

    public int getAdjustedCrimeRating() {
        return crimeRating + crimePirateModifier;
    }

    public @Nullable LocalDate getDateOfLastCrime() {
        return dateOfLastCrime;
    }

    public void setDateOfLastCrime(LocalDate dateOfLastCrime) {
        this.dateOfLastCrime = dateOfLastCrime;
    }

    public int getInitiativeBonus() {
        return initiativeBonus;
    }

    public void setInitiativeBonus(int bonus) {
        initiativeBonus = bonus;
    }

    public void applyInitiativeBonus(int bonus) {
        if (bonus > initiativeMaxBonus) {
            initiativeMaxBonus = bonus;
        }
        if ((bonus + initiativeBonus) > initiativeMaxBonus) {
            initiativeBonus = initiativeMaxBonus;
        } else {
            initiativeBonus += bonus;
        }
    }

    public void initiativeBonusIncrement(boolean change) {
        if (change) {
            setInitiativeBonus(++initiativeBonus);
        } else {
            setInitiativeBonus(--initiativeBonus);
        }
        if (initiativeBonus > initiativeMaxBonus) {
            initiativeBonus = initiativeMaxBonus;
        }
    }

    public int getInitiativeMaxBonus() {
        return initiativeMaxBonus;
    }

    public void setInitiativeMaxBonus(int bonus) {
        initiativeMaxBonus = bonus;
    }

    // endregion Reputation / standing / crime / initiative
}
