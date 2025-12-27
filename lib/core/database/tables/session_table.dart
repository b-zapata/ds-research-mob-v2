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

@DataClassName("Session")
class SessionTable extends Table {
  /// Unique ID for session (auto-increment)
  IntColumn get id => integer().autoIncrement()();

  /// The day this session belongs to (date only, time truncated)
  DateTimeColumn get date => dateTime().withDefault(Constant(DateTime(0)))();

  /// Timestamp when session was created
  DateTimeColumn get createdAt =>
      dateTime().withDefault(Constant(DateTime.now()))();

  /// Timestamp when session was last updated
  DateTimeColumn get updatedAt =>
      dateTime().withDefault(Constant(DateTime.now()))();

  /// The Android package name for the app this session is tracking
  TextColumn get appPackage => text().withDefault(const Constant(""))();

  /// Timestamp when session started (intervention began)
  DateTimeColumn get startedAt =>
      dateTime().withDefault(Constant(DateTime.now()))();

  /// Timestamp when session ended (intervention completed), null if still active
  DateTimeColumn get endedAt =>
      dateTime().nullable().withDefault(const Constant(null))();
}

