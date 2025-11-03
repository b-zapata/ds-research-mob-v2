/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'package:drift/drift.dart';

enum InterventionArm {
  blank,
  mindfulness,
  friction,
  identity;

  static InterventionArm fromString(String value) {
    switch (value.toLowerCase()) {
      case 'blank':
        return InterventionArm.blank;
      case 'mindfulness':
      case 'mindful':
        return InterventionArm.mindfulness;
      case 'friction':
        return InterventionArm.friction;
      case 'identity':
        return InterventionArm.identity;
      default:
        return InterventionArm.blank;
    }
  }
}

// Drift converter for InterventionArm
class InterventionArmConverter extends TypeConverter<InterventionArm, int> {
  const InterventionArmConverter();

  @override
  InterventionArm fromSql(int fromDb) {
    return InterventionArm.values[fromDb];
  }

  @override
  int toSql(InterventionArm value) {
    return value.index;
  }
}

