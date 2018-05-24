/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.inject.writer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

/**
 * A {@link ClassWriterOutputVisitor} that writes to a target target directory.
 *
 * @author graemerocher
 * @since 1.0
 */
public class DirectoryClassWriterOutputVisitor extends AbstractClassWriterOutputVisitor {

    private final File targetDir;

    /**
     * @param targetDir The target directory
     */
    public DirectoryClassWriterOutputVisitor(File targetDir) {
        this.targetDir = targetDir;
    }

    @Override
    public OutputStream visitClass(String className) throws IOException {
        File targetFile = new File(targetDir, getClassFileName(className)).getCanonicalFile();
        File parentDir = targetFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Cannot create parent directory: " + targetFile.getParentFile());
        }
        return new FileOutputStream(targetFile);
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path) {
        return Optional.ofNullable(targetDir).map(root ->
            new FileBackedGeneratedFile(
                new File(root, "META-INF" + File.separator + path)
            )
        );
    }

    private String getClassFileName(String className) {
        return className.replace('.', File.separatorChar) + ".class";
    }

}
