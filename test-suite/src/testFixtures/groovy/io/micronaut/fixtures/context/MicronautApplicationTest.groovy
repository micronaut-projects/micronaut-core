package io.micronaut.fixtures.context

import org.intellij.lang.annotations.Language
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class MicronautApplicationTest extends Specification {
    @TempDir
    Path testDirectory

    @Delegate
    private ApplicationUnderTest app

    def setup() {
        app = new ApplicationUnderTest(
                testDirectory.toFile(),
                compileClasspath,
                annotationProcessorPath
        )
    }

    private static List<File> classpathFromSystemProperty(String name) {
        String cpString = System.getProperty(name, "")
        cpString.split(':').collect { new File(it) }
    }

    protected List<File> getCompileClasspath() {
        classpathFromSystemProperty('testapp.compile.classpath')
    }

    protected List<File> getAnnotationProcessorPath() {
        classpathFromSystemProperty('testapp.annotationprocessor.path')
    }

    void javaSourceFile(String relativePath, @Language("java") String contents) {
        sourceFile(relativePath, contents)
    }

    void groovySourceFile(String relativePath, @Language("groovy") String contents) {
        sourceFile(relativePath, contents)
    }

    void sourceFile(String relativePath, String contents) {
        def targetPath = testDirectory.resolve("src/main/java/$relativePath")
        Files.createDirectories(targetPath.parent)
        def srcFile = targetPath.toFile()
        srcFile.setText(contents, 'utf-8')
        app.addSourceFile(srcFile)
    }
}
