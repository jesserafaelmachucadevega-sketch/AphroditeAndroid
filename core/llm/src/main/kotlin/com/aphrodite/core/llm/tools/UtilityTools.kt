package com.aphrodite.core.llm.tools

import com.aphrodite.core.llm.agent.Tool
import com.aphrodite.core.llm.agent.ToolOutcome
import com.aphrodite.core.llm.agent.requireString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** Anchors the model in real time so it cannot guess the date. */
class CurrentTimeTool @Inject constructor() : Tool {

    override val name = "current_time"
    override val description = "Return the current date and time, including the timezone."
    override val whenToUse =
        "Use before reasoning about anything relative — today, tomorrow, this week, how recent " +
            "something is, or how long until a date. Never assume the date."

    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
    }

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val now = ZonedDateTime.now()
        val formatted = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm:ss"))
        val offset = now.offset.id
        return ToolOutcome.Success(
            "Current date and time: $formatted\nTimezone: ${ZoneId.systemDefault().id} ($offset)",
        )
    }
}

/**
 * Arithmetic the model should not be doing in its head.
 *
 * Small models are unreliable at multi-step arithmetic, and the constitution tells them not to
 * compute from memory when a tool exists. This is that tool.
 */
class CalculateTool @Inject constructor() : Tool {

    override val name = "calculate"
    override val description =
        "Evaluate an arithmetic expression exactly. Supports + - * / % ^, parentheses, and " +
            "sqrt, abs, round, floor, ceil, min, max."

    override val whenToUse =
        "Use for any arithmetic with more than one step, percentages, or large numbers. Do not " +
            "calculate in your head — small errors are invisible and contagious."

    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("expression") {
                put("type", "string")
                put("description", "The expression, for example (1200 * 1.08) / 3.")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("expression")) })
    }

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val expression = arguments.requireString("expression")
            ?: return ToolOutcome.Failure(
                "No expression was provided.",
                "Call calculate with an \"expression\" string, for example \"2 + 2\".",
            )

        return runCatching { ExpressionParser(expression).parse() }
            .fold(
                onSuccess = { value ->
                    val rendered = if (value == value.roundToLong().toDouble() && abs(value) < 1e15) {
                        value.roundToLong().toString()
                    } else {
                        value.toString()
                    }
                    ToolOutcome.Success("$expression = $rendered")
                },
                onFailure = { error ->
                    ToolOutcome.Failure(
                        "Could not evaluate \"$expression\": ${error.message}",
                        "Use only numbers, + - * / % ^, parentheses and the listed functions.",
                    )
                },
            )
    }
}

/**
 * A small recursive-descent arithmetic parser.
 *
 * Deliberately not an expression evaluator over arbitrary code: the model chooses the input, so
 * anything with side effects or host access would be a hole.
 */
internal class ExpressionParser(private val input: String) {

    private var position = 0

    fun parse(): Double {
        val value = expression()
        skipWhitespace()
        require(position == input.length) {
            "unexpected \"${input.substring(position).take(12)}\""
        }
        require(!value.isNaN() && !value.isInfinite()) { "result is not a finite number" }
        return value
    }

    private fun expression(): Double {
        var value = term()
        while (true) {
            skipWhitespace()
            when {
                consume('+') -> value += term()
                consume('-') -> value -= term()
                else -> return value
            }
        }
    }

    private fun term(): Double {
        var value = power()
        while (true) {
            skipWhitespace()
            when {
                consume('*') -> value *= power()
                consume('/') -> {
                    val divisor = power()
                    require(divisor != 0.0) { "division by zero" }
                    value /= divisor
                }
                consume('%') -> {
                    val divisor = power()
                    require(divisor != 0.0) { "division by zero" }
                    value %= divisor
                }
                else -> return value
            }
        }
    }

    private fun power(): Double {
        val base = unary()
        skipWhitespace()
        return if (consume('^')) base.powSafely(unary()) else base
    }

    private fun unary(): Double {
        skipWhitespace()
        return when {
            consume('-') -> -unary()
            consume('+') -> unary()
            else -> primary()
        }
    }

    private fun primary(): Double {
        skipWhitespace()
        if (consume('(')) {
            val value = expression()
            skipWhitespace()
            require(consume(')')) { "missing closing parenthesis" }
            return value
        }

        if (position < input.length && (input[position].isLetter() || input[position] == '_')) {
            val name = buildString {
                while (position < input.length && (input[position].isLetterOrDigit() || input[position] == '_')) {
                    append(input[position++])
                }
            }
            skipWhitespace()
            require(consume('(')) { "expected ( after $name" }
            val args = mutableListOf<Double>()
            skipWhitespace()
            if (!consume(')')) {
                do {
                    args += expression()
                    skipWhitespace()
                } while (consume(','))
                skipWhitespace()
                require(consume(')')) { "missing closing parenthesis after $name" }
            }
            return applyFunction(name, args)
        }

        val start = position
        while (position < input.length && (input[position].isDigit() || input[position] == '.')) position++
        require(position > start) { "expected a number" }
        return input.substring(start, position).toDoubleOrNull()
            ?: throw IllegalArgumentException("\"${input.substring(start, position)}\" is not a number")
    }

    private fun applyFunction(name: String, args: List<Double>): Double = when (name.lowercase()) {
        "sqrt" -> {
            require(args.size == 1) { "sqrt takes one argument" }
            sqrt(args[0])
        }
        "abs" -> {
            require(args.size == 1) { "abs takes one argument" }
            abs(args[0])
        }
        "round" -> {
            require(args.size == 1) { "round takes one argument" }
            args[0].roundToLong().toDouble()
        }
        "floor" -> {
            require(args.size == 1) { "floor takes one argument" }
            kotlin.math.floor(args[0])
        }
        "ceil" -> {
            require(args.size == 1) { "ceil takes one argument" }
            kotlin.math.ceil(args[0])
        }
        "min" -> {
            require(args.isNotEmpty()) { "min takes at least one argument" }
            args.min()
        }
        "max" -> {
            require(args.isNotEmpty()) { "max takes at least one argument" }
            args.max()
        }
        else -> throw IllegalArgumentException("unknown function $name")
    }

    private fun skipWhitespace() {
        while (position < input.length && input[position].isWhitespace()) position++
    }

    private fun consume(char: Char): Boolean {
        if (position < input.length && input[position] == char) {
            position++
            return true
        }
        return false
    }

    private fun Double.powSafely(exponent: Double): Double {
        if (this == 0.0 && exponent < 0) throw IllegalArgumentException("division by zero")
        return Math.pow(this, exponent)
    }
}
