package io.micronaut.kotlin.processing.elementapi;

import com.tschuchort.compiletesting.KotlinCompilation;
import com.tschuchort.compiletesting.KspKt;
import com.tschuchort.compiletesting.SourceFile;
import io.micronaut.kotlin.processing.visitor.TypeElementSymbolProcessorProvider;
import org.intellij.lang.annotations.Language;

import java.util.Collections;

public class Compiler {

    static void compile(String name, @Language("kotlin") String clazz) {
        KotlinCompilation compilation = new KotlinCompilation();
        compilation.setSources(Collections.singletonList(SourceFile.Companion.kotlin(name + ".kt", clazz, true)));
        KspKt.setSymbolProcessorProviders(compilation, Collections.singletonList(new TypeElementSymbolProcessorProvider()));
        final KotlinCompilation.Result result = compilation.compile();
        if (result.getExitCode() != KotlinCompilation.ExitCode.OK) {
            throw new RuntimeException(result.getMessages());
        }
    }
}
