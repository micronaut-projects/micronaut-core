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
package io.micronaut.micronautc;

import org.jspecify.annotations.Nullable;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Micronaut-aware javac launcher.
 */
public final class Micronautc {

    private static final String USE_CONTEXT_CLASSLOADER_PROPERTY = "micronaut.processing.use.context.classloader";

    private static final String PROCESSORS = String.join(",",
        "io.micronaut.annotation.processing.MixinVisitorProcessor",
        "io.micronaut.annotation.processing.PackageElementVisitorProcessor",
        "io.micronaut.annotation.processing.TypeElementVisitorProcessor",
        "io.micronaut.annotation.processing.AggregatingTypeElementVisitorProcessor",
        "io.micronaut.annotation.processing.BeanDefinitionInjectProcessor"
    );
    private Micronautc() {
    }

    /**
     * Main entry point.
     *
     * @param args Compiler arguments
     */
    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        var javaHome = resolveJavaHome();
        if (javaHome == null || javaHome.isBlank()) {
            System.err.println("A JDK is required. Set MICRONAUTC_JAVA_HOME or micronautc.java.home.");
            return 1;
        }
        System.setProperty("java.home", javaHome);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("Unable to locate system Java compiler (JDK required).");
            return 1;
        }

        String[] compilerArgs = prepareCompilerArgs(args);
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            URLClassLoader processorClassLoader = createProcessorClassLoader(compilerArgs, originalClassLoader);
            if (processorClassLoader == null) {
                return compiler.run(System.in, System.out, System.err, compilerArgs);
            }
            try (processorClassLoader) {
                Thread.currentThread().setContextClassLoader(processorClassLoader);
                String previousUseContextClassLoader = System.getProperty(USE_CONTEXT_CLASSLOADER_PROPERTY);
                System.setProperty(USE_CONTEXT_CLASSLOADER_PROPERTY, Boolean.TRUE.toString());
                try {
                    return compiler.run(System.in, System.out, System.err, compilerArgs);
                } finally {
                    if (previousUseContextClassLoader == null) {
                        System.clearProperty(USE_CONTEXT_CLASSLOADER_PROPERTY);
                    } else {
                        System.setProperty(USE_CONTEXT_CLASSLOADER_PROPERTY, previousUseContextClassLoader);
                    }
                }
            } finally {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        } catch (IOException e) {
            System.err.println("Unable to configure processor class loader: " + e.getMessage());
            return 1;
        }
    }

    private static String[] prepareCompilerArgs(String[] args) {
        if (hasHelpArgument(args)) {
            return args;
        }
        List<String> compilerArgs = new ArrayList<>(Arrays.asList(args));
        extendProcessorPathWithClassPath(compilerArgs);
        if (!hasProcessorConfiguration(args) && !hasProcNone(args)) {
            compilerArgs.add("-processor");
            compilerArgs.add(PROCESSORS);
        }
        return compilerArgs.toArray(String[]::new);
    }

    private static boolean hasHelpArgument(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-help".equals(arg) || "-?".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void extendProcessorPathWithClassPath(List<String> compilerArgs) {
        String classPath = getPathOptionValue(compilerArgs, "-classpath", "-cp", "--class-path");
        if (classPath == null || classPath.isBlank()) {
            return;
        }
        PathOption processorPathOption = findPathOption(compilerArgs, "-processorpath", "--processor-path");
        if (processorPathOption == null || processorPathOption.value == null || processorPathOption.value.isBlank()) {
            return;
        }
        String merged = mergePathEntries(processorPathOption.value, classPath);
        if (!merged.equals(processorPathOption.value)) {
            setPathOptionValue(compilerArgs, processorPathOption, merged);
        }
    }

    private static boolean hasProcessorConfiguration(String[] args) {
        for (String arg : args) {
            if ("-processor".equals(arg) || arg.startsWith("-processor=")) {
                return true;
            }
            if ("--processor".equals(arg) || arg.startsWith("--processor=")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasProcNone(String[] args) {
        for (String arg : args) {
            if ("-proc:none".equals(arg) || "--proc:none".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable URLClassLoader createProcessorClassLoader(String[] compilerArgs, ClassLoader parent) throws IOException {
        String processorPath = getPathOptionValue(Arrays.asList(compilerArgs), "-processorpath", "--processor-path");
        if (processorPath == null || processorPath.isBlank()) {
            return null;
        }
        List<URL> urls = new ArrayList<>();
        for (String path : processorPath.split(Pattern.quote(File.pathSeparator))) {
            if (!path.isBlank()) {
                urls.add(new File(path).toURI().toURL());
            }
        }
        if (urls.isEmpty()) {
            return null;
        }
        return new URLClassLoader(urls.toArray(URL[]::new), parent);
    }

    private static String mergePathEntries(String first, String second) {
        Set<String> merged = new LinkedHashSet<>();
        addPathEntries(merged, first);
        addPathEntries(merged, second);
        return String.join(File.pathSeparator, merged);
    }

    private static void addPathEntries(Set<String> paths, String value) {
        for (String entry : value.split(Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                paths.add(entry);
            }
        }
    }

    private static @Nullable String getPathOptionValue(List<String> args, String... names) {
        PathOption pathOption = findPathOption(args, names);
        return pathOption == null ? null : pathOption.value;
    }

    private static @Nullable PathOption findPathOption(List<String> args, String... names) {
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            for (String name : names) {
                if (name.equals(arg) && i + 1 < args.size()) {
                    return new PathOption(i, false, name, args.get(i + 1));
                }
                String prefix = name + "=";
                if (arg.startsWith(prefix)) {
                    return new PathOption(i, true, name, arg.substring(prefix.length()));
                }
            }
        }
        return null;
    }

    private static void setPathOptionValue(List<String> args, PathOption pathOption, String newValue) {
        if (pathOption.inline) {
            args.set(pathOption.index, pathOption.name + "=" + newValue);
        } else if (pathOption.index + 1 < args.size()) {
            args.set(pathOption.index + 1, newValue);
        }
    }

    private record PathOption(int index, boolean inline, String name, @Nullable String value) {
    }

    private static String resolveJavaHome() {
        String configured = System.getProperty("micronautc.java.home");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String envConfigured = System.getenv("MICRONAUTC_JAVA_HOME");
        if (envConfigured != null && !envConfigured.isBlank()) {
            return envConfigured;
        }
        String javaHomeEnv = System.getenv("JAVA_HOME");
        if (javaHomeEnv != null && !javaHomeEnv.isBlank()) {
            return javaHomeEnv;
        }
        return System.getProperty("java.home");
    }
}
