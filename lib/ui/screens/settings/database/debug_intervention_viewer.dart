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
import 'package:mindful/core/services/drift_db_service.dart';
import 'package:mindful/ui/common/content_section_header.dart';
import 'package:mindful/ui/common/default_list_tile.dart';
import 'package:mindful/ui/common/sliver_tabs_bottom_padding.dart';
import 'package:mindful/core/extensions/ext_widget.dart';

class DebugInterventionViewer extends ConsumerStatefulWidget {
  const DebugInterventionViewer({super.key});

  @override
  ConsumerState<DebugInterventionViewer> createState() =>
      _DebugInterventionViewerState();
}

class _DebugInterventionViewerState
    extends ConsumerState<DebugInterventionViewer> {
  List<PromptDeliveryLog>? _logs;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _loadLogs();
  }

  Future<void> _loadLogs() async {
    setState(() => _isLoading = true);
    try {
      final dao = DriftDbService.instance.driftDb.dynamicRecordsDao;
      // Get all logs, ordered by most recent
      final allLogs = await dao.getAllPromptDeliveryLogs();
      setState(() {
        _logs = allLogs;
        _isLoading = false;
      });
    } catch (e) {
      setState(() => _isLoading = false);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error loading logs: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return CustomScrollView(
      physics: const BouncingScrollPhysics(),
      slivers: [
        const ContentSectionHeader(
          title: 'Intervention Debug Viewer',
        ).sliver,
        DefaultListTile(
          position: ItemPosition.top,
          titleText: 'Refresh',
          trailing: _isLoading
              ? const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Icon(Icons.refresh),
          onPressed: _loadLogs,
        ).sliver,
        if (_logs != null) ...[
          DefaultListTile(
            position: ItemPosition.mid,
            titleText: 'Total Logs: ${_logs!.length}',
            enabled: false,
          ).sliver,
          if (_logs!.isEmpty)
            const Padding(
              padding: EdgeInsets.all(16.0),
              child: Text('No intervention logs yet.'),
            ).sliver
          else
            SliverList(
              delegate: SliverChildBuilderDelegate(
                (context, index) {
                  final log = _logs![index];
                  return Card(
                    margin: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
                    child: ExpansionTile(
                      title: Text(
                        'Log #${log.id}',
                        style: const TextStyle(fontWeight: FontWeight.bold),
                      ),
                      subtitle: Text(
                        log.appPackage.isEmpty
                            ? 'Unknown app'
                            : log.appPackage.split('.').last,
                      ),
                      children: [
                        Padding(
                          padding: const EdgeInsets.all(16.0),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              _buildInfoRow('Session ID', log.sessionIdFk.toString()),
                              _buildInfoRow('Prompt ID', log.promptIdFk),
                              _buildInfoRow('App Package', log.appPackage),
                              _buildInfoRow('Outcome', log.outcome),
                              _buildInfoRow('Success', log.success ? 'Yes' : 'No'),
                              _buildInfoRow('Seconds Spent', log.secondsSpent.toString()),
                              _buildInfoRow(
                                'Response',
                                log.responseContent.isEmpty
                                    ? '(empty)'
                                    : log.responseContent,
                              ),
                              _buildInfoRow(
                                'Started',
                                log.startedAt.toString().substring(0, 19),
                              ),
                              if (log.completedAt != null)
                                _buildInfoRow(
                                  'Completed',
                                  log.completedAt!
                                      .toString()
                                      .substring(0, 19),
                                ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  );
                },
                childCount: _logs!.length,
              ),
            ),
        ],
        const SliverTabsBottomPadding(),
      ],
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              '$label:',
              style: const TextStyle(fontWeight: FontWeight.w500),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(color: Colors.grey),
            ),
          ),
        ],
      ),
    );
  }
}

