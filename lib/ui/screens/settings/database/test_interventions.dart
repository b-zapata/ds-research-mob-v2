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
import 'package:mindful/ui/screens/settings/database/test_intervention_arms_screen.dart';
import 'package:sliver_tools/sliver_tools.dart';

class TestInterventions extends StatelessWidget {
  const TestInterventions({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiSliver(
      children: [
        const ContentSectionHeader(title: 'Test Interventions').sliver,
        StyledText(
          'Manually test interventions by selecting a prompt to display.',
        ).sliver,
        16.vSliverBox,

        /// Test interventions button
        DefaultListTile(
          position: ItemPosition.top,
          titleText: 'Test interventions',
          subtitleText: 'Browse and test all intervention prompts',
          leadingIcon: FluentIcons.beaker_20_regular,
          trailing: const Icon(FluentIcons.chevron_right_20_regular),
          onPressed: () => showDefaultBottomSheet(
            context: context,
            headerTitle: 'Test Interventions',
            sliverBody: const TestInterventionArmsScreen(),
          ),
        ).sliver,
      ],
    );
  }
}

