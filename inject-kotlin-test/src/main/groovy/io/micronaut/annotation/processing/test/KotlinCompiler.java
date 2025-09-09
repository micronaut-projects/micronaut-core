/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.annotation.processing.test;

import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import io.micronaut.annotation.processing.test.support.KotlinCompilation;
import io.micronaut.annotation.processing.test.support.KspKt;
import io.micronaut.annotation.processing.test.support.SourceFile;
import io.micronaut.aop.internal.InterceptorRegistryBean;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDefinitionsProvider;
import io.micronaut.context.DefaultBeanDefinitionsProvider;
import io.micronaut.context.event.ApplicationEventPublisherFactory;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.provider.BeanProviderDefinition;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import io.micronaut.kotlin.processing.beans.BeanDefinitionProcessorProvider;
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext;
import io.micronaut.kotlin.processing.visitor.TypeElementSymbolProcessor;
import io.micronaut.kotlin.processing.visitor.TypeElementSymbolProcessorProvider;
import kotlin.Pair;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Compiler for kotlin code (for tests).
 */
public class KotlinCompiler {

    private static final KotlinCompilation KOTLIN_COMPILATION = new KotlinCompilation();
    private static final KotlinCompilation KSP_COMPILATION = new KotlinCompilation();

    static {

        KOTLIN_COMPILATION.setJvmDefault("all");
        KOTLIN_COMPILATION.setInheritClassPath(true);

        KSP_COMPILATION.setInheritClassPath(true);
        KSP_COMPILATION.setClasspaths(Arrays.asList(
            new File(KSP_COMPILATION.getWorkingDir(), "ksp/classes"),
            new File(KSP_COMPILATION.getWorkingDir(), "ksp/sources/resources"),
            KOTLIN_COMPILATION.getClassesDir()));
    }

    public static URLClassLoader buildClassLoader(String name, @Language("kotlin") String clazz) {
        Pair<Pair<KotlinCompilation, KotlinCompilation.Result>, Pair<KotlinCompilation, KotlinCompilation.Result>> resultPair = compile(name, clazz, classElement -> {
        });
        return toClassLoader(resultPair);
    }

    @NotNull
    private static URLClassLoader toClassLoader(Pair<Pair<KotlinCompilation, KotlinCompilation.Result>, Pair<KotlinCompilation, KotlinCompilation.Result>> resultPair) {
        try {
            Pair<KotlinCompilation, KotlinCompilation.Result> sourcesCompilation = resultPair.component1();
            Pair<KotlinCompilation, KotlinCompilation.Result> kspCompilation = resultPair.component2();

            KotlinCompilation.Result sourcesCompileResult = sourcesCompilation.component2();
            KotlinCompilation.Result kspCompileResult = kspCompilation.component2();
            List<URL> classpath = new ArrayList<>();
            classpath.add(sourcesCompileResult.getOutputDirectory().toURI().toURL());
            classpath.add(kspCompileResult.getOutputDirectory().toURI().toURL());
            classpath.addAll(kspCompilation.component1().getClasspaths().stream().flatMap(f -> {
                try {
                    return Stream.of(f.toURI().toURL());
                } catch (MalformedURLException e) {
                    return Stream.empty();
                }
            }).toList());
            classpath.addAll(sourcesCompilation.component1().getClasspaths().stream().flatMap(f -> {
                try {
                    return Stream.of(f.toURI().toURL());
                } catch (MalformedURLException e) {
                    return Stream.empty();
                }
            }).toList());

            return new URLClassLoader(
                classpath.toArray(URL[]::new),
                KotlinCompiler.class.getClassLoader()
            );
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public static Pair<Pair<KotlinCompilation, KotlinCompilation.Result>, Pair<KotlinCompilation, KotlinCompilation.Result>> compile(String name, @Language("kotlin") String clazz, Consumer<ClassElement> classElements) {
        try {
            Files.deleteIfExists(KOTLIN_COMPILATION.getWorkingDir().toPath());
        } catch (IOException e) {
            // ignore
        }
        KOTLIN_COMPILATION.setSources(Collections.singletonList(SourceFile.Companion.kotlin(name + ".kt", clazz, true)));
        KotlinCompilation.Result result = KOTLIN_COMPILATION.compile();
        if (result.getExitCode() != KotlinCompilation.ExitCode.OK) {
            throw new RuntimeException(result.getMessages());
        }

        KSP_COMPILATION.setSources(KOTLIN_COMPILATION.getSources());
        ClassElementTypeElementSymbolProcessorProvider classElementTypeElementSymbolProcessorProvider = new ClassElementTypeElementSymbolProcessorProvider(classElements);
        KspKt.setSymbolProcessorProviders(KSP_COMPILATION, Arrays.asList(classElementTypeElementSymbolProcessorProvider, new BeanDefinitionProcessorProvider()));
        KotlinCompilation.Result kspResult = KSP_COMPILATION.compile();
        if (kspResult.getExitCode() != KotlinCompilation.ExitCode.OK) {
            throw new RuntimeException(kspResult.getMessages());
        }

        return new Pair<>(new Pair<>(KOTLIN_COMPILATION, result), new Pair<>(KSP_COMPILATION, kspResult));
    }

    public static Pair<Pair<KotlinCompilation, KotlinCompilation.Result>, Pair<KotlinCompilation, KotlinCompilation.Result>> compileJava(String name, @Language("java") String clazz, Consumer<ClassElement> classElements) {
        try {
            Files.deleteIfExists(KOTLIN_COMPILATION.getWorkingDir().toPath());
        } catch (IOException e) {
            // ignore
        }
        KOTLIN_COMPILATION.setSources(Collections.singletonList(SourceFile.Companion.java(name + ".java", clazz, true)));
        KotlinCompilation.Result result = KOTLIN_COMPILATION.compile();
        if (result.getExitCode() != KotlinCompilation.ExitCode.OK) {
            throw new RuntimeException(result.getMessages());
        }

        KSP_COMPILATION.setSources(KOTLIN_COMPILATION.getSources());
        ClassElementTypeElementSymbolProcessorProvider classElementTypeElementSymbolProcessorProvider = new ClassElementTypeElementSymbolProcessorProvider(classElements);
        KspKt.setSymbolProcessorProviders(KSP_COMPILATION, Arrays.asList(classElementTypeElementSymbolProcessorProvider, new BeanDefinitionProcessorProvider()));
        KotlinCompilation.Result kspResult = KSP_COMPILATION.compile();
        if (kspResult.getExitCode() != KotlinCompilation.ExitCode.OK) {
            throw new RuntimeException(kspResult.getMessages());
        }

        return new Pair<>(new Pair<>(KOTLIN_COMPILATION, result), new Pair<>(KSP_COMPILATION, kspResult));
    }

    public static BeanIntrospection<?> buildBeanIntrospection(String name, @Language("kotlin") String clazz) {
        final URLClassLoader classLoader = buildClassLoader(name, clazz);
        try {
            return BeanIntrospector.forClassLoader(classLoader).findIntrospection(classLoader.loadClass(name)).orElse(null);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static BeanDefinition<?> buildBeanDefinition(String name, @Language("kotlin") String clazz) throws InstantiationException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return buildBeanDefinition(NameUtils.getPackageName(name),
                NameUtils.getSimpleName(name),
                clazz);
    }

    public static BeanDefinition<?> buildBeanDefinition(String packageName, String simpleName, @Language("kotlin") String clazz) throws InstantiationException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        final URLClassLoader classLoader = buildClassLoader(packageName + "." + simpleName, clazz);
        String beanDefName = (simpleName.startsWith("$") ? "" : '$') + simpleName + BeanDefinitionWriter.CLASS_SUFFIX;
        String beanFullName = packageName + "." + beanDefName;
        return (BeanDefinition<?>) loadDefinition(classLoader, beanFullName);
    }

    public static BeanDefinitionReference<?> buildBeanDefinitionReference(String name, @Language("kotlin") String clazz) throws InstantiationException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return loadReference(name, clazz, BeanDefinitionWriter.CLASS_SUFFIX);
    }

    public static BeanDefinition<?> buildIntroducedBeanDefinition(String className, @Language("kotlin") String cls) throws InstantiationException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return loadReference(className, cls, BeanDefinitionVisitor.PROXY_SUFFIX + BeanDefinitionWriter.CLASS_SUFFIX);
    }

    public static BeanDefinition<?> buildInterceptedBeanDefinition(String className, @Language("kotlin") String cls) throws InstantiationException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return loadReference(className, cls, BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionVisitor.PROXY_SUFFIX + BeanDefinitionWriter.CLASS_SUFFIX);
    }

    public static BeanDefinitionReference<?> buildInterceptedBeanDefinitionReference(String className, @Language("kotlin") String cls) throws InstantiationException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return loadReference(className, cls, BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionVisitor.PROXY_SUFFIX + BeanDefinitionWriter.CLASS_SUFFIX);
    }

    private static <T> T loadReference(String className,
                                       @Language("kotlin") String cls,
                                       String suffix
                                       ) throws InstantiationException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String simpleName = NameUtils.getSimpleName(className);
        String beanDefName = (simpleName.startsWith("$") ? "" : '$') + simpleName + suffix;
        String packageName = NameUtils.getPackageName(className);
        String beanFullName = packageName + "." + beanDefName;

        return buildAndLoad(className, beanFullName, cls);
    }

    public static <T> T buildAndLoad(String className, String beanFullName, @Language("kotlin") String cls) throws InstantiationException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ClassLoader classLoader = buildClassLoader(className, cls);
        return (T) loadDefinition(classLoader, beanFullName);
    }

    public static byte[] getClassBytes(String name, @Language("kotlin") String clazz) throws FileNotFoundException, IOException {
        String simpleName = NameUtils.getSimpleName(name);
        String className = (simpleName.startsWith("$") ? "" : '$') + simpleName;
        String packageName = NameUtils.getPackageName(name);
        String fileName = packageName.replace('.', '/') + '/' + className + ".class";
        URLClassLoader classLoader = buildClassLoader(className, clazz);
        File file = null;
        for (URL url: classLoader.getURLs()) {
            file = new File(url.getFile(), fileName);
            if (file.exists()) {
                break;
            } else {
                file = null;
            }
        }
        if (file != null) {
            try (InputStream is = new FileInputStream(file)) {
                ByteArrayOutputStream answer = new ByteArrayOutputStream();
                byte[] byteBuffer = new byte[8192];
                int nbByteRead;
                while ((nbByteRead = is.read(byteBuffer)) != -1) {
                    answer.write(byteBuffer, 0, nbByteRead);
                }
                return answer.toByteArray();
            }
        }
        return null;
    }

    public static ApplicationContext buildContext(@Language("kotlin") String clazz) {
        return buildContext(clazz, false);
    }

    public static ApplicationContext buildContext(@Language("kotlin") String clazz, boolean includeAllBeans) {
        return buildContext(clazz, includeAllBeans, Collections.emptyMap());
    }

    @SuppressWarnings("java:S2095")
    public static ApplicationContext buildContext(@Language("kotlin") String clazz, boolean includeAllBeans, Map<String, Object> config) {
        Pair<Pair<KotlinCompilation, KotlinCompilation.Result>, Pair<KotlinCompilation, KotlinCompilation.Result>> pair = compile("temp", clazz, classElement -> {
        });
        ClassLoader classLoader = toClassLoader(pair);
        var builder = ApplicationContext.builder();
        builder.classLoader(classLoader);
        builder.environments("test");
        builder.properties(config);
        builder.beanDefinitionsProvider(new BeanDefinitionsProvider() {
            @Override
            public List<BeanDefinitionReference<?>> provide(ClassLoader classLoader) {
                List<String> beanDefinitionNames = pair.component2().component1().
                    getClasspaths().stream().filter(f -> f.toURI().toString().contains("/ksp/sources/resources"))
                    .flatMap(dir -> {
                        File[] files = new File(dir, "META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference").listFiles();
                        if (files == null) {
                            return Stream.empty();
                        }
                        return Stream.of(files).filter(f -> f.isFile());
                    }).map(f -> f.getName()).toList();

                List<BeanDefinitionReference<?>> beanDefinitions = new ArrayList<>(beanDefinitionNames.size());
                for (String name : beanDefinitionNames) {
                    try {
                        BeanDefinitionReference br = (BeanDefinitionReference) loadDefinition(classLoader, name);
                        if (br != null) {
                            beanDefinitions.add(br);
                        }
                    } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                    }
                }
                if (includeAllBeans) {
                    beanDefinitions.addAll(new DefaultBeanDefinitionsProvider().provide(classLoader));
                } else {
                    beanDefinitions.add(new InterceptorRegistryBean());
                    beanDefinitions.add(new BeanProviderDefinition());
                    beanDefinitions.add(new ApplicationEventPublisherFactory<>());
                }
                return beanDefinitions;
            }
        });
        return builder.build().start();
    }

    public static Object getBean(BeanContext beanContext, String className) throws ClassNotFoundException {
        return beanContext.getBean(beanContext.getClassLoader().loadClass(className));
    }

    public static BeanDefinition<?> getBeanDefinition(BeanContext beanContext, String className) throws ClassNotFoundException {
        return beanContext.getBeanDefinition(beanContext.getClassLoader().loadClass(className));
    }

    private static Object loadDefinition(ClassLoader classLoader, String name) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        try {
            Class<?> c = classLoader.loadClass(name);
            Constructor<?> constructor = c.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static class ClassElementTypeElementSymbolProcessorProvider extends TypeElementSymbolProcessorProvider {
        Consumer<ClassElement> classElements;

        public ClassElementTypeElementSymbolProcessorProvider(Consumer<ClassElement> classElements) {
            this.classElements = classElements;
        }

        @NotNull
        @Override
        public SymbolProcessor create(@NotNull SymbolProcessorEnvironment environment) {
            return new TypeElementSymbolProcessor(environment) {
                @NotNull
                @Override
                public ClassElement newClassElement(@NotNull KotlinVisitorContext visitorContext, @NotNull KSClassDeclaration classDeclaration) {
                    ClassElement classElement = super.newClassElement(visitorContext, classDeclaration);
                    classElements.accept(classElement);
                    return classElement;
                }
            };
        }
    }
}
