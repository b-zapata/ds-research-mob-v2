/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'package:fluentui_system_icons/fluentui_system_icons.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mindful/core/enums/intervention_arm.dart';
import 'package:mindful/core/enums/item_position.dart';
import 'package:mindful/core/extensions/ext_widget.dart';
import 'package:mindful/providers/system/mindful_settings_provider.dart';
import 'package:mindful/ui/common/content_section_header.dart';
import 'package:mindful/ui/common/default_dropdown_tile.dart';
import 'package:mindful/ui/common/sliver_tabs_bottom_padding.dart';
import 'package:mindful/ui/screens/settings/database/export_clear_crash_logs.dart';
import 'package:mindful/ui/screens/settings/database/import_export_db.dart';
import 'package:mindful/ui/screens/settings/database/test_interventions.dart';
import 'package:mindful/ui/screens/settings/database/view_intervention_logs.dart';

class TabDatabase extends ConsumerWidget {
  const TabDatabase({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mindfulSettings = ref.watch(mindfulSettingsProvider);

    return CustomScrollView(
      physics: const BouncingScrollPhysics(),
      slivers: [
        /// Intervention Settings
        const ContentSectionHeader(
          title: 'Intervention Settings',
        ).sliver,

        /// Intervention Arm
        DefaultDropdownTile<InterventionArm>(
          position: ItemPosition.top,
          value: mindfulSettings.interventionArm,
          dialogIcon: FluentIcons.settings_20_filled,
          titleText: 'Intervention Arm',
          onSelected: ref.read(mindfulSettingsProvider.notifier).changeInterventionArm,
          items: [
            DefaultDropdownItem(
              label: 'Blank',
              value: InterventionArm.blank,
            ),
            DefaultDropdownItem(
              label: 'Mindfulness',
              value: InterventionArm.mindfulness,
            ),
            DefaultDropdownItem(
              label: 'Friction',
              value: InterventionArm.friction,
            ),
            DefaultDropdownItem(
              label: 'Identity',
              value: InterventionArm.identity,
            ),
          ],
        ).sliver,

        /// Backup, restore and reset
        const ImportExportDb(),

        /// Crash logs
        const ExportClearCrashLogs(),

        /// Intervention logs
        const ViewInterventionLogs(),

        /// Test interventions
        const TestInterventions(),

        const SliverTabsBottomPadding()
      ],
    );
  }
}
