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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            SourceSpan.at("app/calc.py", 3, 5), new StaticCompilationDecision.Stats(statements.length, 0, 0, 0), false, false, staticMethod, moduleLevel, exceptions, List.of(), false, null);
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
    void aStaticMethodOrAModuleFunctionCallsTheGeneratedClassAndRunsOnceWithSideEffects() {
        Ir.Expression sum = new Ir.Binary("+", param("int"), new Ir.Const(1L, Ir.LONG), Ir.LONG);
        Ir.Statement returned = new Ir.Return(new Ir.Cast(sum, "int"));
        assertEquals("Outer$Calc#run#static=app.Outer$Calc#kinds=int#retry",
            StaticDelegation.target(body("app.Outer$Calc", true, false, List.of("int"), "int", returned)));
        assertEquals("#run#static=app.Module#kinds=int#retry",
            StaticDelegation.target(body("app.Module", true, true, List.of("int"), "int", returned)));
        // a body with side effects runs once, its failures raised as Python's in the context of the caller
        Ir.Expression call = new Ir.InvokeJava(null, "java.lang.Math", "abs", List.of(Ir.LONG), List.of(param(Ir.LONG)), Ir.LONG);
        assertEquals("#run#static=app.Module#kinds=long#once", StaticDelegation.target(body("app.Module", true, true, List.of(Ir.LONG), Ir.LONG, new Ir.Return(call))));
        assertEquals("#run#static=app.Module#kinds=long#once#python-exceptions",
            StaticDelegation.target(body("app.Module", true, true, Ir.ExceptionCrossing.TRANSLATED, List.of(Ir.LONG), Ir.LONG, new Ir.Return(call))));
        // and none when it also has an operation that promotes
        assertNull(StaticDelegation.target(body("app.Module", true, true, List.of(Ir.LONG), Ir.LONG, new Ir.Return(new Ir.Unary("-", call, Ir.LONG)))));
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
        assertEquals("static-once", StaticDelegation.status(body("app.Module", true, true, List.of(Ir.LONG), Ir.LONG, new Ir.Return(call))));
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

    @Test
    void readingAnAttributeOfAnotherObjectOrAnInjectedBeanHasNoSideEffect() {
        Ir.Expression other = new Ir.SelfProperty("tag", "app.Tag", false, true);
        Ir.Expression name = new Ir.PythonMember(other, "name", Ir.STRING, false);
        assertEquals("retry", StaticDelegation.status(body(List.of(), Ir.STRING, new Ir.Return(name))));
        // a @property of the other object runs its getter
        Ir.Expression property = new Ir.PythonMember(other, "label", Ir.STRING, true);
        assertEquals("once", StaticDelegation.status(body(List.of(), Ir.STRING, new Ir.Return(property))));
        Ir.Expression bean = new Ir.ModuleAttribute("app.Module", "repository", "app.Repository");
        Ir.Expression call = new Ir.InvokeJava(bean, "app.Repository", "count", List.of(), List.of(), Ir.LONG);
        // the call is the side effect, the read of the bean none: nothing promotes, so the body runs once
        assertEquals("once", StaticDelegation.status(body(List.of(), Ir.LONG, new Ir.Return(call))));
    }

    @Test
    void aLocalTheBodyBuildsAndKeepsToItselfIsReturnedAsANewPythonCollection() {
        String type = "java.util.List<java.lang.String>";
        Ir.Statement built = new Ir.Local("xs", type, new Ir.Helper("list", List.of(param(Ir.STRING)), type));
        Ir.Expression xs = new Ir.LocalRef("xs", type);
        Ir.Statement appended = new Ir.Eval(new Ir.Helper("append", List.of(xs, param(Ir.STRING)), Ir.NONE));
        Ir.Statement sized = new Ir.Eval(new Ir.Helper("len", List.of(xs), Ir.LONG));
        assertEquals("#list", StaticDelegation.returnConversion(body(List.of(Ir.STRING), type, built, appended, sized, new Ir.Return(xs))));
        // held by the object as well: the Python body returns the list self.items holds
        Ir.Statement kept = new Ir.PutSelf("items", type, xs, false);
        assertNull(StaticDelegation.returnConversion(body(List.of(Ir.STRING), type, built, kept, new Ir.Return(xs))));
        // an alias of it, which the Python body may return elsewhere
        Ir.Statement alias = new Ir.Local("ys", type, xs);
        assertNull(StaticDelegation.returnConversion(body(List.of(Ir.STRING), type, built, alias, new Ir.Return(new Ir.LocalRef("ys", type)))));
        Ir.Statement passed = new Ir.Eval(new Ir.InvokeJava(null, "java.util.Collections", "shuffle", List.of("java.util.List"), List.of(xs), Ir.VOID));
        assertNull(StaticDelegation.returnConversion(body(List.of(Ir.STRING), type, built, passed, new Ir.Return(xs))));
        // assigned a collection of Python origin on another path
        Ir.Statement reassigned = new Ir.If(new Ir.Const(true, Ir.BOOLEAN),
            new Ir.Body(List.of(new Ir.Assign("xs", new Ir.SelfProperty("items", type, false, false)))), null);
        assertNull(StaticDelegation.returnConversion(body(List.of(Ir.STRING), type, built, reassigned, new Ir.Return(xs))));
    }

    @Test
    void theHelpersThatReadOrBuildHaveNoSideEffectAndIntPromotes() {
        Ir.Expression split = new Ir.Helper("split", List.of(param(Ir.STRING)), "java.util.List<java.lang.String>");
        assertEquals("retry", StaticDelegation.status(body(List.of(Ir.STRING), Ir.LONG, new Ir.Return(new Ir.Helper("len", List.of(split), Ir.LONG)))));
        Ir.Expression parsed = new Ir.Helper("toInt", List.of(param(Ir.STRING)), Ir.LONG);
        assertEquals("retry", StaticDelegation.status(body(List.of(Ir.STRING), Ir.LONG, new Ir.Return(parsed))));
        Ir.Statement write = new Ir.PutSelf("count", Ir.LONG, parsed, false);
        assertEquals("none(it has side effects (the write of self.count) and the helper [toInt] raises where Python promotes)",
            StaticDelegation.status(body(List.of(Ir.STRING), Ir.VOID, write)));
    }

    @Test
    void theImmutableValueClassesOfJavaUtilAreTakenAsJavaObjects() {
        Ir.Statement returned = new Ir.Return(new Ir.Const("a", Ir.STRING));
        assertEquals("Outer$Calc#run#owner=app.Outer$Calc#kinds=java:java.util.UUID,java:java.util.Locale,java:java.util.Currency#retry",
            StaticDelegation.target(body(List.of("java.util.UUID", "java.util.Locale", "java.util.Currency"), Ir.STRING, returned)));
        assertNull(StaticDelegation.target(body(List.of("java.util.Date"), Ir.STRING, returned)));
    }

    @Test
    void aPlainClassOfTheCompilationIsTakenAsThePythonObject() {
        Ir.CompiledBody plain = new Ir.CompiledBody("app.Greeter", "run", List.of("user"), List.of("app.User"), Ir.STRING,
            new Ir.Body(List.of(new Ir.Return(new Ir.Const("a", Ir.STRING)))), SourceSpan.at("app/calc.py", 3, 5),
            new StaticCompilationDecision.Stats(1, 0, 0, 0), false, false, false, false, Ir.ExceptionCrossing.UNTRANSLATED, List.of("User"), false, null);
        assertEquals("Greeter#run#owner=app.Greeter#kinds=python:User#retry", StaticDelegation.target(plain));
        // without the Python class, a Java object of the generated class only
        assertEquals("Outer$Calc#run#owner=app.Outer$Calc#kinds=java:app.User#retry",
            StaticDelegation.target(body(List.of("app.User"), Ir.STRING, new Ir.Return(new Ir.Const("a", Ir.STRING)))));
    }

    @Test
    void aNarrowingThatCannotOverflowOrThatAJavaCallTakesDoesNotPromote() {
        // an int parameter used as a long and narrowed back always fits
        Ir.Expression held = new Ir.Param("p0", Ir.LONG, "int");
        Ir.Expression call = new Ir.InvokeJava(null, "java.lang.Integer", "toHexString", List.of("int"), List.of(new Ir.Cast(held, "int")), Ir.STRING);
        assertEquals("once", StaticDelegation.status(body(List.of("int"), Ir.STRING, new Ir.Return(call))));
        assertEquals("retry", StaticDelegation.status(body(List.of("int"), "int", new Ir.Return(new Ir.Cast(held, "int")))));
        // a long given to an int parameter of a Java call: Python fails the call as well
        Ir.Expression given = new Ir.InvokeJava(null, "java.lang.Integer", "toHexString", List.of("int"), List.of(new Ir.Cast(param(Ir.LONG), "int")), Ir.STRING);
        assertEquals("once", StaticDelegation.status(body(List.of(Ir.LONG), Ir.STRING, new Ir.Return(given))));
        // a long returned as an int: Python returns the int whatever its size
        Ir.Expression count = new Ir.InvokeJava(null, "app.Repository", "count", List.of(), List.of(), Ir.LONG);
        assertEquals("none(it has side effects (the call of [app.Repository.count]) and the narrowing to [int] raises where Python promotes)",
            StaticDelegation.status(body(List.of(), "int", new Ir.Return(new Ir.Cast(count, "int")))));
    }

    @Test
    void aCallOfCodeOfTheCompilationThatMayOverflowPromotes() {
        Ir.Statement sent = new Ir.Eval(new Ir.InvokeSibling("send", List.of(Ir.STRING), List.of(param(Ir.STRING)), Ir.VOID, "java"));
        assertEquals("once", StaticDelegation.status(body(List.of(Ir.STRING), Ir.VOID, sent)));
        Ir.CompiledBody computing = new Ir.CompiledBody("app.Outer$Calc", "run", List.of("p0"), List.of(Ir.STRING), Ir.VOID,
            new Ir.Body(List.of(sent)), SourceSpan.at("app/calc.py", 3, 5), new StaticCompilationDecision.Stats(1, 0, 0, 0),
            false, false, false, false, Ir.ExceptionCrossing.UNTRANSLATED, List.of(), true, null);
        assertEquals("none(it has side effects (the call of [self.send]) and a call of code of the compilation raises where Python promotes)",
            StaticDelegation.status(computing));
        assertTrue(StaticDelegation.promotes(computing));
        // a change of a collection is a side effect, and no number it computes overflows
        Ir.Expression list = new Ir.Helper("list", List.of(), "java.util.List<java.lang.String>");
        Ir.Statement appended = new Ir.Eval(new Ir.Helper("append", List.of(list, param(Ir.STRING)), Ir.NONE));
        assertEquals("once", StaticDelegation.status(body(List.of(Ir.STRING), Ir.VOID, appended)));
    }

    @Test
    void aHelperFailingWithAJavaExceptionKeepsABodyWithSideEffectsInPython() {
        String type = "java.util.List<java.lang.String>";
        Ir.Expression first = new Ir.Helper("at", List.of(new Ir.SelfProperty("items", type, false, false), new Ir.Const(0L, Ir.LONG)), OBJECT);
        // run again in Python when it fails, so the caller sees the IndexError
        assertEquals("retry", StaticDelegation.status(body(List.of(), OBJECT, new Ir.Return(first))));
        Ir.Statement write = new Ir.PutSelf("last", OBJECT, first, false);
        assertEquals("none(it has side effects (the write of self.last) and the helper [at] fails with a Java exception where Python raises its own)",
            StaticDelegation.status(body(List.of(), Ir.VOID, write)));
        // a dict of str keys cannot fail on a key
        Ir.Expression keyed = new Ir.Helper("map", List.of(new Ir.Const("k", Ir.STRING), param(Ir.STRING)), "java.util.Map<java.lang.String,java.lang.String>");
        assertEquals("once", StaticDelegation.status(body(List.of(Ir.STRING), Ir.VOID, new Ir.PutSelf("last", OBJECT, keyed, false))));
        // a list added to a set: Python raises TypeError, Java a ClassCastException
        Ir.Expression set = new Ir.SelfProperty("seen", "java.util.Set<java.lang.Object>", false, false);
        Ir.Statement added = new Ir.Eval(new Ir.Helper("add", List.of(set, new Ir.Helper("list", List.of(), type)), Ir.NONE));
        assertEquals("none(it has side effects (the helper [add]) and the helper [add] fails with a Java exception where Python raises its own)",
            StaticDelegation.status(body(List.of(), Ir.VOID, added)));
        Ir.Statement addedText = new Ir.Eval(new Ir.Helper("add", List.of(set, param(Ir.STRING)), Ir.NONE));
        assertEquals("once", StaticDelegation.status(body(List.of(Ir.STRING), Ir.VOID, addedText)));
        // an attribute of a class serving its attributes itself runs code
        Ir.Expression dynamic = new Ir.PythonMember(new Ir.SelfProperty("tag", "app.Tag", false, true), "name", Ir.STRING, true);
        assertEquals("once", StaticDelegation.status(body(List.of(), Ir.STRING, new Ir.Return(dynamic))));
    }
}
