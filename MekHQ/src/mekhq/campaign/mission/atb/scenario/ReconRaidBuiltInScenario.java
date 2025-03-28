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
package mekhq.campaign.mission.atb.scenario;

import megamek.common.*;
import mekhq.campaign.Campaign;
import mekhq.campaign.force.CombatTeam;
import mekhq.campaign.mission.*;
import mekhq.campaign.mission.ObjectiveEffect.ObjectiveEffectType;
import mekhq.campaign.mission.ScenarioObjective.ObjectiveCriterion;
import mekhq.campaign.mission.ScenarioObjective.TimeLimitType;
import mekhq.campaign.mission.atb.AtBScenarioEnabled;

import java.util.ArrayList;

@AtBScenarioEnabled
public class ReconRaidBuiltInScenario extends AtBScenario {
    @Override
    public int getScenarioType() {
        return RECONRAID;
    }

    @Override
    public String getScenarioTypeDescription() {
        return defaultResourceBundle.getString("battleDetails.reconRaid.name");
    }

    @Override
    public String getResourceKey() {
        return "reconRaid";
    }

    @Override
    public void setExtraScenarioForces(Campaign campaign, ArrayList<Entity> allyEntities,
                                       ArrayList<Entity> enemyEntities) {
        int enemyStart;
        int playerHome;

        if (isAttacker()) {
            playerHome = startPos[Compute.randomInt(4)];
            setStartingPos(playerHome);

            enemyStart = Board.START_CENTER;
            setEnemyHome(playerHome + 4);

            if (getEnemyHome() > 8) {
                setEnemyHome(getEnemyHome() - 8);
            }
        } else {
            setStartingPos(Board.START_CENTER);
            enemyStart = startPos[Compute.randomInt(4)];
            setEnemyHome(enemyStart);
            playerHome = getEnemyHome() + 4;

            if (playerHome > 8) {
                playerHome -= 8;
            }
        }

        if (!allyEntities.isEmpty()) {
            addBotForce(getAllyBotForce(getContract(campaign), getStartingPos(), playerHome, allyEntities), campaign);
        }

        CombatTeam combatTeam = getCombatTeamById(campaign);
        int weightClass = combatTeam != null ? combatTeam.getWeightClass(campaign) : EntityWeightClass.WEIGHT_LIGHT;

        addEnemyForce(enemyEntities, weightClass, isAttacker() ? EntityWeightClass.WEIGHT_ASSAULT : EntityWeightClass.WEIGHT_MEDIUM,
            0, 0, campaign);

        addBotForce(getEnemyBotForce(getContract(campaign), enemyStart, getEnemyHome(), enemyEntities), campaign);
    }

    @Override
    public boolean canAddDropShips() {
        return isAttacker() && (Compute.d6() <= 3);
    }

    @Override
    public void setObjectives(Campaign campaign, AtBContract contract) {
        super.setObjectives(campaign, contract);

        ScenarioObjective destroyHostiles = CommonObjectiveFactory.getDestroyEnemies(contract, 1, 50);
        ScenarioObjective keepAttachedUnitsAlive = CommonObjectiveFactory.getKeepAttachedGroundUnitsAlive(contract,
                this);

        if (keepAttachedUnitsAlive != null) {
            getScenarioObjectives().add(keepAttachedUnitsAlive);
        }

        if (isAttacker()) {
            ScenarioObjective keepFriendliesAlive = CommonObjectiveFactory.getKeepFriendliesAlive(campaign, contract,
                    this, 1, 75, false);
            getScenarioObjectives().add(keepFriendliesAlive);

            ScenarioObjective raidObjective = new ScenarioObjective();
            raidObjective.setObjectiveCriterion(ObjectiveCriterion.Custom);
            raidObjective.setDescription(
                    String.format("%s:", defaultResourceBundle.getString("battleDetails.reconRaid.name")));
            raidObjective.addDetail(String.format(
                    defaultResourceBundle.getString("battleDetails.reconRaid.instructions.oppositeEdge"),
                    OffBoardDirection.translateBoardStart(AtBDynamicScenarioFactory.getOppositeEdge(getStartingPos()))));
            raidObjective.addDetail(defaultResourceBundle.getString("battleDetails.reconRaid.instructions.stayStill"));
            raidObjective.addDetail(
                    String.format(defaultResourceBundle.getString("battleDetails.reconRaid.instructions.returnEdge"),
                            OffBoardDirection.translateBoardStart(getStartingPos())));
            raidObjective.addDetail(defaultResourceBundle.getString("battleDetails.reconRaid.instructions.reward"));

            ObjectiveEffect victoryEffect = new ObjectiveEffect();
            victoryEffect.effectType = ObjectiveEffectType.AtBBonus;
            victoryEffect.howMuch = Compute.d6() - 2;
            raidObjective.addSuccessEffect(victoryEffect);

            getScenarioObjectives().add(raidObjective);
        } else {
            destroyHostiles.setTimeLimit(10);
            destroyHostiles.setTimeLimitAtMost(true);
            destroyHostiles.setTimeLimitType(TimeLimitType.Fixed);
            getScenarioObjectives().add(destroyHostiles);
        }
    }

    @Override
    public String getBattlefieldControlDescription() {
        return getResourceBundle().getString("battleDetails.common.defenderControlsBattlefield");
    }
}
