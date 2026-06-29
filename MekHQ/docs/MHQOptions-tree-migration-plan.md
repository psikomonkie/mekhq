# MekHQ Client Options → Tree-Navigation Migration Plan

Pilot migration of `MHQOptionsDialog` (the **MekHQ Client Options** dialog) from its current
`JTabbedPane` layout to the tree-navigation + search + content-on-the-right pattern introduced by the
Campaign Options dialog.

This dialog is the **first non-Campaign-Options consumer** of that pattern. Its purpose here is twofold:
1. Deliver the migrated dialog.
2. Act as the second concrete consumer that shapes the reusable framework API **before** it is extracted
   into MegaMek, so the API is not accidentally specialised to Campaign Options.

> Scope note: this is a UI/navigation reshaping only. Persistence stays exactly as-is — every control still
> loads from / saves to `MHQOptions` (and `GUIPreferences` for GUI scale), and `MHQOptionsChangedEvent` is
> still fired once on commit.

---

## 1. Source dialog summary

- `MekHQ/src/mekhq/gui/dialog/MHQOptionsDialog.java` — ~2,260 lines, `JTabbedPane`, **8 tabs**, ~113 controls.
- Controls are **hand-built** (no dynamic generation), laid out with `GroupLayout` in `createXTab()` methods.
- Load: `setInitialState()`. Save: `okAction()`. Backing store: `MHQOptions` (`SuiteOptions` → Java
  Preferences), plus `GUIPreferences` for GUI scale.
- Labels come from `MekHQ/resources/mekhq/resources/GUI.properties`.

---

## 2. Proposed navigation tree

Groups (▸) get a landing page (logo + quote + explanation). Leaves hold the actual option controls.
Icon code points are suggestions (Material Symbols, via `FontHandler.symbolIcon`).

```
Display ▸
  ├─ General
  ├─ Interstellar Map
  └─ Personnel List
Colours ▸
  ├─ Unit Status Colours
  └─ Skill & Feedback Colours
Fonts
Autosave
New Day ▸
  ├─ Personnel Pools
  ├─ Automation & Logistics
  ├─ Training & Leveling
  └─ Formation Icons
Campaign Save
Reminders & Confirmations ▸
  ├─ Warnings (Nags)
  └─ Confirmations
Advanced
```

8 top-level nodes; 4 are groups with landing pages (Display, Colours, New Day, Reminders & Confirmations).

---

## 3. Page-by-page control mapping

Every existing control is assigned to exactly one leaf page. Resource keys are preserved.

### Display ▸ General
| Control | Type | Label key | Notes |
|---|---|---|---|
| `optionDisplayDateFormat` | JTextField | `labelDisplayDateFormat.text` | live date-format validation |
| `optionLongDisplayDateFormat` | JTextField | `labelLongDisplayDateFormat.text` | live date-format validation |
| `guiScale` | JSlider (7–24) | `CommonSettingsDialog.guiScale` | **side effect:** LAF reload on commit |
| `optionHideUnitFluff` | JCheckBox | `optionHideUnitFluff.text` | |
| `optionHistoricalDailyLog` | JCheckBox | `optionHistoricalDailyLog.text` | |
| `chkCompanyGeneratorStartup` | JCheckBox | `chkCompanyGeneratorStartup.text` | |
| `chkShowCompanyGenerator` | JCheckBox | `chkShowCompanyGenerator.text` | |
| `chkShowUnitPicturesOnTOE` | JCheckBox | `chkShowUnitPicturesOnTOE.text` | |

### Display ▸ Interstellar Map
| Control | Type | Label key | Notes |
|---|---|---|---|
| `chkInterstellarMapShowJumpRadius` | JCheckBox | `chkInterstellarMapShowJumpRadius.text` | **parent** → enables next two |
| `spnInterstellarMapShowJumpRadiusMinimumZoom` | JSpinner | `lblInterstellarMapShowJumpRadiusMinimumZoom.text` | gated |
| `btnInterstellarMapJumpRadiusColour` | ColourSelectorButton | `btnInterstellarMapJumpRadiusColour.text` | gated |
| `chkInterstellarMapShowPlanetaryAcquisitionRadius` | JCheckBox | `chkInterstellarMapShowPlanetaryAcquisitionRadius.text` | **parent** → enables next two |
| `spnInterstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom` | JSpinner | `lblInterstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom.text` | gated |
| `btnInterstellarMapPlanetaryAcquisitionRadiusColour` | ColourSelectorButton | `btnInterstellarMapPlanetaryAcquisitionRadiusColour.text` | gated |
| `chkInterstellarMapShowContractSearchRadius` | JCheckBox | `chkInterstellarMapShowContractSearchRadius.text` | **parent** → enables next |
| `btnInterstellarMapContractSearchRadiusColour` | ColourSelectorButton | `btnInterstellarMapContractSearchRadiusColour.text` | gated |

### Display ▸ Personnel List
| Control | Type | Label key |
|---|---|---|
| `optionPersonnelFilterStyle` | JComboBox\<PersonnelFilterStyle\> | `optionPersonnelFilterStyle.text` |
| `optionPersonnelFilterOnPrimaryRole` | JCheckBox | `optionPersonnelFilterOnPrimaryRole.text` |
| `chkUnifiedDailyReport` | JCheckBox | `chkUnifiedDailyReport.text` |
| `chkEnableDailyReportAggregateTab` | JCheckBox | `chkEnableDailyReportAggregateTab.text` |

### Colours ▸ Unit Status Colours
- 18 foreground/background `ColourSelectorButton` pairs (36 buttons): Deployed, Below Contract Minimum,
  In Transit, Refitting, Mothballing, Mothballed, Not Repairable, Non-Functional, Needs Parts Fixed,
  Unmaintained, Uncrewed, Loan Overdue, Injured, Healed Injuries, Pregnant, Gone, Absent, Fatigued.
- `optionStratConHexCoordForeground` — ColourSelectorButton.

### Colours ▸ Skill & Feedback Colours
- `optionFontColorNegative`, `optionFontColorWarning`, `optionFontColorPositive`, `optionFontColorAmazing`.
- `optionFontColorSkillUltraGreen`, `optionFontColorSkillGreen`, `optionFontColorSkillRegular`,
  `optionFontColorSkillVeteran`, `optionFontColorSkillElite`.
- `txtDisclaimer` (read-only JTextArea, `coloursTab.disclaimer`) → render as a footer note on this page.

### Fonts (leaf)
| Control | Type | Label key |
|---|---|---|
| `comboMedicalViewDialogHandwritingFont` | FontComboBox | `lblMedicalViewDialogHandwritingFont.text` |

### Autosave (leaf)
- Frequency radio group (mutually exclusive): `optionNoSave`, `optionSaveDaily`, `optionSaveWeekly`,
  `optionSaveMonthly`, `optionSaveYearly`.
- `checkSaveBeforeScenarios`, `checkSaveBeforeContractEnd` (`checkSaveBeforeMissionEnd.text`).
- `spinnerSavedGamesCount` — JSpinner (1–10).

### New Day ▸ Personnel Pools
- 10 Fill / No-Release `JCheckBox` pairs (each **No-Release gated by its Fill**): Aerotech/Tech, Medic,
  Soldier, Battle Armor, Vehicle Crew (Ground), Vehicle Crew (VTOL), Vehicle Crew (Naval), Vessel Pilot,
  Vessel Gunner, Vessel Crew. Pattern `chkNewDay[Pool]PoolFill` / `chkNewDay[Pool]PoolNoRelease`.

### New Day ▸ Automation & Logistics
| Control | Type |
|---|---|
| `chkNewDayMRMS` | JCheckBox |
| `chkNewDayAutoLogistics` | JCheckBox |
| `chkNewDayOptimizeMedicalAssignments` | JCheckBox |
| `chkNewDayAutomaticallyAssignUnmaintainedUnits` | JCheckBox |
| `chkSelfCorrectMaintenance` | JCheckBox |

### New Day ▸ Training & Leveling
| Control | Type | Notes |
|---|---|---|
| `chkNewMonthQuickTrain` | JCheckBox | **parent** → gates spinner |
| `spnQuickTrainTarget` | JSpinner (1–10) | gated |
| `chkLevelArtillery` | JCheckBox | |
| `chkLevelScoutingSkills` | JCheckBox | |
| `chkLevelEscapeSkills` | JCheckBox | |
| `chkLevelLeadership` | JCheckBox | |
| `chkLevelTraining` | JCheckBox | |
| `chkLevelOtherCommandSkills` | JCheckBox | |

### New Day ▸ Formation Icons
| Control | Type | Notes |
|---|---|---|
| `chkNewDayFormationIconOperationalStatus` | JCheckBox | **parent** → gates combo |
| `comboNewDayFormationIconOperationalStatusStyle` | MMComboBox\<FormationIconOperationalStatusStyle\> | gated |

### Campaign Save (leaf) — formerly "Campaign XML Save"
| Control | Type | Label key |
|---|---|---|
| `optionPreferGzippedOutput` | JCheckBox | `optionPreferGzippedOutput.text` |
| `optionWriteCustomsToXML` | JCheckBox | `optionWriteCustomsToXML.text` |
| `optionWriteAllUnitsToXML` | JCheckBox | `optionWriteAllUnitsToXML.text` |
| `optionSaveMothballState` | JCheckBox | `optionSaveMothballState.text` |

### Reminders & Confirmations ▸ Warnings (Nags)
- 27 `JCheckBox` nag toggles: `optionUnmaintainedUnitsNag`, `optionPregnantCombatantNag`,
  `optionPrisonersNag`, `optionHRStrainNag`, `optionUntreatedPersonnelNag`, `optionNoCommanderNag`,
  `optionContractEndedNag`, `optionSingleDropNag`, `optionInsufficientAsTechsNag`,
  `optionInsufficientAsTechTimeNag`, `optionInsufficientMedicsNag`, `optionShortDeploymentNag`,
  `optionCombatChallengeNag`, `optionUnresolvedStratConContactsNag`, `optionOutstandingScenariosNag`,
  `optionInvalidFactionNag`, `optionUnableToAffordExpensesNag`, `optionUnableToAffordRentNag`,
  `optionUnableToAffordLoanPaymentNag`, `optionUnableToAffordJumpNag`,
  `optionUnableToAffordShoppingListNag`, `optionSomeoneRandomlyDiedCombatNag`,
  `optionSomeoneRandomlyDiedTechNag`, `optionSomeoneRandomlyDiedOtherSupportNag`,
  `optionSomeoneRandomlyDiedCivilianNag`, `optionSomeoneRandomlyDiedCampFollowerNag`,
  `optionSomeoneRandomlyDiedRetiredNag`. Persisted via `MHQConstants.NAG_*` / `setNagDialogIgnore()`.

### Reminders & Confirmations ▸ Confirmations
- 8 `JCheckBox` confirmation toggles: `optionContractRentalConfirmation`,
  `optionFactionStandingsUltimatumConfirmation`, `optionBeginTransitConfirmation`,
  `optionStratConBatchallBreachConfirmation`, `optionStratConDeployConfirmation`,
  `optionResolveScenarioConfirmation`, `optionAbandonUnitsConfirmation`, `optionAssignTechsConfirmation`.
  Persisted via `MHQConstants.CONFIRMATION_*`.

### Advanced (leaf) — formerly "Miscellaneous"
| Control | Type | Notes |
|---|---|---|
| `txtUserDir` + chooser + help | JTextField + 2 JButtons | chooser → `CommonSettingsDialog.fileChooseUserDir()`; help → `HelpDialog` |
| `spnStartGameDelay` | JSpinner | |
| `spnStartGameClientDelay` | JSpinner | |
| `spnStartGameClientRetryCount` | JSpinner | |
| `spnStartGameBotClientDelay` | JSpinner | |
| `spnStartGameBotClientRetryCount` | JSpinner | |
| `comboDefaultCompanyGenerationMethod` | MMComboBox\<CompanyGenerationMethod\> | |

> Candidate future split (not for the pilot): move the five "Game Start" spinners into a dedicated
> **Networking / Startup** page, leaving Advanced for the user directory and company-generation default.

---

## 4. Landing-page copy for group nodes

Each group node shows the standard landing layout: logo, an in-character quote, and a short explanation.
Quote text should be sourced in the existing Campaign Options style (a `.border`-style resource key per
group); placeholders below, explanation text proposed.

- **Display** — "Control how MekHQ looks and what it surfaces: date formats, UI scale, and the panels shown
  across the interstellar map and personnel views." *(quote: TBD)*
- **Colours** — "Choose the colours MekHQ uses to flag unit status and to highlight skill levels and
  feedback messages." *(quote: TBD)*
- **New Day** — "Configure what happens automatically when you advance the day: recruitment pools, logistics
  and maintenance automation, training, and formation icons." *(quote: TBD)*
- **Reminders & Confirmations** — "Decide which warnings MekHQ raises and which actions ask for confirmation
  before you proceed." *(quote: TBD)*

---

## 5. Cross-cutting behaviour to preserve

These must survive the migration and are the behaviours that will exercise the framework API.

| Behaviour | Where it lives now | Migration handling |
|---|---|---|
| Load all controls | `setInitialState()` (1 method) | split into per-page `load()` |
| Save all controls | `okAction()` (1 method) | split into per-page `save()`; dialog orchestrates validate→save→commit |
| Fire `MHQOptionsChangedEvent` | end of `okAction()` | fire **once** after all pages saved (dialog post-commit hook) |
| GUI-scale LAF reload | `okAction()` → `MekHQ.updateGuiScaling()` | Display ▸ General page post-commit, only if changed |
| Date-format live validation | Display tab listeners | stays inside Display ▸ General page |
| Parent→child enable/disable | Interstellar Map (3), Pools (10), Formation Icons (1), Quick Train (1) | page-local listeners; framework stays unaware |
| User-dir chooser / help | Miscellaneous tab buttons | stays inside Advanced page |
| Search | n/a (tabbed) | each page contributes `SearchEntry`(label + keywords) per control |

---

## 6. Framework API touchpoints (validation of the draft API)

This consumer confirms the proposed `OptionsPage` shape and flags what must stay out of the core contract.

- **Confirms needed:** `component()`, `load()`, `save()` (with veto), `validate()`, `searchEntries()`, and a
  per-page optional post-commit hook (for the GUI-scale reload).
- **Must stay OUT of core (Campaign-Options-only):** version badges, option flags, and the sticky "details"
  help surface. `MHQOptions` uses plain tooltips and has none of these — if they are required by the base
  page contract, the framework is over-fitted to Campaign Options.
- **Backing-store independence confirmed:** `MHQOptions` (Preferences) vs `CampaignOptions` (domain object)
  save completely differently, so `save()` must remain an opaque per-page callback; the dialog only
  sequences validate → save → commit.

---

## 7. Open decisions

1. `chkSelfCorrectMaintenance` placement — proposed under **Automation & Logistics** (vs. its own
   Maintenance page). Confirm.
2. **Fonts** is a single control — keep as a top-level leaf, or fold into Display? Proposed: keep for now,
   revisit once more font options exist.
3. Per-group **quote** text/keys — needs content sourcing in the Campaign Options style.
4. Defer the **Advanced → Networking/Startup** split? Proposed: defer past the pilot.
5. Tab **mnemonics** (currently N/D/W/M/Y etc.) are obsolete under tree nav — drop.

---

## 8. Suggested implementation order (within this pilot)

1. Land Phase 1 (non-leaf landing pages) in Campaign Options — produces the reusable `LandingPage`.
2. Stand up the candidate generic page/host/nav API **in mekhq**, driven by this dialog as the second
   consumer.
3. Migrate pages leaf-by-leaf, smallest first (Fonts → Campaign Save → Autosave → Advanced → Personnel List
   → Interstellar Map → Colours pages → New Day pages → Nag/Confirmation pages), keeping `MHQOptions`
   load/save intact per page.
4. Once Campaign Options + MHQOptions both drive the same API and it has stabilised, extract the framework
   to MegaMek (MegaMek PR + mekhq adopt), then migrate `CommonSettingsDialog` and (last)
   `GameOptionsDialog`.
