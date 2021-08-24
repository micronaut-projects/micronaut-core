package io.micronaut.ast.groovy.visitor;

import io.micronaut.ast.groovy.utils.InMemoryByteCodeGroovyClassLoader;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.DirectoryClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;
import org.codehaus.groovy.control.CompilationUnit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

class GroovyClassWriterOutputVisitor implements ClassWriterOutputVisitor {

    private final CompilationUnit compilationUnit;

    GroovyClassWriterOutputVisitor(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;
    }

    @Override
    public OutputStream visitClass(String classname, @Nullable Element originatingElement) throws IOException {
        return visitClass(classname, new Element[]{ originatingElement });
    }

    @Override
    public OutputStream visitClass(String classname, Element... originatingElements) throws IOException {
        File classesDir = compilationUnit.getConfiguration().getTargetDirectory();
        if (classesDir != null) {
            DirectoryClassWriterOutputVisitor outputVisitor = new DirectoryClassWriterOutputVisitor(
                    classesDir
            );
            return outputVisitor.visitClass(classname, originatingElements);
        } else {
            // should only arrive here in testing scenarios
            if (compilationUnit.getClassLoader() instanceof InMemoryByteCodeGroovyClassLoader) {
                return new OutputStream() {
                    @Override
                    public void write(int b) {
                        // no-op
                    }

                    @Override
                    public void write(byte[] b) {
                        ((InMemoryByteCodeGroovyClassLoader) compilationUnit.getClassLoader()).addClass(classname, b);
                    }
                };
            } else {
                return new ByteArrayOutputStream(); // in-memory, mock or unit tests situation?
            }
        }
    }

    @Override
    public void visitServiceDescriptor(String type, String classname) {
        File classesDir = compilationUnit.getConfiguration().getTargetDirectory();
        if (classesDir != null) {

            DirectoryClassWriterOutputVisitor outputVisitor = new DirectoryClassWriterOutputVisitor(
                    classesDir
            );
            outputVisitor.visitServiceDescriptor(type, classname);
            outputVisitor.finish();
        }
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path, Element... originatingElements) {
        File classesDir = compilationUnit.getConfiguration().getTargetDirectory();
        if (classesDir != null) {

            DirectoryClassWriterOutputVisitor outputVisitor = new DirectoryClassWriterOutputVisitor(
                    classesDir
            );
            return outputVisitor.visitMetaInfFile(path, originatingElements);
        }

        return Optional.empty();
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path) {
        File classesDir = compilationUnit.getConfiguration().getTargetDirectory();
        if (classesDir != null) {

            DirectoryClassWriterOutputVisitor outputVisitor = new DirectoryClassWriterOutputVisitor(
                    classesDir
            );
            return outputVisitor.visitGeneratedFile(path);
        }

        return Optional.empty();
    }

    @Override
    public void finish() {
        // no-op
    }
}
