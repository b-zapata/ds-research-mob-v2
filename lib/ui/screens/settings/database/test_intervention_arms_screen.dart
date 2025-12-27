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
import 'package:mindful/core/enums/intervention_arm.dart';
import 'package:mindful/core/utils/widget_utils.dart';
import 'package:mindful/ui/common/default_list_tile.dart';
import 'package:mindful/ui/screens/settings/database/test_intervention_prompts_screen.dart';

class TestInterventionArmsScreen extends StatelessWidget {
  const TestInterventionArmsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final arms = [
      InterventionArm.blank,
      InterventionArm.mindfulness,
      InterventionArm.friction,
      InterventionArm.identity,
    ];

    return SliverList.builder(
      itemCount: arms.length,
      itemBuilder: (context, index) {
        final arm = arms[index];
        return DefaultListTile(
          position: getItemPositionInList(index, arms.length),
          titleText: arm.name.toUpperCase(),
          leadingIcon: FluentIcons.beaker_20_regular,
          trailing: const Icon(FluentIcons.chevron_right_20_regular),
          onPressed: () {
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (context) => TestInterventionPromptsScreen(arm: arm),
              ),
            );
          },
        );
      },
    );
  }
}

