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
package mekhq.gui.campaignOptions;

import static megamek.client.ui.util.FlatLafStyleBuilder.setFontScaling;
import static mekhq.utilities.MHQInternationalization.getTextAt;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import megamek.MMConstants;
import megamek.client.ui.Messages;
import megamek.client.ui.buttons.ColourSelectorButton;
import megamek.client.ui.clientGUI.GUIPreferences;
import megamek.client.ui.comboBoxes.FontComboBox;
import megamek.client.ui.comboBoxes.MMComboBox;
import megamek.client.ui.dialogs.buttonDialogs.CommonSettingsDialog;
import megamek.client.ui.dialogs.helpDialogs.HelpDialog;
import megamek.client.ui.displayWrappers.FontDisplay;
import megamek.client.ui.util.UIUtil;
import megamek.common.preference.PreferenceManager;
import megamek.logging.MMLogger;
import mekhq.MHQConstants;
import mekhq.MHQOptions;
import mekhq.MekHQ;
import mekhq.campaign.universe.enums.CompanyGenerationMethod;
import mekhq.gui.campaignOptions.components.CampaignOptionsCheckBox;
import mekhq.gui.campaignOptions.components.CampaignOptionsFormPanel;
import mekhq.gui.campaignOptions.components.CampaignOptionsLabel;
import mekhq.gui.campaignOptions.components.CampaignOptionsPagePanel;
import mekhq.gui.campaignOptions.components.CampaignOptionsSpinner;
import mekhq.gui.enums.FormationIconOperationalStatusStyle;
import mekhq.gui.enums.PersonnelFilterStyle;

/**
 * Pilot pane that reuses the Campaign Options tree-navigation framework (the navigation panel, content host, and page
 * shell) for the MekHQ Client Options. Its purpose is to validate that the framework is generic across a second
 * consumer - one backed by {@link MHQOptions} and the {@code GUI.properties} bundle rather than the campaign - before
 * the framework is extracted into MegaMek.
 *
 * <p>This lives in the {@code campaignOptions} package for now so it can use the framework's package-private classes;
 * when the framework is extracted and made public, this pane will move to its own home.</p>
 */
public class MHQOptionsPane extends JPanel {
    private static final String RESOURCE_BUNDLE = "mekhq.resources.GUI";
    private static final int HEADER_IMAGE_SIZE = 200;
    private static final int FORM_LABEL_WIDTH = CampaignOptionsFormPanel.DEFAULT_LABEL_WIDTH;
    private static final int FORM_CONTROL_WIDTH = CampaignOptionsFormPanel.DEFAULT_CONTROL_WIDTH;
    private static final MMLogger LOGGER = MMLogger.create(MHQOptionsPane.class);

    private final JFrame frame;
    private final MHQOptions options;
    private final List<CampaignOptionsRoute> routes = new ArrayList<>();
    private final Map<String, Supplier<Component>> pageFactories = new HashMap<>();
    private final Map<String, Component> pageCache = new HashMap<>();

    private final List<Runnable> pageSavers = new ArrayList<>();

    private CampaignOptionsContentHost contentHost;
    private CampaignOptionsNavigationPanel navigationPanel;

    public MHQOptionsPane(JFrame frame) {
        super(new BorderLayout());
        setName("mhqOptionsPane");
        this.frame = frame;
        options = MekHQ.getMHQOptions();
        registerRoutes();
        initialize();
    }

    private void registerRoutes() {
        registerRoute("display.general", this::createDisplayGeneralPage, "displayCategory", "displayGeneralPage");
        registerRoute("display.interstellar", this::createDisplayInterstellarPage,
              "displayCategory", "displayInterstellarPage");
        registerRoute("display.personnel", this::createDisplayPersonnelPage, "displayCategory", "displayPersonnelPage");
        registerRoute("colours.unitStatus", this::createColoursUnitStatusPage,
              "coloursCategory", "coloursUnitStatusPage");
        registerRoute("colours.skillFeedback", this::createColoursSkillFeedbackPage,
              "coloursCategory", "coloursSkillFeedbackPage");
        registerRoute("fonts", this::createFontsPage, "fontsTab");
        registerRoute("autosave", this::createAutosavePage, "autosaveTab");
        registerRoute("newDay.pools", this::createNewDayPoolsPage, "newDayCategory", "newDayPoolsPage");
        registerRoute("newDay.automation", this::createNewDayAutomationPage,
              "newDayCategory", "newDayAutomationPage");
        registerRoute("newDay.training", this::createNewDayTrainingPage, "newDayCategory", "newDayTrainingPage");
        registerRoute("newDay.formation", this::createNewDayFormationPage, "newDayCategory", "newDayFormationPage");
        registerRoute("campaignSave", this::createCampaignSavePage, "campaignXMLSaveTab");
        registerRoute("reminders.nags", this::createRemindersNagsPage, "remindersCategory", "remindersNagsPage");
        registerRoute("reminders.confirmations", this::createRemindersConfirmationsPage,
              "remindersCategory", "remindersConfirmationsPage");
        registerRoute("advanced", this::createAdvancedPage, "advancedPage");
    }

    private void registerRoute(String id, Supplier<Component> pageFactory, String... titleResourceNames) {
        List<String> path = new ArrayList<>();
        for (String titleResourceName : titleResourceNames) {
            path.add(getTextAt(RESOURCE_BUNDLE, titleResourceName + ".title"));
        }
        pageFactories.put(id, pageFactory);
        routes.add(new CampaignOptionsRoute(id, path, List.of(titleResourceNames)));
    }

    private void initialize() {
        CampaignOptionsRoute initialRoute = routes.get(0);
        Component initialContent = getPage(initialRoute.getId());
        contentHost = new CampaignOptionsContentHost(initialContent, null, false, RESOURCE_BUNDLE);
        navigationPanel = new CampaignOptionsNavigationPanel(routes, this::selectRoute, RESOURCE_BUNDLE);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigationPanel, contentHost);
        splitPane.setName("mhqOptionsSplitPane");
        splitPane.setResizeWeight(0.0);
        splitPane.setDividerLocation(UIUtil.scaleForGUI(CampaignOptionsNavigationPanel.NAVIGATION_WIDTH));
        add(splitPane, BorderLayout.CENTER);
        setPreferredSize(new Dimension(UIUtil.scaleForGUI(1000), UIUtil.scaleForGUI(700)));

        navigationPanel.selectRoute(initialRoute);
    }

    private void selectRoute(CampaignOptionsRoute route) {
        Component page = getPage(route.getId());
        if (page != null) {
            contentHost.setContent(page, null, false);
        }
    }

    private Component getPage(String routeId) {
        return pageCache.computeIfAbsent(routeId, id -> pageFactories.get(id).get());
    }

    private Component createFontsPage() {
        CampaignOptionsLabel label = new CampaignOptionsLabel(RESOURCE_BUNDLE, "lblMedicalViewDialogHandwritingFont");

        FontComboBox fontCombo = new FontComboBox("comboMedicalViewDialogHandwritingFont");
        fontCombo.setToolTipText(getTextAt(RESOURCE_BUNDLE, "lblMedicalViewDialogHandwritingFont.toolTipText"));
        fontCombo.setSelectedItem(new FontDisplay(options.getMedicalViewDialogHandwritingFont()));
        pageSavers.add(() -> options.setMedicalViewDialogHandwritingFont(fontCombo.getFont().getFamily()));

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQFontsContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addRow(label, fontCombo);
        return buildMHQPage("MHQFontsPage", "lblMHQFontsSection.text", "lblMHQFontsSection.summary", panel);
    }

    private Component createCampaignSavePage() {
        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQCampaignSaveContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addCheckBoxGrid(2,
              createCheckBox("optionPreferGzippedOutput",
                    options::getPreferGzippedOutput, options::setPreferGzippedOutput),
              createCheckBox("optionWriteCustomsToXML", options::getWriteCustomsToXML, options::setWriteCustomsToXML),
              createCheckBox("optionWriteAllUnitsToXML", options::getWriteAllUnitsToXML, options::setWriteAllUnitsToXML),
              createCheckBox("optionSaveMothballState", options::getSaveMothballState, options::setSaveMothballState));
        return buildMHQPage("MHQCampaignSavePage", "lblMHQCampaignSaveSection.text",
              "lblMHQCampaignSaveSection.summary", panel);
    }

    private Component createAutosavePage() {
        ButtonGroup saveFrequencyGroup = new ButtonGroup();
        JRadioButton optionNoSave = createAutosaveFrequencyOption("optionNoSave", saveFrequencyGroup);
        optionNoSave.setSelected(options.getNoAutosaveValue());
        JRadioButton optionSaveDaily = createAutosaveFrequencyOption("optionSaveDaily", saveFrequencyGroup);
        optionSaveDaily.setSelected(options.getAutosaveDailyValue());
        JRadioButton optionSaveWeekly = createAutosaveFrequencyOption("optionSaveWeekly", saveFrequencyGroup);
        optionSaveWeekly.setSelected(options.getAutosaveWeeklyValue());
        JRadioButton optionSaveMonthly = createAutosaveFrequencyOption("optionSaveMonthly", saveFrequencyGroup);
        optionSaveMonthly.setSelected(options.getAutosaveMonthlyValue());
        JRadioButton optionSaveYearly = createAutosaveFrequencyOption("optionSaveYearly", saveFrequencyGroup);
        optionSaveYearly.setSelected(options.getAutosaveYearlyValue());

        CampaignOptionsCheckBox checkSaveBeforeScenarios = createCheckBox("checkSaveBeforeScenarios",
              options::getAutosaveBeforeScenariosValue, options::setAutosaveBeforeScenariosValue);
        CampaignOptionsCheckBox checkSaveBeforeContractEnd = createCheckBox("checkSaveBeforeMissionEnd",
              options::getAutosaveBeforeMissionEndValue, options::setAutosaveBeforeMissionEndValue);

        CampaignOptionsLabel labelSavedGamesCount = new CampaignOptionsLabel(RESOURCE_BUNDLE, "labelSavedGamesCount");
        CampaignOptionsSpinner spinnerSavedGamesCount =
              new CampaignOptionsSpinner(RESOURCE_BUNDLE, "labelSavedGamesCount", 1, 1, 10, 1);
        spinnerSavedGamesCount.setValue(options.getMaximumNumberOfAutoSavesValue());
        pageSavers.add(() -> {
            options.setNoAutosaveValue(optionNoSave.isSelected());
            options.setAutosaveDailyValue(optionSaveDaily.isSelected());
            options.setAutosaveWeeklyValue(optionSaveWeekly.isSelected());
            options.setAutosaveMonthlyValue(optionSaveMonthly.isSelected());
            options.setAutosaveYearlyValue(optionSaveYearly.isSelected());
            options.setMaximumNumberOfAutoSavesValue((Integer) spinnerSavedGamesCount.getValue());
        });

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQAutosaveContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addComponentGrid(1, optionNoSave, optionSaveDaily, optionSaveWeekly, optionSaveMonthly, optionSaveYearly);
        panel.addCheckBoxGrid(2, checkSaveBeforeScenarios, checkSaveBeforeContractEnd);
        panel.addRow(labelSavedGamesCount, spinnerSavedGamesCount);
        return buildMHQPage("MHQAutosavePage", "lblMHQAutosaveSection.text", "lblMHQAutosaveSection.summary", panel);
    }

    /**
     * Creates a save-frequency radio button whose label comes from {@code resourceName + ".text"} in the GUI bundle,
     * applies the shared campaign-options font scaling so it matches the check boxes on the page, and adds it to the
     * mutually-exclusive frequency group.
     */
    private JRadioButton createAutosaveFrequencyOption(String resourceName, ButtonGroup group) {
        JRadioButton radioButton = new JRadioButton(getTextAt(RESOURCE_BUNDLE, resourceName + ".text"));
        radioButton.setName(resourceName);
        setFontScaling(radioButton, false, 1);
        group.add(radioButton);
        return radioButton;
    }

    /**
     * Builds a standard single-section MekHQ option page: the shared MekHQ header image, no details/help panel, and
     * one collapsible section wrapping {@code content}.
     */
    private Component buildMHQPage(String pageName, String sectionTitleKey, String sectionSummaryKey,
          JComponent content) {
        return CampaignOptionsPagePanel.builder(pageName, pageName, "data/images/misc/MekHQ.png")
                     .resourceBundle(RESOURCE_BUNDLE)
                     .headerImageSize(HEADER_IMAGE_SIZE)
                     .tintHeaderImage(false)
                     .showDetailsPanel(false)
                     .section(sectionTitleKey, sectionSummaryKey, content)
                     .build();
    }

    /**
     * Creates a {@link CampaignOptionsCheckBox} whose text/tooltip come from {@code resourceName} in the GUI bundle,
     * loads its state from {@code getter}, and registers a saver that writes it back via {@code setter}.
     */
    private CampaignOptionsCheckBox createCheckBox(String resourceName, BooleanSupplier getter,
          Consumer<Boolean> setter) {
        CampaignOptionsCheckBox checkBox = new CampaignOptionsCheckBox(RESOURCE_BUNDLE, resourceName);
        checkBox.setSelected(getter.getAsBoolean());
        pageSavers.add(() -> setter.accept(checkBox.isSelected()));
        return checkBox;
    }

    private Component createRemindersNagsPage() {
        // Each entry is a {resourceName, nagIgnoreKey} pair. The resource name resolves the check box text/tooltip in
        // the GUI bundle; the ignore key is the MHQConstants key the state is stored under. A few resource names differ
        // from the stored key (e.g. AdminStrain vs HR_STRAIN, Astechs vs AS_TECHS), so they are kept explicit here.
        String[][] nagOptions = {
              { "optionUnmaintainedUnitsNag", MHQConstants.NAG_UNMAINTAINED_UNITS },
              { "optionPregnantCombatantNag", MHQConstants.NAG_PREGNANT_COMBATANT },
              { "optionPrisonersNag", MHQConstants.NAG_PRISONERS },
              { "optionAdminStrainNag", MHQConstants.NAG_HR_STRAIN },
              { "optionUntreatedPersonnelNag", MHQConstants.NAG_UNTREATED_PERSONNEL },
              { "optionNoCommanderNag", MHQConstants.NAG_NO_COMMANDER },
              { "optionContractEndedNag", MHQConstants.NAG_CONTRACT_ENDED },
              { "optionSingleDropNag", MHQConstants.NAG_SINGLE_DROP_SET_UP },
              { "optionInsufficientAstechsNag", MHQConstants.NAG_INSUFFICIENT_AS_TECHS },
              { "optionInsufficientAstechTimeNag", MHQConstants.NAG_INSUFFICIENT_AS_TECH_TIME },
              { "optionInsufficientMedicsNag", MHQConstants.NAG_INSUFFICIENT_MEDICS },
              { "optionShortDeploymentNag", MHQConstants.NAG_SHORT_DEPLOYMENT },
              { "optionCombatChallengeNag", MHQConstants.NAG_COMBAT_CHALLENGE },
              { "optionUnresolvedStratConContactsNag", MHQConstants.NAG_UNRESOLVED_STRAT_CON_CONTACTS },
              { "optionOutstandingScenariosNag", MHQConstants.NAG_OUTSTANDING_SCENARIOS },
              { "optionInvalidFactionNag", MHQConstants.NAG_INVALID_FACTION },
              { "optionUnableToAffordExpensesNag", MHQConstants.NAG_UNABLE_TO_AFFORD_EXPENSES },
              { "optionUnableToAffordRentNag", MHQConstants.NAG_UNABLE_TO_AFFORD_RENT },
              { "optionUnableToAffordLoanPaymentNag", MHQConstants.NAG_UNABLE_TO_AFFORD_LOAN_PAYMENT },
              { "optionUnableToAffordJumpNag", MHQConstants.NAG_UNABLE_TO_AFFORD_JUMP },
              { "optionUnableToAffordShoppingListNag", MHQConstants.NAG_UNABLE_TO_AFFORD_SHOPPING_LIST },
              { "optionSomeoneRandomlyDiedCombatNag", MHQConstants.NAG_SOMEONE_RANDOMLY_DIED_COMBAT },
              { "optionSomeoneRandomlyDiedTechNag", MHQConstants.NAG_SOMEONE_RANDOMLY_DIED_TECH },
              { "optionSomeoneRandomlyDiedOtherSupportNag", MHQConstants.NAG_SOMEONE_RANDOMLY_DIED_OTHER_SUPPORT },
              { "optionSomeoneRandomlyDiedCivilianNag", MHQConstants.NAG_SOMEONE_RANDOMLY_DIED_CIVILIAN },
              { "optionSomeoneRandomlyDiedCampFollowerNag", MHQConstants.NAG_SOMEONE_RANDOMLY_DIED_CAMP_FOLLOWER },
              { "optionSomeoneRandomlyDiedRetiredNag", MHQConstants.NAG_SOMEONE_RANDOMLY_DIED_RETIREE },
        };
        return buildMHQPage("MHQNagPage", "lblMHQNagSection.text", "lblMHQNagSection.summary",
              nagCheckBoxGrid("MHQNagContent", nagOptions));
    }

    private Component createRemindersConfirmationsPage() {
        String[][] confirmationOptions = {
              { "optionContractRentalConfirmation", MHQConstants.CONFIRMATION_CONTRACT_RENTAL },
              { "optionFactionStandingsUltimatumConfirmation", MHQConstants.CONFIRMATION_FACTION_STANDINGS_ULTIMATUM },
              { "optionBeginTransitConfirmation", MHQConstants.CONFIRMATION_BEGIN_TRANSIT },
              { "optionStratConBatchallBreachConfirmation", MHQConstants.CONFIRMATION_STRATCON_BATCHALL_BREACH },
              { "optionStratConDeployConfirmation", MHQConstants.CONFIRMATION_STRATCON_DEPLOY },
              { "optionResolveScenarioConfirmation", MHQConstants.CONFIRMATION_RESOLVE_SCENARIO },
              { "optionAbandonUnitsConfirmation", MHQConstants.CONFIRMATION_ABANDON_UNITS },
              { "optionAssignTechsConfirmation", MHQConstants.CONFIRMATION_ASSIGN_TECHS },
        };
        return buildMHQPage("MHQConfirmationPage", "lblMHQConfirmationSection.text",
              "lblMHQConfirmationSection.summary",
              nagCheckBoxGrid("MHQConfirmationContent", confirmationOptions));
    }

    /**
     * Builds a two-column check box grid from {@code entries}, where each entry is a {@code {resourceName, ignoreKey}}
     * pair. Each check box loads from {@link MHQOptions#getNagDialogIgnore} and registers a saver that writes it back
     * via {@link MHQOptions#setNagDialogIgnore}.
     */
    private CampaignOptionsFormPanel nagCheckBoxGrid(String name, String[][] entries) {
        List<JCheckBox> checkBoxes = new ArrayList<>();
        for (String[] entry : entries) {
            String ignoreKey = entry[1];
            CampaignOptionsCheckBox checkBox = new CampaignOptionsCheckBox(RESOURCE_BUNDLE, entry[0]);
            checkBox.setSelected(options.getNagDialogIgnore(ignoreKey));
            pageSavers.add(() -> options.setNagDialogIgnore(ignoreKey, checkBox.isSelected()));
            checkBoxes.add(checkBox);
        }
        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel(name, FORM_LABEL_WIDTH, FORM_CONTROL_WIDTH);
        panel.addCheckBoxGrid(2, checkBoxes.toArray(new JCheckBox[0]));
        return panel;
    }

    private Component createNewDayPoolsPage() {
        return buildMHQPage("MHQNewDayPoolPage", "lblMHQNewDayPoolSection.text", "lblMHQNewDayPoolSection.summary",
              createNewDayPoolSection());
    }

    private Component createNewDayAutomationPage() {
        return buildMHQPage("MHQNewDayAutomationPage", "lblMHQNewDayTasksSection.text",
              "lblMHQNewDayTasksSection.summary", createNewDayTasksSection());
    }

    private Component createNewDayTrainingPage() {
        return buildMHQPage("MHQNewDayTrainingPage", "lblMHQNewDayTrainingSection.text",
              "lblMHQNewDayTrainingSection.summary", createNewDayTrainingSection());
    }

    private Component createNewDayFormationPage() {
        return buildMHQPage("MHQNewDayFormationPage", "lblMHQNewDayFormationSection.text",
              "lblMHQNewDayFormationSection.summary", createNewDayFormationSection());
    }

    private JPanel createNewDayPoolSection() {
        List<JCheckBox> checkBoxes = new ArrayList<>();
        addPoolPair(checkBoxes, "chkNewDayAstechPoolFill", "chkNewDayAstechPoolNoRelease",
              options::getNewDayAsTechPoolFill, options::setNewDayAsTechPoolFill,
              options::getNewDayAsTechPoolNoRelease, options::setNewDayAsTechPoolNoRelease);
        addPoolPair(checkBoxes, "chkNewDayMedicPoolFill", "chkNewDayMedicPoolNoRelease",
              options::getNewDayMedicPoolFill, options::setNewDayMedicPoolFill,
              options::getNewDayMedicPoolNoRelease, options::setNewDayMedicPoolNoRelease);
        addPoolPair(checkBoxes, "chkNewDaySoldierPoolFill", "chkNewDaySoldierPoolNoRelease",
              options::getNewDaySoldierPoolFill, options::setNewDaySoldierPoolFill,
              options::getNewDaySoldierPoolNoRelease, options::setNewDaySoldierPoolNoRelease);
        addPoolPair(checkBoxes, "chkNewDayBattleArmorPoolFill", "chkNewDayBattleArmorPoolNoRelease",
              options::getNewDayBattleArmorPoolFill, options::setNewDayBattleArmorPoolFill,
              options::getNewDayBattleArmorPoolNoRelease, options::setNewDayBattleArmorPoolNoRelease);
        addPoolPair(checkBoxes, "chkNewDayVehicleCrewGroundPoolFill", "chkNewDayVehicleCrewGroundPoolNoRelease",
              options::getNewDayVehicleCrewGroundPoolFill, options::setNewDayVehicleCrewGroundPoolFill,
              options::getNewDayVehicleCrewGroundPoolNoRelease, options::setNewDayVehicleCrewGroundPoolNoRelease);
        addPoolPair(checkBoxes, "chkNewDayVehicleCrewVTOLPoolFill", "chkNewDayVehicleCrewVTOLPoolNoRelease",
              options::getNewDayVehicleCrewVTOLPoolFill, options::setNewDayVehicleCrewVTOLPoolFill,
              options::getNewDayVehicleCrewVTOLPoolNoRelease, options::setNewDayVehicleCrewVTOLPoolNoRelease);
        addPoolPair(checkBoxes, "chkNewDayVehicleCrewNavalPoolFill", "chkNewDayVehicleCrewNavalPoolNoRelease",
              options::getNewDayVehicleCrewNavalPoolFill, options::setNewDayVehicleCrewNavalPoolFill,
              options::getNewDayVehicleCrewNavalPoolNoRelease, options::setNewDayVehicleCrewNavalPoolNoRelease);
        addPoolPair(checkBoxes, "chkNewDayVesselPilotPoolFill", "chkNewDayVesselPilotPoolNoRelease",
              options::getNewDayVesselPilotPoolFill, options::setNewDayVesselPilotPoolFill,
              options::getNewDayVesselPilotPoolNoRelease, options::setNewDayVesselPilotPoolNoRelease);
        addPoolPair(checkBoxes, "chkNewDayVesselGunnerPoolFill", "chkNewDayVesselGunnerPoolNoRelease",
              options::getNewDayVesselGunnerPoolFill, options::setNewDayVesselGunnerPoolFill,
              options::getNewDayVesselGunnerPoolNoRelease, options::setNewDayVesselGunnerPoolNoRelease);
        addPoolPair(checkBoxes, "chkNewDayVesselCrewPoolFill", "chkNewDayVesselCrewPoolNoRelease",
              options::getNewDayVesselCrewPoolFill, options::setNewDayVesselCrewPoolFill,
              options::getNewDayVesselCrewPoolNoRelease, options::setNewDayVesselCrewPoolNoRelease);

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQNewDayPoolContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addCheckBoxGrid(2, checkBoxes.toArray(new JCheckBox[0]));
        return panel;
    }

    private JPanel createNewDayTasksSection() {
        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQNewDayTasksContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addCheckBoxGrid(2,
              createCheckBox("chkNewDayAutoLogistics", options::getNewDayAutoLogistics, options::setNewDayAutoLogistics),
              createCheckBox("chkNewDayMRMS", options::getNewDayMRMS, options::setNewDayMRMS),
              createCheckBox("chkNewDayOptimizeMedicalAssignments",
                    options::getNewDayOptimizeMedicalAssignments, options::setNewDayOptimizeMedicalAssignments),
              createCheckBox("chkNewDayAutomaticallyAssignUnmaintainedUnits",
                    options::getNewDayAutomaticallyAssignUnmaintainedUnits,
                    options::setNewDayAutomaticallyAssignUnmaintainedUnits),
              createCheckBox("chkSelfCorrectMaintenance",
                    options::getSelfCorrectMaintenance, options::setSelfCorrectMaintenance));
        return panel;
    }

    private JPanel createNewDayTrainingSection() {
        CampaignOptionsLabel labelQuickTrainTarget = new CampaignOptionsLabel(RESOURCE_BUNDLE, "lblQuickTrainTarget");
        CampaignOptionsSpinner spinnerQuickTrainTarget =
              new CampaignOptionsSpinner(RESOURCE_BUNDLE, "lblQuickTrainTarget", 5, 1, 10, 1);
        spinnerQuickTrainTarget.setValue(options.getQuickTrainTarget());
        pageSavers.add(() -> options.setQuickTrainTarget((int) spinnerQuickTrainTarget.getValue()));

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQNewDayTrainingContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addCheckBox(createCheckBox("chkNewMonthQuickTrain",
              options::getNewMonthQuickTrain, options::setNewMonthQuickTrain));
        panel.addRow(labelQuickTrainTarget, spinnerQuickTrainTarget);
        panel.addCheckBoxGrid(2,
              createCheckBox("chkLevelArtillery", options::getLevelArtillery, options::setLevelArtillery),
              createCheckBox("chkLevelScoutingSkills", options::getLevelScouting, options::setLevelScouting),
              createCheckBox("chkLevelEscapeSkills", options::getLevelEscape, options::setLevelEscape),
              createCheckBox("chkLevelLeadership", options::getLevelLeadership, options::setLevelLeadership),
              createCheckBox("chkLevelTraining", options::getLevelTraining, options::setLevelTraining),
              createCheckBox("chkLevelOtherCommandSkills", options::getLevelOtherCommand, options::setLevelOtherCommand));
        return panel;
    }

    private JPanel createNewDayFormationSection() {
        CampaignOptionsCheckBox formationIconStatus = createCheckBox("chkNewDayFormationIconOperationalStatus",
              options::getNewDayFormationIconOperationalStatus, options::setNewDayFormationIconOperationalStatus);

        CampaignOptionsLabel labelStyle =
              new CampaignOptionsLabel(RESOURCE_BUNDLE, "lblNewDayFormationIconOperationalStatusStyle");
        MMComboBox<FormationIconOperationalStatusStyle> comboStyle = new MMComboBox<>(
              "comboNewDayFormationIconOperationalStatusStyle", FormationIconOperationalStatusStyle.values());
        comboStyle.setSelectedItem(options.getNewDayFormationIconOperationalStatusStyle());
        comboStyle.setToolTipText(getTextAt(RESOURCE_BUNDLE,
              "lblNewDayFormationIconOperationalStatusStyle.toolTipText"));
        comboStyle.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index,
                  final boolean isSelected, final boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FormationIconOperationalStatusStyle style) {
                    list.setToolTipText(style.getToolTipText());
                }
                return this;
            }
        });
        pageSavers.add(() -> options.setNewDayFormationIconOperationalStatusStyle(
              Objects.requireNonNull(comboStyle.getSelectedItem())));

        // The style picker only matters when the status feature is on, so it tracks the check box's selected state.
        Runnable syncStyleEnabled = () -> {
            boolean enabled = formationIconStatus.isSelected();
            labelStyle.setEnabled(enabled);
            comboStyle.setEnabled(enabled);
        };
        formationIconStatus.addActionListener(evt -> syncStyleEnabled.run());
        syncStyleEnabled.run();

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQNewDayFormationContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addCheckBox(formationIconStatus);
        panel.addRow(labelStyle, comboStyle);
        return panel;
    }

    private Component createDisplayGeneralPage() {
        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQDisplayGeneralContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addRow(new CampaignOptionsLabel(RESOURCE_BUNDLE, "labelDisplayDateFormat"),
              dateFormatControl("labelDisplayDateFormat", options.getDisplayDateFormat(),
                    options::setDisplayDateFormat));
        panel.addRow(new CampaignOptionsLabel(RESOURCE_BUNDLE, "labelLongDisplayDateFormat"),
              dateFormatControl("labelLongDisplayDateFormat", options.getLongDisplayDateFormat(),
                    options::setLongDisplayDateFormat));

        JLabel guiScaleLabel = new JLabel(Messages.getString("CommonSettingsDialog.guiScale"));
        JSlider guiScale = new JSlider(7, 24);
        guiScale.setName("guiScale");
        guiScale.setMajorTickSpacing(3);
        Hashtable<Integer, JComponent> labelTable = new Hashtable<>();
        labelTable.put(7, new JLabel("70%"));
        labelTable.put(10, new JLabel("100%"));
        labelTable.put(16, new JLabel("160%"));
        labelTable.put(22, new JLabel("220%"));
        guiScale.setLabelTable(labelTable);
        guiScale.setPaintTicks(true);
        guiScale.setPaintLabels(true);
        guiScale.setValue((int) (GUIPreferences.getInstance().getGUIScale() * 10));
        guiScale.setToolTipText(Messages.getString("CommonSettingsDialog.guiScaleTT"));
        pageSavers.add(() -> {
            if (GUIPreferences.getInstance().getGUIScale() * 10 != guiScale.getValue()) {
                GUIPreferences.getInstance().setValue(GUIPreferences.GUI_SCALE, 0.1 * guiScale.getValue());
                MekHQ.updateGuiScaling();
            }
        });
        panel.addRow(guiScaleLabel, guiScale);

        panel.addCheckBoxGrid(2,
              createCheckBox("optionHideUnitFluff", options::getHideUnitFluff, options::setHideUnitFluff),
              createCheckBox("optionHistoricalDailyLog",
                    options::getHistoricalDailyLog, options::setHistoricalDailyLog),
              createCheckBox("chkCompanyGeneratorStartup",
                    options::getCompanyGeneratorStartup, options::setCompanyGeneratorStartup),
              createCheckBox("chkShowCompanyGenerator",
                    options::getShowCompanyGenerator, options::setShowCompanyGenerator),
              createCheckBox("chkShowUnitPicturesOnTOE",
                    options::getShowUnitPicturesOnTOE, options::setShowUnitPicturesOnTOE));

        return buildMHQPage("MHQDisplayGeneralPage", "lblMHQDisplayGeneralSection.text",
              "lblMHQDisplayGeneralSection.summary", panel);
    }

    /**
     * Builds the control for a date-format row: an editable pattern field plus a live example label showing today's
     * date in the entered pattern (or an error when it is invalid). The value is saved only when it is a valid pattern,
     * matching the original dialog.
     */
    private JComponent dateFormatControl(String labelKey, String initialValue, Consumer<String> setter) {
        JTextField field = new JTextField(initialValue, 20);
        field.setName("txt" + labelKey);
        JLabel example = new JLabel();
        Runnable updateExample = () -> example.setText(validateDateFormat(field.getText())
              ? LocalDate.now().format(DateTimeFormatter.ofPattern(field.getText())
                    .withLocale(MekHQ.getMHQOptions().getDateLocale()))
              : getTextAt(RESOURCE_BUNDLE, "invalidDateFormat.error"));
        field.addActionListener(evt -> updateExample.run());
        updateExample.run();
        pageSavers.add(() -> {
            if (validateDateFormat(field.getText())) {
                setter.accept(field.getText());
            }
        });

        JPanel control = new JPanel(new FlowLayout(FlowLayout.LEFT, UIUtil.scaleForGUI(6), 0));
        control.setOpaque(false);
        control.add(field);
        control.add(example);
        return control;
    }

    private boolean validateDateFormat(String format) {
        try {
            LocalDate.now().format(DateTimeFormatter.ofPattern(format)
                  .withLocale(MekHQ.getMHQOptions().getDateLocale()));
        } catch (Exception ignored) {
            return false;
        }
        return true;
    }

    private Component createDisplayInterstellarPage() {
        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQDisplayInterstellarContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        addInterstellarRadiusGroup(panel, "chkInterstellarMapShowJumpRadius",
              options::getInterstellarMapShowJumpRadius, options::setInterstellarMapShowJumpRadius,
              "lblInterstellarMapShowJumpRadiusMinimumZoom", 3d,
              options::getInterstellarMapShowJumpRadiusMinimumZoom, options::setInterstellarMapShowJumpRadiusMinimumZoom,
              "btnInterstellarMapJumpRadiusColour",
              options::getInterstellarMapJumpRadiusColour, options::setInterstellarMapJumpRadiusColour);
        addInterstellarRadiusGroup(panel, "chkInterstellarMapShowPlanetaryAcquisitionRadius",
              options::getInterstellarMapShowPlanetaryAcquisitionRadius,
              options::setInterstellarMapShowPlanetaryAcquisitionRadius,
              "lblInterstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom", 2d,
              options::getInterstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom,
              options::setInterstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom,
              "btnInterstellarMapPlanetaryAcquisitionRadiusColour",
              options::getInterstellarMapPlanetaryAcquisitionRadiusColour,
              options::setInterstellarMapPlanetaryAcquisitionRadiusColour);

        CampaignOptionsCheckBox contractCheckBox = createCheckBox("chkInterstellarMapShowContractSearchRadius",
              options::getInterstellarMapShowContractSearchRadius,
              options::setInterstellarMapShowContractSearchRadius);
        ColourSelectorButton contractColour = createColourButton("btnInterstellarMapContractSearchRadiusColour",
              options::getInterstellarMapContractSearchRadiusColour,
              options::setInterstellarMapContractSearchRadiusColour);
        Runnable syncContract = () -> contractColour.setEnabled(contractCheckBox.isSelected());
        contractCheckBox.addActionListener(evt -> syncContract.run());
        syncContract.run();
        panel.addCheckBox(contractCheckBox);
        panel.addComponentGrid(1, contractColour);

        return buildMHQPage("MHQDisplayInterstellarPage", "lblMHQDisplayInterstellarSection.text",
              "lblMHQDisplayInterstellarSection.summary", panel);
    }

    /**
     * Adds an interstellar-map radius group to {@code panel}: a master check box, a gated minimum-zoom spinner, and a
     * gated colour button. The spinner and colour are enabled only while the check box is selected, matching the
     * original dialog.
     */
    private void addInterstellarRadiusGroup(CampaignOptionsFormPanel panel, String checkBoxKey,
          BooleanSupplier checkGetter, Consumer<Boolean> checkSetter, String zoomLabelKey, double zoomDefault,
          Supplier<Double> zoomGetter, Consumer<Double> zoomSetter, String colourKey, Supplier<Color> colourGetter,
          Consumer<Color> colourSetter) {
        CampaignOptionsCheckBox checkBox = createCheckBox(checkBoxKey, checkGetter, checkSetter);
        CampaignOptionsLabel zoomLabel = new CampaignOptionsLabel(RESOURCE_BUNDLE, zoomLabelKey);
        CampaignOptionsSpinner zoomSpinner =
              new CampaignOptionsSpinner(RESOURCE_BUNDLE, zoomLabelKey, zoomDefault, 0d, 10d, 0.5);
        zoomSpinner.setValue(zoomGetter.get());
        pageSavers.add(() -> zoomSetter.accept((Double) zoomSpinner.getValue()));
        ColourSelectorButton colourButton = createColourButton(colourKey, colourGetter, colourSetter);

        Runnable sync = () -> {
            boolean enabled = checkBox.isSelected();
            zoomLabel.setEnabled(enabled);
            zoomSpinner.setEnabled(enabled);
            colourButton.setEnabled(enabled);
        };
        checkBox.addActionListener(evt -> sync.run());
        sync.run();

        panel.addCheckBox(checkBox);
        panel.addRow(zoomLabel, zoomSpinner);
        panel.addComponentGrid(1, colourButton);
    }

    private Component createDisplayPersonnelPage() {
        CampaignOptionsLabel filterStyleLabel =
              new CampaignOptionsLabel(RESOURCE_BUNDLE, "optionPersonnelFilterStyle");
        JComboBox<PersonnelFilterStyle> filterStyle = new JComboBox<>(PersonnelFilterStyle.values());
        filterStyle.setName("optionPersonnelFilterStyle");
        filterStyle.setSelectedItem(options.getPersonnelFilterStyle());
        filterStyle.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index,
                  final boolean isSelected, final boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PersonnelFilterStyle style) {
                    list.setToolTipText(style.getToolTipText());
                }
                return this;
            }
        });
        pageSavers.add(() -> options.setPersonnelFilterStyle(
              (PersonnelFilterStyle) Objects.requireNonNull(filterStyle.getSelectedItem())));

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQDisplayPersonnelContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addRow(filterStyleLabel, filterStyle);
        panel.addCheckBoxGrid(2,
              createCheckBox("optionPersonnelFilterOnPrimaryRole",
                    options::getPersonnelFilterOnPrimaryRole, options::setPersonnelFilterOnPrimaryRole),
              createCheckBox("chkUnifiedDailyReport", options::getUnifiedDailyReport, options::setUnifiedDailyReport),
              createCheckBox("chkEnableDailyReportAggregateTab",
                    options::isUseAggregateDailyReport, options::setAggregateDailyReport));

        return buildMHQPage("MHQDisplayPersonnelPage", "lblMHQDisplayPersonnelSection.text",
              "lblMHQDisplayPersonnelSection.summary", panel);
    }

    private Component createColoursUnitStatusPage() {
        List<JComponent> buttons = new ArrayList<>();
        addColourPair(buttons, "optionDeployedForeground",
              options::getDeployedForeground, options::setDeployedForeground,
              "optionDeployedBackground", options::getDeployedBackground, options::setDeployedBackground);
        addColourPair(buttons, "optionBelowContractMinimumForeground",
              options::getBelowContractMinimumForeground, options::setBelowContractMinimumForeground,
              "optionBelowContractMinimumBackground",
              options::getBelowContractMinimumBackground, options::setBelowContractMinimumBackground);
        addColourPair(buttons, "optionInTransitForeground",
              options::getInTransitForeground, options::setInTransitForeground,
              "optionInTransitBackground", options::getInTransitBackground, options::setInTransitBackground);
        addColourPair(buttons, "optionRefittingForeground",
              options::getRefittingForeground, options::setRefittingForeground,
              "optionRefittingBackground", options::getRefittingBackground, options::setRefittingBackground);
        addColourPair(buttons, "optionMothballingForeground",
              options::getMothballingForeground, options::setMothballingForeground,
              "optionMothballingBackground", options::getMothballingBackground, options::setMothballingBackground);
        addColourPair(buttons, "optionMothballedForeground",
              options::getMothballedForeground, options::setMothballedForeground,
              "optionMothballedBackground", options::getMothballedBackground, options::setMothballedBackground);
        addColourPair(buttons, "optionNotRepairableForeground",
              options::getNotRepairableForeground, options::setNotRepairableForeground,
              "optionNotRepairableBackground", options::getNotRepairableBackground, options::setNotRepairableBackground);
        addColourPair(buttons, "optionNonFunctionalForeground",
              options::getNonFunctionalForeground, options::setNonFunctionalForeground,
              "optionNonFunctionalBackground", options::getNonFunctionalBackground, options::setNonFunctionalBackground);
        addColourPair(buttons, "optionNeedsPartsFixedForeground",
              options::getNeedsPartsFixedForeground, options::setNeedsPartsFixedForeground,
              "optionNeedsPartsFixedBackground",
              options::getNeedsPartsFixedBackground, options::setNeedsPartsFixedBackground);
        addColourPair(buttons, "optionUnmaintainedForeground",
              options::getUnmaintainedForeground, options::setUnmaintainedForeground,
              "optionUnmaintainedBackground", options::getUnmaintainedBackground, options::setUnmaintainedBackground);
        addColourPair(buttons, "optionUncrewedForeground",
              options::getUncrewedForeground, options::setUncrewedForeground,
              "optionUncrewedBackground", options::getUncrewedBackground, options::setUncrewedBackground);
        addColourPair(buttons, "optionLoanOverdueForeground",
              options::getLoanOverdueForeground, options::setLoanOverdueForeground,
              "optionLoanOverdueBackground", options::getLoanOverdueBackground, options::setLoanOverdueBackground);
        addColourPair(buttons, "optionInjuredForeground",
              options::getInjuredForeground, options::setInjuredForeground,
              "optionInjuredBackground", options::getInjuredBackground, options::setInjuredBackground);
        addColourPair(buttons, "optionHealedInjuriesForeground",
              options::getHealedInjuriesForeground, options::setHealedInjuriesForeground,
              "optionHealedInjuriesBackground",
              options::getHealedInjuriesBackground, options::setHealedInjuriesBackground);
        addColourPair(buttons, "optionPregnantForeground",
              options::getPregnantForeground, options::setPregnantForeground,
              "optionPregnantBackground", options::getPregnantBackground, options::setPregnantBackground);
        addColourPair(buttons, "optionGoneForeground",
              options::getGoneForeground, options::setGoneForeground,
              "optionGoneBackground", options::getGoneBackground, options::setGoneBackground);
        addColourPair(buttons, "optionAbsentForeground",
              options::getAbsentForeground, options::setAbsentForeground,
              "optionAbsentBackground", options::getAbsentBackground, options::setAbsentBackground);
        addColourPair(buttons, "optionFatiguedForeground",
              options::getFatiguedForeground, options::setFatiguedForeground,
              "optionFatiguedBackground", options::getFatiguedBackground, options::setFatiguedBackground);
        buttons.add(createColourButton("optionStratConHexCoordForeground",
              options::getStratConHexCoordForeground, options::setStratConHexCoordForeground));

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQColoursUnitStatusContent");
        panel.addComponentGrid(2, buttons.toArray(new JComponent[0]));
        return buildMHQPage("MHQColoursUnitStatusPage", "lblMHQColoursUnitStatusSection.text",
              "lblMHQColoursUnitStatusSection.summary", panel);
    }

    private Component createColoursSkillFeedbackPage() {
        List<JComponent> buttons = new ArrayList<>();
        addColourPair(buttons, "optionFontColorNegative",
              options::getFontColorNegative, options::setFontColorNegative,
              "optionFontColorWarning", options::getFontColorWarning, options::setFontColorWarning);
        addColourPair(buttons, "optionFontColorPositive",
              options::getFontColorPositive, options::setFontColorPositive,
              "optionFontColorAmazing", options::getFontColorAmazing, options::setFontColorAmazing);
        addColourPair(buttons, "optionFontColorSkillUltraGreen",
              options::getFontColorSkillUltraGreen, options::setFontColorSkillUltraGreen,
              "optionFontColorSkillGreen", options::getFontColorSkillGreen, options::setFontColorSkillGreen);
        addColourPair(buttons, "optionFontColorSkillRegular",
              options::getFontColorSkillRegular, options::setFontColorSkillRegular,
              "optionFontColorSkillVeteran", options::getFontColorSkillVeteran, options::setFontColorSkillVeteran);
        buttons.add(createColourButton("optionFontColorSkillElite",
              options::getFontColorSkillElite, options::setFontColorSkillElite));

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQColoursSkillFeedbackContent");
        panel.addComponentGrid(2, buttons.toArray(new JComponent[0]));

        JTextArea disclaimer = new JTextArea(getTextAt(RESOURCE_BUNDLE, "coloursTab.disclaimer"));
        disclaimer.setName("txtColoursDisclaimer");
        disclaimer.setEditable(false);
        disclaimer.setLineWrap(true);
        disclaimer.setWrapStyleWord(true);
        disclaimer.setOpaque(false);
        panel.addFullWidthComponent(disclaimer);

        return buildMHQPage("MHQColoursSkillFeedbackPage", "lblMHQColoursSkillFeedbackSection.text",
              "lblMHQColoursSkillFeedbackSection.summary", panel);
    }

    /**
     * Appends a foreground/background colour button pair to {@code target} (foreground first). Each button loads its
     * colour and registers a saver via {@link #createColourButton}.
     */
    private void addColourPair(List<JComponent> target,
          String foregroundKey, Supplier<Color> foregroundGetter, Consumer<Color> foregroundSetter,
          String backgroundKey, Supplier<Color> backgroundGetter, Consumer<Color> backgroundSetter) {
        target.add(createColourButton(foregroundKey, foregroundGetter, foregroundSetter));
        target.add(createColourButton(backgroundKey, backgroundGetter, backgroundSetter));
    }

    /**
     * Creates a {@link ColourSelectorButton} whose text comes from {@code key} in the GUI bundle, loads its colour from
     * {@code getter}, and registers a saver that writes the chosen colour back via {@code setter}.
     */
    private ColourSelectorButton createColourButton(String key, Supplier<Color> getter, Consumer<Color> setter) {
        ColourSelectorButton button = new ColourSelectorButton(getTextAt(RESOURCE_BUNDLE, key + ".text"));
        button.setName("btn" + key);
        button.setColour(getter.get());
        pageSavers.add(() -> setter.accept(button.getColour()));
        return button;
    }

    private Component createAdvancedPage() {
        CampaignOptionsLabel userDirLabel = new CampaignOptionsLabel(RESOURCE_BUNDLE, "lblUserDir");
        JTextField userDirField = new JTextField(PreferenceManager.getClientPreferences().getUserDir(), 20);
        userDirField.setName("txtUserDir");
        userDirField.setToolTipText(getTextAt(RESOURCE_BUNDLE, "lblUserDir.toolTipText"));
        JButton userDirChooser = new JButton("...");
        userDirChooser.setName("btnUserDirChooser");
        userDirChooser.setToolTipText(getTextAt(RESOURCE_BUNDLE, "userDirChooser.title"));
        userDirChooser.addActionListener(evt -> CommonSettingsDialog.fileChooseUserDir(userDirField, frame));
        JButton userDirHelp = new JButton("Help");
        userDirHelp.setName("btnUserDirHelp");
        try {
            String helpTitle = Messages.getString("UserDirHelpDialog.title");
            URL helpFile = new File(MMConstants.USER_DIR_README_FILE).toURI().toURL();
            userDirHelp.addActionListener(evt -> new HelpDialog(helpTitle, helpFile, frame).setVisible(true));
        } catch (MalformedURLException ex) {
            LOGGER.error("Could not find the user data directory readme file at {}", MMConstants.USER_DIR_README_FILE);
        }
        pageSavers.add(() -> {
            PreferenceManager.getClientPreferences().setUserDir(userDirField.getText());
            PreferenceManager.getInstance().save();
        });
        JPanel userDirControls = new JPanel(new FlowLayout(FlowLayout.LEFT, UIUtil.scaleForGUI(6), 0));
        userDirControls.setOpaque(false);
        userDirControls.add(userDirField);
        userDirControls.add(userDirChooser);
        userDirControls.add(userDirHelp);

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQAdvancedContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addRow(userDirLabel, userDirControls);
        addAdvancedSpinner(panel, "lblStartGameDelay", 1000, 250, 2500, 25,
              options::getStartGameDelay, options::setStartGameDelay);
        addAdvancedSpinner(panel, "lblStartGameClientDelay", 50, 50, 2500, 25,
              options::getStartGameClientDelay, options::setStartGameClientDelay);
        addAdvancedSpinner(panel, "lblStartGameClientRetryCount", 1000, 100, 2500, 50,
              options::getStartGameClientRetryCount, options::setStartGameClientRetryCount);
        addAdvancedSpinner(panel, "lblStartGameBotClientDelay", 50, 50, 2500, 25,
              options::getStartGameBotClientDelay, options::setStartGameBotClientDelay);
        addAdvancedSpinner(panel, "lblStartGameBotClientRetryCount", 250, 100, 2500, 50,
              options::getStartGameBotClientRetryCount, options::setStartGameBotClientRetryCount);

        CampaignOptionsLabel companyGenerationLabel =
              new CampaignOptionsLabel(RESOURCE_BUNDLE, "lblDefaultCompanyGenerationMethod");
        MMComboBox<CompanyGenerationMethod> companyGeneration =
              new MMComboBox<>("comboDefaultCompanyGenerationMethod", CompanyGenerationMethod.values());
        companyGeneration.setSelectedItem(options.getDefaultCompanyGenerationMethod());
        companyGeneration.setToolTipText(getTextAt(RESOURCE_BUNDLE, "lblDefaultCompanyGenerationMethod.toolTipText"));
        companyGeneration.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index,
                  final boolean isSelected, final boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof CompanyGenerationMethod method) {
                    list.setToolTipText(method.getToolTipText());
                }
                return this;
            }
        });
        pageSavers.add(() -> options.setDefaultCompanyGenerationMethod(
              Objects.requireNonNull(companyGeneration.getSelectedItem())));
        panel.addRow(companyGenerationLabel, companyGeneration);

        return buildMHQPage("MHQAdvancedPage", "lblMHQAdvancedSection.text", "lblMHQAdvancedSection.summary", panel);
    }

    private void addAdvancedSpinner(CampaignOptionsFormPanel panel, String labelKey, int defaultValue,
          int minimum, int maximum, int step, IntSupplier getter, IntConsumer setter) {
        CampaignOptionsLabel label = new CampaignOptionsLabel(RESOURCE_BUNDLE, labelKey);
        CampaignOptionsSpinner spinner =
              new CampaignOptionsSpinner(RESOURCE_BUNDLE, labelKey, defaultValue, minimum, maximum, step);
        spinner.setValue(getter.getAsInt());
        pageSavers.add(() -> setter.accept((Integer) spinner.getValue()));
        panel.addRow(label, spinner);
    }

    private void addPoolPair(List<JCheckBox> target, String fillKey, String noReleaseKey,
          BooleanSupplier fillGetter, Consumer<Boolean> fillSetter,
          BooleanSupplier noReleaseGetter, Consumer<Boolean> noReleaseSetter) {
        CampaignOptionsCheckBox fill = createCheckBox(fillKey, fillGetter, fillSetter);
        CampaignOptionsCheckBox noRelease = createCheckBox(noReleaseKey, noReleaseGetter, noReleaseSetter);
        // The "no release" option only applies while the pool is being filled, so it follows the fill check box.
        noRelease.setEnabled(fill.isSelected());
        fill.addItemListener(evt -> noRelease.setEnabled(fill.isSelected()));
        target.add(fill);
        target.add(noRelease);
    }

    /**
     * Writes the edited options back to {@link MHQOptions}. Called by the hosting dialog when the user confirms. Each
     * page contributes its own saver when it is first built, so only pages the user has visited are written back.
     */
    public void save() {
        for (Runnable saver : pageSavers) {
            saver.run();
        }
    }
}
