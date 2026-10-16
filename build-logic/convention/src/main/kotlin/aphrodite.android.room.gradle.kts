import androidx.room.gradle.RoomExtension
import com.aphrodite.buildlogic.catalog
import org.gradle.kotlin.dsl.configure

plugins {
    id("com.google.devtools.ksp")
    id("androidx.room")
}

extensions.configure<RoomExtension> {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("implementation", catalog.findLibrary("androidx-room-runtime").get())
    add("implementation", catalog.findLibrary("androidx-room-ktx").get())
    add("ksp", catalog.findLibrary("androidx-room-compiler").get())
    add("testImplementation", catalog.findLibrary("room-testing").get())
}
