// ignore_for_file: file_names

import 'package:drift/drift.dart';
import 'package:mindful/core/database/schemas/schema_versions.dart';
import 'package:mindful/core/utils/db_utils.dart';

Future<void> from9To10(Migrator m, Schema10 schema) async => await runSafe(
      "Migration(9 to 10)",
      () async {
        /// Add interventionArm column to mindful_settings_table
        /// Schema10 uses Shape27 which has interventionArm
        final mindfulSettings = schema.mindfulSettingsTable as Shape27;
        await m.addColumn(mindfulSettings, mindfulSettings.interventionArm);

        /// Create new intervention-related tables
        /// Note: SessionTable, StudyConfigTable, PromptDeliveryLogTable are new
        /// They are created automatically by drift, no manual ALTER needed
      },
    );
