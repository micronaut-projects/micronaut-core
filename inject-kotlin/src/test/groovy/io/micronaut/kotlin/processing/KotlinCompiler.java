package io.micronaut.kotlin.processing;

import com.tschuchort.compiletesting.KotlinCompilation;
import com.tschuchort.compiletesting.KspKt;
import com.tschuchort.compiletesting.SourceFile;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultApplicationContext;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import io.micronaut.kotlin.processing.beans.BeanDefinitionProcessorProvider;
import io.micronaut.kotlin.processing.visitor.TypeElementSymbolProcessorProvider;
import org.intellij.lang.annotations.Language;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;

public class KotlinCompiler {

    public static URLClassLoader buildClassLoader(String name, @Language("kotlin") String clazz) {
        return compile(name, clazz).getClassLoader();
    }

    public static KotlinCompilation.Result compile(String name, @Language("kotlin") String clazz) {
        KotlinCompilation compilation = new KotlinCompilation();
        compilation.setSources(Collections.singletonList(SourceFile.Companion.kotlin(name + ".kt", clazz, true)));
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
        KspKt.setSymbolProcessorProviders(kspCompilation, Arrays.asList(new TypeElementSymbolProcessorProvider(), new BeanDefinitionProcessorProvider(), new ServiceDescriptionProcessorProvider()));

        result = kspCompilation.compile();
        if (result.getExitCode() != KotlinCompilation.ExitCode.OK) {
            throw new RuntimeException(result.getMessages());
        }

        return result;
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
        final URLClassLoader classLoader = buildClassLoader(name, clazz);
        String simpleName = NameUtils.getSimpleName(name);
        String beanDefName = (simpleName.startsWith("$") ? "" : '$') + simpleName + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionReferenceWriter.REF_SUFFIX;
        String packageName = NameUtils.getPackageName(name);
        String beanFullName = packageName + "." + beanDefName;
        return (BeanDefinitionReference<?>) loadDefinition(classLoader, beanFullName);
    }

    public static ApplicationContext buildContext(@Language("kotlin") String clazz) {
        return buildContext(clazz, false);
    }

    public static ApplicationContext buildContext(@Language("kotlin") String clazz, boolean includeAllBeans) {
        KotlinCompilation.Result result = compile("temp", clazz);
        ClassLoader classLoader = result.getClassLoader();
        return new DefaultApplicationContext(ClassPathResourceLoader.defaultLoader(classLoader),"test") {
     /*       @Override
            protected List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
                List<String> beanDefinitionNames = result.getCompiledClassAndResourceFiles()
                        .stream()
                        .map(File::getName)
                        .filter(name -> name.endsWith(BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionReferenceWriter.REF_SUFFIX))
                        .collect(Collectors.toList());

                List<BeanDefinitionReference> beanDefinitions = new ArrayList<>(beanDefinitionNames.size());
                for (String name : beanDefinitionNames) {
                    try {
                        beanDefinitions.add((BeanDefinitionReference) loadDefinition(classLoader, name));
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
            }*/
        }.start();
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
