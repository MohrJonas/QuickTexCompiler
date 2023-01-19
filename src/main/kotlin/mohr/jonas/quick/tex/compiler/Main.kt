package mohr.jonas.quick.tex.compiler

import eu.jrie.jetbrains.kotlinshell.shell.shell
import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import mohr.jonas.quick.tex.dsl.elements.latex.QuickTex
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

@OptIn(ExperimentalPathApi::class)
fun main(args: Array<String>) {
    val parser = ArgParser("quicktex")
    val srcDirectory by parser.option(ArgType.String, "source", "s", "Source directory").required()
    val outDirectory by parser.option(ArgType.String, "out", "o", "Output directory").required()
    val tectonicPath by parser.option(ArgType.String, "tectonic", "t", "Tectonic path").default("/usr/bin/tectonic")
    val watch by parser.option(ArgType.Boolean, "watch", "w", "Watch for change in the source directory").default(false)
    val polling by parser.option(ArgType.Boolean, "poll", "p", "Use polling rather than filesystem events").default(false)
    parser.parse(args)
    val (srcDirectoryPath, outDirectoryPath) = validatePaths(srcDirectory, outDirectory, tectonicPath)
    if (watch) {
        println("Watching for changes in ${srcDirectoryPath.absolutePathString()}")
        if(polling) {
            val fileMap = mutableMapOf<Path, String>()
            while (true) {
                srcDirectoryPath.walk().forEach {
                    if(it.extension != "kts") return@forEach
                    if(!fileMap.containsKey(it) || fileMap[it] != it.readText()) {
                        fileMap[it] = it.readText()
                        runScript(it)
                    }
                }
                Thread.sleep(1000L)
            }
        }
        else DirectoryWatcher.builder().path(srcDirectoryPath).listener {
            when (it.eventType()) {
                DirectoryChangeEvent.EventType.MODIFY, DirectoryChangeEvent.EventType.CREATE -> {
                    if(it.path().extension != "kts") return@listener
                    println("${it.path().absolutePathString()} changed. Recompiling...")
                    runScript(it.path()).compile(
                        outDirectoryPath,
                        it.path().nameWithoutExtension,
                        Path.of(tectonicPath)
                    )
                }

                else -> Unit
            }
        }.build().watch()
    } else {
        srcDirectoryPath.walk().filter { it.extension == "kts" }.forEach {
            println("Compiling ${it.absolutePathString()}")
            runScript(it).compile(outDirectoryPath, it.nameWithoutExtension, Path.of(tectonicPath))
        }
    }
}

private fun validatePaths(srcDirectory: String, outDirectory: String, tectonicPath: String): Pair<Path, Path> {
    val srcDirectoryPath = Path.of(srcDirectory)
    if (srcDirectoryPath.notExists()) error("Source directory $srcDirectory doesn't exist")
    if (!srcDirectoryPath.isReadable()) error("Source directory $srcDirectory is not readable")
    val outDirectoryPath = Path.of(outDirectory)
    outDirectoryPath.createDirectories()
    if (!outDirectoryPath.isWritable()) error("Out directory $outDirectory is not writable")
    if (!Path.of(tectonicPath).exists()) error("tectonic $tectonicPath doesn't exist")
    return Pair(srcDirectoryPath, outDirectoryPath)
}

private fun runScript(scriptPath: Path): QuickTex {
    val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<Script> { }
    when (val evalResult =
        BasicJvmScriptingHost().eval(scriptPath.toFile().toScriptSource(), compilationConfiguration, null)) {
        is ResultWithDiagnostics.Failure -> error(evalResult.reports.filter { it.severity >= ScriptDiagnostic.Severity.WARNING })
        is ResultWithDiagnostics.Success -> {
            val returnValue = evalResult.value.returnValue
            val scriptInstance = returnValue.scriptInstance
            val scriptClass = returnValue.scriptClass
            if (scriptInstance == null || scriptClass == null) error("Script does not return anything")
            return scriptClass.java.getDeclaredField("\$\$result").get(scriptInstance) as QuickTex
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun QuickTex.compile(outDirectory: Path, scriptName: String, tectonicPath: Path) {
    runBlocking {
        shell {
            pipeline {
                this@compile.toString() pipe "${tectonicPath.absolutePathString()} -c minimal -o ${outDirectory.absolutePathString()} -".process()
            }
        }
        if (outDirectory.resolve("texput.pdf").exists())
            outDirectory.resolve("texput.pdf").moveTo(outDirectory.resolve("$scriptName.pdf"), true)
    }
}