/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.python.processing.staticcompile;

import io.micronaut.python.processing.model.SourceSpan;
import io.micronaut.sourcegen.JavaPoetSourceGenerator;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.LineNumber;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticLineMapTest {

    // a stub as the stub generator writes it: a bridged method and a compiled one
    private static final String STUB = """
        package example;

        public class Sample {
          public int bridged(int n) {
            return n + 1;
          }

          /**
           * Compiled from /work/example/sample.py:3:5
           */
          public int compiled(int n) {
            long total = 0L;
            // Python line 4
            if (n > 1) {
              // Python line 5
              total = n * 2L;
            } else {
              // Python line 7
              total = n;
            }
            // Python line 8
            return (int) total;
          }
        }
        """;

    @Test
    void eachJavaLineOfACompiledMethodHasThePythonLineOfItsStatement() {
        StaticLineMap map = StaticLineMap.of(STUB);
        assertNotNull(map);
        assertEquals("sample.py", map.sourceFile());
        // the bridged method has no Python line
        assertEquals(0, map.pythonLine(5));
        // the signature and the code before the first statement: the line of the def
        assertEquals(3, map.pythonLine(11));
        assertEquals(3, map.pythonLine(12));
        assertEquals(4, map.pythonLine(14));
        assertEquals(5, map.pythonLine(16));
        // the else belongs to the if
        assertEquals(4, map.pythonLine(17));
        assertEquals(7, map.pythonLine(19));
        assertEquals(4, map.pythonLine(20));
        assertEquals(8, map.pythonLine(22));
        assertEquals(3, map.pythonLine(23));
        // after the method
        assertEquals(0, map.pythonLine(24));
        assertEquals(0, map.pythonLine(1000));
    }

    @Test
    void aSourceWithoutCompiledMethodsOrWithMoreThanOnePythonFileHasNoMap() {
        assertNull(StaticLineMap.of("package example;\n\npublic class Plain {\n}\n"));
        String twoFiles = STUB.replace("  public int bridged(int n) {\n", """
              /**
               * Compiled from /work/example/other.py:1:1
               */
              public int bridged(int n) {
            """);
        assertNull(StaticLineMap.of(twoFiles));
    }

    @Test
    void theMarkersOfTheGeneratedBodyBecomeComments() throws IOException {
        // def check(n: int) -> int:            line 3
        //     if n > 10:                       line 4
        //         raise IllegalArgumentException("big")
        //     return n                         line 6
        Ir.Expression n = new Ir.Param("n", Ir.LONG, "int");
        Ir.Statement raise = new Ir.Throw(new Ir.NewJava("java.lang.IllegalArgumentException", List.of(Ir.STRING), List.of(new Ir.Const("big", Ir.STRING))));
        Ir.Body body = new Ir.Body(List.of(
            new Ir.If(new Ir.Compare(">", n, new Ir.Const(10L, Ir.LONG)), new Ir.Body(List.of(raise), List.of(5)), null),
            new Ir.Return(new Ir.Cast(n, "int"))
        ), List.of(4, 6));
        Ir.CompiledBody compiled = new Ir.CompiledBody("example.Sample", "check", List.of("n"), List.of("int"), "int", body,
            SourceSpan.at("app/sample.py", 3, 5), new StaticCompilationDecision.Stats(2, 0, 0, 0), false, false, true, true, Ir.ExceptionCrossing.UNTRANSLATED, List.of(), false);
        ClassDef classDef = ClassDef.builder("example.Sample")
            .addModifiers(Modifier.PUBLIC)
            .addMethod(MethodDef.builder("check")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(TypeDef.Primitive.INT)
                .addParameter(ParameterDef.of("n", TypeDef.Primitive.INT))
                .addJavadoc("Compiled from " + compiled.span().location())
                .build((aThis, parameters) -> StaticBodyGenerator.generate(compiled, parameters, new NoSelf())))
            .build();
        StringWriter written = new StringWriter();
        new JavaPoetSourceGenerator().write(classDef, written);
        assertTrue(written.toString().contains("__mnPythonLine5();"), written.toString());

        String source = StaticLineMap.withLineComments(written.toString());
        assertFalse(source.contains("__mnPythonLine"), source);
        assertTrue(source.contains("// Python line 4\n"), source);
        assertTrue(source.contains("// Python line 5\n"), source);
        assertTrue(source.contains("// Python line 6\n"), source);
        StaticLineMap map = StaticLineMap.of(source);
        assertNotNull(map);
        List<String> lines = source.lines().toList();
        int raised = lines.indexOf(lines.stream().filter(line -> line.contains("throw new")).findFirst().orElseThrow()) + 1;
        assertEquals(5, map.pythonLine(raised), source);
    }

    @Test
    void theClassFilesOfTheSourceNameThePythonFileAndLines(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("src/example"));
        Path classes = Files.createDirectories(directory.resolve("classes"));
        String failing = STUB.replace("total = n * 2L;", "throw new IllegalStateException(\"big\");");
        Path source = Files.writeString(sources.resolve("Sample.java"), failing);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, javac.run(null, null, null, "-d", classes.toString(), source.toString()));
        Path classFile = classes.resolve("example/Sample.class");
        byte[] original = Files.readAllBytes(classFile);

        StaticLineMap map = StaticLineMap.of(failing);
        assertNotNull(map);
        // a class file of another source is left as it is, and so is one that is not a class file
        assertArrayEquals(original, map.apply(original, "Other.java"));
        byte[] garbage = {1, 2, 3};
        assertArrayEquals(garbage, map.apply(garbage, "Sample.java"));

        byte[] rewritten = map.apply(original, "Sample.java");
        ClassModel model = ClassFile.of().parse(rewritten);
        assertEquals("sample.py", model.findAttribute(Attributes.sourceFile()).orElseThrow().sourceFile().stringValue());
        assertEquals(List.of(), lines(model, "bridged"));
        assertEquals(List.of(3, 4, 5, 7, 8), lines(model, "compiled"));

        Files.write(classFile, rewritten);
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null)) {
            Class<?> sample = loader.loadClass("example.Sample");
            Object instance = sample.getConstructor().newInstance();
            assertEquals(2, sample.getMethod("bridged", int.class).invoke(instance, 1));
            InvocationTargetException failure = assertThrows(InvocationTargetException.class, () -> sample.getMethod("compiled", int.class).invoke(instance, 2));
            StackTraceElement frame = failure.getCause().getStackTrace()[0];
            assertEquals("sample.py", frame.getFileName(), frame.toString());
            assertEquals(5, frame.getLineNumber(), frame.toString());
        }
    }

    private static List<Integer> lines(ClassModel model, String methodName) {
        MethodModel method = model.methods().stream().filter(m -> m.methodName().equalsString(methodName)).findFirst().orElseThrow();
        return method.code().orElseThrow().elementStream()
            .filter(LineNumber.class::isInstance)
            .map(element -> ((LineNumber) element).line())
            .distinct()
            .toList();
    }

    private static final class NoSelf implements StaticBodyGenerator.SelfAccess {
        @Override
        public ExpressionDef read(String property, String typeName, TypeDef type, boolean accessor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExpressionDef invoke(String name, List<TypeDef> parameterTypes, List<ExpressionDef> arguments, String typeName, TypeDef type, boolean direct) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExpressionDef invokeOn(ExpressionDef value, String owner, String name, List<ExpressionDef> arguments, String typeName, TypeDef type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExpressionDef readOf(ExpressionDef value, String owner, String property, String typeName, TypeDef type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StatementDef write(String property, TypeDef type, ExpressionDef value, boolean accessor) {
            throw new UnsupportedOperationException();
        }
    }
}
