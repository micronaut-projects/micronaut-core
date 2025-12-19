/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.test.pytest.discovery;

import org.graalvm.polyglot.Context;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.discovery.DirectorySelector;
import org.junit.platform.engine.discovery.FileSelector;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Resolves JUnit 5 discovery selectors into pytest test descriptors.
 */
public class PytestDiscoverySelectorResolver {

    private static final Logger LOG = LoggerFactory.getLogger(PytestDiscoverySelectorResolver.class);

    public PytestDiscoverySelectorResolver(Context context) {
        this.context = context;
    }

    private final Context context;

    /**
     * Resolves discovery selectors and adds corresponding test descriptors.
     */
    public void resolveSelectors(DiscoverySelector selector, EngineDescriptor engineDescriptor) {
        switch (selector) {
            case DirectorySelector directorySelector ->
                resolveDirectorySelector(directorySelector, engineDescriptor);
            case FileSelector fileSelector -> resolveFileSelector(fileSelector, engineDescriptor);
            default ->
                LOG.debug("Unsupported selector type: {}", selector.getClass().getSimpleName());
        }
    }


    private void resolveDirectorySelector(DirectorySelector selector, EngineDescriptor engineDescriptor) {
        Path directory = selector.getPath();
        LOG.debug("Resolving directory: {}", directory);

        scanPythonDirectory(directory, engineDescriptor);
    }

    private void resolveFileSelector(FileSelector selector, EngineDescriptor engineDescriptor) {
        Path file = selector.getPath();
        LOG.debug("Resolving file: {}", file);

        if (file.toString().endsWith(".py")) {
            addPythonFile(file, engineDescriptor, isTestFile(file));
        }
    }

    private void scanPythonDirectory(Path directory, EngineDescriptor engineDescriptor) {
        LOG.debug("Scanning Python directory: {}", directory);

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".py"))
                 .forEach(path -> addPythonFile(path, engineDescriptor, true));
        } catch (IOException e) {
            LOG.error("Error scanning directory: {}", directory, e);
        }
    }

    private void addPythonFile(Path filePath, EngineDescriptor engineDescriptor, boolean isTestDirectory) {
        LOG.debug("Adding Python file: {}", filePath);

        try {
            PytestAstParser astParser = new PytestAstParser(context);
            TestDescriptor fileDescriptor = astParser.parsePythonFile(filePath, isTestDirectory);

            if (fileDescriptor != null) {
                engineDescriptor.addChild(fileDescriptor);
            }
        } catch (Exception e) {
            LOG.error("Error parsing Python file: {}", filePath, e);
        }
    }

    private boolean isTestFile(Path file) {
        return file.getFileName().startsWith("test_");
    }
}
