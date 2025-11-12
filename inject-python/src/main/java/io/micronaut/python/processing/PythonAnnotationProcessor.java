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
package io.micronaut.python.processing;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import io.micronaut.core.naming.NameUtils;
import org.graalvm.polyglot.Source;

import io.micronaut.annotation.processing.AbstractInjectAnnotationProcessor;
import io.micronaut.annotation.processing.visitor.JavaNativeElement;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.python.processing.annotation.PythonApplication;
import io.micronaut.python.processing.beans.PythonBeanDefinitionProcessor;
import io.micronaut.python.processing.visitor.PythonTypeElementVisitorProcessor;

/**
 * Annotation processor for {@link PythonApplication} that enables Python AST processing
 * during Java compilation.
 *
 * @author Micronaut
 * @since 4.8.0
 */
@SupportedAnnotationTypes("io.micronaut.python.processing.annotation.PythonApplication")
public class PythonAnnotationProcessor extends AbstractInjectAnnotationProcessor implements AutoCloseable {
    public static final String APPLICATION_PATH = "GRAALPY-VFS/micronaut-application/";
    public static final String APPLICATION_LAUNCHER_PATH = APPLICATION_PATH + "main.py";
    public static final String APPLICATION_SRC_PATH = "GRAALPY-VFS/micronaut-application/src/";

    private PythonAstParser parser;
    private Consumer<ClassElement> classElementCallback;

    /**
     * Set the callback to be invoked for each class element created during processing.
     * This is primarily used for testing purposes.
     *
     * @param callback The callback function
     */
    public void setClassElementCallback(Consumer<ClassElement> callback) {
        this.classElementCallback = callback;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        parser = new PythonAstParser();
    }

    @Override
    public void close() throws Exception {
        parser.close();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }

        for (TypeElement annotation : annotations) {
            if (PythonApplication.class.getName().equals(annotation.getQualifiedName().toString())) {
                processPythonApplications(roundEnv);
            }
        }
        if (roundEnv.processingOver()) {
            parser.close();
            return false;
        } else {
            return true;
        }
    }

    private void processPythonApplications(RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(PythonApplication.class);

        for (Element element : elements) {
            PythonApplication annotation = element.getAnnotation(PythonApplication.class);
            if (annotation != null && element instanceof TypeElement typeElement) {
                processAnnotation(typeElement, annotation);
            }
        }
    }

    private void processAnnotation(TypeElement element, PythonApplication annotation) {
        try {
            ClassElement originatingElement = javaVisitorContext.getRequiredClassElement(
                element.getQualifiedName().toString(),
                javaVisitorContext.getElementAnnotationMetadataFactory()
            );
            PythonEnvironment environment;
            // Transform the code for processing (to detect Micronaut annotations)
            List<PythonAstParser.TransformResult> transformedList =
                applyASTTransforms(annotation, originatingElement);
            // Extract decorators from the code
            if (transformedList.isEmpty()) {
                return;
            }

            // Then parse the transformed code
            String srcDir = annotation.src();
            try {
                List<Source> sourceList = transformedList
                    .stream()
                    .map(PythonAstParser.TransformResult::transformedSource)
                    .toList();
                environment = parser.parse(
                    sourceList,
                    srcDir
                );
            } catch (Exception e) {
                throw new ProcessingException(originatingElement, "Error parsing transformed python code: " + e.getMessage());
            }

            String mainPy;
            StringBuilder filesList = new StringBuilder();
            if (StringUtils.isNotEmpty(annotation.code())) {
                mainPy = annotation.code();
            } else {
                // on concrete main
                mainPy = null;

                if (StringUtils.isNotEmpty(srcDir)) {
                    // source mode, so we need to write out each source to META-INF
                    Map<PathEntry, List<String>> exportedModules = new LinkedHashMap<>();
                    for (PythonAstParser.TransformResult transformResult : transformedList) {
                        Source source = transformResult.originalSource();
                        String path = source.getPath();
                        int i = path.indexOf(srcDir);
                        if (i > 0) {
                            path = path.substring(i + srcDir.length() + 1);
                        }

                        if (!srcDir.isEmpty() && path.startsWith(srcDir)) {
                            path = path.substring(srcDir.length() + 1);
                        }
                        String targetSource = APPLICATION_SRC_PATH + path;
                        if (!transformResult.exportedTypes().isEmpty()) {
                            // has exported types
                            int parentIndex = path.lastIndexOf('/');
                            if (parentIndex > -1) {
                                String parentPath = path.substring(0, parentIndex + 1);
                                exportedModules.computeIfAbsent(new PathEntry(parentPath, path.substring(parentIndex)), k -> new ArrayList<>())
                                    .addAll(transformResult.exportedTypes());
                            } else {
                                exportedModules.computeIfAbsent(new PathEntry("", path), k -> new ArrayList<>())
                                    .addAll(transformResult.exportedTypes());
                            }
                        }
                        filesList.append("/META-INF/").append(targetSource).append("\n");
                        javaVisitorContext.visitMetaInfFile(targetSource, originatingElement)
                            .ifPresent(generatedFile -> {
                                try (var writer = generatedFile.openWriter()) {
                                    writer.write(source.getCharacters().toString());
                                } catch (IOException e) {
                                    throw new ProcessingException(originatingElement, "Failed to write transformed Python code to META-INF", e);
                                }
                            });
                    }

                    exportedModules.forEach((path, types) -> {
                        String initFilePath = APPLICATION_SRC_PATH + path.parent + "__init__.py";
                        StringBuilder initContent = new StringBuilder();
                        if (!types.isEmpty()) {
                            for (String type : types) {
                                initContent.append("from .").append(NameUtils.filename(path.filename())).append(" import ").append(type).append('\n');
                            }
                            initContent.append("\n__all__ = ").append(types).append("\n");
                        }
                        filesList.append("/META-INF/").append(initFilePath).append("\n");
                        javaVisitorContext.visitMetaInfFile(initFilePath, originatingElement)
                            .ifPresent(generatedFile -> {
                                try (var writer = generatedFile.openWriter()) {
                                    writer.write(initContent.toString());
                                } catch (IOException e) {
                                    throw new ProcessingException(originatingElement, "Failed to write transformed Python code to META-INF", e);
                                }
                            });
                    });
                }

            }

            // Create processing environment and visitor context
            PythonProcessingEnvironment processingEnvironment =
                new PythonProcessingEnvironment(environment, javaVisitorContext, element);

            Map<String, String> allDecorators = new LinkedHashMap<>();
            Map<String, List<Map<String, String>>> allImports = new LinkedHashMap<>();
            for (PythonAstParser.TransformResult transformResult : transformedList) {

                Map<String, String> decorators = transformResult.decorators();
                allDecorators.putAll(decorators);

                Map<String, List<Map<String, String>>> javaClassImports = transformResult.javaClassImports();
                allImports.putAll(javaClassImports);
            }
            writeAllToVFS(filesList, allDecorators, allImports, originatingElement);

            // Run type element visitor processing
            PythonTypeElementVisitorProcessor typeElementVisitorProcessor =
                new PythonTypeElementVisitorProcessor();
            typeElementVisitorProcessor.init(processingEnvironment);
            typeElementVisitorProcessor.process(processingEnvironment);

            // Process bean definitions for Python classes
            var beanDefinitionProcessor = new PythonBeanDefinitionProcessor();
            beanDefinitionProcessor.processBeanDefinitions(processingEnvironment);

            // Invoke callback for each class element if callback is set
            if (classElementCallback != null) {
                processingEnvironment.classes().values().forEach(classElementCallback);
            }

            // Write original Python code to META-INF
            if (mainPy != null) {
                javaVisitorContext.visitMetaInfFile(APPLICATION_LAUNCHER_PATH, originatingElement)
                    .ifPresent(generatedFile -> {
                        try (var writer = generatedFile.openWriter()) {
                            writer.write(mainPy);
                        } catch (IOException e) {
                            throw new ProcessingException(originatingElement, "Failed to write transformed Python code to META-INF", e);
                        }
                    });
            }

            // The visitor context is now ready for use by Micronaut's type visitors
            note("Successfully processed Python environment with " +
                environment.classes().size() + " classes and " +
                environment.decorators().size() + " decorators");

        } catch (ProcessingException e) {
            error(e.getMessage());
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String stacktrace = sw.toString();
            error("Failed Trace: %s", stacktrace);
            error("Fatal error processing Python code: %s", e.getMessage());
        }
    }

    private List<PythonAstParser.TransformResult> applyASTTransforms(PythonApplication annotation, ClassElement originatingElement) {
        // Process inline code if provided
        String code = annotation.code();
        if (!code.isEmpty()) {
            // Transform the source code first
            try {
                return parser.transform(javaVisitorContext, Source.create("python", code));
            } catch (Exception e) {
                throw new ProcessingException(originatingElement, "Error transforming python code: " + e.getMessage(), e);
            }

        } else {
            // Process directory scanning if provided
            String srcDir = annotation.src();
            List<Source> sources = new ArrayList<>();
            if (!srcDir.isEmpty()) {
                Path directory = Paths.get(srcDir);
                if (Files.isDirectory(directory)) {
                    try (Stream<Path> stream = Files.walk(directory)) {
                        Path[] files = stream
                            .filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".py"))
                            .toArray(Path[]::new);
                        for (Path file : files) {
                            sources.add(Source.newBuilder("python", file.toFile()).build());
                        }
                    } catch (IOException e) {
                        throw new ProcessingException(originatingElement, "Error processing python code in directory [" + directory + "]: " + e.getMessage());
                    }
                } else {
                    throw new ProcessingException(originatingElement, "Source directory does not exist: " + srcDir);
                }
            } else {
                throw new ProcessingException(originatingElement, "Source directory does not exist: " + srcDir);
            }

            return parser.transform(javaVisitorContext, sources.toArray(new Source[0]));
        }
    }

    private void writeAllToVFS(
        StringBuilder filesList,
        Map<String, String> decorators,
        Map<String, List<Map<String, String>>> javaClassImports,
        ClassElement originatingElement) {
        // Collect all packages that need __init__.py files
        Map<String, List<String>> decoratorsByPackage = new LinkedHashMap<>();
        Map<String, Map<String, String>> javaClassesByPackage = new LinkedHashMap<>();



        // Process decorators
        if (decorators != null && !decorators.isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : decorators.entrySet()) {
                String decoratorName = entry.getKey();
                String decoratorCode = entry.getValue();

                // Transform io. prefixed package names to avoid conflict with Python's builtin io module
                String transformedDecoratorName = decoratorName.startsWith("io.") ? decoratorName.substring(3) : decoratorName;

                // Split into package and simple name
                int lastDotIndex = transformedDecoratorName.lastIndexOf('.');
                String packageName;

                ClassElement classElement = javaVisitorContext.getClassElement(decoratorName).orElse(null);
                if (classElement != null && isNestedClass(classElement) && classElement.getEnclosingType().isPresent()) {
                    packageName = classElement.getEnclosingType().get().getPackageName();
                } else {
                    packageName = lastDotIndex > 0 ? transformedDecoratorName.substring(0, lastDotIndex) : "";
                }

                String simpleName = transformedDecoratorName.substring(lastDotIndex + 1);

                // Add to package map
                decoratorsByPackage.computeIfAbsent(packageName, k -> new java.util.ArrayList<>()).add(simpleName);

                // Determine file path
                String packagePath = transformedDecoratorName.replace('.', '/');
                String filePath = APPLICATION_SRC_PATH + packagePath + ".py";

                // Write decorator file
                javaVisitorContext.visitMetaInfFile(filePath, originatingElement)
                    .ifPresent(generatedFile -> {
                        try (var writer = generatedFile.openWriter()) {
                            writer.write(decoratorCode);
                        } catch (IOException e) {
                            throw new ProcessingException(originatingElement, "Failed to write decorator to VFS: " + filePath, e);
                        }
                    });

                // Add to files list
                filesList.append("/META-INF/").append(filePath).append("\n");
            }
        }

        // Process Java class imports
        if (javaClassImports != null && !javaClassImports.isEmpty()) {
            javaClassImports.forEach((packageName, imports) -> {
                Map<String, String> classMappings = new LinkedHashMap<>();
                for (Map<String, String> importInfo : imports) {
                    String variable = importInfo.get("variable");
                    String className = importInfo.get("class_name");
                    classMappings.put(variable, className);
                }
                javaClassesByPackage.put(packageName, classMappings);
            });
        }

        // Collect all packages that need __init__.py files
        java.util.Set<String> allPackages = new LinkedHashSet<>();

        // Add packages from decorators
        collectPackageNames(decoratorsByPackage.keySet(), allPackages);

        // Add packages from Java classes
        collectPackageNames(javaClassesByPackage.keySet(), allPackages);

        // Write __init__.py files for all packages
        for (String packageName : allPackages) {
            List<String> decoratorsInPackage = decoratorsByPackage.get(packageName);
            Map<String, String> javaClassesInPackage = javaClassesByPackage.get(packageName);

            String packagePath = packageName.replace('.', '/');
            String initFilePath = APPLICATION_SRC_PATH + packagePath + "/__init__.py";

            StringBuilder initContent = new StringBuilder();

            // Add Java import if we have Java classes
            if (javaClassesInPackage != null && !javaClassesInPackage.isEmpty()) {
                initContent.append("import java\n\n");
            }

            List<String> allNames = new java.util.ArrayList<>();

            // Add decorator imports
            if (decoratorsInPackage != null) {
                for (String decoratorName : decoratorsInPackage) {
                    initContent.append("from .").append(decoratorName).append(" import ").append(decoratorName).append("\n");
                    allNames.add(decoratorName);
                }
            }

            // Add Java class assignments
            if (javaClassesInPackage != null) {
                for (java.util.Map.Entry<String, String> mapping : javaClassesInPackage.entrySet()) {
                    initContent.append(mapping.getKey()).append(" = java.type('").append(mapping.getValue()).append("')\n");
                    allNames.add(mapping.getKey());
                }
            }

            // Add imports for subpackages
            for (String subPackage : allPackages) {
                if (subPackage.startsWith(packageName + ".") && !subPackage.equals(packageName)) {
                    String relativeSubPackage = subPackage.substring(packageName.length() + 1);
                    if (!relativeSubPackage.contains(".")) { // Direct child package
                        initContent.append("from . import ").append(relativeSubPackage).append("\n");
                        allNames.add(relativeSubPackage);
                    }
                }
            }

            // Add __all__
            if (!allNames.isEmpty()) {
                initContent.append("\n__all__ = ").append(allNames).append("\n");
            }

            // Write the __init__.py file
            javaVisitorContext.visitMetaInfFile(initFilePath, originatingElement)
                .ifPresent(generatedFile -> {
                    try (var writer = generatedFile.openWriter()) {
                        writer.write(initContent.toString());
                    } catch (IOException e) {
                        throw new ProcessingException(originatingElement, "Failed to write __init__.py to VFS: " + initFilePath, e);
                    }
                });

            filesList.append("/META-INF/").append(initFilePath).append("\n");
        }

        // Write fileslist.txt
        javaVisitorContext.visitMetaInfFile(APPLICATION_PATH + "fileslist.txt", originatingElement)
            .ifPresent(generatedFile -> {
                try (var writer = generatedFile.openWriter()) {
                    writer.write(filesList.toString());
                } catch (IOException e) {
                    throw new ProcessingException(originatingElement, "Failed to write fileslist.txt to VFS");
                }
            });
    }

    private static void collectPackageNames(Set<String> decoratorsByPackage, Set<String> allPackages) {
        for (String packageName : decoratorsByPackage) {
            if (!packageName.isEmpty()) {
                String[] parts = packageName.split("\\.");
                for (int i = 1; i <= parts.length; i++) {
                    allPackages.add(String.join(".", java.util.Arrays.copyOf(parts, i)));
                }
            }
        }
    }

    private static boolean isNestedClass(ClassElement classElement) {
        try {
            if (classElement.getNativeType() instanceof JavaNativeElement jne &&
                jne.element() instanceof TypeElement typeElement) {

                javax.lang.model.element.NestingKind nestingKind = typeElement.getNestingKind();
                return nestingKind == javax.lang.model.element.NestingKind.MEMBER;
            }
        } catch (Exception e) {
            // Ignore and return false
        }
        return false;
    }

    record PathEntry(String parent, String filename) {}
}
