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

@DataClassName("StudyConfig")
class StudyConfigTable extends Table {
  /// Unique ID for study config (should always be singleton: ID = 1)
  IntColumn get id => integer().withDefault(const Constant(1))();

  @override
  Set<Column<Object>>? get primaryKey => {id};

  /// JSON string containing runtime parameters
  /// Expected structure: {"flaggedPackages": [...], "retriggerMinutes": number}
  TextColumn get parameters => text().withDefault(const Constant("{}"))();
}

