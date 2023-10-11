/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.ast.groovy.visitor;

import io.micronaut.ast.groovy.utils.InMemoryByteCodeGroovyClassLoader;
import io.micronaut.core.annotation.Internal;
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

@Internal
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
    @SuppressWarnings("java:S1075")
    public void visitServiceDescriptor(String type, String classname, Element originatingElement) {
        File classesDir = compilationUnit.getConfiguration().getTargetDirectory();
        if (classesDir != null) {
            DirectoryClassWriterOutputVisitor outputVisitor = new DirectoryClassWriterOutputVisitor(
                    classesDir
            );
            outputVisitor.visitServiceDescriptor(type, classname, originatingElement);
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
        return getGeneratedFile(path);
    }

    private Optional<GeneratedFile> getGeneratedFile(String path) {
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
    public Optional<GeneratedFile> visitGeneratedFile(String path, Element... originatingElements) {
        return getGeneratedFile(path);
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedSourceFile(String packageName, String fileNameWithoutExtension, Element... originatingElements) {
        File classesDir = compilationUnit.getConfiguration().getTargetDirectory();
        if (classesDir != null) {
            DirectoryClassWriterOutputVisitor outputVisitor = new DirectoryClassWriterOutputVisitor(
                classesDir
            );
            return outputVisitor.visitGeneratedSourceFile(packageName, fileNameWithoutExtension, originatingElements);
        }

        return Optional.empty();
    }

    @Override
    public void finish() {
        // no-op
    }
}
