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
import static megamek.client.ui.util.FontHandler.symbolIcon;
import static mekhq.gui.campaignOptions.CampaignOptionsUtilities.getImageDirectory;
import static mekhq.gui.campaignOptions.CampaignOptionsUtilities.getMetadata;
import static mekhq.gui.campaignOptions.CampaignOptionsUtilities.sendTipToDetailsPanel;
import static mekhq.gui.campaignOptions.CampaignOptionsUtilities.setSmallSizeVariant;
import static mekhq.utilities.MHQInternationalization.getTextAt;
import static mekhq.utilities.MHQInternationalization.isResourceKeyValid;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import jakarta.annotation.Nullable;
import megamek.client.ui.Messages;
import megamek.client.ui.buttons.ColourSelectorButton;
import megamek.client.ui.comboBoxes.FontComboBox;
import megamek.client.ui.comboBoxes.MMComboBox;
import megamek.client.ui.dialogs.buttonDialogs.CommonSettingsDialog;
import megamek.client.ui.dialogs.helpDialogs.HelpDialog;
import megamek.client.ui.displayWrappers.FontDisplay;
import megamek.client.ui.util.UIUtil;
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
    private static final int CONTENT_MARGIN = UIUtil.scaleForGUI(4);
    private static final int FORM_LABEL_WIDTH = CampaignOptionsFormPanel.DEFAULT_LABEL_WIDTH;
    private static final int FORM_CONTROL_WIDTH = CampaignOptionsFormPanel.DEFAULT_CONTROL_WIDTH;
    private static final MMLogger LOGGER = MMLogger.create(MHQOptionsPane.class);

    /**
     * Faction emblem shown in each page's header, hardcoded per page (chosen arbitrarily) so a page always shows the
     * same logo - mirroring how the Campaign Options pages each use a fixed faction logo instead of the MekHQ logo.
     */
    private static final Map<String, String> PAGE_FACTION_LOGOS = Map.ofEntries(
          Map.entry("MHQDisplayPage", "logo_federated_suns.png"),
          Map.entry("MHQColoursPage", "logo_taurian_concordat.png"),
          Map.entry("MHQFontsPage", "logo_rasalhague_dominion.png"),
          Map.entry("MHQSaveOptionsPage", "logo_clan_ghost_bear.png"),
          Map.entry("MHQNewDayPage", "logo_outworld_alliance.png"),
          Map.entry("MHQRemindersPage", "logo_rim_worlds_republic.png"),
          Map.entry("MHQAdvancedPage", "logo_republic_of_the_sphere.png"));

    private final JFrame frame;
    private final MHQOptions options;
    private final MHQOptionsModel model;
    private final List<CampaignOptionsRoute> routes = new ArrayList<>();
    private final Map<String, Supplier<Component>> pageFactories = new HashMap<>();
    private final Map<String, Component> pageCache = new HashMap<>();

    private CampaignOptionsContentHost contentHost;
    private CampaignOptionsNavigationPanel navigationPanel;

    // region Page controls
    // These are populated as each page is lazily built and are read back into the model by the writeXToModel methods
    // when the dialog is confirmed. A page that was never opened leaves its controls null, so its writeXToModel is a
    // no-op and the model keeps the original values it was constructed with.

    // Fonts
    private FontComboBox comboMedicalViewDialogHandwritingFont;

    // Display - General
    private JTextField fieldDisplayDateFormat;
    private JTextField fieldLongDisplayDateFormat;
    private JSlider guiScaleSlider;
    private CampaignOptionsCheckBox chkHideUnitFluff;
    private CampaignOptionsCheckBox chkHistoricalDailyLog;
    private CampaignOptionsCheckBox chkCompanyGeneratorStartup;
    private CampaignOptionsCheckBox chkShowCompanyGenerator;
    private CampaignOptionsCheckBox chkShowUnitPicturesOnTOE;

    // Display - Interstellar Map
    private CampaignOptionsCheckBox chkInterstellarMapShowJumpRadius;
    private CampaignOptionsSpinner spinnerInterstellarMapShowJumpRadiusMinimumZoom;
    private ColourSelectorButton btnInterstellarMapJumpRadiusColour;
    private CampaignOptionsCheckBox chkInterstellarMapShowPlanetaryAcquisitionRadius;
    private CampaignOptionsSpinner spinnerInterstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom;
    private ColourSelectorButton btnInterstellarMapPlanetaryAcquisitionRadiusColour;
    private CampaignOptionsCheckBox chkInterstellarMapShowContractSearchRadius;
    private ColourSelectorButton btnInterstellarMapContractSearchRadiusColour;

    // Display - Personnel List
    private JComboBox<PersonnelFilterStyle> comboPersonnelFilterStyle;
    private CampaignOptionsCheckBox chkPersonnelFilterOnPrimaryRole;
    private CampaignOptionsCheckBox chkUnifiedDailyReport;
    private CampaignOptionsCheckBox chkEnableDailyReportAggregateTab;

    // Colours (keyed by resource name, matching MHQOptionsModel.statusColours)
    private final Map<String, ColourSelectorButton> colourButtons = new HashMap<>();

    // Autosave
    private JRadioButton optionNoSave;
    private JRadioButton optionSaveDaily;
    private JRadioButton optionSaveWeekly;
    private JRadioButton optionSaveMonthly;
    private JRadioButton optionSaveYearly;
    private CampaignOptionsCheckBox chkSaveBeforeScenarios;
    private CampaignOptionsCheckBox chkSaveBeforeMissionEnd;
    private CampaignOptionsSpinner spinnerSavedGamesCount;

    // New Day - Personnel Pools (keyed by resource name, matching MHQOptionsModel.newDayPools)
    private final Map<String, CampaignOptionsCheckBox> poolCheckBoxes = new HashMap<>();

    // New Day - Automation
    private CampaignOptionsCheckBox chkNewDayAutoLogistics;
    private CampaignOptionsCheckBox chkNewDayMRMS;
    private CampaignOptionsCheckBox chkNewDayOptimizeMedicalAssignments;
    private CampaignOptionsCheckBox chkNewDayAutomaticallyAssignUnmaintainedUnits;
    private CampaignOptionsCheckBox chkSelfCorrectMaintenance;

    // New Day - Training
    private CampaignOptionsCheckBox chkNewMonthQuickTrain;
    private CampaignOptionsSpinner spinnerQuickTrainTarget;
    private CampaignOptionsCheckBox chkLevelArtillery;
    private CampaignOptionsCheckBox chkLevelScoutingSkills;
    private CampaignOptionsCheckBox chkLevelEscapeSkills;
    private CampaignOptionsCheckBox chkLevelLeadership;
    private CampaignOptionsCheckBox chkLevelTraining;
    private CampaignOptionsCheckBox chkLevelOtherCommandSkills;

    // New Day - Formation Icons
    private CampaignOptionsCheckBox chkNewDayFormationIconOperationalStatus;
    private MMComboBox<FormationIconOperationalStatusStyle> comboNewDayFormationIconOperationalStatusStyle;

    // Campaign Save
    private CampaignOptionsCheckBox chkPreferGzippedOutput;
    private CampaignOptionsCheckBox chkWriteCustomsToXML;
    private CampaignOptionsCheckBox chkWriteAllUnitsToXML;
    private CampaignOptionsCheckBox chkSaveMothballState;

    // Reminders & Confirmations (keyed by MHQConstants nag/confirmation key, matching MHQOptionsModel.nagIgnores)
    private final Map<String, CampaignOptionsCheckBox> nagCheckBoxes = new HashMap<>();

    // Advanced
    private JTextField userDirField;
    private CampaignOptionsSpinner spinnerStartGameDelay;
    private CampaignOptionsSpinner spinnerStartGameClientDelay;
    private CampaignOptionsSpinner spinnerStartGameClientRetryCount;
    private CampaignOptionsSpinner spinnerStartGameBotClientDelay;
    private CampaignOptionsSpinner spinnerStartGameBotClientRetryCount;
    private MMComboBox<CompanyGenerationMethod> comboDefaultCompanyGenerationMethod;
    // endregion Page controls

    public MHQOptionsPane(JFrame frame) {
        super(new BorderLayout());
        setName("mhqOptionsPane");
        this.frame = frame;
        options = MekHQ.getMHQOptions();
        model = new MHQOptionsModel(options);
        registerRoutes();
        initialize();
    }

    private void registerRoutes() {
        registerRoute("display", this::createDisplayPage, "displayPage");
        registerRoute("colours", this::createColoursPage, "coloursPage");
        registerRoute("fonts", this::createFontsPage, "fontsPage");
        registerRoute("saveOptions", this::createSaveOptionsPage, "saveOptionsPage");
        registerRoute("newDay", this::createNewDayPage, "newDayPage");
        registerRoute("reminders", this::createRemindersPage, "remindersPage");
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

        // Match the Campaign Options pane: a small content margin (0 at the bottom, since the footer adds its own top
        // padding) and no fixed preferred size, so the dialog packs to its content the same way Campaign Options does.
        setBorder(BorderFactory.createEmptyBorder(CONTENT_MARGIN, CONTENT_MARGIN, 0, CONTENT_MARGIN));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigationPanel, contentHost);
        splitPane.setName("mhqOptionsSplitPane");
        splitPane.setResizeWeight(0.0);
        splitPane.setDividerLocation(UIUtil.scaleForGUI(CampaignOptionsNavigationPanel.NAVIGATION_WIDTH));
        add(splitPane, BorderLayout.CENTER);

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

        comboMedicalViewDialogHandwritingFont = new FontComboBox("comboMedicalViewDialogHandwritingFont");
        comboMedicalViewDialogHandwritingFont.setToolTipText(
              getTextAt(RESOURCE_BUNDLE, "lblMedicalViewDialogHandwritingFont.toolTipText"));
        comboMedicalViewDialogHandwritingFont.setSelectedItem(new FontDisplay(model.medicalViewDialogHandwritingFont));

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQFontsContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addRow(label, comboMedicalViewDialogHandwritingFont);
        return buildMHQPage("MHQFontsPage", "lblMHQFontsSection.text", "lblMHQFontsSection.summary", panel);
    }

    private void writeFontsToModel() {
        if (comboMedicalViewDialogHandwritingFont == null) {
            return;
        }
        model.medicalViewDialogHandwritingFont = comboMedicalViewDialogHandwritingFont.getFont().getFamily();
    }

    private Component createSaveOptionsPage() {
        JComponent autosaveContent = createAutosaveSection();
        JComponent campaignSaveContent = createCampaignSaveSection();
        // Non-short-circuit | so tips are registered for both section bodies before the page is built.
        boolean hasTooltips = registerDetailsTips(autosaveContent) | registerDetailsTips(campaignSaveContent);
        return pageBuilder("MHQSaveOptionsPage", hasTooltips)
                     .section("lblMHQAutosaveSection.text", "lblMHQAutosaveSection.summary", autosaveContent)
                     .section("lblMHQCampaignSaveSection.text", "lblMHQCampaignSaveSection.summary",
                           campaignSaveContent)
                     .build();
    }

    private JPanel createCampaignSaveSection() {
        chkPreferGzippedOutput = checkBox("optionPreferGzippedOutput", model.preferGzippedOutput);
        chkWriteCustomsToXML = checkBox("optionWriteCustomsToXML", model.writeCustomsToXML);
        chkWriteAllUnitsToXML = checkBox("optionWriteAllUnitsToXML", model.writeAllUnitsToXML,
              getMetadata(null, CampaignOptionFlag.IMPORTANT));
        chkSaveMothballState = checkBox("optionSaveMothballState", model.saveMothballState);

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQCampaignSaveContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addCheckBoxGrid(2, chkPreferGzippedOutput, chkWriteCustomsToXML, chkWriteAllUnitsToXML,
              chkSaveMothballState);
        return panel;
    }

    private void writeCampaignSaveToModel() {
        if (chkPreferGzippedOutput == null) {
            return;
        }
        model.preferGzippedOutput = chkPreferGzippedOutput.isSelected();
        model.writeCustomsToXML = chkWriteCustomsToXML.isSelected();
        model.writeAllUnitsToXML = chkWriteAllUnitsToXML.isSelected();
        model.saveMothballState = chkSaveMothballState.isSelected();
    }

    private JPanel createAutosaveSection() {
        ButtonGroup saveFrequencyGroup = new ButtonGroup();
        optionNoSave = createAutosaveFrequencyOption("optionNoSave", saveFrequencyGroup);
        optionNoSave.setSelected(model.noAutosave);
        optionSaveDaily = createAutosaveFrequencyOption("optionSaveDaily", saveFrequencyGroup);
        optionSaveDaily.setSelected(model.autosaveDaily);
        optionSaveWeekly = createAutosaveFrequencyOption("optionSaveWeekly", saveFrequencyGroup);
        optionSaveWeekly.setSelected(model.autosaveWeekly);
        optionSaveMonthly = createAutosaveFrequencyOption("optionSaveMonthly", saveFrequencyGroup);
        optionSaveMonthly.setSelected(model.autosaveMonthly);
        optionSaveYearly = createAutosaveFrequencyOption("optionSaveYearly", saveFrequencyGroup);
        optionSaveYearly.setSelected(model.autosaveYearly);

        chkSaveBeforeScenarios = checkBox("checkSaveBeforeScenarios", model.autosaveBeforeScenarios);
        chkSaveBeforeMissionEnd = checkBox("checkSaveBeforeMissionEnd", model.autosaveBeforeMissionEnd);

        CampaignOptionsLabel labelSavedGamesCount = new CampaignOptionsLabel(RESOURCE_BUNDLE, "labelSavedGamesCount");
        spinnerSavedGamesCount =
              new CampaignOptionsSpinner(RESOURCE_BUNDLE, "labelSavedGamesCount", 1, 1, 10, 1);
        spinnerSavedGamesCount.setValue(model.maximumNumberOfAutoSaves);

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQAutosaveContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addComponentGrid(1, optionNoSave, optionSaveDaily, optionSaveWeekly, optionSaveMonthly, optionSaveYearly);
        panel.addCheckBoxGrid(2, chkSaveBeforeScenarios, chkSaveBeforeMissionEnd);
        panel.addRow(labelSavedGamesCount, spinnerSavedGamesCount);
        return panel;
    }

    private void writeAutosaveToModel() {
        if (optionNoSave == null) {
            return;
        }
        model.noAutosave = optionNoSave.isSelected();
        model.autosaveDaily = optionSaveDaily.isSelected();
        model.autosaveWeekly = optionSaveWeekly.isSelected();
        model.autosaveMonthly = optionSaveMonthly.isSelected();
        model.autosaveYearly = optionSaveYearly.isSelected();
        model.autosaveBeforeScenarios = chkSaveBeforeScenarios.isSelected();
        model.autosaveBeforeMissionEnd = chkSaveBeforeMissionEnd.isSelected();
        model.maximumNumberOfAutoSaves = (Integer) spinnerSavedGamesCount.getValue();
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
     * Builds a standard single-section MekHQ option page: a per-page faction emblem header (matching the Campaign
     * Options pages), a shared "Option Details" help box for its tip-bearing controls, and one collapsible section
     * wrapping {@code content}. The lone section starts expanded, since collapsing the only section on a page would
     * hide everything for no benefit.
     */
    private Component buildMHQPage(String pageName, String sectionTitleKey, String sectionSummaryKey,
          JComponent content) {
        return buildMHQPage(pageName, null, sectionTitleKey, sectionSummaryKey, content);
    }

    /**
     * Builds a single-section MekHQ option page as {@link #buildMHQPage(String, String, String, JComponent)} does, but
     * with an intro paragraph (resolved from {@code introKey}) shown above the section - used for pages that need a
     * note at the top, such as the colours disclaimer.
     */
    private Component buildMHQPage(String pageName, @Nullable String introKey, String sectionTitleKey,
          String sectionSummaryKey, JComponent content) {
        // Route each control's tooltip to the shared "Option Details" box (like Campaign Options) and drop the floating
        // tooltip. Only pages that actually have tip-bearing controls get the box, so tooltip-free pages (the colour
        // grids) are not saddled with an empty details area.
        CampaignOptionsPagePanel.Builder builder = pageBuilder(pageName, registerDetailsTips(content))
                                                         .sectionsExpandedByDefault(true);
        if (introKey != null) {
            builder.intro(introKey);
        }
        return builder.section(sectionTitleKey, sectionSummaryKey, content).build();
    }

    /**
     * Creates the shared page builder used by every MekHQ option page: the per-page faction emblem header (matching
     * Campaign Options), the GUI resource bundle, whether to show the "Option Details" help box, and sections collapsed
     * by default. Multi-section pages keep that collapsed default; the single-section {@link #buildMHQPage} wrapper
     * re-expands its lone section. Callers add their section(s) and call {@code build()}.
     */
    private CampaignOptionsPagePanel.Builder pageBuilder(String pageName, boolean showDetailsPanel) {
        return CampaignOptionsPagePanel.builder(pageName, pageName, getImageDirectory() + factionLogo(pageName))
                     .resourceBundle(RESOURCE_BUNDLE)
                     .showDetailsPanel(showDetailsPanel)
                     .sectionsExpandedByDefault(false);
    }

    /**
     * Recursively wires every tip-bearing control under {@code component} to the shared "Option Details" help box: on
     * mouse-over the control sends its tooltip text there, and its floating Swing tooltip is removed so the help shows
     * only in the box, mirroring the Campaign Options behaviour. Buttons are skipped so their action tooltips (such as
     * the user-directory chooser and help buttons) keep working as ordinary tooltips.
     *
     * @param component the subtree to process
     *
     * @return {@code true} if at least one control was wired, so the caller shows the details box only when the page
     *       actually has tips
     */
    private static boolean registerDetailsTips(Component component) {
        boolean anyTip = false;
        if (component instanceof JComponent jComponent && !(component instanceof JButton)) {
            String tooltip = jComponent.getToolTipText();
            if (tooltip != null && !tooltip.isBlank()) {
                String detailsText = detailsTextFor(jComponent, tooltip);
                jComponent.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent event) {
                        sendTipToDetailsPanel(detailsText);
                    }
                });
                jComponent.setToolTipText(null);
                anyTip = true;
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                anyTip |= registerDetailsTips(child);
            }
        }
        return anyTip;
    }

    /**
     * Resolves the text a control should show in the "Option Details" box. The shared checkbox, spinner, and label
     * components word-wrap their Swing tooltip at a fixed character count; for those the raw bundle text is re-resolved
     * from the control's name so the box can soft-wrap it to its own width (and so intentional line breaks are kept),
     * matching Campaign Options. Other controls (combo boxes, text fields) already carry raw tooltip text, so their
     * tooltip is used as-is.
     *
     * @param component the tip-bearing control
     * @param tooltip   the control's current Swing tooltip, used as-is when the raw text cannot be re-resolved
     *
     * @return the help text to show in the details box
     */
    private static String detailsTextFor(JComponent component, String tooltip) {
        boolean wordWrapsTooltip = component instanceof CampaignOptionsCheckBox
              || component instanceof CampaignOptionsSpinner
              || component instanceof CampaignOptionsLabel;
        if (wordWrapsTooltip) {
            // These components set their Swing name to a 3-character prefix ("chk"/"spn"/"lbl") plus the resource base
            // name, so stripping the prefix recovers the base used for the ".tooltip"/".toolTipText" keys.
            String name = component.getName();
            if (name != null && name.length() > 3) {
                String base = name.substring(3);
                String raw = getTextAt(RESOURCE_BUNDLE, base + ".tooltip");
                if (!isResourceKeyValid(raw)) {
                    raw = getTextAt(RESOURCE_BUNDLE, base + ".toolTipText");
                }
                if (isResourceKeyValid(raw)) {
                    return raw;
                }
            }
        }
        return tooltip;
    }

    /**
     * Returns the faction emblem file name hardcoded for {@code pageName} (see {@link #PAGE_FACTION_LOGOS}), falling
     * back to a default so a page without an explicit mapping still shows a faction logo rather than failing.
     */
    private static String factionLogo(String pageName) {
        return PAGE_FACTION_LOGOS.getOrDefault(pageName, "logo_star_league.png");
    }

    /**
     * Creates a {@link CampaignOptionsCheckBox} whose text/tooltip come from {@code resourceName} in the GUI bundle and
     * sets its initial state to {@code selected}. The value is read back into the model by the owning page's
     * {@code writeXToModel} method.
     */
    private CampaignOptionsCheckBox checkBox(String resourceName, boolean selected) {
        return checkBox(resourceName, selected, null);
    }

    /**
     * Creates a {@link CampaignOptionsCheckBox} as {@link #checkBox(String, boolean)} does, but with badge metadata
     * (such as the "important information" flag) shown after the text.
     */
    private CampaignOptionsCheckBox checkBox(String resourceName, boolean selected,
          @Nullable CampaignOptionsMetadata metadata) {
        CampaignOptionsCheckBox checkBox = new CampaignOptionsCheckBox(RESOURCE_BUNDLE, resourceName, metadata);
        checkBox.setSelected(selected);
        return checkBox;
    }

    /**
     * Creates a {@link ColourSelectorButton} whose text comes from {@code key} in the GUI bundle and whose initial
     * colour is {@code colour}. The chosen colour is read back into the model by the owning page's
     * {@code writeXToModel} method.
     */
    private ColourSelectorButton colourButton(String key, Color colour) {
        ColourSelectorButton button = new ColourSelectorButton(getTextAt(RESOURCE_BUNDLE, key + ".text"));
        button.setName("btn" + key);
        button.setColour(colour);
        return button;
    }

    private Component createRemindersPage() {
        JComponent nagsContent = createRemindersNagsSection();
        JComponent confirmationsContent = createRemindersConfirmationsSection();
        // Non-short-circuit | so tips are registered for both section bodies before the page is built.
        boolean hasTooltips = registerDetailsTips(nagsContent) | registerDetailsTips(confirmationsContent);
        return pageBuilder("MHQRemindersPage", hasTooltips)
                     .section("lblMHQNagSection.text", "lblMHQNagSection.summary", nagsContent)
                     .section("lblMHQConfirmationSection.text", "lblMHQConfirmationSection.summary",
                           confirmationsContent)
                     .build();
    }

    private JPanel createRemindersNagsSection() {
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
        return nagCheckBoxGrid("MHQNagContent", nagOptions);
    }

    private JPanel createRemindersConfirmationsSection() {
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
        return nagCheckBoxGrid("MHQConfirmationContent", confirmationOptions);
    }

    /**
     * Builds a two-column check box grid from {@code entries}, where each entry is a {@code {resourceName, ignoreKey}}
     * pair. Each check box loads its state from {@link MHQOptionsModel#nagIgnores} and is registered in
     * {@link #nagCheckBoxes} under its ignore key so {@link #writeNagsToModel()} can read it back.
     */
    private CampaignOptionsFormPanel nagCheckBoxGrid(String name, String[][] entries) {
        List<JCheckBox> checkBoxes = new ArrayList<>();
        for (String[] entry : entries) {
            String ignoreKey = entry[1];
            CampaignOptionsCheckBox checkBox = new CampaignOptionsCheckBox(RESOURCE_BUNDLE, entry[0]);
            checkBox.setSelected(Boolean.TRUE.equals(model.nagIgnores.get(ignoreKey)));
            nagCheckBoxes.put(ignoreKey, checkBox);
            checkBoxes.add(checkBox);
        }
        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel(name, FORM_LABEL_WIDTH, FORM_CONTROL_WIDTH);
        panel.addCheckBoxGrid(2, checkBoxes.toArray(new JCheckBox[0]));
        return panel;
    }

    private void writeNagsToModel() {
        nagCheckBoxes.forEach((key, checkBox) -> model.nagIgnores.put(key, checkBox.isSelected()));
    }

    private Component createNewDayPage() {
        JComponent poolContent = createNewDayPoolSection();
        JComponent tasksContent = createNewDayTasksSection();
        JComponent trainingContent = createNewDayTrainingSection();
        JComponent formationContent = createNewDayFormationSection();
        // Non-short-circuit | so tips are registered for every section body before the page is built.
        boolean hasTooltips = registerDetailsTips(poolContent)
              | registerDetailsTips(tasksContent)
              | registerDetailsTips(trainingContent)
              | registerDetailsTips(formationContent);
        return pageBuilder("MHQNewDayPage", hasTooltips)
                     .section("lblMHQNewDayPoolSection.text", "lblMHQNewDayPoolSection.summary", poolContent)
                     .section("lblMHQNewDayTasksSection.text", "lblMHQNewDayTasksSection.summary", tasksContent)
                     .section("lblMHQNewDayTrainingSection.text", "lblMHQNewDayTrainingSection.summary",
                           trainingContent)
                     .section("lblMHQNewDayFormationSection.text", "lblMHQNewDayFormationSection.summary",
                           formationContent)
                     .build();
    }

    private JPanel createNewDayPoolSection() {
        List<JCheckBox> checkBoxes = new ArrayList<>();
        addPoolPair(checkBoxes, "chkNewDayAstechPoolFill", "chkNewDayAstechPoolNoRelease");
        addPoolPair(checkBoxes, "chkNewDayMedicPoolFill", "chkNewDayMedicPoolNoRelease");
        addPoolPair(checkBoxes, "chkNewDaySoldierPoolFill", "chkNewDaySoldierPoolNoRelease");
        addPoolPair(checkBoxes, "chkNewDayBattleArmorPoolFill", "chkNewDayBattleArmorPoolNoRelease");
        addPoolPair(checkBoxes, "chkNewDayVehicleCrewGroundPoolFill", "chkNewDayVehicleCrewGroundPoolNoRelease");
        addPoolPair(checkBoxes, "chkNewDayVehicleCrewVTOLPoolFill", "chkNewDayVehicleCrewVTOLPoolNoRelease");
        addPoolPair(checkBoxes, "chkNewDayVehicleCrewNavalPoolFill", "chkNewDayVehicleCrewNavalPoolNoRelease");
        addPoolPair(checkBoxes, "chkNewDayVesselPilotPoolFill", "chkNewDayVesselPilotPoolNoRelease");
        addPoolPair(checkBoxes, "chkNewDayVesselGunnerPoolFill", "chkNewDayVesselGunnerPoolNoRelease");
        addPoolPair(checkBoxes, "chkNewDayVesselCrewPoolFill", "chkNewDayVesselCrewPoolNoRelease");

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQNewDayPoolContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addCheckBoxGrid(2, checkBoxes.toArray(new JCheckBox[0]));
        return panel;
    }

    private void writePoolsToModel() {
        poolCheckBoxes.forEach((key, checkBox) -> model.newDayPools.put(key, checkBox.isSelected()));
    }

    private JPanel createNewDayTasksSection() {
        chkNewDayAutoLogistics = checkBox("chkNewDayAutoLogistics", model.newDayAutoLogistics);
        chkNewDayMRMS = checkBox("chkNewDayMRMS", model.newDayMRMS);
        chkNewDayOptimizeMedicalAssignments =
              checkBox("chkNewDayOptimizeMedicalAssignments", model.newDayOptimizeMedicalAssignments);
        chkNewDayAutomaticallyAssignUnmaintainedUnits =
              checkBox("chkNewDayAutomaticallyAssignUnmaintainedUnits",
                    model.newDayAutomaticallyAssignUnmaintainedUnits);
        chkSelfCorrectMaintenance = checkBox("chkSelfCorrectMaintenance", model.selfCorrectMaintenance);

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQNewDayTasksContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addCheckBoxGrid(2, chkNewDayAutoLogistics, chkNewDayMRMS, chkNewDayOptimizeMedicalAssignments,
              chkNewDayAutomaticallyAssignUnmaintainedUnits, chkSelfCorrectMaintenance);
        return panel;
    }

    private void writeNewDayTasksToModel() {
        if (chkNewDayAutoLogistics == null) {
            return;
        }
        model.newDayAutoLogistics = chkNewDayAutoLogistics.isSelected();
        model.newDayMRMS = chkNewDayMRMS.isSelected();
        model.newDayOptimizeMedicalAssignments = chkNewDayOptimizeMedicalAssignments.isSelected();
        model.newDayAutomaticallyAssignUnmaintainedUnits = chkNewDayAutomaticallyAssignUnmaintainedUnits.isSelected();
        model.selfCorrectMaintenance = chkSelfCorrectMaintenance.isSelected();
    }

    private JPanel createNewDayTrainingSection() {
        chkNewMonthQuickTrain = checkBox("chkNewMonthQuickTrain", model.newMonthQuickTrain);

        CampaignOptionsLabel labelQuickTrainTarget = new CampaignOptionsLabel(RESOURCE_BUNDLE, "lblQuickTrainTarget");
        spinnerQuickTrainTarget =
              new CampaignOptionsSpinner(RESOURCE_BUNDLE, "lblQuickTrainTarget", 5, 1, 10, 1);
        spinnerQuickTrainTarget.setValue(model.quickTrainTarget);

        chkLevelArtillery = checkBox("chkLevelArtillery", model.levelArtillery);
        chkLevelScoutingSkills = checkBox("chkLevelScoutingSkills", model.levelScouting);
        chkLevelEscapeSkills = checkBox("chkLevelEscapeSkills", model.levelEscape);
        chkLevelLeadership = checkBox("chkLevelLeadership", model.levelLeadership);
        chkLevelTraining = checkBox("chkLevelTraining", model.levelTraining);
        chkLevelOtherCommandSkills = checkBox("chkLevelOtherCommandSkills", model.levelOtherCommand);

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQNewDayTrainingContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addCheckBox(chkNewMonthQuickTrain);
        panel.addRow(labelQuickTrainTarget, spinnerQuickTrainTarget);
        panel.addCheckBoxGrid(2, chkLevelArtillery, chkLevelScoutingSkills, chkLevelEscapeSkills, chkLevelLeadership,
              chkLevelTraining, chkLevelOtherCommandSkills);
        return panel;
    }

    private void writeNewDayTrainingToModel() {
        if (chkNewMonthQuickTrain == null) {
            return;
        }
        model.newMonthQuickTrain = chkNewMonthQuickTrain.isSelected();
        model.quickTrainTarget = (int) spinnerQuickTrainTarget.getValue();
        model.levelArtillery = chkLevelArtillery.isSelected();
        model.levelScouting = chkLevelScoutingSkills.isSelected();
        model.levelEscape = chkLevelEscapeSkills.isSelected();
        model.levelLeadership = chkLevelLeadership.isSelected();
        model.levelTraining = chkLevelTraining.isSelected();
        model.levelOtherCommand = chkLevelOtherCommandSkills.isSelected();
    }

    private JPanel createNewDayFormationSection() {
        chkNewDayFormationIconOperationalStatus =
              checkBox("chkNewDayFormationIconOperationalStatus", model.newDayFormationIconOperationalStatus);

        CampaignOptionsLabel labelStyle =
              new CampaignOptionsLabel(RESOURCE_BUNDLE, "lblNewDayFormationIconOperationalStatusStyle");
        comboNewDayFormationIconOperationalStatusStyle = new MMComboBox<>(
              "comboNewDayFormationIconOperationalStatusStyle", FormationIconOperationalStatusStyle.values());
        comboNewDayFormationIconOperationalStatusStyle.setSelectedItem(
              model.newDayFormationIconOperationalStatusStyle);
        comboNewDayFormationIconOperationalStatusStyle.setToolTipText(getTextAt(RESOURCE_BUNDLE,
              "lblNewDayFormationIconOperationalStatusStyle.toolTipText"));
        comboNewDayFormationIconOperationalStatusStyle.setRenderer(new DefaultListCellRenderer() {
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

        // The style picker only matters when the status feature is on, so it tracks the check box's selected state.
        Runnable syncStyleEnabled = () -> {
            boolean enabled = chkNewDayFormationIconOperationalStatus.isSelected();
            labelStyle.setEnabled(enabled);
            comboNewDayFormationIconOperationalStatusStyle.setEnabled(enabled);
        };
        chkNewDayFormationIconOperationalStatus.addActionListener(evt -> syncStyleEnabled.run());
        syncStyleEnabled.run();

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQNewDayFormationContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addCheckBox(chkNewDayFormationIconOperationalStatus);
        panel.addRow(labelStyle, comboNewDayFormationIconOperationalStatusStyle);
        return panel;
    }

    private void writeNewDayFormationToModel() {
        if (chkNewDayFormationIconOperationalStatus == null) {
            return;
        }
        model.newDayFormationIconOperationalStatus = chkNewDayFormationIconOperationalStatus.isSelected();
        model.newDayFormationIconOperationalStatusStyle =
              Objects.requireNonNull(comboNewDayFormationIconOperationalStatusStyle.getSelectedItem());
    }

    private Component createDisplayPage() {
        JComponent generalContent = createDisplayGeneralSection();
        JComponent interstellarContent = createDisplayInterstellarSection();
        JComponent personnelContent = createDisplayPersonnelSection();
        // Non-short-circuit | so tips are registered for every section body before the page is built.
        boolean hasTooltips = registerDetailsTips(generalContent)
              | registerDetailsTips(interstellarContent)
              | registerDetailsTips(personnelContent);
        return pageBuilder("MHQDisplayPage", hasTooltips)
                     .section("lblMHQDisplayGeneralSection.text", "lblMHQDisplayGeneralSection.summary",
                           generalContent)
                     .section("lblMHQDisplayInterstellarSection.text", "lblMHQDisplayInterstellarSection.summary",
                           interstellarContent)
                     .section("lblMHQDisplayPersonnelSection.text", "lblMHQDisplayPersonnelSection.summary",
                           personnelContent)
                     .build();
    }

    private JPanel createDisplayGeneralSection() {
        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQDisplayGeneralContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);

        fieldDisplayDateFormat = new JTextField(model.displayDateFormat, 20);
        fieldDisplayDateFormat.setName("txtlabelDisplayDateFormat");
        panel.addRow(new CampaignOptionsLabel(RESOURCE_BUNDLE, "labelDisplayDateFormat"),
              dateFormatControl(fieldDisplayDateFormat));
        fieldLongDisplayDateFormat = new JTextField(model.longDisplayDateFormat, 20);
        fieldLongDisplayDateFormat.setName("txtlabelLongDisplayDateFormat");
        panel.addRow(new CampaignOptionsLabel(RESOURCE_BUNDLE, "labelLongDisplayDateFormat"),
              dateFormatControl(fieldLongDisplayDateFormat));

        JLabel guiScaleLabel = new JLabel(Messages.getString("CommonSettingsDialog.guiScale"));
        guiScaleSlider = new JSlider(7, 24);
        guiScaleSlider.setName("guiScale");
        guiScaleSlider.setMajorTickSpacing(3);
        Hashtable<Integer, JComponent> labelTable = new Hashtable<>();
        labelTable.put(7, new JLabel("70%"));
        labelTable.put(10, new JLabel("100%"));
        labelTable.put(16, new JLabel("160%"));
        labelTable.put(22, new JLabel("220%"));
        guiScaleSlider.setLabelTable(labelTable);
        guiScaleSlider.setPaintTicks(true);
        guiScaleSlider.setPaintLabels(true);
        guiScaleSlider.setValue(model.guiScaleValue);
        guiScaleSlider.setToolTipText(Messages.getString("CommonSettingsDialog.guiScaleTT"));
        panel.addRow(guiScaleLabel, guiScaleSlider);

        chkHideUnitFluff = checkBox("optionHideUnitFluff", model.hideUnitFluff);
        chkHistoricalDailyLog = checkBox("optionHistoricalDailyLog", model.historicalDailyLog);
        chkCompanyGeneratorStartup = checkBox("chkCompanyGeneratorStartup", model.companyGeneratorStartup,
              getMetadata(null, CampaignOptionFlag.UNIMPLEMENTED));
        chkShowCompanyGenerator = checkBox("chkShowCompanyGenerator", model.showCompanyGenerator);
        chkShowUnitPicturesOnTOE = checkBox("chkShowUnitPicturesOnTOE", model.showUnitPicturesOnTOE);
        panel.addCheckBoxGrid(2, chkHideUnitFluff, chkHistoricalDailyLog, chkCompanyGeneratorStartup,
              chkShowCompanyGenerator, chkShowUnitPicturesOnTOE);

        return panel;
    }

    private void writeDisplayGeneralToModel() {
        if (fieldDisplayDateFormat == null) {
            return;
        }
        // Only accept a valid pattern, matching the original dialog; an invalid one keeps the previous value.
        if (validateDateFormat(fieldDisplayDateFormat.getText())) {
            model.displayDateFormat = fieldDisplayDateFormat.getText();
        }
        if (validateDateFormat(fieldLongDisplayDateFormat.getText())) {
            model.longDisplayDateFormat = fieldLongDisplayDateFormat.getText();
        }
        model.guiScaleValue = guiScaleSlider.getValue();
        model.hideUnitFluff = chkHideUnitFluff.isSelected();
        model.historicalDailyLog = chkHistoricalDailyLog.isSelected();
        model.companyGeneratorStartup = chkCompanyGeneratorStartup.isSelected();
        model.showCompanyGenerator = chkShowCompanyGenerator.isSelected();
        model.showUnitPicturesOnTOE = chkShowUnitPicturesOnTOE.isSelected();
    }

    /**
     * Builds the control for a date-format row: the supplied editable pattern field plus a live example label showing
     * today's date in the entered pattern (or an error when it is invalid). The field's value is read back into the
     * model - only when it is a valid pattern - by {@link #writeDisplayGeneralToModel()}, matching the original dialog.
     */
    private JComponent dateFormatControl(JTextField field) {
        JLabel example = new JLabel();
        Runnable updateExample = () -> example.setText(validateDateFormat(field.getText())
              ? LocalDate.now().format(DateTimeFormatter.ofPattern(field.getText())
                    .withLocale(MekHQ.getMHQOptions().getDateLocale()))
              : getTextAt(RESOURCE_BUNDLE, "invalidDateFormat.error"));
        field.addActionListener(evt -> updateExample.run());
        updateExample.run();

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

    private JPanel createDisplayInterstellarSection() {
        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQDisplayInterstellarContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);

        chkInterstellarMapShowJumpRadius =
              checkBox("chkInterstellarMapShowJumpRadius", model.interstellarMapShowJumpRadius);
        CampaignOptionsLabel jumpZoomLabel =
              new CampaignOptionsLabel(RESOURCE_BUNDLE, "lblInterstellarMapShowJumpRadiusMinimumZoom");
        spinnerInterstellarMapShowJumpRadiusMinimumZoom = new CampaignOptionsSpinner(RESOURCE_BUNDLE,
              "lblInterstellarMapShowJumpRadiusMinimumZoom", 3d, 0d, 10d, 0.5);
        spinnerInterstellarMapShowJumpRadiusMinimumZoom.setValue(model.interstellarMapShowJumpRadiusMinimumZoom);
        btnInterstellarMapJumpRadiusColour =
              colourButton("btnInterstellarMapJumpRadiusColour", model.interstellarMapJumpRadiusColour);
        bindRadiusGating(chkInterstellarMapShowJumpRadius, jumpZoomLabel,
              spinnerInterstellarMapShowJumpRadiusMinimumZoom, btnInterstellarMapJumpRadiusColour);

        chkInterstellarMapShowPlanetaryAcquisitionRadius = checkBox(
              "chkInterstellarMapShowPlanetaryAcquisitionRadius", model.interstellarMapShowPlanetaryAcquisitionRadius);
        CampaignOptionsLabel acquisitionZoomLabel = new CampaignOptionsLabel(RESOURCE_BUNDLE,
              "lblInterstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom");
        spinnerInterstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom = new CampaignOptionsSpinner(RESOURCE_BUNDLE,
              "lblInterstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom", 2d, 0d, 10d, 0.5);
        spinnerInterstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom.setValue(
              model.interstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom);
        btnInterstellarMapPlanetaryAcquisitionRadiusColour = colourButton(
              "btnInterstellarMapPlanetaryAcquisitionRadiusColour",
              model.interstellarMapPlanetaryAcquisitionRadiusColour);
        bindRadiusGating(chkInterstellarMapShowPlanetaryAcquisitionRadius, acquisitionZoomLabel,
              spinnerInterstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom,
              btnInterstellarMapPlanetaryAcquisitionRadiusColour);

        chkInterstellarMapShowContractSearchRadius = checkBox("chkInterstellarMapShowContractSearchRadius",
              model.interstellarMapShowContractSearchRadius);
        btnInterstellarMapContractSearchRadiusColour = colourButton("btnInterstellarMapContractSearchRadiusColour",
              model.interstellarMapContractSearchRadiusColour);
        bindRadiusGating(chkInterstellarMapShowContractSearchRadius, btnInterstellarMapContractSearchRadiusColour);

        // The "Show ..." toggles share the left column with the "Minimum Zoom" labels, so keep their box and text packed
        // to the start instead of centred when the grid stretches that column to the shared label width.
        for (JCheckBox toggle : List.of(chkInterstellarMapShowJumpRadius,
              chkInterstellarMapShowPlanetaryAcquisitionRadius, chkInterstellarMapShowContractSearchRadius)) {
            toggle.setHorizontalAlignment(SwingConstants.LEADING);
        }
        // One shared width for the three swatch buttons so they line up in the right-hand column.
        setUniformWidth(List.of(btnInterstellarMapJumpRadiusColour, btnInterstellarMapPlanetaryAcquisitionRadiusColour,
              btnInterstellarMapContractSearchRadiusColour));

        // Each overlay is a "[Show X] [colour]" row, then a "[Minimum Zoom] [spinner]" row where one applies, reusing
        // the standard two-column grid so the swatches align under one another and with the spinners.
        panel.addComponentGrid(2, chkInterstellarMapShowJumpRadius, btnInterstellarMapJumpRadiusColour);
        panel.addRow(jumpZoomLabel, spinnerInterstellarMapShowJumpRadiusMinimumZoom);
        panel.addComponentGrid(2, chkInterstellarMapShowPlanetaryAcquisitionRadius,
              btnInterstellarMapPlanetaryAcquisitionRadiusColour);
        panel.addRow(acquisitionZoomLabel, spinnerInterstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom);
        panel.addComponentGrid(2, chkInterstellarMapShowContractSearchRadius,
              btnInterstellarMapContractSearchRadiusColour);

        return panel;
    }

    /**
     * Enables every component in {@code gated} only while {@code checkBox} is selected, and keeps them in sync when it
     * is toggled - matching the original dialog's gating of each interstellar-map radius group. The gated set varies by
     * group; the contract-search overlay has no minimum-zoom control.
     */
    private void bindRadiusGating(JCheckBox checkBox, JComponent... gated) {
        Runnable sync = () -> {
            boolean enabled = checkBox.isSelected();
            for (JComponent component : gated) {
                component.setEnabled(enabled);
            }
        };
        checkBox.addActionListener(evt -> sync.run());
        sync.run();
    }

    private void writeDisplayInterstellarToModel() {
        if (chkInterstellarMapShowJumpRadius == null) {
            return;
        }
        model.interstellarMapShowJumpRadius = chkInterstellarMapShowJumpRadius.isSelected();
        model.interstellarMapShowJumpRadiusMinimumZoom =
              (Double) spinnerInterstellarMapShowJumpRadiusMinimumZoom.getValue();
        model.interstellarMapJumpRadiusColour = btnInterstellarMapJumpRadiusColour.getColour();
        model.interstellarMapShowPlanetaryAcquisitionRadius =
              chkInterstellarMapShowPlanetaryAcquisitionRadius.isSelected();
        model.interstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom =
              (Double) spinnerInterstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom.getValue();
        model.interstellarMapPlanetaryAcquisitionRadiusColour =
              btnInterstellarMapPlanetaryAcquisitionRadiusColour.getColour();
        model.interstellarMapShowContractSearchRadius = chkInterstellarMapShowContractSearchRadius.isSelected();
        model.interstellarMapContractSearchRadiusColour = btnInterstellarMapContractSearchRadiusColour.getColour();
    }

    private JPanel createDisplayPersonnelSection() {
        CampaignOptionsLabel filterStyleLabel =
              new CampaignOptionsLabel(RESOURCE_BUNDLE, "optionPersonnelFilterStyle");
        comboPersonnelFilterStyle = new JComboBox<>(PersonnelFilterStyle.values());
        comboPersonnelFilterStyle.setName("optionPersonnelFilterStyle");
        comboPersonnelFilterStyle.setSelectedItem(model.personnelFilterStyle);
        comboPersonnelFilterStyle.setRenderer(new DefaultListCellRenderer() {
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

        chkPersonnelFilterOnPrimaryRole =
              checkBox("optionPersonnelFilterOnPrimaryRole", model.personnelFilterOnPrimaryRole);
        chkUnifiedDailyReport = checkBox("chkUnifiedDailyReport", model.unifiedDailyReport);
        chkEnableDailyReportAggregateTab = checkBox("chkEnableDailyReportAggregateTab", model.aggregateDailyReport,
              getMetadata(null, CampaignOptionFlag.IMPORTANT));

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQDisplayPersonnelContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addRow(filterStyleLabel, comboPersonnelFilterStyle);
        panel.addCheckBoxGrid(2, chkPersonnelFilterOnPrimaryRole, chkUnifiedDailyReport,
              chkEnableDailyReportAggregateTab);

        return panel;
    }

    private void writeDisplayPersonnelToModel() {
        if (comboPersonnelFilterStyle == null) {
            return;
        }
        model.personnelFilterStyle =
              (PersonnelFilterStyle) Objects.requireNonNull(comboPersonnelFilterStyle.getSelectedItem());
        model.personnelFilterOnPrimaryRole = chkPersonnelFilterOnPrimaryRole.isSelected();
        model.unifiedDailyReport = chkUnifiedDailyReport.isSelected();
        model.aggregateDailyReport = chkEnableDailyReportAggregateTab.isSelected();
    }

    private Component createColoursPage() {
        List<JComponent> unitStatusButtons = createColoursUnitStatusButtons();
        List<JComponent> personnelStatusButtons = createColoursPersonnelStatusButtons();
        List<JComponent> otherButtons = createColoursOtherButtons();
        List<JComponent> skillFeedbackButtons = createColoursSkillFeedbackButtons();
        // Size every colour button on the page to one shared width (across all sections) so all the 2-column grids
        // line their columns up at the same x - the way the checkbox grids do through a shared label-column width.
        // Letting each section size its own buttons would let their differing longest labels push the columns to
        // different positions.
        List<JComponent> allButtons = new ArrayList<>(unitStatusButtons);
        allButtons.addAll(personnelStatusButtons);
        allButtons.addAll(otherButtons);
        allButtons.addAll(skillFeedbackButtons);
        setUniformWidth(allButtons);

        JComponent unitStatusContent = colourButtonGrid("MHQColoursUnitStatusContent", unitStatusButtons);
        JComponent personnelStatusContent = colourButtonGrid("MHQColoursPersonnelStatusContent", personnelStatusButtons);
        JComponent otherContent = colourButtonGrid("MHQColoursOtherContent", otherButtons);
        JComponent skillFeedbackContent = colourButtonGrid("MHQColoursSkillFeedbackContent", skillFeedbackButtons);
        // Register each section body's tips before the page is built; |= always evaluates its right side, so no
        // section's registration is skipped.
        boolean hasTooltips = registerDetailsTips(unitStatusContent);
        hasTooltips |= registerDetailsTips(personnelStatusContent);
        hasTooltips |= registerDetailsTips(otherContent);
        hasTooltips |= registerDetailsTips(skillFeedbackContent);
        // The disclaimer that some colours live in MegaMek's Client Options is shown as the page intro, above the
        // sections.
        return pageBuilder("MHQColoursPage", hasTooltips)
                     .intro("coloursTab.disclaimer")
                     .section("lblMHQColoursUnitStatusSection.text", "lblMHQColoursUnitStatusSection.summary",
                           unitStatusContent)
                     .section("lblMHQColoursPersonnelStatusSection.text",
                           "lblMHQColoursPersonnelStatusSection.summary", personnelStatusContent)
                     .section("lblMHQColoursOtherSection.text", "lblMHQColoursOtherSection.summary", otherContent)
                     .section("lblMHQColoursSkillFeedbackSection.text", "lblMHQColoursSkillFeedbackSection.summary",
                           skillFeedbackContent)
                     .build();
    }

    private List<JComponent> createColoursUnitStatusButtons() {
        // Order follows Unit#determineForegroundColor so the swatches read in the same priority the app applies them.
        // Deployed is shared with personnel (Unit and the personnel list both use it) and lives here as a unit state.
        return buildColourButtons(
              "optionDeployedForeground", "optionDeployedBackground",
              "optionInTransitForeground", "optionInTransitBackground",
              "optionRefittingForeground", "optionRefittingBackground",
              "optionMothballingForeground", "optionMothballingBackground",
              "optionMothballedForeground", "optionMothballedBackground",
              "optionUnmaintainedForeground", "optionUnmaintainedBackground",
              "optionNotRepairableForeground", "optionNotRepairableBackground",
              "optionNonFunctionalForeground", "optionNonFunctionalBackground",
              "optionNeedsPartsFixedForeground", "optionNeedsPartsFixedBackground",
              "optionUncrewedForeground", "optionUncrewedBackground");
    }

    private List<JComponent> createColoursPersonnelStatusButtons() {
        // Order follows PersonnelTableModel's status priority.
        return buildColourButtons(
              "optionAbsentForeground", "optionAbsentBackground",
              "optionGoneForeground", "optionGoneBackground",
              "optionAwayFromMainForceForeground", "optionAwayFromMainForceBackground",
              "optionInjuredForeground", "optionInjuredBackground",
              "optionPregnantForeground", "optionPregnantBackground",
              "optionFatiguedForeground", "optionFatiguedBackground",
              "optionHealedInjuriesForeground", "optionHealedInjuriesBackground");
    }

    private List<JComponent> createColoursOtherButtons() {
        // Colours used outside the unit and personnel lists: contract deployment coverage, the finances loan table,
        // and StratCon map hex coordinates.
        return buildColourButtons(
              "optionBelowContractMinimumForeground", "optionBelowContractMinimumBackground",
              "optionLoanOverdueForeground", "optionLoanOverdueBackground",
              "optionStratConHexCoordForeground");
    }

    private List<JComponent> createColoursSkillFeedbackButtons() {
        return buildColourButtons(
              "optionFontColorNegative", "optionFontColorWarning",
              "optionFontColorPositive", "optionFontColorAmazing",
              "optionFontColorSkillUltraGreen", "optionFontColorSkillGreen",
              "optionFontColorSkillRegular", "optionFontColorSkillVeteran",
              "optionFontColorSkillElite");
    }

    /**
     * Creates a colour button for each of {@code keys} (loading its initial colour from
     * {@link MHQOptionsModel#statusColours}), registers it in {@link #colourButtons} under its key so
     * {@link #writeColoursToModel()} can read it back, left-aligns its swatch and text, and returns the buttons in
     * order for placement in a grid. Button widths are equalised later, once every section's buttons exist (see
     * {@link #setUniformWidth(List)}), so the sections' grid columns line up with each other.
     */
    private List<JComponent> buildColourButtons(String... keys) {
        List<JComponent> buttons = new ArrayList<>();
        for (String key : keys) {
            ColourSelectorButton button = colourButton(key, model.statusColours.get(key));
            button.setHorizontalAlignment(SwingConstants.LEFT);
            colourButtons.put(key, button);
            buttons.add(button);
        }
        return buttons;
    }

    /** Lays {@code buttons} out in a 2-column colour grid panel named {@code name}. */
    private JPanel colourButtonGrid(String name, List<JComponent> buttons) {
        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel(name);
        panel.addComponentGrid(2, buttons.toArray(new JComponent[0]));
        return panel;
    }

    /**
     * Sizes every component in {@code components} to the widest one's preferred width (heights unchanged) so they line
     * up in even columns when laid out in a grid - used across a page's colour buttons at once so their columns match.
     */
    private static void setUniformWidth(List<? extends JComponent> components) {
        int width = 0;
        for (JComponent component : components) {
            width = Math.max(width, component.getPreferredSize().width);
        }
        for (JComponent component : components) {
            component.setPreferredSize(new Dimension(width, component.getPreferredSize().height));
        }
    }

    private void writeColoursToModel() {
        colourButtons.forEach((key, button) -> model.statusColours.put(key, button.getColour()));
    }

    private Component createAdvancedPage() {
        CampaignOptionsLabel userDirLabel = new CampaignOptionsLabel(RESOURCE_BUNDLE, "lblUserDir");
        userDirField = new JTextField(model.userDir, 20);
        userDirField.setName("txtUserDir");
        userDirField.setToolTipText(getTextAt(RESOURCE_BUNDLE, "lblUserDir.toolTipText"));
        JButton userDirChooser = new JButton();
        userDirChooser.setName("btnUserDirChooser");
        userDirChooser.setToolTipText(getTextAt(RESOURCE_BUNDLE, "userDirChooser.title"));
        // Material Symbols "folder_open" glyph (https://fonts.google.com/icons), matching the icon buttons elsewhere in
        // the dialog, in place of a "..." text button. Sized a little above the label font so it fills the enlarged
        // square chooser button.
        userDirChooser.setIcon(symbolIcon(0xE2C8, userDirChooser.getFont().getSize() + UIUtil.scaleForGUI(2),
              userDirChooser.getForeground()));
        userDirChooser.addActionListener(evt -> CommonSettingsDialog.fileChooseUserDir(userDirField, frame));

        JButton userDirHelp = new JButton("Help");
        userDirHelp.setName("btnUserDirHelp");
        // MekHQ ships its own copy of the user-directory help under docs/Customization/MekHQ; MMConstants points at
        // MegaMek's docs/Customization/UserDir path, which is absent from MekHQ's working directory.
        String userDirHelpPath = "docs/Customization/MekHQ/UserDirHelp.html";
        try {
            String helpTitle = Messages.getString("UserDirHelpDialog.title");
            URL helpFile = new File(userDirHelpPath).toURI().toURL();
            userDirHelp.addActionListener(evt -> new HelpDialog(helpTitle, helpFile, frame).setVisible(true));
        } catch (MalformedURLException ex) {
            LOGGER.error("Could not find the user data directory help file at {}", userDirHelpPath);
        }

        // Render Help a touch more compact, and enlarge the icon-only chooser to a square as tall as the path field so
        // it is an easier click target.
        setSmallSizeVariant(userDirHelp);
        int chooserSide = userDirField.getPreferredSize().height;
        Dimension chooserSize = new Dimension(chooserSide, chooserSide);
        userDirChooser.setPreferredSize(chooserSize);
        userDirChooser.setMinimumSize(chooserSize);
        userDirChooser.setMaximumSize(chooserSize);

        // Let the path field fill the control column so its left edge lines up with the spinners below (and its right
        // edge, past the buttons, with theirs) instead of sitting at a fixed width nudged over by FlowLayout's leading
        // gap. The chooser and help buttons sit at the row's right end.
        JPanel userDirButtons = new JPanel();
        userDirButtons.setLayout(new BoxLayout(userDirButtons, BoxLayout.LINE_AXIS));
        userDirButtons.setOpaque(false);
        userDirButtons.add(userDirChooser);
        userDirButtons.add(Box.createHorizontalStrut(UIUtil.scaleForGUI(6)));
        userDirButtons.add(userDirHelp);

        JPanel userDirControls = new JPanel(new BorderLayout(UIUtil.scaleForGUI(6), 0));
        userDirControls.setOpaque(false);
        userDirControls.add(userDirField, BorderLayout.CENTER);
        userDirControls.add(userDirButtons, BorderLayout.LINE_END);

        CampaignOptionsFormPanel panel = new CampaignOptionsFormPanel("MHQAdvancedContent", FORM_LABEL_WIDTH,
              FORM_CONTROL_WIDTH);
        panel.addRow(userDirLabel, userDirControls);
        spinnerStartGameDelay = advancedSpinner(panel, "lblStartGameDelay", 1000, 250, 2500, 25, model.startGameDelay);
        spinnerStartGameClientDelay =
              advancedSpinner(panel, "lblStartGameClientDelay", 50, 50, 2500, 25, model.startGameClientDelay);
        spinnerStartGameClientRetryCount = advancedSpinner(panel, "lblStartGameClientRetryCount", 1000, 100, 2500, 50,
              model.startGameClientRetryCount);
        spinnerStartGameBotClientDelay =
              advancedSpinner(panel, "lblStartGameBotClientDelay", 50, 50, 2500, 25, model.startGameBotClientDelay);
        spinnerStartGameBotClientRetryCount = advancedSpinner(panel, "lblStartGameBotClientRetryCount", 250, 100, 2500,
              50, model.startGameBotClientRetryCount);

        CampaignOptionsLabel companyGenerationLabel =
              new CampaignOptionsLabel(RESOURCE_BUNDLE, "lblDefaultCompanyGenerationMethod");
        comboDefaultCompanyGenerationMethod =
              new MMComboBox<>("comboDefaultCompanyGenerationMethod", CompanyGenerationMethod.values());
        comboDefaultCompanyGenerationMethod.setSelectedItem(model.defaultCompanyGenerationMethod);
        comboDefaultCompanyGenerationMethod.setToolTipText(
              getTextAt(RESOURCE_BUNDLE, "lblDefaultCompanyGenerationMethod.toolTipText"));
        comboDefaultCompanyGenerationMethod.setRenderer(new DefaultListCellRenderer() {
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
        panel.addRow(companyGenerationLabel, comboDefaultCompanyGenerationMethod);

        return buildMHQPage("MHQAdvancedPage", "lblMHQAdvancedSection.text", "lblMHQAdvancedSection.summary", panel);
    }

    /**
     * Creates a labelled integer spinner, initialises it to {@code value}, adds it to {@code panel} as a row, and
     * returns it so the caller can store it in a field for {@link #writeAdvancedToModel()}.
     */
    private CampaignOptionsSpinner advancedSpinner(CampaignOptionsFormPanel panel, String labelKey, int defaultValue,
          int minimum, int maximum, int step, int value) {
        CampaignOptionsLabel label = new CampaignOptionsLabel(RESOURCE_BUNDLE, labelKey);
        CampaignOptionsSpinner spinner =
              new CampaignOptionsSpinner(RESOURCE_BUNDLE, labelKey, defaultValue, minimum, maximum, step);
        spinner.setValue(value);
        panel.addRow(label, spinner);
        return spinner;
    }

    private void writeAdvancedToModel() {
        if (userDirField == null) {
            return;
        }
        model.userDir = userDirField.getText();
        model.startGameDelay = (int) spinnerStartGameDelay.getValue();
        model.startGameClientDelay = (int) spinnerStartGameClientDelay.getValue();
        model.startGameClientRetryCount = (int) spinnerStartGameClientRetryCount.getValue();
        model.startGameBotClientDelay = (int) spinnerStartGameBotClientDelay.getValue();
        model.startGameBotClientRetryCount = (int) spinnerStartGameBotClientRetryCount.getValue();
        model.defaultCompanyGenerationMethod =
              Objects.requireNonNull(comboDefaultCompanyGenerationMethod.getSelectedItem());
    }

    /**
     * Builds a pool fill / no-release check box pair, registers both in {@link #poolCheckBoxes} under their resource
     * names (matching {@link MHQOptionsModel#newDayPools}), and appends them to {@code target}. The "no release" box is
     * enabled only while the "fill" box is selected.
     */
    private void addPoolPair(List<JCheckBox> target, String fillKey, String noReleaseKey) {
        CampaignOptionsCheckBox fill = checkBox(fillKey, Boolean.TRUE.equals(model.newDayPools.get(fillKey)));
        CampaignOptionsCheckBox noRelease =
              checkBox(noReleaseKey, Boolean.TRUE.equals(model.newDayPools.get(noReleaseKey)));
        poolCheckBoxes.put(fillKey, fill);
        poolCheckBoxes.put(noReleaseKey, noRelease);
        // The "no release" option only applies while the pool is being filled, so it follows the fill check box.
        noRelease.setEnabled(fill.isSelected());
        fill.addItemListener(evt -> noRelease.setEnabled(fill.isSelected()));
        target.add(fill);
        target.add(noRelease);
    }

    /**
     * Writes the edited options back to {@link MHQOptions}. Called by the hosting dialog when the user confirms. Each
     * visited page copies its controls into the shared {@link MHQOptionsModel}; a page the user never opened leaves its
     * controls null, so its {@code writeXToModel} is a no-op and the model keeps the values it was built with. The
     * fully-populated model is then applied to {@link MHQOptions} (and the GUI-scale and user-directory stores) in one
     * step.
     */
    public void save() {
        writeFontsToModel();
        writeDisplayGeneralToModel();
        writeDisplayInterstellarToModel();
        writeDisplayPersonnelToModel();
        writeColoursToModel();
        writeAutosaveToModel();
        writePoolsToModel();
        writeNewDayTasksToModel();
        writeNewDayTrainingToModel();
        writeNewDayFormationToModel();
        writeCampaignSaveToModel();
        writeNagsToModel();
        writeAdvancedToModel();
        model.applyTo(options);
    }
}
