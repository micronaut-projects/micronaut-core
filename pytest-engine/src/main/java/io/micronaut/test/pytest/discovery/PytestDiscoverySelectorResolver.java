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

import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.DirectorySelector;
import org.junit.platform.engine.discovery.FileSelector;
import org.junit.platform.engine.discovery.MethodSelector;
import org.junit.platform.engine.discovery.PackageSelector;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Resolves JUnit 5 discovery selectors into pytest test descriptors.
 */
public class PytestDiscoverySelectorResolver {

    private static final Logger LOG = LoggerFactory.getLogger(PytestDiscoverySelectorResolver.class);

    private static final String SRC_TEST_PYTHON = "src/test/python";
    private static final String SRC_MAIN_PYTHON = "src/main/python";

    /**
     * Resolves discovery selectors and adds corresponding test descriptors.
     */
    public void resolveSelectors(DiscoverySelector selector, EngineDescriptor engineDescriptor) {
        if (selector instanceof ClasspathRootSelector classpathRootSelector) {
            resolveClasspathRootSelector(classpathRootSelector, engineDescriptor);
        } else if (selector instanceof PackageSelector packageSelector) {
            resolvePackageSelector(packageSelector, engineDescriptor);
        } else if (selector instanceof ClassSelector classSelector) {
            resolveClassSelector(classSelector, engineDescriptor);
        } else if (selector instanceof MethodSelector methodSelector) {
            resolveMethodSelector(methodSelector, engineDescriptor);
        } else if (selector instanceof DirectorySelector directorySelector) {
            resolveDirectorySelector(directorySelector, engineDescriptor);
        } else if (selector instanceof FileSelector fileSelector) {
            resolveFileSelector(fileSelector, engineDescriptor);
        } else {
            LOG.debug("Unsupported selector type: {}", selector.getClass().getSimpleName());
        }
    }

    private void resolveClasspathRootSelector(ClasspathRootSelector selector, EngineDescriptor engineDescriptor) {
        Path classpathRoot = Paths.get(selector.getClasspathRoot());
        LOG.debug("Resolving classpath root: {}", classpathRoot);

        // Look for Python test directories in the classpath root
        Path testPythonDir = classpathRoot.resolve(SRC_TEST_PYTHON);
        if (Files.exists(testPythonDir)) {
            scanPythonDirectory(testPythonDir, engineDescriptor, true);
        }

        Path mainPythonDir = classpathRoot.resolve(SRC_MAIN_PYTHON);
        if (Files.exists(mainPythonDir)) {
            scanPythonDirectory(mainPythonDir, engineDescriptor, false);
        }
    }

    private void resolvePackageSelector(PackageSelector selector, EngineDescriptor engineDescriptor) {
        String packageName = selector.getPackageName();
        LOG.debug("Resolving package: {}", packageName);

        // Convert package name to directory path
        String packagePath = packageName.replace('.', '/');

        // Scan both test and main directories
        scanPackageInDirectory(SRC_TEST_PYTHON, packagePath, engineDescriptor, true);
        scanPackageInDirectory(SRC_MAIN_PYTHON, packagePath, engineDescriptor, false);
    }

    private void resolveClassSelector(ClassSelector selector, EngineDescriptor engineDescriptor) {
        String className = selector.getClassName();
        LOG.debug("Resolving class: {}", className);

        // Convert class name to file path
        String filePath = className.replace('.', '/') + ".py";

        // Look for the file in test and main directories
        findAndAddPythonFile(SRC_TEST_PYTHON, filePath, engineDescriptor, true);
        findAndAddPythonFile(SRC_MAIN_PYTHON, filePath, engineDescriptor, false);
    }

    private void resolveMethodSelector(MethodSelector selector, EngineDescriptor engineDescriptor) {
        String className = selector.getClassName();
        String methodName = selector.getMethodName();
        LOG.debug("Resolving method: {}.{}", className, methodName);

        // Convert class name to file path
        String filePath = className.replace('.', '/') + ".py";

        // Look for the file and add specific method
        findAndAddPythonMethod(SRC_TEST_PYTHON, filePath, methodName, engineDescriptor, true);
        findAndAddPythonMethod(SRC_MAIN_PYTHON, filePath, methodName, engineDescriptor, false);
    }

    private void resolveDirectorySelector(DirectorySelector selector, EngineDescriptor engineDescriptor) {
        Path directory = selector.getPath();
        LOG.debug("Resolving directory: {}", directory);

        scanPythonDirectory(directory, engineDescriptor, isTestDirectory(directory));
    }

    private void resolveFileSelector(FileSelector selector, EngineDescriptor engineDescriptor) {
        Path file = selector.getPath();
        LOG.debug("Resolving file: {}", file);

        if (file.toString().endsWith(".py")) {
            addPythonFile(file, engineDescriptor, isTestFile(file));
        }
    }

    private void scanPythonDirectory(Path directory, EngineDescriptor engineDescriptor, boolean isTestDirectory) {
        LOG.debug("Scanning Python directory: {}", directory);

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".py"))
                 .forEach(path -> addPythonFile(path, engineDescriptor, isTestDirectory));
        } catch (IOException e) {
            LOG.error("Error scanning directory: {}", directory, e);
        }
    }

    private void scanPackageInDirectory(String baseDir, String packagePath, EngineDescriptor engineDescriptor, boolean isTestDirectory) {
        Path basePath = Paths.get(baseDir);
        Path packageDir = basePath.resolve(packagePath);

        if (Files.exists(packageDir)) {
            scanPythonDirectory(packageDir, engineDescriptor, isTestDirectory);
        }
    }

    private void findAndAddPythonFile(String baseDir, String filePath, EngineDescriptor engineDescriptor, boolean isTestDirectory) {
        Path basePath = Paths.get(baseDir);
        Path fullPath = basePath.resolve(filePath);

        if (Files.exists(fullPath)) {
            addPythonFile(fullPath, engineDescriptor, isTestDirectory);
        }
    }

    private void findAndAddPythonMethod(String baseDir, String filePath, String methodName, EngineDescriptor engineDescriptor, boolean isTestDirectory) {
        Path basePath = Paths.get(baseDir);
        Path fullPath = basePath.resolve(filePath);

        if (Files.exists(fullPath)) {
            addPythonMethod(fullPath, methodName, engineDescriptor, isTestDirectory);
        }
    }

    private void addPythonFile(Path filePath, EngineDescriptor engineDescriptor, boolean isTestDirectory) {
        LOG.debug("Adding Python file: {}", filePath);

        try {
            PytestAstParser astParser = new PytestAstParser();
            TestDescriptor fileDescriptor = astParser.parsePythonFile(filePath, isTestDirectory);

            if (fileDescriptor != null) {
                engineDescriptor.addChild(fileDescriptor);
            }
        } catch (Exception e) {
            LOG.error("Error parsing Python file: {}", filePath, e);
        }
    }

    private void addPythonMethod(Path filePath, String methodName, EngineDescriptor engineDescriptor, boolean isTestDirectory) {
        LOG.debug("Adding Python method: {}::{}", filePath, methodName);

        try {
            PytestAstParser astParser = new PytestAstParser();
            TestDescriptor methodDescriptor = astParser.parsePythonMethod(filePath, methodName, isTestDirectory);

            if (methodDescriptor != null) {
                engineDescriptor.addChild(methodDescriptor);
            }
        } catch (Exception e) {
            LOG.error("Error parsing Python method: {}::{}", filePath, methodName, e);
        }
    }

    private boolean isTestDirectory(Path directory) {
        return directory.toString().contains(SRC_TEST_PYTHON);
    }

    private boolean isTestFile(Path file) {
        return file.toString().contains(SRC_TEST_PYTHON);
    }
}
