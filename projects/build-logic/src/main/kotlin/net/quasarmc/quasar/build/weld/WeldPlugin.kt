package net.quasarmc.quasar.build.weld

import com.pswidersk.gradle.python.VenvTask
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class WeldPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.register<VenvTask>("configureSmithed") {
            venvExec = "pip"
            args = listOf("install", "smithed")
        }
    }
}
