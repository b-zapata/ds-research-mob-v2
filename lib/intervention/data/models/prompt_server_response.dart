/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

/// Server response models for prompt library sync
/// These models represent the JSON structure from the server

class PromptLibraryResponse {
  final List<PromptGroup> promptLibrary;

  PromptLibraryResponse({required this.promptLibrary});

  factory PromptLibraryResponse.fromJson(Map<String, dynamic> json) {
    return PromptLibraryResponse(
      promptLibrary: (json['prompt_library'] as List)
          .map((item) => PromptGroup.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }
}

class PromptGroup {
  final String group; // "identity", "mindfulness", "friction", "blank"
  final int level; // 1, 2, or 3
  final List<PromptItem> prompts;

  PromptGroup({
    required this.group,
    required this.level,
    required this.prompts,
  });

  factory PromptGroup.fromJson(Map<String, dynamic> json) {
    // Handle simplified blank pause structure (no prompts array)
    if (json['min_lock_seconds'] != null && json['prompts'] == null) {
      // This is a simplified blank pause entry
      final group = json['group'] as String;
      final level = json['level'] as int;
      return PromptGroup(
        group: group,
        level: level,
        prompts: [
          PromptItem(
            id: '${group}_${level}',
            text: '', // Blank pause has no text
            minLockSeconds: json['min_lock_seconds'] as int,
            expectedInteraction: json['expected_interaction'] as String,
          ),
        ],
      );
    }

    // Normal structure with prompts array
    return PromptGroup(
      group: json['group'] as String,
      level: json['level'] as int,
      prompts: (json['prompts'] as List)
          .map((item) => PromptItem.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }
}

class PromptItem {
  final String id; // Unique identifier (e.g., "identity_1_1")
  final String text; // Prompt text with placeholders
  final int minLockSeconds;
  final String expectedInteraction; // Interaction type string

  PromptItem({
    required this.id,
    required this.text,
    required this.minLockSeconds,
    required this.expectedInteraction,
  });

  factory PromptItem.fromJson(Map<String, dynamic> json) {
    return PromptItem(
      id: json['id'] as String? ??
          '${json['group']}_${json['level']}_${json['index'] ?? 0}',
      text: json['text'] as String,
      minLockSeconds: json['min_lock_seconds'] as int,
      expectedInteraction: json['expected_interaction'] as String,
    );
  }
}
