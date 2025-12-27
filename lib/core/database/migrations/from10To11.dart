// ignore_for_file: file_names

import 'package:drift/drift.dart';
import 'package:mindful/core/database/schemas/schema_versions.dart';
import 'package:mindful/core/utils/db_utils.dart';

Future<void> from10To11(Migrator m, Schema11 schema) async => await runSafe(
      "Migration(10 to 11)",
      () async {
        /// Create prompt_table
        await m.createTable(schema.promptTable);

        /// Add missing columns to existing tables (for databases created before these columns were added)
        /// Check if columns exist before adding to avoid errors on fresh installs
        
        // Add prompt_id_fk to prompt_delivery_log_table
        try {
          await m.database.customStatement(
            'ALTER TABLE prompt_delivery_log_table ADD COLUMN prompt_id_fk TEXT NOT NULL DEFAULT ""',
          );
        } catch (e) {
          // Column might already exist, ignore
        }

        // Add columns to session_table
        try {
          await m.database.customStatement(
            'ALTER TABLE session_table ADD COLUMN app_package TEXT NOT NULL DEFAULT ""',
          );
        } catch (e) {
          // Column might already exist, ignore
        }
        
        try {
          await m.database.customStatement(
            'ALTER TABLE session_table ADD COLUMN started_at INTEGER NOT NULL DEFAULT (strftime(\'%s\', \'now\'))',
          );
        } catch (e) {
          // Column might already exist, ignore
        }
        
        try {
          await m.database.customStatement(
            'ALTER TABLE session_table ADD COLUMN ended_at INTEGER',
          );
        } catch (e) {
          // Column might already exist, ignore
        }
      },
    );
