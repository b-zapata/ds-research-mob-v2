/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mindful/core/database/app_database.dart';
import 'package:mindful/core/enums/intervention_arm.dart';
import 'package:mindful/core/extensions/ext_widget.dart';
import 'package:mindful/core/services/drift_db_service.dart';
import 'package:mindful/core/services/method_channel_service.dart';
import 'package:mindful/core/utils/widget_utils.dart';
import 'package:mindful/ui/common/content_section_header.dart';
import 'package:mindful/ui/common/default_list_tile.dart';
import 'package:mindful/ui/common/sliver_shimmer_list.dart';
import 'package:mindful/ui/common/sliver_tabs_bottom_padding.dart';

final _promptsProvider = FutureProvider.family<List<Prompt>, InterventionArm>(
  (ref, arm) async {
    final dynamicDao = DriftDbService.instance.driftDb.dynamicRecordsDao;
    return await dynamicDao.getActivePromptsByGroup(arm.index);
  },
);

class TestInterventionPromptsScreen extends ConsumerWidget {
  final InterventionArm arm;

  const TestInterventionPromptsScreen({
    super.key,
    required this.arm,
  });

  Future<void> _triggerIntervention(BuildContext context, Prompt prompt) async {
    try {
      // Use a test app package for manual testing
      const testAppPackage = 'com.mindful.android.debug';

      // Create session
      final dynamicDao = DriftDbService.instance.driftDb.dynamicRecordsDao;
      final session = await dynamicDao.createSession(
        appPackage: testAppPackage,
      );

      // Create prompt delivery log
      final promptDeliveryLog = await dynamicDao.createPromptDeliveryLog(
        sessionId: session.id,
        promptId: prompt.id,
        appPackage: testAppPackage,
        triggerType: 'manual_test',
      );

      // Prepare prompt data for Android
      final promptData = {
        'sessionId': session.id,
        'promptDeliveryLogId': promptDeliveryLog.id,
        'promptId': prompt.id,
        'text': prompt.promptText,
        'expectedInteraction': prompt.expectedInteraction.toJsonString(),
        'minLockSeconds': prompt.minLockSeconds,
        'appPackage': testAppPackage,
      };

      // Call Android to show the intervention overlay
      await MethodChannelService.instance.showTestIntervention(promptData);

      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Intervention triggered'),
            duration: Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Get prompts for the selected arm
    final promptsAsync = ref.watch(_promptsProvider(arm));

    return Scaffold(
      appBar: AppBar(
        title: Text('${arm.name.toUpperCase()} Prompts'),
      ),
      body: promptsAsync.when(
        loading: () => const SliverShimmerList(includeSubtitle: true),
        error: (e, st) => CustomScrollView(
          slivers: [
            ContentSectionHeader(title: 'Error').sliver,
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Text('Error loading prompts: $e'),
              ),
            ),
          ],
        ),
        data: (prompts) {
          // Group prompts by level
          final promptsByLevel = <int, List<Prompt>>{};
          for (final prompt in prompts) {
            promptsByLevel.putIfAbsent(prompt.level, () => []).add(prompt);
          }

          final levels = promptsByLevel.keys.toList()..sort();

          return CustomScrollView(
            physics: const BouncingScrollPhysics(),
            slivers: [
              if (prompts.isEmpty)
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Text('No prompts available for ${arm.name}'),
                  ),
                )
              else
                ...levels.map((level) {
                  final levelPrompts = promptsByLevel[level]!;
                  return [
                    ContentSectionHeader(
                      title: 'Level $level (${levelPrompts.length} prompts)',
                    ).sliver,
                    SliverList.builder(
                      itemCount: levelPrompts.length,
                      itemBuilder: (context, index) {
                        final prompt = levelPrompts[index];
                        return DefaultListTile(
                          position: getItemPositionInList(
                            index,
                            levelPrompts.length,
                          ),
                          titleText: prompt.promptText,
                          subtitleText: 'ID: ${prompt.id} | ${prompt.expectedInteraction.toJsonString()}',
                          onPressed: () => _triggerIntervention(context, prompt),
                        );
                      },
                    ),
                  ];
                }).expand((x) => x),
              const SliverTabsBottomPadding(),
            ],
          );
        },
      ),
    );
  }
}

