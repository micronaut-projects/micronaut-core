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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.annotation.processing.AbstractInjectAnnotationProcessor;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.python.processing.beans.PythonBeanDefinitionProcessor;
import io.micronaut.python.processing.util.PythonKeywords;
import io.micronaut.python.processing.visitor.PythonTypeElementVisitorProcessor;
import io.micronaut.python.compiler.PythonBytecodeCompiler;
import org.graalvm.polyglot.Source;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Annotation processor for {@code io.micronaut.context.python.annotation.PythonApplication} that enables Python AST processing
 * during Java compilation.
 *
 * @author Micronaut
 * @since 5.2.0
 */
@SupportedAnnotationTypes(PythonAnnotationProcessor.PYTHON_APPLICATION_ANNOTATION)
@Experimental
public class PythonAnnotationProcessor extends AbstractInjectAnnotationProcessor implements AutoCloseable {
    public static final String APPLICATION_PATH = "GRAALPY-VFS/micronaut-application/";
    public static final String APPLICATION_SRC_PATH = "GRAALPY-VFS/micronaut-application/src/";
    /**
     * The compiler option naming the directory that relative {@code @PythonApplication(src = ...)}
     * directories are resolved against. Without it they resolve against the working directory.
     */
    public static final String SOURCE_ROOT_OPTION = "micronaut.python.source.root";
    public static final String APPLICATION_LAUNCHER_PATH = APPLICATION_SRC_PATH + "__main__.py";
    /**
     * The prefix of the modules through which a compilation contributes members to a package (or, at
     * the root of the sources, to the launcher). The main and the test sources of a project, or an
     * application and a library, are compiled separately into virtual file system roots that share
     * one namespace at run time, where a file present in several roots is served from one of them.
     * A package initialiser therefore carries no members itself: every compilation writes them to
     * a module named after a hash of its content, and the initialiser, identical in every root,
     * merges the modules it finds.
     */
    public static final String PACKAGE_MEMBERS_MODULE_PREFIX = "__micronaut_members_";
    static final String PYTHON_APPLICATION_ANNOTATION = "io.micronaut.context.python.annotation.PythonApplication";
    private static final String PYTHON_LANGUAGE = "python";
    private static final String RELATIVE_IMPORT_PREFIX = "from .";
    private static final String IMPORT_SEPARATOR = " import ";
    /**
     * The Python facade of a Java type, defined in the package initializers that need it: it maps
     * keyword-safe member names ({@code with_}) to the Java member and lets a Python class list a
     * Java interface among its bases without GraalPy creating a host adapter.
     */
    private static final String JAVA_TYPE_FACADE = """
        import keyword

        class _MicronautJavaType:
            def __init__(self, target, interface=False):
                self._target = target
                self._interface = interface

            def __getattr__(self, name):
                if name.endswith('_') and keyword.iskeyword(name[:-1]):
                    name = name[:-1]
                return getattr(self._target, name)

            def __call__(self, *args, **kwargs):
                return self._target(*args, **kwargs)

            def __getitem__(self, item):
                if self._interface:
                    return self
                return self._target[item]

            def __mro_entries__(self, bases):
                if self._interface:
                    return ()
                return (self._target,)

        """;
    private static final String PACKAGE_INIT_SOURCE = """
        # Generated by the Python compiler. A compilation contributes the members of this package
        # through a module named __micronaut_members_<hash>; this initialiser merges the modules of
        # every compilation on the class path, so the source roots that share a package do not
        # shadow each other. The code of a members module runs here in a namespace of its own rather
        # than being imported: a package importing its subpackages eagerly nests the import machinery
        # once per package level, and GraalPy keeps dozens of Java frames per Python frame, so the
        # frames this initialiser adds to every level decide whether a deep package tree bootstraps
        # within the thread stack.
        import importlib as __micronaut_importlib
        import os as __micronaut_os
        from importlib.machinery import SourceFileLoader as __micronaut_SourceFileLoader

        # the members modules next to this initialiser, by name
        __micronaut_contributions = dict(sorted(
            (file[:-3], directory + '/' + file)
            for directory in __path__
            for file in __micronaut_os.listdir(directory)
            if file.startswith('__micronaut_members_') and file.endswith('.py')
        ))
        # the namespace of each contribution merged so far, partial while its code runs
        __micronaut_namespaces = {}
        __all__ = []


        def __micronaut_merge_members(contribution):
            namespace = {'__name__': __name__ + '.' + contribution, '__package__': __name__, '__file__': __micronaut_contributions[contribution]}
            __micronaut_namespaces[contribution] = namespace
            exec(__micronaut_SourceFileLoader(namespace['__name__'], namespace['__file__']).get_code(namespace['__name__']), namespace)
            # a module still being imported (a member of it imports this package) has no members yet;
            # they are merged when its import completes
            for name in namespace.get('__all__', ()):
                value = namespace[name]
                # the first module defining a name wins, unless a later one wraps the Java type in a
                # keyword-safe facade
                if name in __all__ and type(value).__name__ != '_MicronautJavaType':
                    continue
                globals()[name] = value
                if name not in __all__:
                    __all__.append(name)


        def __getattr__(name):
            # a module of this package importing a member from the package while the package initialises:
            # a contribution whose code runs may have bound the member already, or names the module defining
            # it (its member map is bound before its imports run, so a sibling is served whichever module the
            # contribution imports first); otherwise the remaining contributions are merged until one defines
            # the member. A subpackage (from . import annotation) is left to the import system, which imports
            # it itself.
            if not name.startswith('__') and not any(__micronaut_os.path.isdir(directory + '/' + name) for directory in __path__):
                for contribution in __micronaut_contributions:
                    namespace = __micronaut_namespaces.get(contribution)
                    if namespace is None:
                        continue
                    if name in namespace:
                        return namespace[name]
                    module_name = namespace.get('__micronaut_member_modules__', {}).get(name)
                    if module_name is not None:
                        return getattr(__micronaut_importlib.import_module(__name__ + '.' + module_name), name)
                for contribution in __micronaut_contributions:
                    if contribution not in __micronaut_namespaces:
                        __micronaut_merge_members(contribution)
                    if name in __all__:
                        return globals()[name]
            raise AttributeError(f"module '{__name__}' has no attribute '{name}'")


        try:
            for __micronaut_contribution in __micronaut_contributions:
                if __micronaut_contribution not in __micronaut_namespaces:
                    __micronaut_merge_members(__micronaut_contribution)
        finally:
            del __getattr__
        del __micronaut_merge_members, __micronaut_namespaces, __micronaut_contributions
        del __micronaut_importlib, __micronaut_os, __micronaut_SourceFileLoader
        globals().pop('__micronaut_contribution', None)
        """;
    private static final String LAUNCHER_SOURCE = """
        # Generated by the Python compiler. A compilation contributes the classes of its top-level
        # modules through a module named __micronaut_members_<hash>; this launcher runs the code of
        # the modules of every compilation on the class path from the source directory of the virtual
        # file system, mounted where the runtime evaluates this launcher. The code runs here rather
        # than being imported, which keeps the import machinery off the stack of the application
        # modules it imports (see the package initialisers).
        import os as __micronaut_os
        from importlib.machinery import SourceFileLoader as __micronaut_SourceFileLoader

        for __micronaut_file in sorted(__micronaut_os.listdir('/graalpy_vfs/src')):
            if __micronaut_file.startswith('__micronaut_members_') and __micronaut_file.endswith('.py'):
                __micronaut_namespace = {'__name__': __micronaut_file[:-3], '__file__': '/graalpy_vfs/src/' + __micronaut_file}
                exec(__micronaut_SourceFileLoader(__micronaut_namespace['__name__'], __micronaut_namespace['__file__']).get_code(__micronaut_namespace['__name__']), __micronaut_namespace)
                globals().update({member: __micronaut_namespace[member] for member in __micronaut_namespace['__all__']})
        globals().pop('__micronaut_namespace', None)
        del __micronaut_file, __micronaut_os, __micronaut_SourceFileLoader
        """;
    private PythonAstParser parser;
    private Consumer<ClassElement> classElementCallback;
    private List<PythonSourceVisitor> pythonSourceVisitors = List.of();
    private ClassLoader classLoader;
    private boolean compilePythonBytecode;
    private PythonBytecodeCompiler bytecodeCompiler;
    private Set<String> incrementalSources;
    private boolean processAggregatingVisitors = true;
    private Path outputDirectory;
    private PythonProcessingSession processingSession;
    private final Set<String> writtenVfsPaths = new LinkedHashSet<>();

    /**
     * Set the callback to be invoked for each class element created during processing.
     * This is primarily used for testing purposes.
     *
     * @param callback The callback function
     */
    public void setClassElementCallback(Consumer<ClassElement> callback) {
        this.classElementCallback = callback;
    }

    /**
     * Sets visitors for Python source metadata in this compilation.
     *
     * @param pythonSourceVisitors the visitors
     */
    public void setPythonSourceVisitors(List<PythonSourceVisitor> pythonSourceVisitors) {
        this.pythonSourceVisitors = List.copyOf(pythonSourceVisitors);
    }

    /**
     * Set whether generated Python VFS resources should include optional GraalPy bytecode caches.
     * Application sources that require runtime compatibility transformations may include a cache
     * regardless of this setting so that the original source remains available for debugging.
     *
     * @param compilePythonBytecode Whether optional bytecode caches should be emitted
     * @since 5.2.0
     */
    public void setCompilePythonBytecode(boolean compilePythonBytecode) {
        this.compilePythonBytecode = compilePythonBytecode;
    }

    /**
     * Restricts isolating visitors and bean generation to affected Python sources.
     *
     * @param incrementalSources The affected absolute source paths, or {@code null} for all sources
     */
    @Internal
    public void setIncrementalSources(Set<String> incrementalSources) {
        this.incrementalSources = incrementalSources == null ? null : Set.copyOf(incrementalSources);
    }

    /**
     * Sets whether aggregating Python type visitors should run.
     *
     * @param processAggregatingVisitors Whether aggregating visitors should run
     */
    @Internal
    public void setProcessAggregatingVisitors(boolean processAggregatingVisitors) {
        this.processAggregatingVisitors = processAggregatingVisitors;
    }

    /**
     * Sets the class output directory used to reuse unchanged generated Python resources.
     *
     * @param outputDirectory The class output directory, or {@code null} for in-memory compilation
     */
    @Internal
    public void setOutputDirectory(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Sets a session that owns the GraalPy context across compiler invocations.
     *
     * @param processingSession The reusable processing session
     */
    @Internal
    public void setProcessingSession(PythonProcessingSession processingSession) {
        this.processingSession = processingSession;
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
    }

    @Override
    public void close() throws Exception {
        if (parser != null && processingSession == null) {
            parser.close();
        }
        if (bytecodeCompiler != null) {
            bytecodeCompiler.close();
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            if (parser != null && processingSession == null) {
                parser.close();
            }
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
                    initializeParser();
                    processAnnotation(typeElement, values);
                }
            }
        }
    }

    private void initializeParser() {
        if (parser == null) {
            parser = processingSession == null
                ? new PythonAstParser(classLoader, incrementalSources != null)
                : processingSession.parser(classLoader, incrementalSources != null);
        }
    }

    private void processAnnotation(TypeElement element, PythonApplicationValues values) {
        writtenVfsPaths.clear();
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
            processPythonSourceVisitors(transformedList, values);
            transformedList.stream()
                .flatMap(transformResult -> transformResult.validationErrors().stream())
                .findFirst()
                .ifPresent(message -> {
                    throw new ProcessingException(originatingElement, message);
                });

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
                    throw new ProcessingException(originatingElement, "Error parsing transformed python code: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
                }
            }

            String mainPy;
            StringBuilder filesList = new StringBuilder();
            Set<String> initialisedPackages = new HashSet<>();
            boolean processSharedOutputs = incrementalSources == null || processAggregatingVisitors;
            if (StringUtils.isNotEmpty(values.code())) {
                PythonAstParser.TransformResult transformResult = transformedList.get(0);
                mainPy = transformResult.originalSource().getCharacters().toString();
                writeApplicationPythonToVfs(
                    filesList,
                    APPLICATION_LAUNCHER_PATH,
                    mainPy,
                    transformResult,
                    originatingElement
                );
            } else {
                if (hasSrcDirs) {
                    // source mode, so we need to write out each source to META-INF
                    Map<PathEntry, List<String>> allModules = new LinkedHashMap<>();
                    for (PythonAstParser.TransformResult transformResult : transformedList) {
                        Source source = transformResult.originalSource();
                        // a source outside every source directory was already rejected by the parser
                        for (String configuredSrcDir : srcDirs) {
                            String srcDir = normalizeResourcePath(configuredSrcDir);
                            String path = normalizeResourcePath(source.getPath());
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
                            List<String> classNames = importedClassNames(transformResult, environment);
                            if (processSharedOutputs && !classNames.isEmpty()) {
                                // has classes
                                int parentIndex = path.lastIndexOf('/');
                                if (parentIndex > -1) {
                                    String parentPath = path.substring(0, parentIndex + 1);
                                    allModules.computeIfAbsent(new PathEntry(parentPath, path.substring(parentIndex)), k -> new ArrayList<>())
                                        .addAll(classNames);
                                } else {
                                    allModules.computeIfAbsent(new PathEntry("", path), k -> new ArrayList<>())
                                        .addAll(classNames);
                                }
                            }
                            if (isAffectedSource(source)) {
                                writeApplicationPythonToVfs(
                                    filesList,
                                    targetSource,
                                    source.getCharacters().toString(),
                                    transformResult,
                                    originatingElement
                                );
                            }
                        }
                    }

                    if (processSharedOutputs) {
                        TreeSet<String> byParent = allModules.keySet().stream().map(pe -> pe.parent)
                            .collect(Collectors.toCollection(TreeSet::new));
                        for (String parent : byParent) {
                            // the root contributes to the launcher (__main__.py), a package to its initialiser
                            boolean root = StringUtils.isEmpty(parent);
                            StringBuilder membersContent = new StringBuilder();
                            List<Map.Entry<PathEntry, List<String>>> entries = allModules.entrySet().stream()
                                .filter(entry -> entry.getKey().parent.equals(parent))
                                .toList();
                            List<String> members = new ArrayList<>();
                            // the module defining each member, bound before the imports so the package initialiser
                            // can serve a sibling to a module importing it from the package while this module imports
                            StringBuilder memberModules = new StringBuilder("__micronaut_member_modules__ = {");
                            for (Map.Entry<PathEntry, List<String>> entry : entries) {
                                List<String> types = entry.getValue();
                                String moduleName = NameUtils.filename(entry.getKey().filename);
                                for (String type : types) {
                                    membersContent.append(root ? "from " : RELATIVE_IMPORT_PREFIX).append(moduleName).append(IMPORT_SEPARATOR).append(type).append('\n');
                                    if (!members.isEmpty()) {
                                        memberModules.append(", ");
                                    }
                                    memberModules.append('"').append(type).append("\": \"").append(moduleName).append('"');
                                    members.add(type);
                                }
                            }
                            membersContent.insert(0, memberModules.append("}\n").toString());
                            writePackageMembers(filesList, APPLICATION_SRC_PATH + parent, root, membersContent, members, initialisedPackages, originatingElement);
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
            if (processSharedOutputs) {
                writeAllToVFS(filesList, allDecorators, allImports, initialisedPackages, originatingElement);
            }

            // Run type element visitor processing
            ClassLoader effectiveClassLoader = this.classLoader != null
                ? this.classLoader : PythonAnnotationProcessor.class.getClassLoader();
            Predicate<ClassElement> affectedElements = affectedElements(transformedList, srcDirs);
            if (processAggregatingVisitors) {
                PythonTypeElementVisitorProcessor aggregatingVisitors =
                    new PythonTypeElementVisitorProcessor(effectiveClassLoader, io.micronaut.inject.visitor.TypeElementVisitor.VisitorKind.AGGREGATING);
                aggregatingVisitors.init(processingEnvironment);
                aggregatingVisitors.process(
                    processingEnvironment,
                    ignored -> true,
                    incrementalSources == null,
                    false
                );
            }
            PythonTypeElementVisitorProcessor isolatingVisitors =
                new PythonTypeElementVisitorProcessor(effectiveClassLoader, io.micronaut.inject.visitor.TypeElementVisitor.VisitorKind.ISOLATING);
            isolatingVisitors.init(processingEnvironment);
            isolatingVisitors.process(
                processingEnvironment,
                affectedElements,
                incrementalSources != null,
                true
            );

            // Process bean definitions for Python classes
            var beanDefinitionProcessor = new PythonBeanDefinitionProcessor();
            beanDefinitionProcessor.processBeanDefinitions(processingEnvironment, affectedElements);

            // Invoke callback for each class element if callback is set
            if (classElementCallback != null) {
                processingEnvironment.classes().values().forEach(classElementCallback);
            }

            // The visitor context is now ready for use by Micronaut's type visitors
            note("Successfully processed Python environment with " +
                environment.classes().size() + " classes and " +
                environment.decorators().size() + " decorators");

        } catch (io.micronaut.python.compiler.PyronautCompilerException e) {
            throw e;
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

    private boolean isAffectedSource(Source source) {
        if (incrementalSources == null) {
            return true;
        }
        String path = source.getPath();
        if (path == null) {
            return false;
        }
        Path sourcePath = Path.of(path).toAbsolutePath().normalize();
        if (incrementalSources.contains(sourcePath.toString())) {
            return true;
        }
        for (String incrementalSource : incrementalSources) {
            try {
                if (Files.isSameFile(sourcePath, Path.of(incrementalSource))) {
                    return true;
                }
            } catch (IOException ignored) {
                // A missing source cannot be an affected source being processed.
            }
        }
        return false;
    }

    private Predicate<ClassElement> affectedElements(List<PythonAstParser.TransformResult> transformedList,
                                                     String[] srcDirs) {
        if (incrementalSources == null) {
            return ignored -> true;
        }
        Set<String> names = new LinkedHashSet<>();
        for (PythonAstParser.TransformResult transformed : transformedList) {
            Source source = transformed.originalSource();
            if (!isAffectedSource(source)) {
                continue;
            }
            String packageName = PYTHON_LANGUAGE;
            for (String srcDir : srcDirs) {
                if (source.getPath() != null && source.getPath().startsWith(srcDir)) {
                    packageName = PythonAstParser.getPackageNameOfSource(srcDir, source);
                    break;
                }
            }
            for (String className : transformed.allClassNames()) {
                names.add(packageName + '.' + className);
            }
            String sourceName = source.getName();
            if (sourceName.endsWith(".py")) {
                sourceName = sourceName.substring(0, sourceName.length() - ".py".length());
            }
            if (!sourceName.isEmpty()) {
                names.add(packageName + '.' + Character.toUpperCase(sourceName.charAt(0)) + sourceName.substring(1));
            }
        }
        return element -> names.contains(element.getName());
    }

    private void processPythonSourceVisitors(List<PythonAstParser.TransformResult> transformedList,
                                             PythonApplicationValues values) {
        if (pythonSourceVisitors.isEmpty()) {
            return;
        }
        for (PythonAstParser.TransformResult transformed : transformedList) {
            Source source = transformed.originalSource();
            String packageName = "";
            for (String srcDir : values.src()) {
                if (source.getPath() == null || source.getPath().startsWith(srcDir)) {
                    packageName = PythonAstParser.getPackageNameOfSource(srcDir, source);
                    break;
                }
            }
            PythonSource pythonSource = new PythonSource(source.getName(), packageName, parser.extractCalls(source));
            for (PythonSourceVisitor visitor : pythonSourceVisitors) {
                visitor.visit(pythonSource, javaVisitorContext);
            }
        }
        for (PythonSourceVisitor visitor : pythonSourceVisitors) {
            visitor.finish(javaVisitorContext);
        }
    }

    private List<PythonAstParser.TransformResult> applyASTTransforms(PythonApplicationValues values, ClassElement originatingElement) {
        // Process inline code if provided
        String code = values.code();
        if (!code.isEmpty()) {
            // Transform the source code first
            try {
                return parser.transform(javaVisitorContext, Source.create(PYTHON_LANGUAGE, code));
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
                                        var relative = directory.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
                                        if (relative.equals("setup.py")) {
                                            // temporary workaround
                                            return FileVisitResult.CONTINUE;
                                        }
                                        if ("__init__.py".equals(file.getFileName().toString())) {
                                            throw new ProcessingException(
                                                originatingElement,
                                                "Custom __init__.py files are not supported in Pyronaut applications. " +
                                                    "Micronaut generates package __init__.py files for the GraalPy VFS; remove [" + relative + "] from the project source."
                                            );
                                        }
                                        sources.add(Source.newBuilder(PYTHON_LANGUAGE, file.toFile()).build());
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
            return parser.transform(
                javaVisitorContext,
                Arrays.asList(srcDirs),
                selectTransformSources(sources).toArray(new Source[0])
            );
        }
    }

    final List<Source> selectTransformSources(List<Source> sources) {
        if (incrementalSources == null || processAggregatingVisitors) {
            return sources;
        }
        return sources.stream().filter(this::isAffectedSource).toList();
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
        String[] src = resolveSourceDirectories(getStringArray(values.get("src")));
        return Optional.of(new PythonApplicationValues(code, src));
    }

    /**
     * Resolves the configured source directories against the {@link #SOURCE_ROOT_OPTION} directory, or the
     * working directory when the option is absent. A build tool can then compile a relative directory into
     * the {@code @PythonApplication} annotation, which keeps the compiled output free of absolute paths,
     * while the processor keeps matching the absolute paths of the parsed sources against absolute roots.
     *
     * @param src The configured source directories
     * @return The absolute source directories
     */
    private String[] resolveSourceDirectories(String[] src) {
        if (src == null) {
            return null;
        }
        String root = processingEnv.getOptions().get(SOURCE_ROOT_OPTION);
        Path base = root == null || root.isBlank() ? Paths.get("") : Paths.get(root);
        String[] resolved = new String[src.length];
        for (int i = 0; i < src.length; i++) {
            String directory = src[i];
            resolved[i] = directory == null || directory.isBlank()
                ? directory
                : base.resolve(directory).toAbsolutePath().normalize().toString();
        }
        return resolved;
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

    private void writePythonToVfs(StringBuilder filesList,
                                  String filePath,
                                  String content,
                                  ClassElement originatingElement) {
        boolean unchanged = writePythonSourceToVfs(filesList, filePath, content, originatingElement);

        if (!compilePythonBytecode || unchanged) {
            return;
        }
        try {
            PythonBytecodeCompiler.Result result = bytecodeCompiler().compile(content, filePath);
            writePythonBytecodeToVfs(filesList, filePath, result, originatingElement);
        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ProcessingException(originatingElement, "Failed to compile Python bytecode for [" + filePath + "]: " + e.getMessage(), e);
        }
    }

    private void writeApplicationPythonToVfs(StringBuilder filesList,
                                             String filePath,
                                             String content,
                                             PythonAstParser.TransformResult transformResult,
                                             ClassElement originatingElement) {
        writePythonSourceToVfs(filesList, filePath, content, originatingElement);
        if (!compilePythonBytecode && !parser.requiresRuntimeBytecode(transformResult)) {
            return;
        }
        try {
            PythonBytecodeCompiler.Result result = parser.compileRuntimeBytecode(
                transformResult,
                runtimeFilename(filePath)
            );
            writePythonBytecodeToVfs(filesList, filePath, result, originatingElement);
        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ProcessingException(originatingElement, "Failed to compile mapped Python bytecode for [" + filePath + "]: " + e.getMessage(), e);
        }
    }

    private boolean writePythonSourceToVfs(StringBuilder filesList,
                                           String filePath,
                                           String content,
                                           ClassElement originatingElement) {
        boolean unchanged = false;
        if (!writtenVfsPaths.add(filePath)) {
            // Surfaced here instead of as the opaque "Output stream or writer has already been opened" IOException
            throw new ProcessingException(
                originatingElement,
                "Python source [" + (filePath.startsWith(APPLICATION_SRC_PATH) ? filePath.substring(APPLICATION_SRC_PATH.length()) : filePath)
                    + "] is written twice: an application "
                    + "module and a module generated for an imported Java package or annotation map to the same file; rename the application module."
            );
        }
        var generatedFile = javaVisitorContext.visitMetaInfFile(filePath, originatingElement).orElse(null);
        if (generatedFile != null) {
            try {
                if (outputDirectory != null) {
                    Path existingFile = outputDirectory.resolve("META-INF").resolve(filePath);
                    unchanged = Files.isRegularFile(existingFile) && content.equals(Files.readString(existingFile));
                }
                if (unchanged) {
                    generatedFile.getTextContent();
                } else {
                    try (var writer = generatedFile.openWriter()) {
                        writer.write(content);
                    }
                }
            } catch (IOException e) {
                throw new ProcessingException(originatingElement, "Failed to write Python code to [" + filePath + "]: " + e.getMessage(), e);
            }
        }
        filesList.append("/META-INF/").append(filePath).append('\n');
        return unchanged;
    }

    private PythonBytecodeCompiler bytecodeCompiler() {
        if (bytecodeCompiler == null) {
            bytecodeCompiler = parser.bytecodeCompiler();
        }
        return bytecodeCompiler;
    }

    private void writePythonBytecodeToVfs(StringBuilder filesList,
                                          String sourcePath,
                                          PythonBytecodeCompiler.Result result,
                                          ClassElement originatingElement) {
        String bytecodePath = cacheFilePath(sourcePath, result.cachePath());
        boolean unchanged = false;
        if (outputDirectory != null) {
            Path existingFile = outputDirectory.resolve("META-INF").resolve(bytecodePath);
            try {
                unchanged = Files.isRegularFile(existingFile) && Arrays.equals(result.bytes(), Files.readAllBytes(existingFile));
            } catch (IOException e) {
                throw new ProcessingException(originatingElement, "Failed to read Python bytecode from [" + bytecodePath + "]: " + e.getMessage(), e);
            }
        }
        boolean bytecodeUnchanged = unchanged;
        javaVisitorContext.visitMetaInfFile(bytecodePath, originatingElement)
            .ifPresent(bytecodeFile -> {
                try {
                    if (bytecodeUnchanged) {
                        // Opening the existing output records it for incremental compilation.
                        bytecodeFile.openInputStream().close();
                    } else {
                        try (var output = bytecodeFile.openOutputStream()) {
                            output.write(result.bytes());
                        }
                    }
                } catch (IOException e) {
                    throw new ProcessingException(originatingElement, "Failed to write Python bytecode to [" + bytecodePath + "]: " + e.getMessage(), e);
                }
            });
        filesList.append("/META-INF/").append(bytecodePath).append('\n');
    }

    private static String runtimeFilename(String filePath) {
        String relativePath = filePath.substring(APPLICATION_PATH.length());
        return "/graalpy_vfs/" + relativePath;
    }

    static String normalizeResourcePath(String path) {
        return path.replace('\\', '/');
    }

    private static String cacheFilePath(String sourcePath, String cachePath) {
        int sourceSeparator = sourcePath.lastIndexOf('/');
        int cacheSeparator = cachePath.lastIndexOf('/');
        String parent = sourceSeparator == -1 ? "" : sourcePath.substring(0, sourceSeparator + 1);
        String cacheFile = cacheSeparator == -1 ? cachePath : cachePath.substring(cacheSeparator + 1);
        return parent + "__pycache__/" + cacheFile;
    }

    /**
     * Writes the members a compilation contributes to a package, or to the launcher at the root of
     * the sources, and the initialiser (or launcher) merging the contributions of every compilation.
     *
     * @param filesList The virtual file system file list
     * @param directory The directory of the package (or the sources root) in the virtual file system
     * @param root Whether the directory is the root of the sources, whose members the launcher imports
     * @param membersContent The Python source binding the members
     * @param members The names of the members
     * @param initialisedPackages The initialisers and members modules already written by this compilation
     * @param originatingElement The originating element
     */
    private void writePackageMembers(StringBuilder filesList,
                                     String directory,
                                     boolean root,
                                     StringBuilder membersContent,
                                     List<String> members,
                                     Set<String> initialisedPackages,
                                     ClassElement originatingElement) {
        String content = membersContent.append("\n__all__ = ").append(toListOfString(members)).append('\n').toString();
        String membersPath = directory + PACKAGE_MEMBERS_MODULE_PREFIX + contentHash(content) + ".py";
        if (initialisedPackages.add(membersPath)) {
            writePythonToVfs(filesList, membersPath, content, originatingElement);
        }
        String initialiserPath = directory + (root ? "__main__.py" : "__init__.py");
        if (initialisedPackages.add(initialiserPath)) {
            writePythonToVfs(filesList, initialiserPath, root ? LAUNCHER_SOURCE : PACKAGE_INIT_SOURCE, originatingElement);
        }
    }

    private static String contentHash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Writes the Python modules standing for the imported Java types.
     *
     * <p>A Java package is a Python package whose {@code __init__.py} exports every annotation
     * (each generated as its own module, {@code jakarta/inject/Singleton.py}) and every Java class
     * ({@code Thread = java.type('java.lang.Thread')}) imported from it. A Java type whose nested types
     * are imported through it ({@code from a.b.Outer import Inner}) is a package as well: its
     * {@code __init__.py} binds the type itself under its own simple name, next to the nested types,
     * and the enclosing package imports the type from it ({@code from .Outer import Outer}), so
     * {@code from a.b import Outer}, {@code Outer.Inner} and {@code from a.b.Outer import Inner}
     * all resolve to the same objects.</p>
     */
    private void writeAllToVFS(
        StringBuilder filesList,
        Map<String, String> decorators,
        Map<String, List<Map<String, String>>> javaClassImports,
        Set<String> initialisedPackages,
        ClassElement originatingElement) {
        // Python package -> decorator simple name -> decorator source
        Map<String, Map<String, String>> decoratorsByPackage = new LinkedHashMap<>();
        Map<String, Map<String, JavaClassImport>> javaClassesByPackage = new LinkedHashMap<>();
        // Python packages that are the modules of Java types, mapped to the type's binary name
        Map<String, String> typeModules = new LinkedHashMap<>();

        // Process decorators
        if (decorators != null && !decorators.isEmpty()) {
            for (Map.Entry<String, String> entry : decorators.entrySet()) {
                String annotationName = entry.getKey();
                registerEnclosingTypeModules(annotationName, typeModules);
                String moduleName = toPythonModuleName(annotationName);
                int lastDotIndex = moduleName.lastIndexOf('.');
                String packageName = lastDotIndex > 0 ? moduleName.substring(0, lastDotIndex) : "";
                String simpleName = moduleName.substring(lastDotIndex + 1);
                decoratorsByPackage.computeIfAbsent(packageName, k -> new LinkedHashMap<>()).put(simpleName, entry.getValue());
            }
        }

        // Process Java class imports
        if (javaClassImports != null && !javaClassImports.isEmpty()) {
            javaClassImports.forEach((packageName, imports) -> {
                String pythonPackageName = toPythonImportName(packageName);
                Map<String, JavaClassImport> classMappings = javaClassesByPackage.computeIfAbsent(pythonPackageName, (k) ->
                    new LinkedHashMap<>()
                );
                for (Map<String, String> importInfo : imports) {
                    String variable = importInfo.get("variable");
                    String className = importInfo.get("class_name");
                    registerEnclosingTypeModules(className, typeModules);
                    if (toPythonModuleName(className).equals(pythonPackageName)) {
                        // from a.b.Outer import Outer: the module of the type exports the type itself
                        typeModules.putIfAbsent(pythonPackageName, className);
                    }
                    JavaClassImport javaClassImport = new JavaClassImport(
                        className,
                        Boolean.parseBoolean(importInfo.get("interface")),
                        Boolean.parseBoolean(importInfo.get("keyword_safe"))
                    );
                    classMappings.merge(variable, javaClassImport, JavaClassImport::merge);
                }
            });
        }

        // Collect all packages that need __init__.py files
        Set<String> allPackages = new LinkedHashSet<>();
        collectPackageNames(decoratorsByPackage.keySet(), allPackages);
        collectPackageNames(javaClassesByPackage.keySet(), allPackages);
        collectPackageNames(typeModules.keySet(), allPackages);

        // The bindings of a type module that its enclosing package hands down to it
        Map<String, String> typeModuleDecorators = new LinkedHashMap<>();
        Map<String, JavaClassImport> typeModuleClasses = new LinkedHashMap<>();

        // Annotations of the default package have no package initializer
        Map<String, String> rootDecorators = decoratorsByPackage.getOrDefault("", Map.of());
        for (Map.Entry<String, String> entry : rootDecorators.entrySet()) {
            if (typeModules.containsKey(entry.getKey())) {
                typeModuleDecorators.put(entry.getKey(), entry.getValue());
            } else {
                writePythonToVfs(filesList, APPLICATION_SRC_PATH + entry.getKey() + ".py", entry.getValue(), originatingElement);
            }
        }

        // Write the members and the __init__.py files of all packages, enclosing packages first
        for (String packageName : allPackages) {
            Map<String, String> decoratorsInPackage = decoratorsByPackage.getOrDefault(packageName, Map.of());
            Map<String, JavaClassImport> javaClassesInPackage = javaClassesByPackage.getOrDefault(packageName, Map.of());
            String packagePath = packageName.replace('.', '/');
            String ownTypeName = typeModules.get(packageName);
            String ownSimpleName = ownTypeName == null ? null : packageName.substring(packageName.lastIndexOf('.') + 1);
            String ownDecoratorCode = ownTypeName == null ? null : typeModuleDecorators.get(packageName);
            JavaClassImport ownClassImport = null;
            if (ownTypeName != null && ownDecoratorCode == null) {
                // The enclosing package may already have bound the type; look it up only otherwise
                ownClassImport = typeModuleClasses.get(packageName);
                if (ownClassImport == null) {
                    ownClassImport = classBinding(javaClassesInPackage, ownSimpleName, ownTypeName);
                }
            }

            List<JavaClassImport> classBindings = new ArrayList<>(javaClassesInPackage.values());
            if (ownClassImport != null) {
                classBindings.add(ownClassImport);
            }
            StringBuilder initContent = new StringBuilder();
            if (!classBindings.isEmpty()) {
                initContent.append("import java\n\n");
                if (classBindings.stream().anyMatch(JavaClassImport::requiresFacade)) {
                    initContent.append(JAVA_TYPE_FACADE);
                }
            }

            List<String> allNames = new ArrayList<>();

            // The type this module stands for
            if (ownDecoratorCode != null) {
                initContent.append(ownDecoratorCode).append('\n');
                allNames.add(ownSimpleName);
            } else if (ownClassImport != null && !javaClassesInPackage.containsKey(ownSimpleName)) {
                appendClassBinding(initContent, ownSimpleName, ownClassImport);
                allNames.add(ownSimpleName);
            }

            // Add decorator imports
            for (Map.Entry<String, String> decorator : decoratorsInPackage.entrySet()) {
                String decoratorName = decorator.getKey();
                String decoratorModule = childModule(packageName, decoratorName);
                if (typeModules.containsKey(decoratorModule)) {
                    typeModuleDecorators.put(decoratorModule, decorator.getValue());
                } else {
                    writePythonToVfs(filesList, APPLICATION_SRC_PATH + packagePath + "/" + decoratorName + ".py", decorator.getValue(), originatingElement);
                }
                initContent.append(RELATIVE_IMPORT_PREFIX).append(decoratorName).append(IMPORT_SEPARATOR).append(decoratorName).append("\n");
                allNames.add(decoratorName);
            }

            // Add Java class assignments
            for (Map.Entry<String, JavaClassImport> mapping : javaClassesInPackage.entrySet()) {
                String typeName = mapping.getKey();
                JavaClassImport javaClassImport = mapping.getValue();
                String typeModule = childModule(packageName, typeName);
                if (typeModules.containsKey(typeModule) && toPythonModuleName(javaClassImport.className()).equals(typeModule)) {
                    typeModuleClasses.put(typeModule, javaClassImport);
                    initContent.append(RELATIVE_IMPORT_PREFIX).append(typeName).append(IMPORT_SEPARATOR).append(typeName).append("\n");
                } else {
                    appendClassBinding(initContent, typeName, javaClassImport);
                }
                allNames.add(typeName);
            }

            // Add imports for subpackages
            for (String subPackage : allPackages) {
                if (subPackage.startsWith(packageName + ".") && !subPackage.equals(packageName)) {
                    String relativeSubPackage = subPackage.substring(packageName.length() + 1);
                    if (!relativeSubPackage.contains(".") && !allNames.contains(relativeSubPackage)) { // Direct child package
                        if (typeModules.containsKey(subPackage)) {
                            initContent.append(RELATIVE_IMPORT_PREFIX).append(relativeSubPackage).append(IMPORT_SEPARATOR).append(relativeSubPackage).append("\n");
                        } else {
                            initContent.append("from . import ").append(relativeSubPackage).append("\n");
                        }
                        allNames.add(relativeSubPackage);
                    }
                }
            }

            writePackageMembers(filesList, APPLICATION_SRC_PATH + packagePath + "/", false, initContent, allNames, initialisedPackages, originatingElement);
        }

        // Write fileslist.txt
        javaVisitorContext.visitMetaInfFile(APPLICATION_PATH + "fileslist.txt", originatingElement)
            .ifPresent(generatedFile -> {
                try (var writer = generatedFile.openWriter()) {
                    List<String> entries = filesList.toString().lines().distinct().sorted().toList();
                    if (!entries.isEmpty()) {
                        writer.write(String.join("\n", entries));
                        writer.write('\n');
                    }
                } catch (IOException e) {
                    throw new ProcessingException(originatingElement, "Failed to write fileslist.txt to VFS");
                }
            });
    }

    /**
     * The top-level classes of a source the package initializer imports: all of them except those
     * another module's definition of the same generated Java type replaces (see {@link PythonEnvironment#shadowedTypes()}).
     */
    private static List<String> importedClassNames(PythonAstParser.TransformResult transformResult, @Nullable PythonEnvironment environment) {
        List<String> classNames = transformResult.allClassNames();
        String path = transformResult.originalSource().getPath();
        List<String> shadowed = environment == null || path == null ? List.of() : environment.shadowedTypes().getOrDefault(path, List.of());
        if (shadowed.isEmpty()) {
            return classNames;
        }
        return classNames.stream().filter(name -> !shadowed.contains(name)).toList();
    }

    /**
     * The binding of a type module for its own type when the enclosing package does not hand one
     * down: the binding the type module itself imports ({@code from a.b.Outer import Outer}), or one
     * synthesized from the type.
     */
    private @Nullable JavaClassImport classBinding(Map<String, JavaClassImport> javaClassesInPackage, String simpleName, String className) {
        JavaClassImport imported = javaClassesInPackage.get(simpleName);
        if (imported != null && imported.className().equals(className)) {
            return imported;
        }
        boolean interfaceType = javaVisitorContext.getClassElement(className)
            .map(ClassElement::isInterface)
            .orElse(false);
        return new JavaClassImport(className, interfaceType, false);
    }

    private static void appendClassBinding(StringBuilder initContent, String typeName, JavaClassImport javaClassImport) {
        if (javaClassImport.requiresFacade()) {
            initContent.append(typeName)
                .append(" = _MicronautJavaType(java.type('")
                .append(javaClassImport.className())
                .append("'), ")
                .append(javaClassImport.interfaceType() ? "True" : "False")
                .append(")\n");
        } else {
            initContent.append(typeName)
                .append(" = java.type('")
                .append(javaClassImport.className())
                .append("')\n");
        }
    }

    private static String childModule(String packageName, String simpleName) {
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }

    /**
     * Records the modules of the types enclosing a nested type: every type on the way to
     * {@code a.b.Outer$Mid$Inner} ({@code a.b.Outer} and {@code a.b.Outer$Mid}) is a package
     * so the nested type can be imported from it.
     */
    private static void registerEnclosingTypeModules(String binaryName, Map<String, String> typeModules) {
        int separator = binaryName.indexOf('$');
        while (separator > 0) {
            String enclosingName = binaryName.substring(0, separator);
            typeModules.putIfAbsent(toPythonModuleName(enclosingName), enclosingName);
            separator = binaryName.indexOf('$', separator + 1);
        }
    }

    /**
     * The Python module of a Java type: its binary name with nested types as sub-modules
     * ({@code a.b.Outer$Inner} is {@code a.b.Outer.Inner}), keyword-safe and without the
     * {@code io.} prefix.
     */
    private static String toPythonModuleName(String binaryName) {
        return toPythonImportName(binaryName.replace('$', '.'));
    }

    private static @NotNull String toListOfString(List<String> allNames) {
        return "[" + String.join(",", allNames.stream().map(n -> "\"" + n + "\"").toList()) + "]";
    }

    private static String toPythonImportName(String qualifiedName) {
        String name = qualifiedName.startsWith("io.") ? qualifiedName.substring(3) : qualifiedName;
        return Arrays.stream(name.split("\\."))
            .map(PythonKeywords::toPythonName)
            .collect(Collectors.joining("."));
    }

    private static String toPackageName(String parentPath) {
        String path = parentPath.endsWith("/") ? parentPath.substring(0, parentPath.length() - 1) : parentPath;
        return path.replace('/', '.');
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

    /**
     * Sets the class loader used for in-memory compiler execution.
     *
     * @param classLoader The class loader
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    record PathEntry(String parent, String filename) {
    }

    private record PythonApplicationValues(String code, String[] src) {
    }

    private record JavaClassImport(String className, boolean interfaceType, boolean keywordSafe) {
        private boolean requiresFacade() {
            return interfaceType || keywordSafe;
        }

        private JavaClassImport merge(JavaClassImport other) {
            return new JavaClassImport(
                other.className,
                interfaceType || other.interfaceType,
                keywordSafe || other.keywordSafe
            );
        }
    }
}
