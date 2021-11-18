package io.micronaut.kotlin.processing.elementapi;

import com.tschuchort.compiletesting.KotlinCompilation;
import com.tschuchort.compiletesting.KspKt;
import com.tschuchort.compiletesting.SourceFile;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.kotlin.processing.visitor.TypeElementSymbolProcessorProvider;
import org.intellij.lang.annotations.Language;

import java.io.File;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;

public class Compiler {

    static BeanIntrospection<?> buildBeanIntrospection(String name, @Language("kotlin") String clazz) {
        KotlinCompilation compilation = new KotlinCompilation();
        compilation.setSources(Collections.singletonList(SourceFile.Companion.kotlin(name + ".kt", clazz, true)));
        compilation.setInheritClassPath(true);

        KotlinCompilation.Result result = compilation.compile();
        if (result.getExitCode() != KotlinCompilation.ExitCode.OK) {
            throw new RuntimeException(result.getMessages());
        }

        KotlinCompilation kspCompilation = new KotlinCompilation();
        kspCompilation.setSources(compilation.getSources());
        kspCompilation.setInheritClassPath(true);
        kspCompilation.setClasspaths(Arrays.asList(
                new File(kspCompilation.getWorkingDir(), "ksp/classes"),
                new File(kspCompilation.getWorkingDir(), "ksp/sources/resources"),
                compilation.getClassesDir()));
        KspKt.setSymbolProcessorProviders(kspCompilation, Collections.singletonList(new TypeElementSymbolProcessorProvider()));

        result = kspCompilation.compile();
        if (result.getExitCode() != KotlinCompilation.ExitCode.OK) {
            throw new RuntimeException(result.getMessages());
        }

        final URLClassLoader classLoader = result.getClassLoader();
        try {
            return BeanIntrospector.forClassLoader(classLoader).findIntrospection(classLoader.loadClass(name)).orElse(null);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
