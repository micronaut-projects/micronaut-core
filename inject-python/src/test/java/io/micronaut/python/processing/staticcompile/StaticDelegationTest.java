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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StaticDelegationTest {

    private static final String OBJECT = "java.lang.Object";

    private static Ir.CompiledBody body(List<String> types, String returnType, Ir.Statement... statements) {
        return body("app.Outer$Calc", false, false, types, returnType, statements);
    }

    private static Ir.CompiledBody body(String className, boolean staticMethod, boolean moduleLevel, List<String> types, String returnType, Ir.Statement... statements) {
        return body(className, staticMethod, moduleLevel, Ir.ExceptionCrossing.UNTRANSLATED, types, returnType, statements);
    }

    private static Ir.CompiledBody body(String className, boolean staticMethod, boolean moduleLevel, Ir.ExceptionCrossing exceptions, List<String> types, String returnType, Ir.Statement... statements) {
        List<String> names = IntStream.range(0, types.size()).mapToObj(i -> "p" + i).toList();
        return new Ir.CompiledBody(className, "run", names, types, returnType, new Ir.Body(List.of(statements)),
            SourceSpan.at("app/calc.py", 3, 5), new StaticCompilationDecision.Stats(statements.length, 0, 0, 0), false, false, staticMethod, moduleLevel, exceptions);
    }

    private static Ir.Expression param(String type) {
        return new Ir.Param("p0", Ir.LONG.equals(type) || "int".equals(type) ? Ir.LONG : type, type);
    }

    @Test
    void aBodyWithoutSideEffectsIsRunAgainInPythonWhenItFails() {
        // n + 1, narrowed to the int the stub returns: both raise where Python promotes
        Ir.Expression sum = new Ir.Binary("+", param("int"), new Ir.Const(1L, Ir.LONG), Ir.LONG);
        assertEquals("Outer$Calc#run#owner=app.Outer$Calc#kinds=int#retry",
            StaticDelegation.target(body(List.of("int"), "int", new Ir.Return(new Ir.Cast(sum, "int")))));
    }

    @Test
    void aBodyWithSideEffectsDelegatesOnlyWithoutOperationsThatPromote() {
        Ir.Statement write = new Ir.PutSelf("label", Ir.STRING, param(Ir.STRING), false);
        assertEquals("Outer$Calc#run#owner=app.Outer$Calc#kinds=str#once", StaticDelegation.target(body(List.of(Ir.STRING), Ir.VOID, write)));
        Ir.Statement count = new Ir.PutSelf("count", "int", new Ir.Cast(new Ir.Binary("+", param("int"), new Ir.Const(1L, Ir.LONG), Ir.LONG), "int"), false);
        assertNull(StaticDelegation.target(body(List.of("int"), Ir.VOID, count)));
        // a division only raises where Python raises: the ZeroDivisionError reaches the caller
        Ir.Statement ratio = new Ir.PutSelf("ratio", Ir.DOUBLE, new Ir.Binary("/", param("int"), new Ir.Const(2L, Ir.LONG), Ir.DOUBLE), false);
        assertEquals("Outer$Calc#run#owner=app.Outer$Calc#kinds=int#once", StaticDelegation.target(body(List.of("int"), Ir.VOID, ratio)));
    }

    @Test
    void aParameterAPythonArgumentDoesNotReachUnchangedKeepsThePythonBody() {
        Ir.Statement returned = new Ir.Return(new Ir.Const(null, Ir.NONE));
        assertNull(StaticDelegation.target(body(List.of("java.util.List"), OBJECT, returned)));
        assertNull(StaticDelegation.target(body(List.of("float"), OBJECT, returned)));
        assertEquals("Outer$Calc#run#owner=app.Outer$Calc#kinds=?int,?float,bool,float,long#retry",
            StaticDelegation.target(body(List.of("java.lang.Integer", "java.lang.Double", Ir.BOOLEAN, Ir.DOUBLE, Ir.LONG), OBJECT, returned)));
    }

    @Test
    void aParameterOfAJavaClassTakesAJavaObjectOfThatClass() {
        Ir.Statement returned = new Ir.Return(new Ir.Const(null, Ir.NONE));
        assertEquals("Outer$Calc#run#owner=app.Outer$Calc#kinds=java:io.micronaut.aop.MethodInvocationContext#retry",
            StaticDelegation.target(body(List.of("io.micronaut.aop.MethodInvocationContext"), OBJECT, returned)));
        // an Object parameter takes a Java object, as itself; a collection, a parameterized or an array type is not taken
        assertEquals("Outer$Calc#run#owner=app.Outer$Calc#kinds=java:java.lang.Object#retry", StaticDelegation.target(body(List.of(OBJECT), OBJECT, returned)));
        assertNull(StaticDelegation.target(body(List.of("java.util.Map"), OBJECT, returned)));
        assertNull(StaticDelegation.target(body(List.of("java.util.function.Supplier<java.lang.String>"), OBJECT, returned)));
        assertNull(StaticDelegation.target(body(List.of("java.lang.String[]"), OBJECT, returned)));
    }

    @Test
    void aStaticMethodOrAModuleFunctionCallsTheGeneratedClassAndDelegatesOnlyWhenItMayBeRunAgain() {
        Ir.Expression sum = new Ir.Binary("+", param("int"), new Ir.Const(1L, Ir.LONG), Ir.LONG);
        Ir.Statement returned = new Ir.Return(new Ir.Cast(sum, "int"));
        assertEquals("Outer$Calc#run#static=app.Outer$Calc#kinds=int#retry",
            StaticDelegation.target(body("app.Outer$Calc", true, false, List.of("int"), "int", returned)));
        assertEquals("#run#static=app.Module#kinds=int#retry",
            StaticDelegation.target(body("app.Module", true, true, List.of("int"), "int", returned)));
        // no bound object raises a failure as Python's exception: a body with side effects keeps the Python body
        Ir.Expression call = new Ir.InvokeJava(null, "java.lang.Math", "abs", List.of(Ir.LONG), List.of(param(Ir.LONG)), Ir.LONG);
        assertNull(StaticDelegation.target(body("app.Module", true, true, List.of(Ir.LONG), Ir.LONG, new Ir.Return(call))));
    }

    @Test
    void aJavaCallIsASideEffect() {
        Ir.Expression call = new Ir.InvokeJava(null, "java.lang.Math", "abs", List.of(Ir.LONG), List.of(param(Ir.LONG)), Ir.LONG);
        assertEquals("Outer$Calc#run#owner=app.Outer$Calc#kinds=long#once", StaticDelegation.target(body(List.of(Ir.LONG), Ir.LONG, new Ir.Return(call))));
        Ir.Expression negated = new Ir.Unary("-", call, Ir.LONG);
        assertNull(StaticDelegation.target(body(List.of(Ir.LONG), Ir.LONG, new Ir.Return(negated))));
    }

    @Test
    void theJavaExceptionOfAPythonExceptionClassReachesThePythonCallerAsTheBodyAllows() {
        Ir.Statement write = new Ir.PutSelf("label", Ir.STRING, param(Ir.STRING), false);
        // only the compilation throws it: the rewritten function raises the Python exception it carries
        assertEquals("Outer$Calc#run#owner=app.Outer$Calc#kinds=str#once#python-exceptions",
            StaticDelegation.target(body("app.Outer$Calc", false, false, Ir.ExceptionCrossing.TRANSLATED, List.of(Ir.STRING), Ir.VOID, write)));
        // it may have crossed Java code, or come from the compilation: the Python callers keep the Python body
        assertNull(StaticDelegation.target(body("app.Outer$Calc", false, false, Ir.ExceptionCrossing.PYTHON_BODY, List.of(Ir.STRING), Ir.VOID, write)));
        // a static body is run again in Python when it fails: there is nothing to translate
        Ir.Statement returned = new Ir.Return(new Ir.Const(null, Ir.NONE));
        assertEquals("#run#static=app.Module#kinds=str#retry",
            StaticDelegation.target(body("app.Module", true, true, Ir.ExceptionCrossing.TRANSLATED, List.of(Ir.STRING), OBJECT, returned)));
    }

    @Test
    void aCollectionTheBodyBuildsBecomesAPythonOneForAPythonCaller() {
        // the lowering spells the return type with its type arguments
        Ir.Expression built = new Ir.Helper("list", List.of(new Ir.Const("a", Ir.STRING)), "java.util.List<java.lang.String>");
        assertEquals("#list", StaticDelegation.returnConversion(body(List.of(), "java.util.List<java.lang.String>", new Ir.Return(built))));
        Ir.Expression map = new Ir.Helper("putAll", List.of(new Ir.Helper("map", List.of(), "java.util.Map<java.lang.String, java.lang.Long>"), param("java.util.Map")),
            "java.util.Map<java.lang.String, java.lang.Long>");
        assertEquals("#dict", StaticDelegation.returnConversion(body(List.of(), "java.util.Map<java.lang.String, java.lang.Long>", new Ir.Return(map))));
        assertEquals("", StaticDelegation.returnConversion(body(List.of(), Ir.STRING, new Ir.Return(new Ir.Const("a", Ir.STRING)))));
    }

    @Test
    void aJavaCollectionReachesAPythonCallerAsItIs() {
        Ir.Expression call = new Ir.InvokeJava(null, "java.util.Collections", "emptySet", List.of(), List.of(), "java.util.Set<java.lang.String>");
        assertEquals("", StaticDelegation.returnConversion(body(List.of(), "java.util.Set<java.lang.String>", new Ir.Return(call))));
    }

    @Test
    void aCollectionOfPythonOriginIsNotDelegated() {
        // the Python body returns the attribute itself, which a Java body cannot give back
        Ir.Expression attribute = new Ir.SelfProperty("items", "java.util.List<java.lang.String>", false, false);
        assertNull(StaticDelegation.returnConversion(body(List.of(), "java.util.List<java.lang.String>", new Ir.Return(attribute))));
        Ir.Expression call = new Ir.InvokeJava(null, "java.util.Collections", "emptyList", List.of(), List.of(), "java.util.List<java.lang.String>");
        Ir.Statement mixed = new Ir.If(new Ir.Const(true, Ir.BOOLEAN), new Ir.Body(List.of(new Ir.Return(call))), null);
        assertNull(StaticDelegation.returnConversion(body(List.of(), "java.util.List<java.lang.String>", mixed, new Ir.Return(new Ir.Const(null, Ir.NONE)))));
    }

    @Test
    void theStatusSaysHowThePythonCallersRunTheBodyOrWhyTheyDoNot() {
        Ir.Expression sum = new Ir.Binary("+", param("int"), new Ir.Const(1L, Ir.LONG), Ir.LONG);
        Ir.Statement returned = new Ir.Return(new Ir.Cast(sum, "int"));
        assertEquals("retry", StaticDelegation.status(body(List.of("int"), "int", returned)));
        assertEquals("static", StaticDelegation.status(body("app.Module", true, true, List.of("int"), "int", returned)));
        Ir.Statement write = new Ir.PutSelf("label", Ir.STRING, param(Ir.STRING), false);
        assertEquals("once", StaticDelegation.status(body(List.of(Ir.STRING), Ir.VOID, write)));
        assertEquals("none(parameter [p0] is a [java.util.Map], which a Python argument does not reach unchanged)",
            StaticDelegation.status(body(List.of("java.util.Map"), OBJECT, new Ir.Return(new Ir.Const(null, Ir.NONE)))));
        Ir.Expression call = new Ir.InvokeJava(null, "java.lang.Math", "abs", List.of(Ir.LONG), List.of(param(Ir.LONG)), Ir.LONG);
        assertEquals("none(it has side effects (the call of [java.lang.Math.abs]) and the long negation raises where Python promotes)",
            StaticDelegation.status(body(List.of(Ir.LONG), Ir.LONG, new Ir.Return(new Ir.Unary("-", call, Ir.LONG)))));
        assertEquals("none(a static body with side effects (the call of [java.lang.Math.abs]) has no bound object to raise Python's exception through)",
            StaticDelegation.status(body("app.Module", true, true, List.of(Ir.LONG), Ir.LONG, new Ir.Return(call))));
        assertEquals("none(a Java exception of a Python exception class may come from the compilation or from Java code)",
            StaticDelegation.status(body("app.Outer$Calc", false, false, Ir.ExceptionCrossing.PYTHON_BODY, List.of(Ir.STRING), Ir.VOID, write)));
        Ir.Expression attribute = new Ir.SelfProperty("items", "java.util.List<java.lang.String>", false, false);
        assertEquals("none(a returned collection may be one the Python body returns as the same Python object)",
            StaticDelegation.status(body(List.of(), "java.util.List<java.lang.String>", new Ir.Return(attribute))));
    }

    @Test
    void aBridgedModuleFunctionIsAMethodOfTheModulesBeanAndNotDelegated() {
        Ir.Statement returned = new Ir.Return(new Ir.Const("a", Ir.STRING));
        assertNull(StaticDelegation.target(body("app.Module", false, true, List.of(), Ir.STRING, returned)));
        assertEquals("none(the module-level function is a method of the module's bean, which a Python caller does not reach)",
            StaticDelegation.status(body("app.Module", false, true, List.of(), Ir.STRING, returned)));
    }
}
