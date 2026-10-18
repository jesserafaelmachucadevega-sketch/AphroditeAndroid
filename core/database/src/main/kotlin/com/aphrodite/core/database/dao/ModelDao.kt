package com.aphrodite.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aphrodite.core.database.entity.ModelEndpointEntity
import com.aphrodite.core.database.entity.ModelEntity
import com.aphrodite.core.database.entity.PresetEntity
import com.aphrodite.core.database.entity.PresetGroupEntity
import com.aphrodite.core.database.entity.PresetTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelEndpointDao {

    @Query("SELECT * FROM model_endpoints ORDER BY sort_order ASC, name ASC")
    fun observeAll(): Flow<List<ModelEndpointEntity>>

    @Query("SELECT * FROM model_endpoints WHERE enabled = 1 ORDER BY sort_order ASC, name ASC")
    fun observeEnabled(): Flow<List<ModelEndpointEntity>>

    @Query("SELECT * FROM model_endpoints WHERE id = :id")
    suspend fun getById(id: String): ModelEndpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(endpoint: ModelEndpointEntity)

    @Query("DELETE FROM model_endpoints WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE model_endpoints SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE model_endpoints SET last_probed_at = :at, last_error = :error WHERE id = :id")
    suspend fun recordProbe(id: String, at: Long, error: String?)

    @Query("UPDATE model_endpoints SET sort_order = :order WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int)
}

@Dao
interface ModelDao {

    /**
     * Models on one endpoint.
     *
     * Free models sort first. OpenRouter names them with a `:free` suffix and they are the
     * sensible default for anyone browsing a catalogue of hundreds — paying models are one
     * scroll away, but nothing costs money by accident.
     */
    @Query(
        "SELECT * FROM models WHERE endpoint_id = :endpointId AND is_hidden = 0 " +
            "ORDER BY is_favourite DESC, " +
            "CASE WHEN model_id LIKE '%:free' THEN 0 ELSE 1 END ASC, " +
            "model_id ASC",
    )
    fun observeForEndpoint(endpointId: String): Flow<List<ModelEntity>>

    /** Every model across every endpoint, ordered the same way. Used by the chat model picker. */
    @Query(
        "SELECT * FROM models WHERE is_hidden = 0 " +
            "ORDER BY is_favourite DESC, " +
            "CASE WHEN model_id LIKE '%:free' THEN 0 ELSE 1 END ASC, " +
            "model_id ASC",
    )
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE is_favourite = 1 AND is_hidden = 0 ORDER BY model_id ASC")
    fun observeFavourites(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE endpoint_id = :endpointId AND model_id = :modelId")
    suspend fun get(endpointId: String, modelId: String): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(models: List<ModelEntity>)

    @Query("DELETE FROM models WHERE endpoint_id = :endpointId")
    suspend fun deleteForEndpoint(endpointId: String)

    @Query("UPDATE models SET is_favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: String, favourite: Boolean)

    @Query("UPDATE models SET is_hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)
}

@Dao
interface PresetDao {

    @Query("SELECT * FROM presets WHERE enabled = 1 ORDER BY is_builtin DESC, name ASC")
    fun observeEnabled(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets ORDER BY is_builtin DESC, name ASC")
    fun observeAll(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun getById(id: String): PresetEntity?

    @Query("SELECT * FROM presets WHERE is_builtin = 1")
    suspend fun builtins(): List<PresetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: PresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(presets: List<PresetEntity>)

    @Query("DELETE FROM presets WHERE id = :id AND is_builtin = 0")
    suspend fun deleteCustom(id: String)

    @Query("SELECT * FROM preset_templates ORDER BY sort_order ASC, name ASC")
    fun observeTemplates(): Flow<List<PresetTemplateEntity>>

    @Query("SELECT * FROM preset_templates WHERE shortcut = :shortcut LIMIT 1")
    suspend fun templateByShortcut(shortcut: String): PresetTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: PresetTemplateEntity)

    @Query("DELETE FROM preset_templates WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    @Query("SELECT * FROM preset_groups ORDER BY sort_order ASC, name ASC")
    fun observeGroups(): Flow<List<PresetGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: PresetGroupEntity)

    @Query("DELETE FROM preset_groups WHERE id = :id")
    suspend fun deleteGroup(id: String)
}
