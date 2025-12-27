import 'package:flutter/material.dart';
import 'package:mindful/core/services/drift_db_service.dart';
import 'package:mindful/core/services/method_channel_service.dart';
import 'package:mindful/intervention/data/participant_repo.dart';
import 'package:mindful/intervention/data/prompt_repo.dart';

/// Initializer to initialize necessary things.
class Initializer {
  /// Initializes all the required services and schedules.
  ///
  /// This method must be called after initializing `DATABASE` and `METHOD CHANNEL`.
  static Future<void> initializeServicesAndSchedules() async {
    final startTimeStamp = DateTime.now();

    final dynamicDao = DriftDbService.instance.driftDb.dynamicRecordsDao;
    final uniqueDao = DriftDbService.instance.driftDb.uniqueRecordsDao;

    /// Initialize intervention arm (syncs to SharedPreferences for Android)
    /// The default is identity - loaded from MindfulSettings table
    try {
      final participantRepo = ParticipantRepo(DriftDbService.instance.driftDb);
      await participantRepo
          .getAssignedArm(); // Loads from MindfulSettings, syncs to SharedPreferences
    } catch (e) {
      debugPrint(
          '[Intervention] Error loading participant arm (database not migrated yet): $e');
      // Set default arm in SharedPreferences anyway
      await MethodChannelService.instance.setInterventionArm('blank');
      // ====== The arm options are: blank, mindfulness, friction, identity ======
    }

    /// Ensure tracker service is running for interventions
    await MethodChannelService.instance.ensureTrackerServiceRunning();

    /// Load prompts from asset file if not already loaded
    /// This loads prompts for ALL arms into the database
    try {
      final promptRepo = PromptRepo(DriftDbService.instance.driftDb);
      final dynamicDao = DriftDbService.instance.driftDb.dynamicRecordsDao;
      
      // Check if ANY prompts exist (for any arm)
      bool hasAnyPrompts = false;
      for (final arm in [0, 1, 2, 3]) {
        if (await dynamicDao.hasPromptsForGroup(arm)) {
          hasAnyPrompts = true;
          break;
        }
      }
      
      if (!hasAnyPrompts) {
        debugPrint('[Initializer] No prompts found, loading from asset file...');
        await promptRepo.loadPromptsFromAsset();
        debugPrint('[Initializer] Successfully loaded all prompts from asset');
      } else {
        debugPrint('[Initializer] Prompts already loaded, skipping asset load');
      }
    } catch (e) {
      debugPrint('[Initializer] Error loading prompts: $e');
      // Don't fail initialization if prompts fail to load
    }

    /// fetch app restrictions
    var appRestrictions = await dynamicDao.fetchAppsRestrictions();
    final internetBlockedApps = appRestrictions
        .where((e) => !e.canAccessInternet)
        .map((e) => e.appPackage)
        .toList();

    /// filter out restrictions
    appRestrictions.removeWhere(
      (e) =>
          e.timerSec <= 0 &&
          e.periodDurationInMins <= 0 &&
          e.launchLimit <= 0 &&
          e.associatedGroupId == null,
    );

    /// update tracker service
    await MethodChannelService.instance.updateAppRestrictions(appRestrictions);

    /// update vpn service
    await MethodChannelService.instance
        .updateInternetBlockedApps(internetBlockedApps);

    /// Update restriction groups
    final restrictionGroups = await dynamicDao.fetchRestrictionGroups();
    await MethodChannelService.instance
        .updateRestrictionsGroups(restrictionGroups);

    /// Fetch and update bedtime routine
    final bedtime = await uniqueDao.loadBedtimeSchedule();
    await MethodChannelService.instance.updateBedtimeSchedule(bedtime);

    /// Fetch and update wellbeing
    final wellbeing = await uniqueDao.loadWellBeingSettings();
    await MethodChannelService.instance.updateWellBeingSettings(wellbeing);

    /// Fetch and update notification settings
    final notificationSettings = await uniqueDao.loadNotificationSettings();
    await MethodChannelService.instance
        .updateNotificationSettings(notificationSettings);

    debugPrint(
      "All necessary services and schedules are initialized and it took ${DateTime.now().difference(startTimeStamp).inMilliseconds}ms.",
    );
  }
}
