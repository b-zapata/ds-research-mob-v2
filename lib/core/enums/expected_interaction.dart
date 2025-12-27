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

/// Types of user interactions expected for intervention prompts
enum ExpectedInteraction {
  /// Yes/No button interaction
  yesNo,

  /// Wait for timeout (no user action required)
  waitOut,

  /// Tap and hold interaction
  tapHold,

  /// Slider interaction
  slider,

  /// Single tap interaction
  tap,

  /// Sequence of taps in order
  tapSequence,

  /// Type a specific phrase
  typePhrase,

  /// Combination of multiple interactions
  combo,

  /// Multiple choice selection
  multipleChoice,

  /// Short text input
  shortText,

  /// Tap at middle and end of wait period
  tapMidAndEnd,

  /// Tap breathing bubble animation
  tapBreathingBubble;

  /// Convert string to ExpectedInteraction enum
  static ExpectedInteraction fromString(String value) {
    switch (value.toLowerCase().replaceAll('_', '')) {
      case 'yesno':
      case 'yes_no':
        return ExpectedInteraction.yesNo;
      case 'waitout':
      case 'wait_out':
        return ExpectedInteraction.waitOut;
      case 'taphold':
      case 'tap_hold':
        return ExpectedInteraction.tapHold;
      case 'slider':
        return ExpectedInteraction.slider;
      case 'tap':
        return ExpectedInteraction.tap;
      case 'tapsequence':
      case 'tap_sequence':
        return ExpectedInteraction.tapSequence;
      case 'typephrase':
      case 'type_phrase':
        return ExpectedInteraction.typePhrase;
      case 'combo':
        return ExpectedInteraction.combo;
      case 'multiplechoice':
      case 'multiple_choice':
        return ExpectedInteraction.multipleChoice;
      case 'shorttext':
      case 'short_text':
        return ExpectedInteraction.shortText;
      case 'tapmidandend':
      case 'tap_mid_and_end':
        return ExpectedInteraction.tapMidAndEnd;
      case 'tapbreathingbubble':
      case 'tap_breathing_bubble':
        return ExpectedInteraction.tapBreathingBubble;
      default:
        return ExpectedInteraction.waitOut; // Default fallback
    }
  }

  /// Convert enum to string for JSON/serialization
  String toJsonString() {
    switch (this) {
      case ExpectedInteraction.yesNo:
        return 'yes_no';
      case ExpectedInteraction.waitOut:
        return 'wait_out';
      case ExpectedInteraction.tapHold:
        return 'tap_hold';
      case ExpectedInteraction.slider:
        return 'slider';
      case ExpectedInteraction.tap:
        return 'tap';
      case ExpectedInteraction.tapSequence:
        return 'tap_sequence';
      case ExpectedInteraction.typePhrase:
        return 'type_phrase';
      case ExpectedInteraction.combo:
        return 'combo';
      case ExpectedInteraction.multipleChoice:
        return 'multiple_choice';
      case ExpectedInteraction.shortText:
        return 'short_text';
      case ExpectedInteraction.tapMidAndEnd:
        return 'tap_mid_and_end';
      case ExpectedInteraction.tapBreathingBubble:
        return 'tap_breathing_bubble';
    }
  }
}

/// Drift converter for ExpectedInteraction enum
class ExpectedInteractionConverter extends TypeConverter<ExpectedInteraction, String> {
  const ExpectedInteractionConverter();

  @override
  ExpectedInteraction fromSql(String fromDb) {
    return ExpectedInteraction.fromString(fromDb);
  }

  @override
  String toSql(ExpectedInteraction value) {
    return value.toJsonString();
  }
}
