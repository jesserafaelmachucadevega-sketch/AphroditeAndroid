package com.aphrodite.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A reusable set of instructions the model can apply.
 *
 * Skills are loaded on demand rather than all at once. Every enabled skill pasted into the prompt
 * would crowd out the conversation and, on a small model, the instructions themselves become the
 * thing it fails to follow. The model is told what exists and asks for the one it needs.
 */
@Entity(
    tableName = "skills",
    indices = [Index("enabled"), Index("name")],
)
@Serializable
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** One line, shown to the model so it knows when the skill is worth loading. */
    val description: String,
    /** The instructions themselves. */
    val body: String,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
