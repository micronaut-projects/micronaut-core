/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.compiler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

import io.micronaut.inject.ast.ClassElement;

/**
 * Compiler for Python applications using Micronaut annotation processing.
 * This compiler can operate in two modes:
 * <ul>
 *   <li>In-memory mode: {@link #buildClassLoader()} - compiles to memory and returns a ClassLoader</li>
 *   <li>File system mode: {@link #compile()} - compiles to disk when targetDir is specified</li>
 * </ul>
 *
 * @author Micronaut
 * @since 4.8.0
 */
public final class PyronautCompiler {

    private static final Pattern JAVA_PACKAGE_PATTERN = Pattern.compile("^[a-z][a-zA-Z0-9_]*(\\.[a-z][a-zA-Z0-9_]*)*$");
    private static final String DEFAULT_PACKAGE_NAME = "pyronaut_application";

    private final String packageName;
    private final String pythonSrc;
    private final String pythonCode;
    private final String javaSrc;
    private final String applicationClass;
    private final File targetDir;
    private final List<File> classpath;
    private final Consumer<ClassElement> classElementCallback;

    private PyronautCompiler(Builder builder) {
        this.packageName = builder.packageName;
        this.pythonSrc = builder.pythonSrc;
        this.pythonCode = builder.pythonCode;
        this.javaSrc = builder.javaSrc;
        this.applicationClass = builder.applicationClass;
        this.targetDir = builder.targetDir;
        this.classpath = builder.classpath != null ? new ArrayList<>(builder.classpath) : null;
        this.classElementCallback = builder.classElementCallback;

        validateConfiguration();
    }

    /**
     * Create a new builder for PyronautCompiler.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Compile the application in-memory and return a ClassLoader containing the compiled classes.
     * This method is used when you want to load and run the application without writing to disk.
     *
     * @return A ClassLoader containing the compiled application classes
     * @throws IllegalStateException if compilation fails
     */
    public ClassLoader buildClassLoader() {
        PyronautJavaCompiler compiler = new PyronautJavaCompiler();
        if (classElementCallback != null) {
            compiler.setClassElementCallback(classElementCallback);
        }
        JavaFileObject[] sources = createJavaSources();
        Iterable<JavaFileObject> compiledClasses = compiler.compileInMemory(sources, classpath);
        return new JavaFileObjectClassLoader(compiledClasses);
    }

    /**
     * Compile the application to the file system.
     * Requires that targetDir was specified in the builder.
     *
     * @throws IllegalStateException if targetDir is not set or compilation fails
     */
    public void compile() {
        if (targetDir == null) {
            throw new IllegalStateException("targetDir must be specified for file system compilation mode");
        }

        PyronautJavaCompiler compiler = new PyronautJavaCompiler();
        JavaFileObject[] sources = createJavaSources();

        compiler.compileToDisk(targetDir, sources, classpath);
    }

    private void validateConfiguration() {
        // Validate package name
        if (packageName != null && !JAVA_PACKAGE_PATTERN.matcher(packageName).matches()) {
            throw new IllegalArgumentException("Invalid package name: " + packageName);
        }

        // Validate that either pythonSrc or pythonCode is specified
        if ((pythonSrc == null || pythonSrc.isEmpty()) && (pythonCode == null || pythonCode.isEmpty())) {
            throw new IllegalArgumentException("Either pythonSrc or pythonCode must be specified");
        }
    }

    private JavaFileObject[] createJavaSources() {
        List<JavaFileObject> sources = new ArrayList<>();

        // Add user-provided Java sources if specified
        if (javaSrc != null && !javaSrc.isEmpty()) {
            try {
                addJavaSourcesFromDirectory(sources, Paths.get(javaSrc));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read Java sources from: " + javaSrc, e);
            }
        }

        if (applicationClass != null && !applicationClass.isEmpty()) {
            // User provided application class - check if it exists in javaSrc
            if (javaSrc == null || javaSrc.isEmpty()) {
                throw new IllegalArgumentException("javaSrc must be specified when applicationClass is provided");
            }
            // The application class should already be in the sources list from javaSrc
        } else {
            // Generate the default PyronautMain class
            String className = getPackageName() + ".PyronautMain";
            String sourceCode = generateMainClassSource();
            sources.add(new SimpleJavaFileObject(
                java.net.URI.create("string:///" + className.replace('.', '/') + ".java"),
                JavaFileObject.Kind.SOURCE
            ) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return sourceCode;
                }
            });
        }

        return sources.toArray(new JavaFileObject[0]);
    }

    private void addJavaSourcesFromDirectory(List<JavaFileObject> sources, Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(path -> {
                     try {
                         String content = Files.readString(path);
                         sources.add(new SimpleJavaFileObject(
                             java.net.URI.create("file:///" + path),
                             JavaFileObject.Kind.SOURCE
                         ) {
                             @Override
                             public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                                 return content;
                             }
                         });
                     } catch (IOException e) {
                         throw new RuntimeException("Failed to read Java source: " + path, e);
                     }
                 });
        }
    }

    private String generateMainClassSource() {
        boolean hasSrc = pythonSrc != null && !pythonSrc.isEmpty();
        boolean hasCode = pythonCode != null && !pythonCode.isEmpty();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(getPackageName()).append(";\n\n");
        sb.append("import io.micronaut.runtime.Micronaut;\n");
        sb.append("import io.micronaut.python.processing.annotation.PythonApplication;\n\n");
        sb.append("@PythonApplication(\n");

        if (hasSrc) {
            sb.append("    src = \"").append(pythonSrc).append("\"");
            if (hasCode) {
                sb.append(",\n");
            }
        }
        if (hasCode) {
            sb.append("    code = \"").append(escapeJavaString(pythonCode)).append("\"");
        }

        sb.append("\n)\n");
        sb.append("class PyronautMain {\n");
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        Micronaut.run(args);\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String getPackageName() {
        return DEFAULT_PACKAGE_NAME;
    }

    private String escapeJavaString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Builder for PyronautCompiler.
     */
    public static final class Builder {
        private String packageName;
        private String pythonSrc;
        private String pythonCode;
        private String javaSrc;
        private String applicationClass;
        private File targetDir;
        private List<File> classpath;
        private Consumer<ClassElement> classElementCallback;

        private Builder() {
        }

        /**
         * Set the package name for the generated application class.
         * Must be a valid Java package name. Defaults to "pyronaut_application".
         *
         * @param packageName The package name
         * @return This builder
         */
        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        /**
         * Set the Python source directory to scan for .py files.
         *
         * @param pythonSrc The Python source directory path
         * @return This builder
         */
        public Builder pythonSrc(String pythonSrc) {
            this.pythonSrc = pythonSrc;
            return this;
        }

        /**
         * Set the inline Python code to process.
         *
         * @param pythonCode The Python code
         * @return This builder
         */
        public Builder pythonCode(String pythonCode) {
            this.pythonCode = pythonCode;
            return this;
        }

        /**
         * Set the Java source directory to scan for .java files to include in compilation.
         *
         * @param javaSrc The Java source directory path
         * @return This builder
         */
        public Builder javaSrc(String javaSrc) {
            this.javaSrc = javaSrc;
            return this;
        }

        /**
         * Set the fully qualified name of an existing application class annotated with @PythonApplication.
         * If specified, no Java source will be generated. Requires javaSrc to be set.
         *
         * @param applicationClass The application class name
         * @return This builder
         */
        public Builder applicationClass(String applicationClass) {
            this.applicationClass = applicationClass;
            return this;
        }

        /**
         * Set the target directory for file system compilation mode.
         *
         * @param targetDir The target directory
         * @return This builder
         */
        public Builder targetDir(File targetDir) {
            this.targetDir = targetDir;
            return this;
        }

        /**
         * Set additional classpath entries for compilation.
         *
         * @param classpath The classpath files
         * @return This builder
         */
        public Builder classpath(List<File> classpath) {
            this.classpath = classpath != null ? new ArrayList<>(classpath) : null;
            return this;
        }

        /**
         * Set a callback to be invoked for each class element created during processing.
         * This is primarily used for testing purposes to capture class elements.
         *
         * @param classElementCallback The callback function
         * @return This builder
         */
        public Builder classElementCallback(Consumer<ClassElement> classElementCallback) {
            this.classElementCallback = classElementCallback;
            return this;
        }

        /**
         * Build the PyronautCompiler instance.
         *
         * @return A new PyronautCompiler
         */
        public PyronautCompiler build() {
            return new PyronautCompiler(this);
        }
    }
}
