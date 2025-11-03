/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'package:mindful/core/database/app_database.dart';
import 'package:mindful/core/enums/intervention_arm.dart';
import 'package:mindful/core/services/method_channel_service.dart';

/// Repository for participant data (uses MindfulSettings table)
class ParticipantRepo {
  final AppDatabase _database;

  ParticipantRepo(this._database);

  /// Get the current participant's assigned arm
  Future<InterventionArm> getAssignedArm() async {
    final uniqueDao = _database.uniqueRecordsDao;
    final settings = await uniqueDao.loadMindfulSettings();

    // Sync to SharedPreferences for Android side (in case it changed externally)
    await MethodChannelService.instance
        .setInterventionArm(settings.interventionArm.name);

    return settings.interventionArm;
  }

  /// Update the participant's arm (syncs to database AND SharedPreferences)
  Future<void> updateArm(InterventionArm arm) async {
    final uniqueDao = _database.uniqueRecordsDao;
    final currentSettings = await uniqueDao.loadMindfulSettings();

    // Update settings with new arm
    final updatedSettings = currentSettings.copyWith(interventionArm: arm);
    await uniqueDao.saveMindfulSettings(updatedSettings);

    // Sync to SharedPreferences for Android side
    await MethodChannelService.instance.setInterventionArm(arm.name);
  }
}
