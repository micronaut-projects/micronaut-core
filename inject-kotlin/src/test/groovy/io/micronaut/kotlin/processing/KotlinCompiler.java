package io.micronaut.kotlin.processing;

import com.tschuchort.compiletesting.KotlinCompilation;
import com.tschuchort.compiletesting.KspKt;
import com.tschuchort.compiletesting.SourceFile;
import io.micronaut.aop.internal.InterceptorRegistryBean;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.DefaultApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisherFactory;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.provider.BeanProviderDefinition;
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import io.micronaut.kotlin.processing.beans.BeanDefinitionProcessorProvider;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class KotlinCompiler {

    public static URLClassLoader buildClassLoader(String name, @Language("kotlin") String clazz) {
        Pair<Pair<KotlinCompilation, KotlinCompilation.Result>, Pair<KotlinCompilation, KotlinCompilation.Result>> resultPair = compile(name, clazz);
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

    public static Pair<Pair<KotlinCompilation, KotlinCompilation.Result>, Pair<KotlinCompilation, KotlinCompilation.Result>> compile(String name, @Language("kotlin") String clazz) {
        KotlinCompilation compilation = new KotlinCompilation();
        compilation.setSources(Collections.singletonList(SourceFile.Companion.kotlin(name + ".kt", clazz, true)));
        compilation.setJvmDefault("all");
        compilation.setInheritClassPath(true);

        KotlinCompilation.Result result = compilation.compile();
        if (result.getExitCode() != KotlinCompilation.ExitCode.OK) {
            throw new RuntimeException(result.getMessages());
        }

        KotlinCompilation kspCompilation = new KotlinCompilation();
        kspCompilation.setJavacArguments(Collections.singletonList("-Xopt-in=kotlin.RequiresOptIn"));
        kspCompilation.setSources(compilation.getSources());
        kspCompilation.setInheritClassPath(true);
        kspCompilation.setClasspaths(Arrays.asList(
                new File(kspCompilation.getWorkingDir(), "ksp/classes"),
                new File(kspCompilation.getWorkingDir(), "ksp/sources/resources"),
                compilation.getClassesDir()));
        KspKt.setSymbolProcessorProviders(kspCompilation, Arrays.asList(new TypeElementSymbolProcessorProvider(), new BeanDefinitionProcessorProvider()));

        KotlinCompilation.Result kspResult = kspCompilation.compile();
        if (kspResult.getExitCode() != KotlinCompilation.ExitCode.OK) {
            throw new RuntimeException(kspResult.getMessages());
        }

        return new Pair<>(new Pair<>(compilation, result), new Pair<>(kspCompilation, kspResult));
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
        return loadReference(name, clazz, BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionReferenceWriter.REF_SUFFIX);
    }

    public static BeanDefinition<?> buildInterceptedBeanDefinition(String className, @Language("kotlin") String cls) throws InstantiationException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return loadReference(className, cls, BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionVisitor.PROXY_SUFFIX + BeanDefinitionWriter.CLASS_SUFFIX);
    }

    public static BeanDefinitionReference<?> buildInterceptedBeanDefinitionReference(String className, @Language("kotlin") String cls) throws InstantiationException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return loadReference(className, cls, BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionVisitor.PROXY_SUFFIX + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionReferenceWriter.REF_SUFFIX);
    }

    private static <T> T loadReference(String className,
                                       @Language("kotlin") String cls,
                                       String suffix
                                       ) throws InstantiationException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String simpleName = NameUtils.getSimpleName(className);
        String beanDefName = (simpleName.startsWith("$") ? "" : '$') + simpleName + suffix;
        String packageName = NameUtils.getPackageName(className);
        String beanFullName = packageName + "." + beanDefName;

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
        Pair<Pair<KotlinCompilation, KotlinCompilation.Result>, Pair<KotlinCompilation, KotlinCompilation.Result>> pair = compile("temp", clazz);
        ClassLoader classLoader = toClassLoader(pair);
        return new DefaultApplicationContext(ClassPathResourceLoader.defaultLoader(classLoader),"test") {
            @Override
            protected List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
                List<String> beanDefinitionNames = pair.component2().component1().
                    getClasspaths().stream().filter(f -> f.toURI().toString().contains("/ksp/sources/resources"))
                        .flatMap(dir -> {
                        File[] files = new File(dir, "META-INF/micronaut/io/micronaut/inject/BeanDefinitionReference").listFiles();
                        if (files == null) {
                            return Stream.empty();
                        }
                        return Stream.of(files).filter(f -> f.isFile());
                    }).map(f -> f.getName()).toList();

                List<BeanDefinitionReference> beanDefinitions = new ArrayList<>(beanDefinitionNames.size());
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
                    beanDefinitions.addAll(super.resolveBeanDefinitionReferences());
                } else {
                    beanDefinitions.add(new InterceptorRegistryBean());
                    beanDefinitions.add(new BeanProviderDefinition());
                    beanDefinitions.add(new ApplicationEventPublisherFactory<>());
                }
                return beanDefinitions;
            }
        }.start();
    }

    public static Object getBean(BeanContext beanContext, String className) throws ClassNotFoundException {
        return beanContext.getBean(beanContext.getClassLoader().loadClass(className));
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
}
