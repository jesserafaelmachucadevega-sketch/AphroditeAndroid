package com.aphrodite.core.llm

import com.aphrodite.core.database.dao.PresetDao
import com.aphrodite.core.database.entity.PresetEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the built-in preset on first run.
 *
 * The constitution used to be a constant compiled into the request path, which meant changing a
 * word required a new build. Seeding it as a preset makes it editable in the app while keeping the
 * same text as the default — the request path falls back to [SystemConstitution.TEXT] only when no
 * preset is active.
 */
@Singleton
class PresetSeeder @Inject constructor(
    private val presetDao: PresetDao,
) {
    suspend fun seedIfEmpty() {
        if (presetDao.count() > 0) return
        val now = System.currentTimeMillis()
        presetDao.upsertAll(builtIns(now))
    }

    /** The presets shipped with the app. Ids are stable so edits survive re-seeding. */
    fun builtIns(now: Long = System.currentTimeMillis()): List<PresetEntity> = listOf(
        PresetEntity(
            id = CONSTITUTION_ID,
            name = "Aphrodite Constitution",
            description = "The operational rules the app is built around. Edit to tune behaviour.",
            systemPrompt = SystemConstitution.TEXT,
            thinkingMode = null,
            isBuiltin = true,
            enabled = true,
            createdAt = now,
            updatedAt = now,
        ),
        PresetEntity(
            id = RESEARCH_ID,
            name = "Researcher",
            description = "Grounded answers with sources; prefers looking things up over recall.",
            systemPrompt = SystemConstitution.TEXT + "\n\n" + """
                ADDITIONAL ROLE: RESEARCHER
                - Cite the source URL for every factual claim that came from a tool.
                - If sources disagree, say so and give both.
                - If you could not find evidence, say that plainly instead of filling the gap.
                - Prefer one more search over one more guess.
            """.trimIndent(),
            temperature = 0.3,
            isBuiltin = true,
            enabled = false,
            createdAt = now,
            updatedAt = now,
        ),
        PresetEntity(
            id = CONCISE_ID,
            name = "Terse",
            description = "Shortest correct answer. Useful on a small local model with a short context.",
            systemPrompt = SystemConstitution.TEXT + "\n\n" + """
                ADDITIONAL ROLE: TERSE
                - Answer in as few words as correctness allows.
                - No preamble, no restating the question, no summary at the end.
                - Use a list only when the answer is genuinely a list.
            """.trimIndent(),
            temperature = 0.2,
            maxOutputTokens = 1024,
            isBuiltin = true,
            enabled = false,
            createdAt = now,
            updatedAt = now,
        ),
    )

    companion object {
        /** Fixed id so a user's edits to the constitution are not lost on re-seed. */
        const val CONSTITUTION_ID = "builtin-constitution"
        const val RESEARCH_ID = "builtin-researcher"
        const val CONCISE_ID = "builtin-terse"

        fun newCustomId(): String = "custom-" + UUID.randomUUID()
    }
}
