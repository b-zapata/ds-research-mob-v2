/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'dart:math';
import 'package:flutter/foundation.dart';
import 'package:mindful/core/database/app_database.dart';
import 'package:mindful/core/enums/intervention_arm.dart';
import 'package:mindful/intervention/data/prompt_repo.dart';

/// Engine for selecting prompts using random without replacement per (arm, level)
/// Resets pool when all prompts have been used
class PromptSelectionEngine {
  final AppDatabase _database;
  final PromptRepo _promptRepo;
  final Random _random = Random();

  PromptSelectionEngine(this._database) : _promptRepo = PromptRepo(_database);

  /// Selects a prompt for the given arm and level using random without replacement
  /// Returns null if no prompts available
  Future<Prompt?> selectPrompt({
    required InterventionArm arm,
    required int level,
    required int participantId, // From MindfulSettingsTable.id
  }) async {
    try {
      // Get all active prompts for this arm and level
      final availablePrompts = await _promptRepo.getPromptsByArmAndLevel(
        arm: arm,
        level: level,
      );

      if (availablePrompts.isEmpty) {
        debugPrint(
          '[PromptSelectionEngine] No prompts available for arm=${arm.name}, level=$level',
        );
        return null;
      }

      // Get all prompt IDs that have been shown to this participant at this (arm, level)
      final dynamicDao = _database.dynamicRecordsDao;
      final shownPromptIds = await dynamicDao.getShownPromptIds(
        participantId: participantId,
        arm: arm,
        level: level,
      );

      // Filter out prompts that have been shown
      final unshownPrompts = availablePrompts
          .where((p) => !shownPromptIds.contains(p.id))
          .toList();

      // If all prompts have been shown, reset the pool
      List<Prompt> promptsToSelectFrom;
      if (unshownPrompts.isEmpty) {
        debugPrint(
          '[PromptSelectionEngine] All prompts shown for arm=${arm.name}, level=$level, resetting pool',
        );
        promptsToSelectFrom = availablePrompts;
      } else {
        promptsToSelectFrom = unshownPrompts;
      }

      // Randomly select one prompt
      if (promptsToSelectFrom.isEmpty) {
        return null;
      }

      final selectedPrompt =
          promptsToSelectFrom[_random.nextInt(promptsToSelectFrom.length)];

      debugPrint(
        '[PromptSelectionEngine] Selected prompt: ${selectedPrompt.id} for arm=${arm.name}, level=$level',
      );

      return selectedPrompt;
    } catch (e) {
      debugPrint('[PromptSelectionEngine] Error selecting prompt: $e');
      return null;
    }
  }
}
