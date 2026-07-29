import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.sqldelight) apply false
}

allprojects {
    group = "com.digibuddy"
    version = "0.1.0-SNAPSHOT"
}

val detektVersion = libs.versions.detekt.get()

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<KtlintExtension> {
        debug.set(false)
        ignoreFailures.set(false)
        outputToConsole.set(true)
        verbose.set(true)
        filter {
            exclude("**/build/**")
            exclude("**/generated/**")
            exclude { source ->
                val path = source.file.invariantSeparatorsPath
                path.contains("/build/") || path.contains("/generated/")
            }
        }
    }

    extensions.configure<DetektExtension> {
        toolVersion = detektVersion
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        basePath = rootProject.projectDir.absolutePath
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    }

    tasks.withType<Detekt>().configureEach {
        exclude("**/build/**", "**/generated/**")
        reports {
            html.required.set(true)
            sarif.required.set(true)
            txt.required.set(false)
            xml.required.set(true)
        }
    }

    tasks.withType<BaseKtLintCheckTask>().configureEach {
        exclude { source ->
            val path = source.file.invariantSeparatorsPath
            path.contains("/build/") || path.contains("/generated/")
        }
    }
}
