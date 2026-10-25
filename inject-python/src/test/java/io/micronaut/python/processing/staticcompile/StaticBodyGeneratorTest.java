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

import io.micronaut.python.processing.staticcompile.StaticCompilationDecision.Stats;
import io.micronaut.sourcegen.JavaPoetSourceGenerator;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.junit.jupiter.api.Test;

import javax.lang.model.element.Modifier;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IR in, Java out: each test builds the {@link Ir} of a body by hand, generates the method, checks
 * the source and compiles and runs it.
 */
class StaticBodyGeneratorTest {

    private static final String OVERLOADS = GeneratorOverloads.class.getName();
    private static final StaticBodyGenerator.SelfAccess NO_SELF = new StaticBodyGenerator.SelfAccess() {
        @Override
        public ExpressionDef read(String property, String typeName, TypeDef type, boolean accessor) {
            throw new UnsupportedOperationException("self." + property);
        }

        @Override
        public ExpressionDef invoke(String name, List<TypeDef> parameterTypes, List<ExpressionDef> arguments, String typeName, TypeDef type, boolean direct) {
            throw new UnsupportedOperationException("self." + name + "()");
        }

        @Override
        public ExpressionDef invokeOn(ExpressionDef value, String owner, String name, List<ExpressionDef> arguments, String typeName, TypeDef type) {
            throw new UnsupportedOperationException(owner + "." + name + "()");
        }

        @Override
        public ExpressionDef readOf(ExpressionDef value, String owner, String property, String typeName, TypeDef type) {
            throw new UnsupportedOperationException(owner + "." + property);
        }

        @Override
        public StatementDef write(String property, TypeDef type, ExpressionDef value, boolean accessor) {
            throw new UnsupportedOperationException("self." + property);
        }
    };

    @Test
    void localsBranchesAndExactIntegerArithmetic() throws Exception {
        // total = n + 1; if total > 10: total = total * 2; return total (narrowed exactly to the int the stub returns)
        Ir.Param n = new Ir.Param("n", Ir.LONG, "int");
        Ir.LocalRef total = new Ir.LocalRef("total", Ir.LONG);
        Ir.Body body = new Ir.Body(List.of(
            new Ir.Local("total", Ir.LONG, new Ir.Binary("+", n, new Ir.Const(1L, Ir.LONG), Ir.LONG)),
            new Ir.If(new Ir.Compare(">", total, new Ir.Const(10L, Ir.LONG)),
                new Ir.Body(List.of(new Ir.Assign("total", new Ir.Binary("*", total, new Ir.Const(2L, Ir.LONG), Ir.LONG)))),
                null),
            new Ir.Return(new Ir.Cast(total, "int"))));
        Generated generated = generate(body, "int", List.of("n"), List.of("int"));

        assertTrue(generated.source.contains("long total = Math.addExact((long) n, 1l);"), generated.source);
        assertTrue(generated.source.contains("total = Math.multiplyExact(total, 2l);"), generated.source);
        assertTrue(generated.source.contains("return Math.toIntExact(total);"), generated.source);
        assertEquals(5, generated.invoke(4));
        assertEquals(22, generated.invoke(10));
        // where Python would promote to a big int, the compiled body raises
        assertInstanceOf(ArithmeticException.class, assertThrows(InvocationTargetException.class, () -> generated.invoke(Integer.MAX_VALUE)).getCause());
    }

    @Test
    void floatsDivideAndSpellAsPython() throws Exception {
        // return f"{a / b} {not flag}"
        Ir.Param a = new Ir.Param("a", Ir.DOUBLE, Ir.DOUBLE);
        Ir.Param b = new Ir.Param("b", Ir.DOUBLE, Ir.DOUBLE);
        Ir.Param flag = new Ir.Param("flag", Ir.BOOLEAN, Ir.BOOLEAN);
        Ir.Body body = new Ir.Body(List.of(new Ir.Return(new Ir.StrJoin(List.of(
            new Ir.Binary("/", a, b, Ir.DOUBLE), new Ir.Const(" ", Ir.STRING), new Ir.Unary("not", flag, Ir.BOOLEAN))))));
        Generated generated = generate(body, Ir.STRING, List.of("a", "b", "flag"), List.of(Ir.DOUBLE, Ir.DOUBLE, Ir.BOOLEAN));

        assertTrue(generated.source.contains("PythonStatic.divide(a, b)"), generated.source);
        assertEquals("0.25 False", generated.invoke(1.0d, 4.0d, true));
        assertEquals("1e+16 True", generated.invoke(1e16d, 1.0d, false));
        // Python raises ZeroDivisionError where Java would give an infinity
        assertThrows(InvocationTargetException.class, () -> generated.invoke(1.0d, 0.0d, true));
    }

    @Test
    void javaCallsAndConstructions() throws Exception {
        // return StringBuilder(name).append(Math.max(n, 3)).toString()
        Ir.Param name = new Ir.Param("name", Ir.STRING, Ir.STRING);
        Ir.Param n = new Ir.Param("n", Ir.LONG, "int");
        Ir.Expression max = new Ir.InvokeJava(null, "java.lang.Math", "max", List.of(Ir.LONG, Ir.LONG), List.of(n, new Ir.Const(3L, Ir.LONG)), Ir.LONG);
        Ir.Expression builder = new Ir.NewJava("java.lang.StringBuilder", List.of(Ir.STRING), List.of(name));
        Ir.Expression appended = new Ir.InvokeJava(builder, "java.lang.StringBuilder", "append", List.of(Ir.LONG), List.of(max), "java.lang.StringBuilder");
        Ir.Expression text = new Ir.InvokeJava(appended, "java.lang.StringBuilder", "toString", List.of(), List.of(), Ir.STRING);
        Generated generated = generate(new Ir.Body(List.of(new Ir.Return(text))), Ir.STRING, List.of("name", "n"), List.of(Ir.STRING, "int"));

        assertTrue(generated.source.contains("new java.lang.StringBuilder(name).append(Math.max((long) n, 3l)).toString()"), generated.source);
        assertEquals("ab7", generated.invoke("ab", 7));
        assertEquals("ab3", generated.invoke("ab", 1));
    }

    @Test
    void anArgumentIsCastToTheParameterTypeOfTheOverloadTheLoweringChose() throws Exception {
        // which(value) bound to which(CharSequence): javac alone would pick which(String)
        Ir.Param value = new Ir.Param("value", Ir.STRING, Ir.STRING);
        Ir.Expression sequence = new Ir.InvokeJava(null, OVERLOADS, "which", List.of("java.lang.CharSequence"), List.of(value), Ir.STRING);
        Ir.Expression string = new Ir.InvokeJava(null, OVERLOADS, "which", List.of(Ir.STRING), List.of(value), Ir.STRING);
        Ir.Body body = new Ir.Body(List.of(new Ir.Return(new Ir.StrJoin(List.of(sequence, new Ir.Const(", ", Ir.STRING), string)))));
        Generated generated = generate(body, Ir.STRING, List.of("value"), List.of(Ir.STRING));

        assertTrue(generated.source.contains("GeneratorOverloads.which((CharSequence) value)"), generated.source);
        assertTrue(generated.source.contains("GeneratorOverloads.which(value)"), generated.source);
        assertEquals("CharSequence x, String x", generated.invoke("x"));
    }

    @Test
    void anArgumentToATypeVariableIsNotCastSoJavacInfersItsType() throws Exception {
        // requireNonNull(T) as the facts spell it, requireNonNull(Object): (Object) value would make T an Object, no String
        Ir.Param value = new Ir.Param("value", Ir.STRING, Ir.STRING);
        Ir.Expression call = new Ir.InvokeJava(null, "java.util.Objects", "requireNonNull", List.of("java.lang.Object"), List.of(value), Ir.STRING);
        Generated generated = generate(new Ir.Body(List.of(new Ir.Return(call))), Ir.STRING, List.of("value"), List.of(Ir.STRING));

        assertTrue(generated.source.contains("Objects.requireNonNull(value)"), generated.source);
        assertEquals("x", generated.invoke("x"));
    }

    @Test
    void aNoneArgumentHasTheTypeOfItsParameter() throws Exception {
        // which(None) bound to which(CharSequence): a bare null would bind to which(String)
        Ir.Expression sequence = new Ir.InvokeJava(null, OVERLOADS, "which", List.of("java.lang.CharSequence"), List.of(new Ir.Const(null, "java.lang.CharSequence")), Ir.STRING);
        Ir.Expression string = new Ir.InvokeJava(null, OVERLOADS, "which", List.of(Ir.STRING), List.of(new Ir.Const(null, Ir.STRING)), Ir.STRING);
        Ir.Expression again = new Ir.InvokeJava(null, OVERLOADS, "which", List.of(Ir.STRING), List.of(new Ir.Const(null, Ir.STRING)), Ir.STRING);
        Ir.Body body = new Ir.Body(List.of(new Ir.Return(new Ir.StrJoin(List.of(sequence, new Ir.Const(", ", Ir.STRING), string, new Ir.Const(", ", Ir.STRING), again)))));
        // a local of the body already has the first name a typed None would take
        Ir.Body withLocal = new Ir.Body(List.of(new Ir.Local("none0", Ir.STRING, new Ir.Const("taken", Ir.STRING)), body));
        Generated generated = generate(withLocal, Ir.STRING, List.of(), List.of());

        assertTrue(generated.source.contains("CharSequence none1 = null;"), generated.source);
        assertTrue(generated.source.contains("String none2 = null;"), generated.source);
        assertTrue(generated.source.contains("GeneratorOverloads.which(none1)"), generated.source);
        assertEquals("CharSequence null, String null, String null", generated.invoke());
    }

    private static Generated generate(Ir.Body body, String returnType, List<String> parameterNames, List<String> parameterTypes) throws Exception {
        Ir.CompiledBody compiled = new Ir.CompiledBody("generated.Body", "run", parameterNames, parameterTypes, returnType, body, null, Stats.NONE, false, false, false, false);
        MethodDef.MethodDefBuilder method = MethodDef.builder("run").addModifiers(Modifier.PUBLIC).returns(StaticBodyGenerator.type(returnType));
        for (int i = 0; i < parameterNames.size(); i++) {
            method.addParameter(ParameterDef.of(parameterNames.get(i), StaticBodyGenerator.type(parameterTypes.get(i))));
        }
        ClassDef classDef = ClassDef.builder("generated.Body")
            .addModifiers(Modifier.PUBLIC)
            .addMethod(method.buildStatic(methodParameters -> StaticBodyGenerator.generate(compiled, methodParameters, NO_SELF)))
            .build();
        StringWriter writer = new StringWriter();
        new JavaPoetSourceGenerator().write(classDef, writer);
        String source = writer.toString();
        Class<?> type = compile("generated.Body", source);
        Method run = null;
        for (Method candidate : type.getMethods()) {
            if (candidate.getName().equals("run")) {
                run = candidate;
            }
        }
        return new Generated(source, run);
    }

    private static Class<?> compile(String className, String source) throws ClassNotFoundException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager standard = compiler.getStandardFileManager(null, null, null);
        Map<String, ByteArrayOutputStream> classes = new HashMap<>();
        JavaFileManager fileManager = new ForwardingJavaFileManager<>(standard) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject sibling) {
                return new SimpleJavaFileObject(URI.create("mem:///" + name.replace('.', '/') + kind.extension), kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        return classes.computeIfAbsent(name, ignored -> new ByteArrayOutputStream());
                    }
                };
            }
        };
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        StringWriter errors = new StringWriter();
        boolean compiled = compiler.getTask(errors, fileManager, null, List.of("-classpath", System.getProperty("java.class.path")), null, List.of(file)).call();
        assertTrue(compiled, errors + "\n" + source);
        ClassLoader loader = new ClassLoader(StaticBodyGeneratorTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                ByteArrayOutputStream bytes = classes.get(name);
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] code = bytes.toByteArray();
                return defineClass(name, code, 0, code.length);
            }
        };
        return loader.loadClass(className);
    }

    private record Generated(String source, Method run) {
        Object invoke(Object... arguments) throws IllegalAccessException, InvocationTargetException {
            return run.invoke(null, arguments);
        }
    }
}
