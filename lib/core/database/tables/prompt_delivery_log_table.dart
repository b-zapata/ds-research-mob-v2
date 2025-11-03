/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'package:drift/drift.dart';

@DataClassName("PromptDeliveryLog")
class PromptDeliveryLogTable extends Table {
  /// Unique ID for each prompt delivery log entry (auto-increment)
  IntColumn get id => integer().autoIncrement()();

  /// Foreign key to Session table
  IntColumn get sessionIdFk => integer()();

  /// Foreign key to template (nullable for placeholders)
  IntColumn get templateIdFk =>
      integer().nullable().withDefault(const Constant(null))();

  /// Outcome: 'completed' or 'abandoned'
  TextColumn get outcome => text().withDefault(const Constant("abandoned"))();

  /// Response content (empty string for placeholders)
  TextColumn get responseContent => text().withDefault(const Constant(""))();

  /// Trigger type: 'launch' or 'retrigger'
  TextColumn get triggerType => text().withDefault(const Constant("launch"))();

  /// The Android package name that triggered this intervention
  TextColumn get appPackage => text().withDefault(const Constant(""))();

  /// Number of seconds spent in the intervention
  IntColumn get secondsSpent => integer().withDefault(const Constant(0))();

  /// Timestamp when intervention started (overlay became visible)
  DateTimeColumn get startedAt =>
      dateTime().withDefault(Constant(DateTime.now()))();

  /// Timestamp when intervention completed (overlay dismissed)
  DateTimeColumn get completedAt =>
      dateTime().nullable().withDefault(const Constant(null))();

  /// Success flag: true if intervention completed normally
  BoolColumn get success => boolean().withDefault(const Constant(false))();
}
