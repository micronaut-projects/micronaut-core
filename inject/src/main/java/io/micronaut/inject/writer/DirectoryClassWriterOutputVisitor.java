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

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.Element;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Optional;

/**
 * A {@link ClassWriterOutputVisitor} that writes to a target target directory.
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
    public OutputStream visitClass(String classname, @Nullable Element originatingElement) throws IOException {
        File targetFile = new File(targetDir, getClassFileName(classname)).getCanonicalFile();
        File parentDir = targetFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Cannot create parent directory: " + targetFile.getParentFile());
        }
        return Files.newOutputStream(targetFile.toPath());
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path) {
        return Optional.ofNullable(targetDir).map(root ->
            new FileBackedGeneratedFile(
                new File(root, "META-INF" + File.separator + path)
            )
        );
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path) {
        File parentFile = targetDir.getParentFile();
        File generatedDir = new File(parentFile, "generated");
        File f = new File(generatedDir, path);
        if (f.getParentFile().mkdirs()) {
            return Optional.of(new FileBackedGeneratedFile(f));
        }
        return Optional.empty();
    }

    private String getClassFileName(String className) {
        return className.replace('.', File.separatorChar) + ".class";
    }

}
