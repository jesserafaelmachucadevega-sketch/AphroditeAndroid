package com.aphrodite.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * The `libs` version catalog, resolved from the consuming project so convention plugins and
 * modules always read the same source of truth (`gradle/libs.versions.toml`).
 */
internal val Project.catalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Reads a `[versions]` entry from the catalog and requires it to be an integer. */
internal fun VersionCatalog.versionInt(alias: String): Int =
    findVersion(alias).orElseThrow {
        IllegalStateException("Missing integer version '$alias' in gradle/libs.versions.toml")
    }.requiredVersion.toInt()

/** Reads a `[versions]` entry from the catalog. */
internal fun VersionCatalog.versionString(alias: String): String =
    findVersion(alias).orElseThrow {
        IllegalStateException("Missing version '$alias' in gradle/libs.versions.toml")
    }.requiredVersion

/**
 * Derives an Android namespace from the Gradle project path, so each module gets a stable
 * package root without repeating it in every build file.
 *
 * `:core:designsystem` -> `com.aphrodite.core.designsystem`
 * `:feature:image-editor` -> `com.aphrodite.feature.image_editor`
 */
internal val Project.defaultNamespace: String
    get() = "com.aphrodite" + path.replace(':', '.').replace('-', '_')
