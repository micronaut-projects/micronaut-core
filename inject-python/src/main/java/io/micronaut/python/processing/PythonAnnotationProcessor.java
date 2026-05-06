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

import io.micronaut.annotation.processing.AbstractInjectAnnotationProcessor;
import io.micronaut.annotation.processing.visitor.JavaNativeElement;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.python.processing.beans.PythonBeanDefinitionProcessor;
import io.micronaut.python.processing.visitor.PythonTypeElementVisitorProcessor;
import org.graalvm.polyglot.Source;
import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Annotation processor for {@code io.micronaut.context.python.annotation.PythonApplication} that enables Python AST processing
 * during Java compilation.
 *
 * @author Micronaut
 * @since 4.8.0
 */
@SupportedAnnotationTypes(PythonAnnotationProcessor.PYTHON_APPLICATION_ANNOTATION)
public class PythonAnnotationProcessor extends AbstractInjectAnnotationProcessor implements AutoCloseable {
    static final String PYTHON_APPLICATION_ANNOTATION = "io.micronaut.context.python.annotation.PythonApplication";
    public static final String APPLICATION_PATH = "GRAALPY-VFS/micronaut-application/";
    public static final String APPLICATION_SRC_PATH = "GRAALPY-VFS/micronaut-application/src/";
    public static final String APPLICATION_LAUNCHER_PATH = APPLICATION_SRC_PATH + "__main__.py";
    private static final Set<String> PYTHON_KEYWORDS = Set.of(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break",
        "class", "continue", "def", "del", "elif", "else", "except", "finally",
        "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
        "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
    );

    private PythonAstParser parser;
    private Consumer<ClassElement> classElementCallback;
    private ClassLoader classLoader;

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
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = PythonAnnotationProcessor.class.getClassLoader();
            }
        }
        parser = new PythonAstParser(classLoader);
    }

    @Override
    public void close() throws Exception {
        parser.close();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            parser.close();
            return false;
        }
        if (annotations.isEmpty()) {
            return false;
        }

        for (TypeElement annotation : annotations) {
            if (PYTHON_APPLICATION_ANNOTATION.equals(annotation.getQualifiedName().toString())) {
                processPythonApplications(roundEnv);
            }
        }
        return false;
    }

    private void processPythonApplications(RoundEnvironment roundEnv) {
        TypeElement pythonApplication = processingEnv.getElementUtils().getTypeElement(PYTHON_APPLICATION_ANNOTATION);
        if (pythonApplication == null) {
            return;
        }
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(pythonApplication);

        for (Element element : elements) {
            if (element instanceof TypeElement typeElement) {
                PythonApplicationValues values = readPythonApplicationValues(element).orElse(null);
                if (values != null) {
                    processAnnotation(typeElement, values);
                }
            }
        }
    }

    private void processAnnotation(TypeElement element, PythonApplicationValues values) {
        try {
            ClassElement originatingElement = javaVisitorContext.getRequiredClassElement(
                element.getQualifiedName().toString(),
                javaVisitorContext.getElementAnnotationMetadataFactory()
            );
            PythonEnvironment environment = null;
            // Transform the code for processing (to detect Micronaut annotations)
            List<PythonAstParser.TransformResult> transformedList =
                applyASTTransforms(values, originatingElement);
            // Extract decorators from the code
            if (transformedList.isEmpty()) {
                return;
            }

            // Then parse the transformed code
            String[] srcDirs = values.src();
            boolean hasSrcDirs = srcDirs != null && srcDirs.length != 0;
            if (hasSrcDirs) {
                try {
                    List<Source> sourceList = transformedList
                        .stream()
                        .map(PythonAstParser.TransformResult::transformedSource)
                        .toList();
                    environment = parser.parse(
                        sourceList,
                        Arrays.asList(srcDirs),
                        javaVisitorContext
                    );
                } catch (Exception e) {
                    throw new ProcessingException(originatingElement, "Error parsing transformed python code: " + e.getMessage());
                }
            }

            String mainPy;
            StringBuilder filesList = new StringBuilder();
            if (StringUtils.isNotEmpty(values.code())) {
                mainPy = values.code();
                // Write original Python code to META-INF
                javaVisitorContext.visitMetaInfFile(APPLICATION_LAUNCHER_PATH, originatingElement)
                    .ifPresent(generatedFile -> {
                        try (var writer = generatedFile.openWriter()) {
                            writer.write(mainPy);
                        } catch (IOException e) {
                            throw new ProcessingException(originatingElement, "Failed to write Python code to [" + APPLICATION_LAUNCHER_PATH + "]: " + e.getMessage(), e);
                        }
                    });
            } else {
                if (hasSrcDirs) {
                    // source mode, so we need to write out each source to META-INF
                    Map<PathEntry, List<String>> allModules = new LinkedHashMap<>();
                    Set<String> allExportedTypes = transformedList.stream()
                        .flatMap(tr -> tr.exportedTypes().stream())
                        .collect(Collectors.toSet());
                    for (PythonAstParser.TransformResult transformResult : transformedList) {
                        for (String srcDir : srcDirs) {
                            Source source = transformResult.originalSource();
                            String path = source.getPath();
                            int i = path.indexOf(srcDir);
                            if (i == -1) {
                                continue;
                            }
                            if (i > 0) {
                                path = path.substring(i + srcDir.length() + 1);
                            }

                            if (!srcDir.isEmpty() && path.startsWith(srcDir)) {
                                path = path.substring(srcDir.length() + 1);
                            }
                            String targetSource = APPLICATION_SRC_PATH + path;
                            if (!transformResult.allClassNames().isEmpty()) {
                                // has classes
                                int parentIndex = path.lastIndexOf('/');
                                if (parentIndex > -1) {
                                    String parentPath = path.substring(0, parentIndex + 1);
                                    allModules.computeIfAbsent(new PathEntry(parentPath, path.substring(parentIndex)), k -> new ArrayList<>())
                                        .addAll(transformResult.allClassNames());
                                } else {
                                    allModules.computeIfAbsent(new PathEntry("", path), k -> new ArrayList<>())
                                        .addAll(transformResult.allClassNames());
                                }
                            }
                            filesList.append("/META-INF/").append(targetSource).append("\n");
                            javaVisitorContext.visitMetaInfFile(targetSource, originatingElement)
                                .ifPresent(generatedFile -> {
                                    try (var writer = generatedFile.openWriter()) {
                                        writer.write(source.getCharacters().toString());
                                    } catch (IOException e) {
                                        throw new ProcessingException(originatingElement, "Failed to write Python code to [" + targetSource + "]: " + e.getMessage(), e);
                                    }
                                });
                        }
                    }

                    TreeSet<String> byParent = allModules.keySet().stream().map(pe -> pe.parent)
                        .collect(Collectors.toCollection(TreeSet::new));
                    for (String parent : byParent) {
                        if (StringUtils.isEmpty(parent)) {
                            // root, generate __main__.py instead
                            String mainFilePath = APPLICATION_SRC_PATH + parent + "__main__.py";
                            StringBuilder mainContent = new StringBuilder();
                            List<Map.Entry<PathEntry, List<String>>> entries = allModules.entrySet().stream()
                                .filter(entry -> entry.getKey().parent.equals(parent))
                                .toList();
                            for (Map.Entry<PathEntry, List<String>> entry : entries) {
                                List<String> types = entry.getValue();
                                String filename = entry.getKey().filename;
                                if (!types.isEmpty()) {
                                    for (String type : types) {
                                        mainContent.append("from ").append(NameUtils.filename(filename)).append(" import ").append(type).append('\n');
                                    }
                                }
                            }
                            filesList.append("/META-INF/").append(mainFilePath).append("\n");
                            javaVisitorContext.visitMetaInfFile(mainFilePath, originatingElement)
                                .ifPresent(generatedFile -> {
                                    try (var writer = generatedFile.openWriter()) {
                                        writer.write(mainContent.toString());
                                    } catch (IOException e) {
                                        throw new ProcessingException(originatingElement, "Failed to write Python code to [" + mainFilePath + "]: " + e.getMessage(), e);
                                    }
                                });
                        } else {

                            String initFilePath = APPLICATION_SRC_PATH + parent + "__init__.py";
                            StringBuilder initContent = new StringBuilder();
                            List<Map.Entry<PathEntry, List<String>>> entries = allModules.entrySet().stream()
                                .filter(entry -> entry.getKey().parent.equals(parent))
                                .toList();
                            List<String> exportedTypes = new ArrayList<>();
                            for (Map.Entry<PathEntry, List<String>> entry : entries) {
                                List<String> types = entry.getValue();
                                String filename = entry.getKey().filename;
                                if (!types.isEmpty()) {
                                    for (String type : types) {
                                        initContent.append("from .").append(NameUtils.filename(filename)).append(" import ").append(type).append('\n');
                                        // Check if this type has decorators (is in allExportedTypes)
                                        if (allExportedTypes.contains(type)) {
                                            exportedTypes.add(type);
                                        }
                                    }
                                }
                            }
                            if (!exportedTypes.isEmpty()) {
                                initContent.append("\n__all__ = ").append(toListOfString(exportedTypes)).append("\n");
                            }
                            filesList.append("/META-INF/").append(initFilePath).append("\n");
                            javaVisitorContext.visitMetaInfFile(initFilePath, originatingElement)
                                .ifPresent(generatedFile -> {
                                    try (var writer = generatedFile.openWriter()) {
                                        writer.write(initContent.toString());
                                    } catch (IOException e) {
                                        throw new ProcessingException(originatingElement, "Failed to write Python code to [" + initFilePath + "]: " + e.getMessage(), e);
                                    }
                                });
                        }
                    }
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
                javaClassImports.forEach((pkg, imports) -> {
                    allImports.computeIfAbsent(pkg, (k) -> new ArrayList<>())
                        .addAll(imports);
                });

            }
            writeAllToVFS(filesList, allDecorators, allImports, originatingElement);

            // Run type element visitor processing
            PythonTypeElementVisitorProcessor typeElementVisitorProcessor =
                new PythonTypeElementVisitorProcessor(this.classLoader != null ? this.classLoader : PythonAnnotationProcessor.class.getClassLoader());
            typeElementVisitorProcessor.init(processingEnvironment);
            typeElementVisitorProcessor.process(processingEnvironment);

            // Process bean definitions for Python classes
            var beanDefinitionProcessor = new PythonBeanDefinitionProcessor();
            beanDefinitionProcessor.processBeanDefinitions(processingEnvironment);

            // Invoke callback for each class element if callback is set
            if (classElementCallback != null) {
                processingEnvironment.classes().values().forEach(classElementCallback);
            }

            // The visitor context is now ready for use by Micronaut's type visitors
            note("Successfully processed Python environment with " +
                environment.classes().size() + " classes and " +
                environment.decorators().size() + " decorators");

        } catch (ProcessingException e) {
            String ls = System.lineSeparator();
            io.micronaut.inject.ast.Element el = e.getElement();
            if (el != null) {
                String description = el.getDescription(true);
                error(e.getMessage() + ls + ls + " -> " + description + ls + ls);
            } else {
                error(e.getMessage());
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String stacktrace = sw.toString();
            error("Failed Trace: %s", stacktrace);
            error("Fatal error processing Python code: %s", e.getMessage());
        }
    }

    private List<PythonAstParser.TransformResult> applyASTTransforms(PythonApplicationValues values, ClassElement originatingElement) {
        // Process inline code if provided
        String code = values.code();
        if (!code.isEmpty()) {
            // Transform the source code first
            try {
                return parser.transform(javaVisitorContext, Source.create("python", code));
            } catch (Exception e) {
                throw new ProcessingException(originatingElement, "Error transforming python code: " + e.getMessage(), e);
            }

        } else {
            // Process directory scanning if provided
            String[] srcDirs = values.src();
            List<Source> sources = new ArrayList<>();
            if (srcDirs != null) {
                for (var srcDir : srcDirs) {
                    Path directory = Paths.get(srcDir);
                    if (Files.isDirectory(directory)) {
                        try {
                            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                                @Override
                                public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir,
                                                                                  @NotNull BasicFileAttributes attrs)
                                    throws IOException {
                                    if (Files.isHidden(dir) || dir.toFile().getName().startsWith(".")) {
                                        return FileVisitResult.SKIP_SUBTREE;
                                    }
                                    return super.preVisitDirectory(dir, attrs);
                                }

                                @Override
                                public @NotNull FileVisitResult visitFile(@NotNull Path file,
                                                                          @NotNull BasicFileAttributes attrs)
                                    throws IOException {
                                    if (file.toString().endsWith(".py")) {
                                        var relative = directory.relativize(file).toString();
                                        if (relative.equals("setup.py")) {
                                            // temporary workaround
                                            return FileVisitResult.CONTINUE;
                                        }
                                        sources.add(Source.newBuilder("python", file.toFile()).build());
                                    }
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        } catch (IOException e) {
                            throw new ProcessingException(originatingElement, "Error processing python code in directory [" + directory + "]: " + e.getMessage());
                        }
                    } else {
                        throw new ProcessingException(originatingElement, "Source directory does not exist: " + srcDir);
                    }
                }
            } else {
                throw new ProcessingException(originatingElement, "Source directories are not set.");
            }
            return parser.transform(javaVisitorContext, sources.toArray(new Source[0]));
        }
    }

    private Optional<PythonApplicationValues> readPythonApplicationValues(Element element) {
        AnnotationMirror mirror = getAnnotationMirror(element, PYTHON_APPLICATION_ANNOTATION);
        if (mirror == null) {
            return Optional.empty();
        }

        Map<String, AnnotationValue> values = new LinkedHashMap<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
            values.put(e.getKey().getSimpleName().toString(), e.getValue());
        }

        String code = getString(values.get("code"));
        String[] src = getStringArray(values.get("src"));
        return Optional.of(new PythonApplicationValues(code, src));
    }

    private static AnnotationMirror getAnnotationMirror(Element element, String annotationFqcn) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            Element annotationElement = am.getAnnotationType().asElement();
            if (annotationElement instanceof TypeElement te && annotationFqcn.equals(te.getQualifiedName().toString())) {
                return am;
            }
        }
        return null;
    }

    private static String getString(AnnotationValue value) {
        if (value == null) {
            return "";
        }
        Object v = value.getValue();
        return v instanceof String s ? s : String.valueOf(v);
    }

    private static String[] getStringArray(AnnotationValue value) {
        if (value == null) {
            return null;
        }
        Object v = value.getValue();
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o instanceof AnnotationValue av) {
                    Object vv = av.getValue();
                    if (vv instanceof String s) {
                        out.add(s);
                    }
                }
            }
            return out.toArray(String[]::new);
        }
        if (v instanceof String s) {
            return new String[]{s};
        }
        return null;
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
                String transformedDecoratorName = toPythonImportName(decoratorName);

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
                String pythonPackageName = toPythonImportName(packageName);
                Map<String, String> classMappings = javaClassesByPackage.computeIfAbsent(pythonPackageName, (k) ->
                    new LinkedHashMap<>()
                );
                for (Map<String, String> importInfo : imports) {
                    String variable = importInfo.get("variable");
                    String className = importInfo.get("class_name");
                    classMappings.put(variable, className);
                }
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
                    String typeName = mapping.getKey();
                    initContent.append(typeName).append(" = java.type('").append(mapping.getValue()).append("')\n");
                    allNames.add(typeName);
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
                initContent.append("\n__all__ = ").append(toListOfString(allNames)).append("\n");
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

    private static @NotNull String toListOfString(List<String> allNames) {
        return "[" + String.join(",", allNames.stream().map(n -> "\"" + n + "\"").toList()) + "]";
    }

    private static String toPythonImportName(String qualifiedName) {
        String name = qualifiedName.startsWith("io.") ? qualifiedName.substring(3) : qualifiedName;
        return Arrays.stream(name.split("\\."))
            .map(part -> PYTHON_KEYWORDS.contains(part) ? part + "_" : part)
            .collect(Collectors.joining("."));
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

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    record PathEntry(String parent, String filename) {
    }

    private record PythonApplicationValues(String code, String[] src) {
    }
}
