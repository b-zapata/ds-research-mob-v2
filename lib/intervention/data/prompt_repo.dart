/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:mindful/core/database/app_database.dart';
import 'package:mindful/core/enums/expected_interaction.dart';
import 'package:mindful/core/enums/intervention_arm.dart';
import 'package:mindful/intervention/data/models/prompt_server_response.dart';
import 'package:mindful/intervention/data/participant_repo.dart';

/// Repository for managing intervention prompts
/// Handles syncing prompts from server and storing them locally
class PromptRepo {
  final AppDatabase _database;
  final ParticipantRepo _participantRepo;

  PromptRepo(this._database)
      : _participantRepo = ParticipantRepo(_database);

  /// Extract placeholders from prompt text (e.g., "{goal_1}", "{top_value_1}")
  /// Returns a list of placeholder names without the curly braces
  static List<String> extractPlaceholders(String text) {
    final regex = RegExp(r'\{([^}]+)\}');
    final matches = regex.allMatches(text);
    return matches.map((match) => match.group(1) ?? '').toList();
  }

  /// Sync prompts from server response for a specific intervention arm
  /// This will replace all existing prompts for the given arm
  Future<void> syncPromptsFromServer({
    required InterventionArm arm,
    required PromptLibraryResponse serverResponse,
  }) async {
    final dynamicDao = _database.dynamicRecordsDao;

    // Delete existing prompts for this arm
    await dynamicDao.deletePromptsByGroup(arm.index);

    // Convert server response to Prompt entities and insert
    final prompts = <Prompt>[];
    
    for (final group in serverResponse.promptLibrary) {
      // Only process prompts for the requested arm
      final groupArm = InterventionArm.fromString(group.group);
      if (groupArm != arm) continue;

      for (var i = 0; i < group.prompts.length; i++) {
        final item = group.prompts[i];
        
        // Generate ID if not provided: "{group}_{level}_{index}"
        final promptId = item.id.isNotEmpty
            ? item.id
            : '${group.group}_${group.level}_$i';

        // Extract placeholders from text
        final placeholders = extractPlaceholders(item.text);
        final placeholdersJson = jsonEncode(placeholders);

        // Create Prompt entity using Companion for insert
        final prompt = Prompt(
          id: promptId,
          group: arm,
          level: group.level,
          promptText: item.text,
          minLockSeconds: item.minLockSeconds,
          expectedInteraction: ExpectedInteraction.fromString(
            item.expectedInteraction,
          ),
          active: true,
          updatedAt: DateTime.now(),
          requiredPlaceholders: placeholdersJson,
        );

        prompts.add(prompt);
      }
    }

    // Batch insert all prompts
    if (prompts.isNotEmpty) {
      await dynamicDao.upsertPrompts(prompts);
      debugPrint(
          '[PromptRepo] Synced ${prompts.length} prompts for arm: ${arm.name}');
    } else {
      debugPrint('[PromptRepo] No prompts to sync for arm: ${arm.name}');
    }
  }

  /// Sync prompts from JSON string (for testing or local import)
  Future<void> syncPromptsFromJson({
    required InterventionArm arm,
    required String jsonString,
  }) async {
    final json = jsonDecode(jsonString) as Map<String, dynamic>;
    final response = PromptLibraryResponse.fromJson(json);
    await syncPromptsFromServer(arm: arm, serverResponse: response);
  }

  /// Check if prompts exist for the current participant's arm
  Future<bool> hasPromptsForCurrentArm() async {
    final arm = await _participantRepo.getAssignedArm();
    final dynamicDao = _database.dynamicRecordsDao;
    return await dynamicDao.hasPromptsForGroup(arm.index);
  }

  /// Get all active prompts for the current participant's arm
  Future<List<Prompt>> getPromptsForCurrentArm() async {
    final arm = await _participantRepo.getAssignedArm();
    final dynamicDao = _database.dynamicRecordsDao;
    return await dynamicDao.getActivePromptsByGroup(arm.index);
  }

  /// Get all active prompts for a specific arm and level
  Future<List<Prompt>> getPromptsByArmAndLevel({
    required InterventionArm arm,
    required int level,
  }) async {
    final dynamicDao = _database.dynamicRecordsDao;
    return await dynamicDao.getActivePromptsByGroupAndLevel(
      groupIndex: arm.index,
      level: level,
    );
  }

  /// Get a specific prompt by ID
  Future<Prompt?> getPromptById(String promptId) async {
    final dynamicDao = _database.dynamicRecordsDao;
    return await dynamicDao.getPromptById(promptId);
  }

  /// Resync prompts when participant arm changes
  /// This will delete old prompts and should be followed by a sync from server
  Future<void> clearPromptsForArm(InterventionArm arm) async {
    final dynamicDao = _database.dynamicRecordsDao;
    await dynamicDao.deletePromptsByGroup(arm.index);
    debugPrint('[PromptRepo] Cleared prompts for arm: ${arm.name}');
  }

  /// Select a prompt for intervention using random without replacement
  /// Returns null if no prompts available for the given arm and level
  Future<Prompt?> selectPromptForIntervention({
    required InterventionArm arm,
    required int level,
  }) async {
    try {
      final dynamicDao = _database.dynamicRecordsDao;
      
      // Get all active prompts for this arm and level
      final allPrompts = await dynamicDao.getActivePromptsByGroupAndLevel(
        groupIndex: arm.index,
        level: level,
      );

      if (allPrompts.isEmpty) {
        debugPrint('[PromptRepo] No prompts available for arm: ${arm.name}, level: $level');
        return null;
      }

      // Get participant ID (from MindfulSettingsTable.id)
      final uniqueDao = _database.uniqueRecordsDao;
      final settings = await uniqueDao.loadMindfulSettings();
      final participantId = settings.id;

      // Get prompt IDs that have already been shown for this (arm, level)
      final shownPromptIds = await dynamicDao.getShownPromptIds(
        participantId: participantId,
        arm: arm,
        level: level,
      );

      // Filter out already-shown prompts
      final availablePrompts = allPrompts
          .where((p) => !shownPromptIds.contains(p.id))
          .toList();

      // If all prompts have been shown, reset the pool (make all available again)
      if (availablePrompts.isEmpty) {
        debugPrint(
            '[PromptRepo] All prompts shown for ${arm.name} level $level, resetting pool');
        final selectedPrompt = allPrompts[
            (DateTime.now().millisecondsSinceEpoch % allPrompts.length)];
        return selectedPrompt;
      }

      // Randomly select from available prompts
      final random = DateTime.now().millisecondsSinceEpoch % availablePrompts.length;
      final selectedPrompt = availablePrompts[random];

      debugPrint(
          '[PromptRepo] Selected prompt: ${selectedPrompt.id} for ${arm.name} level $level (${availablePrompts.length} available, ${shownPromptIds.length} already shown)');

      return selectedPrompt;
    } catch (e) {
      debugPrint('[PromptRepo] Error selecting prompt: $e');
      rethrow;
    }
  }

  /// Load all prompts from local JSON asset file
  /// This loads prompts for ALL arms into the database
  Future<void> loadPromptsFromAsset() async {
    try {
      debugPrint('[PromptRepo] Loading prompts from asset file...');
      
      // Load JSON from asset
      final jsonString = await rootBundle.loadString('assets/prompt_library.json');
      final json = jsonDecode(jsonString) as Map<String, dynamic>;
      final response = PromptLibraryResponse.fromJson(json);

      // Load prompts for each arm
      final allArms = InterventionArm.values;
      final dynamicDao = _database.dynamicRecordsDao;

      for (final arm in allArms) {
        // Delete existing prompts for this arm (in case of re-sync)
        await dynamicDao.deletePromptsByGroup(arm.index);

        // Convert and insert prompts for this arm
        final prompts = <Prompt>[];
        
        for (final group in response.promptLibrary) {
          // Only process prompts for the current arm
          final groupArm = InterventionArm.fromString(group.group);
          if (groupArm != arm) continue;

          for (var i = 0; i < group.prompts.length; i++) {
            final item = group.prompts[i];
            
            // Generate ID if not provided: "{group}_{level}_{index}"
            final promptId = item.id.isNotEmpty
                ? item.id
                : '${group.group}_${group.level}_$i';

            // Extract placeholders from text
            final placeholders = extractPlaceholders(item.text);
            final placeholdersJson = jsonEncode(placeholders);

            // Create Prompt entity
            final prompt = Prompt(
              id: promptId,
              group: arm,
              level: group.level,
              promptText: item.text,
              minLockSeconds: item.minLockSeconds,
              expectedInteraction: ExpectedInteraction.fromString(
                item.expectedInteraction,
              ),
              active: true,
              updatedAt: DateTime.now(),
              requiredPlaceholders: placeholdersJson,
            );

            prompts.add(prompt);
          }
        }

        // Batch insert all prompts for this arm
        if (prompts.isNotEmpty) {
          await dynamicDao.upsertPrompts(prompts);
          debugPrint(
              '[PromptRepo] Loaded ${prompts.length} prompts for arm: ${arm.name}');
        }
      }

      debugPrint('[PromptRepo] Successfully loaded all prompts from asset file');
    } catch (e) {
      debugPrint('[PromptRepo] Error loading prompts from asset: $e');
      rethrow;
    }
  }
}
