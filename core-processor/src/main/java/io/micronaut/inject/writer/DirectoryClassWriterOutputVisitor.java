/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.writer;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.Element;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * A {@link ClassWriterOutputVisitor} that writes to a target directory.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class DirectoryClassWriterOutputVisitor extends AbstractClassWriterOutputVisitor {

    private final File targetDir;

    /**
     * @param targetDir The target directory
     */
    public DirectoryClassWriterOutputVisitor(File targetDir) {
        super(true);
        this.targetDir = targetDir;
    }

    @Override
    @SuppressWarnings("java:S3878")
    public OutputStream visitClass(String classname, @Nullable Element originatingElement) throws IOException {
        return visitClass(classname, new Element[] {originatingElement});
    }

    @Override
    public OutputStream visitClass(String classname, Element... originatingElements) throws IOException {
        File targetFile = new File(targetDir, getClassFileName(classname)).getCanonicalFile();
        makeParent(targetFile.toPath());
        return Files.newOutputStream(targetFile.toPath());
    }

    @Override
    @SuppressWarnings("java:S1075")
    public void visitServiceDescriptor(String type, String classname, Element originatingElement) {
        final String path = "META-INF/micronaut/" + type + "/" + classname;
        try {
            final Path filePath = targetDir.toPath().resolve(path);
            makeParent(filePath);
            Files.writeString(filePath, "",
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE
            );
        } catch (IOException e) {
            throw new ClassGenerationException("Unable to generate Bean entry at path: " + path, e);
        }
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path, Element... originatingElements) {
        return Optional.ofNullable(targetDir).map(root ->
            new FileBackedGeneratedFile(
                new File(root, "META-INF" + File.separator + path)
            )
        );
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path) {
        return getGeneratedFile(path);
    }

    @NonNull
    private Optional<GeneratedFile> getGeneratedFile(String path) {
        File parentFile = targetDir.getParentFile();
        File generatedDir = new File(parentFile, "generated");
        File f = new File(generatedDir, path);
        if (f.getParentFile().mkdirs()) {
            return Optional.of(new FileBackedGeneratedFile(f));
        }
        return Optional.empty();
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path, Element... originatingElements) {
        return getGeneratedFile(path);
    }

    private void makeParent(Path filePath) throws IOException {
        final Path parent = filePath.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private String getClassFileName(String className) {
        return className.replace('.', File.separatorChar) + ".class";
    }

}
