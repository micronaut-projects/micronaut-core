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
     * @param lines      The Python line each statement is lowered from, 0 for one lowered from none;
     *                   empty when no line is known. The generated Java maps its lines to them
     *                   (see {@link StaticLineMap})
     */
    record Body(List<Statement> statements, List<Integer> lines) implements Statement {
        public Body {
            statements = List.copyOf(statements);
            // the lowering passes Python ints, which a polyglot list may give as any Number
            List<?> given = lines;
            List<Integer> copied = new java.util.ArrayList<>(given.size());
            for (Object line : given) {
                copied.add(line instanceof Number number ? number.intValue() : 0);
            }
            if (!copied.isEmpty() && copied.size() != statements.size()) {
                throw new IllegalArgumentException("A body of " + statements.size() + " statements has " + copied.size() + " lines");
            }
            lines = List.copyOf(copied);
        }

        /**
         * A body whose statements have no known line.
         *
         * @param statements The statements
         */
        public Body(List<Statement> statements) {
            this(statements, List.of());
        }

        /**
         * @param index The index of a statement
         * @return The Python line of the statement, 0 when it is not known
         */
        public int line(int index) {
            return lines.isEmpty() ? 0 : lines.get(index);
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
     * @param fixed    Whether the attribute is fixed at construction: {@code __init__} assigns it and nothing
     *                 writes it later, so the stub reads it from the Python object once and from a Java field
     *                 afterwards (see {@code StaticPlanner.fixed_attribute})
     */
    record SelfProperty(String property, String type, boolean accessor, boolean fixed) implements Expression {
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
     * Dispatched {@code shared} for a compiled method of the class taking a list, set or dict: the
     * shared entry of its compiled body is called, which works on the caller's collections where the
     * stub's Java method would copy them, see {@link StaticBodyGenerator#generateShared}.
     *
     * @param name           The method name
     * @param parameterTypes The Java parameter types of the stub method, for a {@code java} or {@code shared} dispatch
     * @param arguments      The arguments
     * @param type           The Java return type, {@code void} for none
     * @param dispatch       {@code java}, {@code shared} or {@code python}
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
     * A call of a method of a Python object of the compilation through the Python object itself: the
     * generated class of the receiver declares no Java method for it (a method the class does not
     * bridge, a call relying on default arguments), so the method is invoked as Python code would.
     *
     * @param receiver  The receiver, a value of a generated class
     * @param name      The method name
     * @param arguments The arguments, boxed
     * @param type      The Java return type, {@code void} for none
     */
    record InvokePython(Expression receiver, String name, List<Expression> arguments, String type) implements Expression {
        public InvokePython {
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * A read of an attribute of a Python object of the compilation through the Python object: an
     * attribute the generated class of the receiver declares no accessor for.
     *
     * @param receiver The receiver, a value of a generated class
     * @param name     The attribute
     * @param type     The Java type of the value
     */
    record PythonMember(Expression receiver, String name, String type) implements Expression {
    }

    /**
     * A read of an injected attribute of a module: a bean the module's generated class holds in a
     * field of its instance, set when the bean is injected.
     *
     * @param owner The generated class of the module
     * @param name  The attribute
     * @param type  The Java type of the bean
     */
    record ModuleAttribute(String owner, String name, String type) implements Expression {
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
     * An exception of a Python exception class of the compilation, created as Python creates it:
     * the Python class called with the arguments, the Python exception wrapped in the generated Java
     * class through its {@code Value} constructor, as the runtime wraps a Python exception that
     * reaches Java code. Its Java value is the {@code RuntimeException} a {@code raise} throws.
     * <p>
     * A lazy exception is created as the Java exception alone, with the message
     * {@code BaseException.__str__} gives the arguments: its generated class creates the Python
     * exception from the class reference and the arguments the first time the Python object is
     * needed (it reaches Python, or an exception handler converts it). The planner makes an
     * exception lazy only when nothing of its class can tell the difference: neither the class
     * nor a Python base defines {@code __new__}, {@code __init__}, {@code __str__}, {@code __repr__}
     * or {@code args}, and its builtin base takes its arguments as {@code BaseException} does.
     *
     * @param type      The generated class, a RuntimeException
     * @param arguments The arguments of the Python class, boxed
     * @param lazy      Whether the Python exception is created when it is first needed
     */
    record NewPythonException(String type, List<Expression> arguments, boolean lazy) implements Expression {
        public NewPythonException {
            arguments = List.copyOf(arguments);
        }

        /**
         * An exception created through its Python class when it is raised.
         *
         * @param type      The generated class, a RuntimeException
         * @param arguments The arguments of the Python class, boxed
         */
        public NewPythonException(String type, List<Expression> arguments) {
            this(type, arguments, false);
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
     * @param advised        Whether the method is advised: the generated method runs the
     *                       interceptor chain the proxy binds before the body
     * @param staticMethod   Whether the body is generated as a static Java method: a static method
     *                       of a class, or a plain function of a module
     * @param moduleLevel    Whether the function is a module-level function, generated into the
     *                       module's class
     * @param exceptions     How the Java exception of a Python exception class the body throws reaches a
     *                       Python caller of the rewritten function
     */
    record CompiledBody(String className,
                        String methodName,
                        List<String> parameterNames,
                        List<String> parameterTypes,
                        String returnType,
                        Body body,
                        @Nullable SourceSpan span,
                        StaticCompilationDecision.Stats stats,
                        boolean wrapsCheckedExceptions,
                        boolean advised,
                        boolean staticMethod,
                        boolean moduleLevel,
                        ExceptionCrossing exceptions) {
        public CompiledBody {
            Objects.requireNonNull(className, "className");
            Objects.requireNonNull(exceptions, "exceptions");
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

    /**
     * How a Java exception carrying a Python exception (the generated Java class of a Python
     * exception class, see {@code ValueCoercible}) that a compiled body throws reaches a Python caller
     * of the rewritten function. In Python, a Python exception raised by Python code is the Python
     * exception, and one that crossed Java code (a Java method calling back into Python) is the Java
     * class: a compiled body cannot tell the two apart once both are the Java class.
     */
    enum ExceptionCrossing {
        /**
         * The body calls no Java code outside the compilation: any such exception comes from the
         * compilation (a compiled raise, Python code of the compilation), and the Python caller gets
         * the Python exception it carries.
         */
        TRANSLATED,
        /**
         * The body calls Java code outside the compilation and no code of the compilation: any such
         * exception crossed Java code, and the Python caller gets it as it is.
         */
        UNTRANSLATED,
        /**
         * The body calls Java code outside the compilation and code of the compilation that may raise
         * a Python exception class: the Python callers keep the Python body.
         */
        PYTHON_BODY
    }
}
