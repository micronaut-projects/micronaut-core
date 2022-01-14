package io.micronaut.kotlin.processing;

import com.tschuchort.compiletesting.KotlinCompilation;
import com.tschuchort.compiletesting.KspKt;
import com.tschuchort.compiletesting.SourceFile;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.BeanDefinition;
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
        KspKt.setSymbolProcessorProviders(kspCompilation, Arrays.asList(new TypeElementSymbolProcessorProvider(), new BeanDefinitionProcessorProvider()));

        result = kspCompilation.compile();
        if (result.getExitCode() != KotlinCompilation.ExitCode.OK) {
            throw new RuntimeException(result.getMessages());
        }

        return result.getClassLoader();
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
        final URLClassLoader classLoader = buildClassLoader(name, clazz);
        String simpleName = NameUtils.getSimpleName(name);
        String beanDefName = (simpleName.startsWith("$") ? "" : '$') + simpleName + BeanDefinitionWriter.CLASS_SUFFIX;
        String packageName = NameUtils.getPackageName(name);
        String beanFullName = packageName + "." + beanDefName;
        try {
            Class<?> c = classLoader.loadClass(beanFullName);
            Constructor<?> constructor = c.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (BeanDefinition<?>) constructor.newInstance();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
