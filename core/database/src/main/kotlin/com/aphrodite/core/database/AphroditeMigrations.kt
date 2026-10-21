package com.aphrodite.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations.
 *
 * Every one is additive: a user's conversations, providers and profiles are the only copy of that
 * data anywhere, so nothing here may drop or rewrite an existing table.
 */
object AphroditeMigrations {

    /**
     * 1 → 2: MCP servers and their discovered tools.
     *
     * The statements mirror what Room generates for the entities exactly, including the index
     * names, because Room validates the resulting schema on open and will refuse to start if they
     * differ.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `mcp_servers` (" +
                    "`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`transport` TEXT NOT NULL, " +
                    "`url` TEXT NOT NULL, " +
                    "`headers_json` TEXT, " +
                    "`auth_ref` TEXT, " +
                    "`enabled` INTEGER NOT NULL, " +
                    "`disabled_tools` TEXT, " +
                    "`last_connected_at` INTEGER, " +
                    "`last_error` TEXT, " +
                    "`tool_count` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_mcp_servers_enabled` ON `mcp_servers` (`enabled`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_mcp_servers_name` ON `mcp_servers` (`name`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `mcp_tools` (" +
                    "`id` TEXT NOT NULL, " +
                    "`server_id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`description` TEXT, " +
                    "`input_schema_json` TEXT, " +
                    "`enabled` INTEGER NOT NULL, " +
                    "`discovered_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`server_id`) REFERENCES `mcp_servers`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_mcp_tools_server_id` ON `mcp_tools` (`server_id`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_mcp_tools_server_id_name` " +
                    "ON `mcp_tools` (`server_id`, `name`)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
