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
import 'package:mindful/core/enums/item_position.dart';
import 'package:mindful/core/extensions/ext_date_time.dart';
import 'package:mindful/core/extensions/ext_num.dart';
import 'package:mindful/core/extensions/ext_widget.dart';
import 'package:mindful/core/services/drift_db_service.dart';
import 'package:mindful/core/utils/widget_utils.dart';
import 'package:mindful/ui/common/default_expandable_list_tile.dart';
import 'package:mindful/ui/common/empty_list_indicator.dart';
import 'package:mindful/ui/common/rounded_container.dart';
import 'package:mindful/ui/common/sliver_shimmer_list.dart';
import 'package:mindful/ui/common/status_label.dart';
import 'package:mindful/ui/common/styled_text.dart';

final _interventionLogsProvider = FutureProvider.autoDispose<List<PromptDeliveryLog>>(
  (ref) async {
    final allLogs = await DriftDbService.instance.driftDb.dynamicRecordsDao.getAllPromptDeliveryLogs();
    // Return last 10 by default
    return allLogs.take(10).toList();
  },
);

class SliverInterventionLogsList extends ConsumerWidget {
  const SliverInterventionLogsList({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final logs = ref.watch(_interventionLogsProvider);

    return logs.when(
      loading: () => const SliverShimmerList(includeSubtitle: true),
      error: (e, st) => const SliverShimmerList(includeSubtitle: true),
      data: (logs) => logs.isEmpty
          ? EmptyListIndicator(
              isHappy: true,
              info: 'No intervention logs yet.',
            ).sliver
          : SliverList.builder(
              itemCount: logs.length,
              itemBuilder: (context, index) {
                final log = logs[index];

                // Get app name from package
                final appName = log.appPackage.isEmpty
                    ? 'Unknown app'
                    : log.appPackage.split('.').last;

                return DefaultExpandableListTile(
                  position: getItemPositionInList(
                    index,
                    logs.length,
                  ),
                  titleText: log.startedAt.dateTimeString(context),
                  subtitleText: '$appName • ${log.outcome}',
                  content: RoundedContainer(
                    borderRadius: getBorderRadiusFromPosition(ItemPosition.mid),
                    margin: const EdgeInsets.only(top: 2),
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        /// Status labels
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            StatusLabel(
                              label: log.outcome,
                            ),
                            if (log.success)
                              StatusLabel(
                                label: 'Success',
                              ),
                            StatusLabel(
                              label: '${log.secondsSpent}s',
                            ),
                          ],
                        ),

                        12.vBox,

                        /// Details
                        _buildInfoRow('Prompt ID', log.promptIdFk),
                        _buildInfoRow('Session ID', log.sessionIdFk.toString()),
                        _buildInfoRow('App Package', log.appPackage),
                        _buildInfoRow('Trigger', log.triggerType),
                        if (log.responseContent.isNotEmpty)
                          _buildInfoRow('Response', log.responseContent),
                        if (log.completedAt != null)
                          _buildInfoRow(
                            'Completed',
                            log.completedAt!.dateTimeString(context),
                          ),
                      ],
                    ),
                  ),
                );
              },
            ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 100,
            child: Text(
              '$label:',
              style: const TextStyle(fontWeight: FontWeight.w500),
            ),
          ),
          Expanded(
            child: StyledText(value),
          ),
        ],
      ),
    );
  }
}

