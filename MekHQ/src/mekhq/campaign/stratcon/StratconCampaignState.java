/*
 * Copyright (C) 2019-2025 The MegaMek Team. All Rights Reserved.
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
 */
package mekhq.campaign.stratcon;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import megamek.common.annotations.Nullable;
import megamek.logging.MMLogger;
import mekhq.campaign.Campaign;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.AtBScenario;
import org.w3c.dom.Node;

/**
 * Contract-level state object for a StratCon campaign.
 *
 * @author NickAragua
 */
@XmlRootElement(name = "StratconCampaignState")
public class StratconCampaignState {
    private static final MMLogger logger = MMLogger.create(StratconCampaignState.class);

    public static final String ROOT_XML_ELEMENT_NAME = "StratconCampaignState";

    @XmlTransient
    private AtBContract contract;

    // these are all state variables that affect the current Stratcon Campaign
    private double globalOpforBVMultiplier;
    private int supportPoints;
    private int victoryPoints;
    private String briefingText;
    private boolean allowEarlyVictory;

    // these are applied to any scenario generated in the campaign; use sparingly
    private List<String> globalScenarioModifiers = new ArrayList<>();

    @XmlElementWrapper(name = "campaignTracks")
    @XmlElement(name = "campaignTrack")
    private final List<StratconTrackState> tracks;

    private List<LocalDate> weeklyScenarios;

    @XmlTransient
    public AtBContract getContract() {
        return contract;
    }

    public void setContract(AtBContract contract) {
        this.contract = contract;
    }

    public StratconCampaignState() {
        tracks = new ArrayList<>();
        weeklyScenarios = new ArrayList<>();
    }

    public StratconCampaignState(AtBContract contract) {
        tracks = new ArrayList<>();
        weeklyScenarios = new ArrayList<>();
        setContract(contract);
    }

    /**
     * The opfor BV multiplier. Intended to be additive.
     *
     * @return The additive opfor BV multiplier.
     */
    public double getGlobalOpforBVMultiplier() {
        return globalOpforBVMultiplier;
    }

    public StratconTrackState getTrack(int index) {
        return tracks.get(index);
    }

    public List<StratconTrackState> getTracks() {
        return tracks;
    }

    public int getTrackCount() {
        return tracks.size();
    }

    public void addTrack(StratconTrackState track) {
        tracks.add(track);
    }

    @XmlJavaTypeAdapter(value = LocalDateAdapter.class)
    @XmlElementWrapper(name = "weeklyScenarios")
    @XmlElement(name = "weeklyScenario")
    public List<LocalDate> getWeeklyScenarios() {
        return weeklyScenarios;
    }

    public void addWeeklyScenario(LocalDate weeklyScenario) {
        weeklyScenarios.add(weeklyScenario);
    }

    public void setWeeklyScenarios(final List<LocalDate> weeklyScenarios) {
        this.weeklyScenarios = weeklyScenarios;
    }

    public int getSupportPoints() {
        return supportPoints;
    }

    /**
     * Modifies the current support points by the specified amount.
     *
     * <p>
     * This method increases or decreases the support points by the given number. It adds the value of {@code change} to
     * the existing support points total. This can be used to reflect changes due to various gameplay events or
     * actions.
     * </p>
     *
     * @param change The amount to adjust the support points by. Positive values will increase the support points, while
     *               negative values will decrease them.
     */
    public void changeSupportPoints(int change) {
        supportPoints += change;
    }

    public void setSupportPoints(int supportPoints) {
        this.supportPoints = supportPoints;
    }

    public int getVictoryPoints() {
        return victoryPoints;
    }

    public void setVictoryPoints(int victoryPoints) {
        this.victoryPoints = victoryPoints;
    }

    public void updateVictoryPoints(int increment) {
        victoryPoints += increment;
    }

    public String getBriefingText() {
        return briefingText;
    }

    public void setBriefingText(String briefingText) {
        this.briefingText = briefingText;
    }

    public boolean allowEarlyVictory() {
        return allowEarlyVictory;
    }

    public void setAllowEarlyVictory(boolean allowEarlyVictory) {
        this.allowEarlyVictory = allowEarlyVictory;
    }

    public List<String> getGlobalScenarioModifiers() {
        return globalScenarioModifiers;
    }

    public void setGlobalScenarioModifiers(List<String> globalScenarioModifiers) {
        this.globalScenarioModifiers = globalScenarioModifiers;
    }

    public void useSupportPoint() {
        supportPoints--;
    }

    /**
     * Decreases the number of support points by the specified decrement.
     *
     * @param decrement The number of support points to use/decrease.
     */
    public void useSupportPoints(int decrement) {
        supportPoints -= decrement;
    }

    /**
     * Convenience/speed method of determining whether or not a force with the given ID has been deployed to a track in
     * this campaign.
     *
     * @param forceID the force ID to check
     *
     * @return Deployed or not.
     */
    public boolean isForceDeployedHere(int forceID) {
        for (StratconTrackState trackState : tracks) {
            if (trackState.getAssignedForceCoords().containsKey(forceID)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Removes the scenario with the given campaign scenario ID from any tracks where it's present
     */
    public void removeStratconScenario(int scenarioID) {
        for (StratconTrackState trackState : tracks) {
            trackState.removeScenario(scenarioID);
        }
    }

    /**
     * Retrieves the {@link StratconScenario} associated with a given {@link AtBScenario}.
     *
     * <p>
     * This method searches through all {@link StratconTrackState} objects in the {@link StratconCampaignState} to find
     * the first {@link StratconScenario} whose backing scenario matches the specified {@link AtBScenario}. If no such
     * scenario is found, it returns {@code null}.
     * </p>
     *
     * <strong>Usage:</strong>
     * <p>
     * Use this method to easily fetch the {@link StratconScenario} associated with the provided {@link AtBScenario}.
     * </p>
     *
     * @param campaign The {@link Campaign} containing the data to search through.
     * @param scenario The {@link AtBScenario} to find the corresponding {@link StratconScenario} for.
     *
     * @return The matching {@link StratconScenario}, or {@code null} if no corresponding scenario is found.
     */
    public static @Nullable StratconScenario getStratconScenarioFromAtBScenario(Campaign campaign,
          AtBScenario scenario) {
        AtBContract contract = scenario.getContract(campaign);
        if (contract == null) {
            return null;
        }

        StratconCampaignState campaignState = contract.getStratconCampaignState();
        if (campaignState == null) {
            return null;
        }

        for (StratconTrackState track : campaignState.getTracks()) {
            for (StratconScenario stratConScenario : track.getScenarios().values()) {
                if (scenario.equals(stratConScenario.getBackingScenario())) {
                    return stratConScenario; // Return the first matching scenario if found
                }
            }
        }

        return null;
    }

    /**
     * Serialize this instance of a campaign state to a PrintWriter Omits initial xml declaration
     *
     * @param pw The destination print writer
     */
    public void Serialize(PrintWriter pw) {
        try {
            JAXBContext context = JAXBContext.newInstance(StratconCampaignState.class);
            JAXBElement<StratconCampaignState> stateElement = new JAXBElement<>(new QName(ROOT_XML_ELEMENT_NAME),
                  StratconCampaignState.class,
                  this);
            Marshaller m = context.createMarshaller();
            m.setProperty(Marshaller.JAXB_FRAGMENT, true);
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            m.marshal(stateElement, pw);
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    /**
     * Attempt to deserialize an instance of a Campaign State from the passed-in XML Node
     *
     * @param xmlNode The node with the campaign state
     *
     * @return Possibly an instance of a StratconCampaignState
     */
    public static StratconCampaignState Deserialize(Node xmlNode) {
        StratconCampaignState resultingCampaignState = null;

        try {
            JAXBContext context = JAXBContext.newInstance(StratconCampaignState.class);
            Unmarshaller um = context.createUnmarshaller();
            JAXBElement<StratconCampaignState> templateElement = um.unmarshal(xmlNode, StratconCampaignState.class);
            resultingCampaignState = templateElement.getValue();
        } catch (Exception e) {
            logger.error("Error Deserializing Campaign State", e);
        }

        // Hack: LocalDate doesn't serialize/deserialize nicely within a map, so we
        // store it as a int-string map instead
        // while we're here, manually restore the coordinate-force lookup
        if (resultingCampaignState != null) {
            for (StratconTrackState track : resultingCampaignState.getTracks()) {
                track.restoreReturnDates();
                track.restoreAssignedCoordForces();
            }
        }

        return resultingCampaignState;
    }

    /**
     * This adapter provides a way to convert between a LocalDate and the ISO-8601 string representation of the date
     * that is used for XML marshaling and unmarshalling in JAXB.
     */
    public static class LocalDateAdapter extends XmlAdapter<String, LocalDate> {
        @Override
        public String marshal(LocalDate date) {
            return date.toString();
        }

        @Override
        public LocalDate unmarshal(String date) throws Exception {
            return LocalDate.parse(date);
        }
    }
}
