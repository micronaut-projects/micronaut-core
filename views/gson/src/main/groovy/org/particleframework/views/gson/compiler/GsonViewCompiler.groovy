package org.particleframework.views.gson.compiler

import groovy.io.FileType
import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.control.io.FileReaderSource

class GsonViewCompiler {

    private static final char SLASH_CHAR = '/' as char
    private static final char DOT_CHAR = '.' as char
    private static final char UNDERSCORE_CHAR = '_' as char

    @Delegate CompilerConfiguration configuration = new CompilerConfiguration()

    String packageName = ""
    File sourceDir

    GsonViewCompiler(File sourceDir) {
        this.sourceDir = sourceDir
        configureCompiler()
    }

    GsonViewCompiler() {
    }

    protected CompilerConfiguration configureCompiler() {
        configuration.compilationCustomizers.clear()

        ImportCustomizer importCustomizer = new ImportCustomizer()
        importCustomizer.addStarImports('groovy.transform')

        configuration.addCompilationCustomizers(importCustomizer)
        configuration.addCompilationCustomizers(new ASTTransformationCustomizer(new GsonViewTransform("gson")))

        configuration.addCompilationCustomizers(
                    new ASTTransformationCustomizer(Collections.singletonMap("extensions", "grails.plugin.json.view.internal.JsonTemplateTypeCheckingExtension"), CompileStatic.class))

        configuration.setScriptBaseClass(
                viewConfiguration.baseTemplateClass.name
        )

        return configuration
    }

    void compile(Iterable<File> sources) {
        configuration.setClasspathList(classpath)
        def pathToSourceDir = sourceDir.canonicalPath
        for(source in sources) {
            configureCompiler()
            def unit = new CompilationUnit(configuration)
            def pathToSource = source.canonicalPath
            def path = pathToSource - pathToSourceDir
            def templateName = resolveTemplateName(packageName, path)
            unit.addSource(new SourceUnit(
                    templateName,
                    new FileReaderSource(source, configuration),
                    configuration,
                    unit.classLoader,
                    unit.errorCollector
            ))
            unit.compile()
        }

    }

    void compile(File...sources) {
        compile Arrays.asList(sources)
    }

    private static String resolveTemplateName(String scope, String path) {
        path = path.substring(1) // remove leading slash '/'
        path = path.replace(File.separatorChar, UNDERSCORE_CHAR)
        path = path.replace(SLASH_CHAR, UNDERSCORE_CHAR)
        path = path.replace(DOT_CHAR, UNDERSCORE_CHAR)
        if(scope) {
            scope = scope.replaceAll(/[\W\s]/, String.valueOf(UNDERSCORE_CHAR))
            path = "${scope}_${path}"
        }
        return path
    }

}
