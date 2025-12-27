# Ben's Mindful Changelog

## 2025-12-26

- Added intervention logs viewer to Database settings tab:
  - Created `ViewInterventionLogs` widget with a "View interventions" button that opens a modal bottom sheet
  - Implemented `SliverInterventionLogsList` to display the last 10 intervention logs by default
  - Shows expandable entries with timestamp, app name, outcome, status labels (success, time spent), and detailed information (Prompt ID, Session ID, app package, trigger type, response content, completion time)
  - Follows the same UI pattern as the crash logs viewer for consistency

## 2025-11-27

- Cleaned the project root by moving `export_db.ps1` into a dedicated `scripts/` directory,creating `db_exports/` for generated SQLite files, and purging stale `.sqlite`, `.ab`, and `.tar` artifacts.
- Added documentation (`scripts/README.md`) explaining how to run the export script and what it produces.
- Updated `.gitignore` so development-only exports stay out of version control.

## 2025-11-06

- Built the PowerShell database export workflow (`scripts/export_db.ps1`) that streams `Mindful.sqlite` directly from the device via `adb exec-out run-as`, ensuring exports succeed even without shared external storage permissions.
- Automated timestamped naming and storage under `db_exports/`, making it easy to inspect the latest intervention data with a SQLite viewer.
- Iterated on the script to handle byte-stream writing, path setup, and verification output after encountering `Set-Content` encoding errors.

## 2025-11-05

- Finished the Android ⇄ Flutter intervention pipeline:
  - `SeverityCalculator` now queries `UsageStatsManager` in a rolling 15-minute window, translating open counts and foreground minutes into level 1/2/3 severity with StudyConfig-backed thresholds.
  - `InterventionManager` requests prompts from Flutter via `getPromptForIntervention`, displays overlays, captures responses, and reports completions (including `responseContent` and dwell time) through `reportInterventionCompleted`.
  - `OverlayManager` supports `yes_no`, `wait_out`, `slider`, and `tap_hold` interactions, calling back with the user’s actual response string.
- Flutter side (`InterventionService`, `PromptRepo`, `DynamicRecordsDao`) now:
  - Creates a `Session` per usage episode and a linked `PromptDeliveryLog` before returning prompt metadata to Android in a single payload.
  - Selects prompts with random-without-replacement per `(arm, level)` pool, automatically resetting pools when exhausted.
  - Records completion metadata, including `responseContent`, `secondsSpent`, and success flags.
- Added in-app controls to change `interventionArm` via the Database settings tab and wired the value through `MindfulSettingsNotifier` so Android SharedPreferences stay in sync.
- Implemented schema upgrades/migrations to add `appPackage`, `startedAt`, `endedAt`, and `promptIdFk` columns for richer analytics, plus safety guards for pre-existing installs.
- Created a (not yet surfaced) debug intervention viewer screen for inspecting `PromptDeliveryLog` entries directly inside the app.
