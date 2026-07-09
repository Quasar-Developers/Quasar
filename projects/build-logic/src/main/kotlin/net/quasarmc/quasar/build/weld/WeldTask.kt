package net.quasarmc.quasar.build.weld

import com.pswidersk.gradle.python.VenvTask
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.util.Vector
import javax.inject.Inject

@CacheableTask
abstract class WeldTask @Inject constructor(
    execOperations: ExecOperations,
    objects: ObjectFactory,
    projectLayout: ProjectLayout
) : VenvTask(execOperations, objects, projectLayout) {
    init {
        dependsOn("configureSmithed")

        outputs.cacheIf { true }
    }

    /**
     * The file to output the merged pack to
     */
    @get:OutputFile
    abstract val output: RegularFileProperty;

    /**
     * The root directories of the packs to be mergeed
     */
    private var sources = Vector<Directory>();

    /**
     * Add a pack to be merged
     */
    fun include(dir: Directory) {
        sources.add(dir);
        inputs.dir(dir);
    }

    @TaskAction
    fun run() {
        super.venvExec = "weld";
        super.args = listOf(
            "--dir",  output.get().asFile.parent,
            "--name", output.get().asFile.name) +
            sources.map { t -> t.asFile.absolutePath }
    }
}
