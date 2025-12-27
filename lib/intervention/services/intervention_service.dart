/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'package:flutter/foundation.dart';
import 'package:mindful/core/database/app_database.dart';
import 'package:mindful/intervention/data/participant_repo.dart';
import 'package:mindful/intervention/data/prompt_repo.dart';

/// Service for handling intervention-related operations
/// Manages prompt selection, session creation, and delivery logging
class InterventionService {
  final AppDatabase _database;
  final PromptRepo _promptRepo;
  final ParticipantRepo _participantRepo;

  InterventionService(this._database)
      : _promptRepo = PromptRepo(_database),
        _participantRepo = ParticipantRepo(_database);

  /// Get a prompt for intervention
  /// Creates Session and PromptDeliveryLog, then selects and returns a prompt
  Future<Map<String, dynamic>?> getPromptForIntervention({
    required int level,
    required String appPackage,
  }) async {
    try {
      debugPrint(
          '[InterventionService] getPromptForIntervention: level=$level, appPackage=$appPackage');

      // Get current intervention arm
      final arm = await _participantRepo.getAssignedArm();
      debugPrint('[InterventionService] Current arm: ${arm.name}');

      // Select a prompt for this arm and level
      final prompt = await _promptRepo.selectPromptForIntervention(
        arm: arm,
        level: level,
      );

      if (prompt == null) {
        debugPrint(
            '[InterventionService] No prompt available for arm: ${arm.name}, level: $level');
        return null;
      }

      // Create Session
      final dynamicDao = _database.dynamicRecordsDao;
      final session = await dynamicDao.createSession(
        appPackage: appPackage,
      );
      debugPrint('[InterventionService] Created session: ${session.id}');

      // Create PromptDeliveryLog
      final promptDeliveryLog = await dynamicDao.createPromptDeliveryLog(
        sessionId: session.id,
        promptId: prompt.id,
        appPackage: appPackage,
        triggerType: 'launch', // TODO: distinguish launch vs retrigger
      );
      debugPrint(
          '[InterventionService] Created PromptDeliveryLog: ${promptDeliveryLog.id}');

      // Return combined response
      return {
        'sessionId': session.id,
        'promptDeliveryLogId': promptDeliveryLog.id,
        'promptId': prompt.id,
        'text': prompt.promptText,
        'expectedInteraction': prompt.expectedInteraction.toJsonString(),
        'minLockSeconds': prompt.minLockSeconds,
      };
    } catch (e) {
      debugPrint('[InterventionService] Error getting prompt: $e');
      rethrow;
    }
  }

  /// Report intervention completion
  /// Updates Session and PromptDeliveryLog with completion data
  Future<void> reportInterventionCompleted({
    required int sessionId,
    required int promptDeliveryLogId,
    required bool success,
    required String outcome,
    required int secondsSpent,
    required String responseContent,
  }) async {
    try {
      debugPrint(
          '[InterventionService] reportInterventionCompleted: sessionId=$sessionId, promptDeliveryLogId=$promptDeliveryLogId, success=$success, outcome=$outcome, secondsSpent=$secondsSpent, responseContent=$responseContent');

      final dynamicDao = _database.dynamicRecordsDao;
      final now = DateTime.now();

      // Update Session.endedAt
      await dynamicDao.updateSessionEndedAt(
        sessionId: sessionId,
        endedAt: now,
      );

      // Update PromptDeliveryLog completion
      await dynamicDao.updatePromptDeliveryLogCompletion(
        promptDeliveryLogId: promptDeliveryLogId,
        success: success,
        outcome: outcome,
        secondsSpent: secondsSpent,
        responseContent: responseContent,
        completedAt: now,
      );

      debugPrint('[InterventionService] Successfully updated completion data with response: $responseContent');
    } catch (e) {
      debugPrint('[InterventionService] Error reporting completion: $e');
      rethrow;
    }
  }
}
