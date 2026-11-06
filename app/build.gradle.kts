plugins {
    id("aphrodite.android.application")
    id("aphrodite.android.hilt")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        // Required by the AppAuth library's merged manifest. OAuth email sign-in will use this
        // scheme; it must match the redirect URI registered with the provider.
        manifestPlaceholders["appAuthRedirectScheme"] = "com.aphrodite"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:adaptive"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    implementation(project(":core:llm"))

    implementation(project(":feature:auth"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:notes"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:contacts"))
    implementation(project(":feature:email"))
    implementation(project(":feature:documents"))
    implementation(project(":feature:memory"))
    implementation(project(":feature:skills"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))

    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)

    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window)
    implementation(libs.compose.ui.tooling.preview)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
