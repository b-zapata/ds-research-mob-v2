/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'package:fluentui_system_icons/fluentui_system_icons.dart';
import 'package:flutter/material.dart';
import 'package:mindful/core/enums/item_position.dart';
import 'package:mindful/core/extensions/ext_num.dart';
import 'package:mindful/core/extensions/ext_widget.dart';
import 'package:mindful/ui/common/content_section_header.dart';
import 'package:mindful/ui/common/default_list_tile.dart';
import 'package:mindful/ui/common/styled_text.dart';
import 'package:mindful/ui/dialogs/modal_bottom_sheet.dart';
import 'package:mindful/ui/screens/settings/database/sliver_intervention_logs_list.dart';
import 'package:sliver_tools/sliver_tools.dart';

class ViewInterventionLogs extends StatelessWidget {
  const ViewInterventionLogs({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiSliver(
      children: [
        const ContentSectionHeader(title: 'Intervention Logs').sliver,
        StyledText(
          'View the last 10 intervention prompts that were shown to you, including their outcomes and responses.',
        ).sliver,
        16.vSliverBox,

        /// View logs
        DefaultListTile(
          position: ItemPosition.top,
          titleText: 'View interventions',
          subtitleText: 'Explore stored intervention logs',
          leadingIcon: FluentIcons.notepad_20_regular,
          trailing: const Icon(FluentIcons.chevron_right_20_regular),
          onPressed: () => showDefaultBottomSheet(
            context: context,
            headerTitle: 'Intervention Logs',
            sliverBody: const SliverInterventionLogsList(),
          ),
        ).sliver,
      ],
    );
  }
}

