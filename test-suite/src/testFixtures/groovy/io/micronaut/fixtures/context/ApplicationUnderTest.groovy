package io.micronaut.fixtures.context

import groovy.transform.CompileStatic
import io.micronaut.context.ApplicationContext
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration

import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.ToolProvider
import java.util.stream.Collectors

@CompileStatic
class ApplicationUnderTest {
    private final File baseDir
    private final File buildDir
    private final List<File> compileClasspath
    private final List<File> annotationProcessorPath
    private final Map<String, List<File>> sourceFiles = [:].withDefault { [] }

    boolean showCompilerOptions = false

    ApplicationUnderTest(File baseDir, List<File> compileClasspath, List<File> annotationProcessorPath) {
        this.baseDir = baseDir
        this.buildDir = new File(baseDir, "build/classes")
        this.compileClasspath = compileClasspath
        this.annotationProcessorPath = annotationProcessorPath
    }

    void compile() {
        compileJava()
        compileGroovy()
    }

    private void compileJava() {
        def javaSources = sourceFiles['java']
        if (javaSources.empty) {
            println "No Java sources to compile"
            return
        }
        println "Compiling ${javaSources.size()} Java source files"
        def compiler = ToolProvider.systemJavaCompiler
        def ds = new DiagnosticCollector<JavaFileObject>()
        try (StandardJavaFileManager mgr = compiler.getStandardFileManager(ds, null, null)) {
            List<String> options = buildCompilerOptions()
            if (showCompilerOptions) {
                "Compiler options:"
                options.each {
                    println "  $it"
                }
            }
            if (buildDir.exists() || buildDir.mkdirs()) {
                Iterable<? extends JavaFileObject> sources = mgr.getJavaFileObjectsFromFiles(javaSources)
                JavaCompiler.CompilationTask task = compiler.getTask(null, mgr, ds, options, null, sources)
                task.call()
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to compile generated classes", e)
        }
        List<Diagnostic<? extends JavaFileObject>> diagnostics = ds.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .collect(Collectors.toList());
        if (!diagnostics.isEmpty()) {
            throwCompilationError(diagnostics)
        }
    }

    private void compileGroovy() {
        def groovySources = sourceFiles['groovy']
        if (groovySources.empty) {
            println "No Groovy sources to compile"
            return
        }
        println "Compiling ${groovySources.size()} Groovy source files"
        def compilerConfiguration = new CompilerConfiguration()
        compilerConfiguration.targetDirectory = buildDir
        def compilationUnit = new CompilationUnit(compilerConfiguration)
        groovySources.each {
            compilationUnit.addSource(it)
        }
        compilationUnit.compile()
    }

    private List<String> buildCompilerOptions() {
        def options = new ArrayList<String>()
        options.add("-source")
        options.add("1.8")
        options.add("-target")
        options.add("1.8")
        options.add("-encoding")
        options.add("utf-8")
        options.add("-classpath")
        String cp = compileClasspath.collect { it.absolutePath }.join(File.pathSeparator)
        options.add(cp)
        if (annotationProcessorPath) {
            options.add("--processor-path")
            cp = annotationProcessorPath.collect { it.absolutePath }.join(File.pathSeparator)
            options.add(cp)
        }
        options.add("-d")
        options.add(buildDir.absolutePath)
        return options
    }

    private static void throwCompilationError(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        def sb = new StringBuilder("Compilation errors:\n")
        for (Diagnostic<? extends JavaFileObject> d : diagnostics) {
            JavaFileObject source = d.source
            String srcFile = source == null ? "unknown" : new File(source.toUri()).getName()
            String diagLine = "File $srcFile, line: ${d.lineNumber}, ${d.columnNumber}, ${d.getMessage(null)}"
            sb.append(diagLine).append("\n")
        }
        throw new RuntimeException(sb.toString())
    }

    void addSourceFile(File file) {
        String langName = file.name.substring(file.name.lastIndexOf('.') + 1).toLowerCase(Locale.ENGLISH)
        sourceFiles[langName] << file
    }

    List<String> getServiceFiles() {
        new File(buildDir, "META-INF/services").list() as List<String> ?: []
    }

    void hasServiceFileFor(Class<?> serviceType, @DelegatesTo(value = ServiceFile, strategy = Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        hasServiceFileFor(serviceType.name, spec)
    }

    void hasServiceFileFor(String serviceType, @DelegatesTo(value = ServiceFile, strategy = Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def serviceFile = new File(buildDir, "META-INF/services/$serviceType")
        assert serviceFile.exists(): "Service file $serviceType doesn't exist. Candidates are: $serviceFiles"
        spec.delegate = new ServiceFile(serviceFile)
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }

    ApplicationContext loadContext(String mainClass) {
        ApplicationContextLoader.load(compileClasspath + buildDir, mainClass)
    }

    static class ServiceFile {
        private final Set<String> actualServiceImpls

        ServiceFile(File serviceFile) {
            actualServiceImpls = serviceFile.readLines().findAll() as Set
        }

        void withImplementations(String... implementations) {
            Set<String> expectedImplementations = implementations as Set<String>
            assert actualServiceImpls == expectedImplementations
        }

    }
}
