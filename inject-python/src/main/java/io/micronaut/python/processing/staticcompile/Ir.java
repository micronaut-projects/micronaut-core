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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.python.processing.model.SourceSpan;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The typed intermediate representation of a compiled function body: what the Python side lowers
 * a body into, and what the Java side turns into SourceGen statements. Types are Java type names
 * as {@code TypeDef.of} reads them ({@code long}, {@code double}, {@code boolean},
 * {@code java.lang.String}, a class name, {@code []} for arrays); a Python {@code int} is a
 * {@code long}, a {@code float} a {@code double}, {@code None} a {@code null}.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public sealed interface Ir {

    /**
     * The Java type name of {@code long}.
     */
    String LONG = "long";
    /**
     * The Java type name of {@code double}.
     */
    String DOUBLE = "double";
    /**
     * The Java type name of {@code boolean}.
     */
    String BOOLEAN = "boolean";
    /**
     * The Java type name of {@code String}.
     */
    String STRING = "java.lang.String";
    /**
     * The Java type name of {@code void}.
     */
    String VOID = "void";
    /**
     * The type of {@code None}, which fits any reference type.
     */
    String NONE = "none";

    /**
     * An expression, which has a type.
     */
    sealed interface Expression extends Ir {
        /**
         * @return The Java type of the value
         */
        String type();
    }

    /**
     * A statement.
     */
    sealed interface Statement extends Ir {
    }

    // ---------------------------------------------------------------- statements

    /**
     * A sequence of statements.
     *
     * @param statements The statements
     */
    record Body(List<Statement> statements) implements Statement {
        public Body {
            statements = List.copyOf(statements);
        }
    }

    /**
     * The declaration of a local with its first value.
     *
     * @param name  The name
     * @param type  The Java type of the local
     * @param value The value
     */
    record Local(String name, String type, Expression value) implements Statement {
    }

    /**
     * The assignment of a new value to a declared local.
     *
     * @param name  The name
     * @param value The value
     */
    record Assign(String name, Expression value) implements Statement {
    }

    /**
     * The assignment of a property of {@code self}.
     *
     * @param property The property
     * @param type     The Java type of the property
     * @param value    The value
     * @param accessor Whether the property is a Python {@code @property}, whose setter runs on the Python object
     */
    record PutSelf(String property, String type, Expression value, boolean accessor) implements Statement {
    }

    /**
     * A conditional statement.
     *
     * @param test   The condition, of type boolean
     * @param then   The statements run when the condition holds
     * @param orElse The statements run otherwise, or {@code null}
     */
    record If(Expression test, Body then, @Nullable Body orElse) implements Statement {
    }

    /**
     * A return.
     *
     * @param value The value, or {@code null} for a return without one
     */
    record Return(@Nullable Expression value) implements Statement {
    }

    /**
     * A loop over a condition.
     *
     * @param loop        The number of the loop, which its breaks and continues name
     * @param test        The condition, of type boolean
     * @param body        The statements run while the condition holds
     * @param hasBreak    Whether the body breaks out of this loop
     * @param hasContinue Whether the body continues this loop
     */
    record While(int loop, Expression test, Body body, boolean hasBreak, boolean hasContinue) implements Statement {
    }

    /**
     * A loop over a range of longs, as Python's {@code for x in range(start, stop, step)}.
     *
     * @param loop        The number of the loop
     * @param variable    The loop variable, a long declared by the loop
     * @param start       The first value
     * @param stop        The bound
     * @param step        The step; zero raises as Python raises ValueError
     * @param body        The statements run per value
     * @param hasBreak    Whether the body breaks out of this loop
     * @param hasContinue Whether the body continues this loop
     */
    record ForRange(int loop, String variable, Expression start, Expression stop, Expression step, Body body, boolean hasBreak, boolean hasContinue) implements Statement {
    }

    /**
     * A loop over the elements of a Java {@code Iterable}.
     *
     * @param loop        The number of the loop
     * @param variable    The loop variable, declared by the loop at {@code type}
     * @param type        The Java type the elements are used at
     * @param elementType The Java type the elements have, which the loop converts to {@code type}
     * @param iterable    The iterable
     * @param body        The statements run per element
     * @param hasBreak    Whether the body breaks out of this loop
     * @param hasContinue Whether the body continues this loop
     */
    record ForEach(int loop, String variable, String type, String elementType, Expression iterable, Body body, boolean hasBreak, boolean hasContinue) implements Statement {
    }

    /**
     * A break out of a loop.
     *
     * @param loop The number of the loop
     */
    record Break(int loop) implements Statement {
    }

    /**
     * A continue of a loop.
     *
     * @param loop The number of the loop
     */
    record Continue(int loop) implements Statement {
    }

    /**
     * A throw of a Java exception.
     *
     * @param exception The exception, a Throwable
     */
    record Throw(Expression exception) implements Statement {
    }

    /**
     * A try statement with typed catches.
     *
     * @param body           The statements tried
     * @param catches        The handlers, in order
     * @param finallyBody    The statements always run, or {@code null}
     */
    record Try(Body body, List<Catch> catches, @Nullable Body finallyBody) implements Statement {
        public Try {
            catches = List.copyOf(catches);
        }
    }

    /**
     * A handler of a try statement.
     *
     * @param type     The Java exception type caught
     * @param variable The name the exception is bound to, or {@code null}
     * @param body     The statements run
     */
    record Catch(String type, @Nullable String variable, Body body) {
    }

    /**
     * An expression evaluated for its effect.
     *
     * @param expression The expression
     */
    record Eval(Expression expression) implements Statement {
    }

    // ---------------------------------------------------------------- expressions

    /**
     * A literal.
     *
     * @param value The value: a Long, a Double, a Boolean, a String, or {@code null} for None
     * @param type  The Java type
     */
    record Const(@Nullable Object value, String type) implements Expression {
    }

    /**
     * A parameter of the compiled method.
     *
     * @param name          The name
     * @param type          The Java type the value is used at
     * @param parameterType The Java type of the method parameter, which may be narrower (an int
     *                      parameter is used as a long)
     */
    record Param(String name, String type, String parameterType) implements Expression {
    }

    /**
     * A declared local.
     *
     * @param name The name
     * @param type The Java type
     */
    record LocalRef(String name, String type) implements Expression {
    }

    /**
     * A property of {@code self}.
     *
     * @param property The property
     * @param type     The Java type of the property
     * @param accessor Whether the property is a Python {@code @property}, whose getter runs on the Python object
     */
    record SelfProperty(String property, String type, boolean accessor) implements Expression {
    }

    /**
     * A call of a Java method with a chosen overload.
     *
     * @param receiver       The receiver, or {@code null} for a static call
     * @param owner          The class declaring the method
     * @param name           The method
     * @param parameterTypes The parameter types of the chosen overload
     * @param arguments      The arguments, one per parameter
     * @param type           The return type
     */
    record InvokeJava(@Nullable Expression receiver,
                      String owner,
                      String name,
                      List<String> parameterTypes,
                      List<Expression> arguments,
                      String type) implements Expression {
        public InvokeJava {
            parameterTypes = List.copyOf(parameterTypes);
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * A call of a method of the class on {@code self}. Dispatched {@code java} when the stub declares
     * the method (a bridged public method, compiled or not): the stub's Java method is called.
     * Dispatched {@code python} otherwise (a method the stub does not bridge, an advised method whose
     * interceptor chain runs on the Python object, a method a subclass overrides, a call relying on
     * default arguments): the method of the Python object is invoked, as the Python code would.
     *
     * @param name           The method name
     * @param parameterTypes The Java parameter types of the stub method, for a {@code java} dispatch
     * @param arguments      The arguments
     * @param type           The Java return type, {@code void} for none
     * @param dispatch       {@code java} or {@code python}
     */
    record InvokeSibling(String name,
                         List<String> parameterTypes,
                         List<Expression> arguments,
                         String type,
                         String dispatch) implements Expression {
        public InvokeSibling {
            parameterTypes = List.copyOf(parameterTypes);
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * A construction of a Java object with a chosen constructor.
     *
     * @param type           The class
     * @param parameterTypes The parameter types of the chosen constructor
     * @param arguments      The arguments, one per parameter
     */
    record NewJava(String type, List<String> parameterTypes, List<Expression> arguments) implements Expression {
        public NewJava {
            parameterTypes = List.copyOf(parameterTypes);
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * A read of a static field or an enum constant.
     *
     * @param owner The class
     * @param name  The field
     * @param type  The type of the field
     */
    record StaticField(String owner, String name, String type) implements Expression {
    }

    /**
     * A read of an instance field.
     *
     * @param receiver The receiver
     * @param name     The field
     * @param type     The type of the field
     */
    record Field(Expression receiver, String name, String type) implements Expression {
    }

    /**
     * A binary operation on numbers or strings.
     *
     * @param op    The operator: {@code +}, {@code -}, {@code *}, {@code /}, {@code //}, {@code %},
     *              {@code **}, or {@code concat} for strings
     * @param left  The left operand
     * @param right The right operand
     * @param type  The result type
     */
    record Binary(String op, Expression left, Expression right, String type) implements Expression {
    }

    /**
     * A unary operation.
     *
     * @param op      The operator: {@code -} or {@code not}
     * @param operand The operand
     * @param type    The result type
     */
    record Unary(String op, Expression operand, String type) implements Expression {
    }

    /**
     * A comparison, of type boolean.
     *
     * @param op    The operator: {@code <}, {@code <=}, {@code >}, {@code >=}, {@code ==},
     *              {@code !=}, {@code is None}, {@code is not None}, {@code equals}, {@code !equals}
     * @param left  The left operand
     * @param right The right operand, or {@code null} for a test against None
     */
    record Compare(String op, Expression left, @Nullable Expression right) implements Expression {
        @Override
        public String type() {
            return BOOLEAN;
        }
    }

    /**
     * A short-circuit conjunction of booleans.
     *
     * @param left  The left operand
     * @param right The right operand
     */
    record And(Expression left, Expression right) implements Expression {
        @Override
        public String type() {
            return BOOLEAN;
        }
    }

    /**
     * A short-circuit disjunction of booleans.
     *
     * @param left  The left operand
     * @param right The right operand
     */
    record Or(Expression left, Expression right) implements Expression {
        @Override
        public String type() {
            return BOOLEAN;
        }
    }

    /**
     * A conditional expression whose branches share a type.
     *
     * @param test   The condition, of type boolean
     * @param then   The value when the condition holds
     * @param orElse The value otherwise
     * @param type   The type of both branches
     */
    record Conditional(Expression test, Expression then, Expression orElse, String type) implements Expression {
    }

    /**
     * The Python truthiness of a value, of type boolean.
     *
     * @param operand The value
     */
    record Truthy(Expression operand) implements Expression {
        @Override
        public String type() {
            return BOOLEAN;
        }
    }

    /**
     * The concatenation of the Python string forms of values: an f-string, or {@code str(x)}.
     *
     * @param parts The parts, each rendered as Python renders it
     */
    record StrJoin(List<Expression> parts) implements Expression {
        public StrJoin {
            parts = List.copyOf(parts);
        }

        @Override
        public String type() {
            return STRING;
        }
    }

    /**
     * A call of a runtime helper reproducing Python semantics ({@code PythonStatic.<name>}).
     *
     * @param name      The helper
     * @param arguments The arguments
     * @param type      The return type
     */
    record Helper(String name, List<Expression> arguments, String type) implements Expression {
        public Helper {
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * A conversion between Java types: a widening or narrowing of numbers, or a cast of references.
     *
     * @param operand The value
     * @param type    The target type
     */
    record Cast(Expression operand, String type) implements Expression {
    }

    /**
     * A compiled function body with the Java signature it is generated into.
     *
     * @param className      The generated class the method belongs to
     * @param methodName     The method
     * @param parameterNames The parameter names, the receiver excluded
     * @param parameterTypes The Java types of the parameters as the stub declares them
     * @param returnType     The Java return type as the stub declares it
     * @param body           The body
     * @param span           Where the Python function is declared
     * @param stats          What the body contains
     * @param wrapsCheckedExceptions Whether a call of the body declares a checked exception, which the
     *                       generated method rethrows unchecked as the bridge would
     */
    record CompiledBody(String className,
                        String methodName,
                        List<String> parameterNames,
                        List<String> parameterTypes,
                        String returnType,
                        Body body,
                        @Nullable SourceSpan span,
                        StaticCompilationDecision.Stats stats,
                        boolean wrapsCheckedExceptions) {
        public CompiledBody {
            Objects.requireNonNull(className, "className");
            Objects.requireNonNull(methodName, "methodName");
            parameterNames = List.copyOf(parameterNames);
            parameterTypes = List.copyOf(parameterTypes);
        }

        /**
         * @return The key of the body: the class and the method
         */
        public String key() {
            return key(className, methodName);
        }

        /**
         * @param className  The class
         * @param methodName The method
         * @return The key of a body
         */
        public static String key(String className, String methodName) {
            return className + "#" + methodName;
        }
    }
}
