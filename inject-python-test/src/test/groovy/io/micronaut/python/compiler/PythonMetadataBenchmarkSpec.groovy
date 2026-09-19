/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.python.compiler

import groovy.json.JsonOutput
import spock.lang.Requires
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * The measurement harness of the metadata backends. Opt in with {@code -Dpython.metadata.benchmark=true}; the
 * fixture size and the rounds are system properties too. Every measurement alternates the backends round by
 * round, compiles into a fresh directory, and starts the application in a fresh JVM that has neither compiler.
 * The report (JSON samples and a Markdown summary) is written under the build directory of the module.
 */
@Requires({ System.getProperty('python.metadata.benchmark') == 'true' })
class PythonMetadataBenchmarkSpec extends Specification {

    static final List<String> BACKENDS = ['compiler', 'model-build-time', 'model-runtime']

    int modules = Integer.getInteger('python.metadata.benchmark.modules', 40)
    int compileRounds = Integer.getInteger('python.metadata.benchmark.compileRounds', 3)
    int startupRounds = Integer.getInteger('python.metadata.benchmark.startupRounds', 3)
    File reportDirectory = new File(System.getProperty('python.metadata.benchmark.report', 'build/reports/python-metadata-benchmark'))

    def "measure the backends"() {
        given:
        File source = File.createTempDir('benchmark-source', '')
        writeFixture(source)
        Map<String, Object> report = [environment: environment(), fixture: [modules: modules, beans: modules * 2, introspections: modules],
                                      compile: [], inventory: [:], startup: []]
        Map<String, File> outputs = [:]

        when: 'compilation, alternating the backends every round'
        for (int round = 0; round < compileRounds; round++) {
            List<String> order = rotate(BACKENDS, round)
            for (String backend : order) {
                File output = File.createTempDir("benchmark-$backend", '')
                long start = System.nanoTime()
                PyronautCompiler.builder().pythonSrc(source.absolutePath).targetDir(output)
                    .options(["-Amicronaut.python.metadata.backend=$backend".toString()]).build().compile()
                double millis = (System.nanoTime() - start) / 1_000_000.0
                report.compile << [round: round, backend: backend, compileMs: millis]
                report.inventory[backend] = inventory(output)
                outputs[backend]?.deleteDir()
                outputs[backend] = output
            }
        }

        and: 'startup and first use in a fresh JVM, for an application using one and using every declared type'
        String classpath = System.getProperty('pythonApplicationClasspath')
        String java = new File(System.getProperty('java.home'), 'bin/java').absolutePath
        List<Map> scenarios = [
            [name: 'few', bean: 'app.Service1', introspection: 'app.Dto1'],
            [name: 'many', bean: (1..modules).collect { "app.Service$it" }.join(','), introspection: (1..modules).collect { "app.Dto$it" }.join(',')],
        ]
        for (int round = 0; round < startupRounds; round++) {
            for (Map scenario : scenarios) {
                for (String backend : rotate(BACKENDS, round)) {
                    List<String> command = [java, '-Dpolyglot.engine.WarnInterpreterOnly=false', '-Xshare:auto', '-cp', classpath,
                        'io.micronaut.context.python.runtime.PythonRuntimeMetadataRunner', outputs[backend].absolutePath,
                        "bean=${scenario.bean}".toString(), "introspection=${scenario.introspection}".toString(), 'property=name', 'startups=2']
                    File log = File.createTempFile('benchmark-run', '.log')
                    long start = System.nanoTime()
                    Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start()
                    assert process.waitFor(10, TimeUnit.MINUTES)
                    double wall = (System.nanoTime() - start) / 1_000_000.0
                    String text = log.text
                    assert process.exitValue() == 0: text
                    Map metrics = text.readLines().findAll { it.contains('=') && !it.startsWith('Picked up') }
                        .collectEntries { def i = it.indexOf('='); [(it.substring(0, i)): it.substring(i + 1)] }
                    report.startup << [round: round, scenario: scenario.name, backend: backend, wallMs: wall] + metrics
                    log.delete()
                }
            }
        }
        reportDirectory.mkdirs()
        new File(reportDirectory, 'benchmark.json').text = JsonOutput.prettyPrint(JsonOutput.toJson(report))
        new File(reportDirectory, 'benchmark.md').text = summarize(report)

        then:
        report.compile.size() == compileRounds * BACKENDS.size()
        report.startup.size() == startupRounds * BACKENDS.size() * scenarios.size()

        cleanup:
        source?.deleteDir()
        outputs.values().each { it.deleteDir() }
    }

    private void writeFixture(File source) {
        File dir = new File(source, 'app')
        dir.mkdirs()
        for (int i = 1; i <= modules; i++) {
            new File(dir, "mod${i}.py").text = """
from typing import Annotated
from jakarta.inject import Singleton
from micronaut.core.annotation import Introspected
from micronaut.context.annotation import Value

@Singleton
class Repo${i}:
    def __init__(self):
        pass

    def find(self) -> str:
        return "r${i}"

@Singleton
class Service${i}:
    def __init__(self, repo: Repo${i}, name: Annotated[str, Value("\${app.name:svc}")]):
        self.repo = repo
        self.name = name

    def call(self) -> str:
        return self.repo.find() + self.name

@Introspected
class Dto${i}:
    name: str
    age: int
    active: bool

    def __init__(self):
        self.name = "n"
        self.age = ${i}
        self.active = True
"""
        }
    }

    private static Map inventory(File output) {
        Path root = output.toPath()
        Map<String, Map> kinds = [:].withDefault { [files: 0, bytes: 0L] }
        Files.walk(root).filter { Files.isRegularFile(it) }.forEach { Path path ->
            String relative = root.relativize(path).toString().replace(File.separatorChar, '/' as char)
            String kind
            if (relative ==~ /app\/\$\w+\$Definition\.class/ && !relative.contains('TargetTypeMapping')) {
                kind = 'definitions'
            } else if (relative ==~ /app\/\$\w+\$Introspection\.class/) {
                kind = 'introspections'
            } else if (relative.contains('TargetTypeMapping')) {
                kind = 'targetTypeMappings'
            } else if (relative.endsWith('.mpym')) {
                kind = 'models'
            } else if (relative.endsWith('/catalog')) {
                kind = 'catalog'
            } else if (relative.startsWith('META-INF/micronaut/io.micronaut.')) {
                kind = 'serviceEntries'
            } else if (relative.startsWith('META-INF/GRAALPY-VFS/')) {
                kind = 'pythonResources'
            } else if (relative.endsWith('.class')) {
                kind = 'wrapperClasses'
            } else if (relative.endsWith('.java')) {
                kind = 'wrapperSources'
            } else {
                kind = 'other'
            }
            kinds[kind].files++
            kinds[kind].bytes += Files.size(path)
        }
        kinds
    }

    private static Map environment() {
        String commit = 'unknown'
        try {
            Process git = new ProcessBuilder('git', 'rev-parse', 'HEAD').redirectErrorStream(true).start()
            commit = git.inputStream.text.trim()
        } catch (IOException ignored) {
            // no git
        }
        String classpath = System.getProperty('pythonApplicationClasspath') ?: ''
        def graalpy = (classpath =~ /python-language-([\d.]+)\.jar/)
        [
            java: System.getProperty('java.vm.name') + ' ' + System.getProperty('java.runtime.version'),
            os: System.getProperty('os.name') + ' ' + System.getProperty('os.arch'),
            cpus: Runtime.runtime.availableProcessors(),
            commit: commit,
            graalpy: graalpy.find() ? graalpy.group(1) : 'unknown',
            pythonRuntime: 'GraalPy fallback interpreter (no Truffle JIT on this JDK: the Graal compiler is not on the class path)',
            micronaut: io.micronaut.core.version.VersionUtils.MICRONAUT_VERSION,
        ]
    }

    private static List<String> rotate(List<String> list, int by) {
        int shift = by % list.size()
        list.drop(shift) + list.take(shift)
    }

    private static String summarize(Map report) {
        StringBuilder md = new StringBuilder()
        md << "# Python metadata backends: measurements\n\n"
        report.environment.each { k, v -> md << "- $k: $v\n" }
        md << "- fixture: ${report.fixture}\n\n"
        md << "## Compilation (ms, ${report.compile.size() / BACKENDS.size()} alternating rounds per backend)\n\n| backend | median | min | max |\n|---|---:|---:|---:|\n"
        BACKENDS.each { backend ->
            List<Double> values = report.compile.findAll { it.backend == backend }*.compileMs.sort()
            md << "| $backend | ${fmt(median(values))} | ${fmt(values.first())} | ${fmt(values.last())} |\n"
        }
        md << "\n## Output inventory\n\n| backend | kind | files | bytes |\n|---|---|---:|---:|\n"
        BACKENDS.each { backend ->
            report.inventory[backend].sort { it.key }.each { kind, v -> md << "| $backend | $kind | ${v.files} | ${v.bytes} |\n" }
        }
        md << "\n## Startup and first use in a fresh JVM (ms, medians; fallback interpreter)\n\n"
        ['few', 'many'].each { scenario ->
            md << "### Scenario: $scenario\n\n| backend | context start | first bean | all beans | first introspection | all introspections | warm start | generated classes | loaded classes | heap after GC (MB) | JVM wall |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n"
            BACKENDS.each { backend ->
                List<Map> rows = report.startup.findAll { it.backend == backend && it.scenario == scenario }
                def m = { String key -> fmt(median(rows.collect { (it[key] as String).toDouble() })) }
                md << "| $backend | ${m('T_CONTEXT_START_MS')} | ${m('T_FIRST_BEAN_MS')} | ${m('T_ALL_BEANS_MS')} | ${m('T_FIRST_INTROSPECTION_MS')} | ${m('T_ALL_INTROSPECTIONS_MS')} | ${m('T_WARM_START_2_MS')} | ${m('GENERATED_CLASSES')} | ${m('LOADED_CLASSES')} | ${fmt(median(rows.collect { (it.HEAP_USED_AFTER_GC_BYTES as String).toDouble() / 1048576 }))} | ${m('JVM_UPTIME_AT_END_MS')} |\n"
            }
            md << "\n"
        }
        md.toString()
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.sort(false)
        int n = sorted.size()
        n % 2 == 1 ? sorted[n.intdiv(2)] : (sorted[n.intdiv(2) - 1] + sorted[n.intdiv(2)]) / 2
    }

    private static String fmt(double value) {
        String.format(Locale.ROOT, '%.1f', value)
    }
}
