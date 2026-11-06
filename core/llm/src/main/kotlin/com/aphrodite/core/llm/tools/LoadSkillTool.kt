package com.aphrodite.core.llm.tools

import com.aphrodite.core.database.dao.SkillDao
import com.aphrodite.core.llm.agent.Tool
import com.aphrodite.core.llm.agent.ToolOutcome
import com.aphrodite.core.llm.agent.requireString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

/**
 * Loads a skill's instructions.
 *
 * Skills are the third kind of thing a model can be told: capabilities are what it can do, memories
 * are what is true, and skills are how to do something. Only names and descriptions are in the
 * prompt, so a model with fifty skills available still gets a prompt small enough to follow.
 */
class LoadSkillTool @Inject constructor(
    private val dao: SkillDao,
) : Tool {

    override val name = "load_skill"
    override val description = "Load the full instructions for one of the user's saved skills."

    override val whenToUse =
        "When the task matches a skill listed as available and you need its actual instructions. " +
            "Only the names and descriptions are shown to you; the instructions are not. " +
            "Load one before starting work it covers rather than improvising the approach."

    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("name") {
                put("type", "string")
                put("description", "The skill's name, exactly as listed.")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("name")) })
    }

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val name = arguments.requireString("name")?.trim()
            ?: return ToolOutcome.Failure("No skill name was provided.", "Call load_skill with a \"name\".")

        val enabled = dao.enabled()
        val skill = dao.byName(name)?.takeIf { it.enabled }
        if (skill == null) {
            // Listing what does exist turns a dead end into a correctable call.
            return ToolOutcome.Failure(
                "There is no enabled skill called \"$name\".",
                if (enabled.isEmpty()) {
                    "No skills are enabled. Tell the user rather than inventing one."
                } else {
                    "Available: " + enabled.joinToString(", ") { it.name }
                },
            )
        }

        return ToolOutcome.Success("Instructions for \"${skill.name}\":\n\n${skill.body}")
    }
}
