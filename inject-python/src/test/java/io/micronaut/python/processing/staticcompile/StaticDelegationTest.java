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
        List<String> names = IntStream.range(0, types.size()).mapToObj(i -> "p" + i).toList();
        return new Ir.CompiledBody("app.Outer$Calc", "run", names, types, returnType, new Ir.Body(List.of(statements)),
            SourceSpan.at("app/calc.py", 3, 5), new StaticCompilationDecision.Stats(statements.length, 0, 0, 0), false, false);
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
    void aJavaCallIsASideEffect() {
        Ir.Expression call = new Ir.InvokeJava(null, "java.lang.Math", "abs", List.of(Ir.LONG), List.of(param(Ir.LONG)), Ir.LONG);
        assertEquals("Outer$Calc#run#owner=app.Outer$Calc#kinds=long#once", StaticDelegation.target(body(List.of(Ir.LONG), Ir.LONG, new Ir.Return(call))));
        Ir.Expression negated = new Ir.Unary("-", call, Ir.LONG);
        assertNull(StaticDelegation.target(body(List.of(Ir.LONG), Ir.LONG, new Ir.Return(negated))));
    }
}
