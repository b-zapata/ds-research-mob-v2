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
import 'package:mindful/core/enums/expected_interaction.dart';
import 'package:mindful/core/enums/intervention_arm.dart';

@DataClassName("Prompt")
class PromptTable extends Table {
  /// Unique string ID for prompt (e.g., "identity_1_1", "mindfulness_2_3")
  /// Not auto-increment - IDs come from server to maintain consistency
  TextColumn get id => text()();

  @override
  Set<Column> get primaryKey => {id};

  /// Intervention group/arm this prompt belongs to
  /// Maps to InterventionArm enum (identity, mindfulness, friction, blank)
  IntColumn get group => intEnum<InterventionArm>()();

  /// Level of the prompt (1 = quick, 2 = moderate, 3 = longer)
  IntColumn get level => integer()();

  /// Prompt text content (may contain placeholders like {goal_1}, {top_value_1})
  TextColumn get promptText => text()();

  /// Minimum lock time in seconds (how long user must wait/interact)
  IntColumn get minLockSeconds => integer()();

  /// Expected interaction type for this prompt
  TextColumn get expectedInteraction => text()
      .map(const ExpectedInteractionConverter())
      .withDefault(const Constant('wait_out'))();

  /// Whether this prompt is active and should be shown
  BoolColumn get active => boolean().withDefault(const Constant(true))();

  /// Timestamp when prompt was last updated (for sync/versioning)
  DateTimeColumn get updatedAt =>
      dateTime().withDefault(Constant(DateTime.now()))();

  /// Optional: JSON array of required placeholders extracted from text
  /// e.g., ["goal_1", "top_value_1", "best_self_description"]
  /// This helps validate that all placeholders are available before showing prompt
  TextColumn get requiredPlaceholders =>
      text().withDefault(const Constant("[]"))(); // JSON array as string
}
