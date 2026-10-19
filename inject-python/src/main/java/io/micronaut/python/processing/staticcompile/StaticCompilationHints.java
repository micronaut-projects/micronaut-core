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
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision.Reason;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What an author can change so that a function the planner refused is compiled: one suggestion
 * per reason rule, sharpened by the message of the reason where one rule covers several
 * situations. The report prints the suggestion next to every reason.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public final class StaticCompilationHints {

    private static final Map<String, String> HINTS = new LinkedHashMap<>();
    /**
     * The rules of the type checker, whose problems in a body are reasons of its decision too.
     */
    private static final Set<String> CHECKER_RULES = Set.of(
        "abstract-instantiation", "argument-type", "decorator-member-type", "decorator-positional", "decorator-target",
        "no-matching-overload", "not-an-annotation", "python-arity", "static-access", "unknown-attribute",
        "unknown-constructor", "unknown-decorator-member", "unknown-method", "unknown-python-member");
    private static final String CHECKER_HINT = "The type checker finds a problem in the body (a member, a constructor or an overload that does not exist, "
        + "or arguments that do not fit). Fix it as the message says: the function is compiled once the checker accepts it.";

    static {
        HINTS.put("class-not-eligible", "Static compilation needs an ordinary generated Java class, which a test module, an enum, a protocol, an interface, "
            + "a class served by a context pool or a class declaring __slots__ or __getattr__ does not have. Move the logic into a plain class or a module-level function.");
        HINTS.put("pooled-module", "The module declares no scope, so the runtime serves it from a pool of Python contexts and its generated class runs without one. "
            + "Keep the function to Java calls and the generated classes of the compilation, pass the bean it needs as a parameter, "
            + "or give the module a scope (a module-level @Singleton) so it is served as one object.");
        HINTS.put("static-method", "A @classmethod takes the class and is only bridged. Make it a @staticmethod, which compiles into the static Java method the stub declares, or an instance method.");
        HINTS.put("async-function", "An async function runs on the Python event loop and is never compiled. Move the synchronous work into a plain method the async function calls: that method compiles.");
        HINTS.put("generator-function", "A generator keeps its frame alive between calls. Build a list in a loop and return it instead of yielding.");
        HINTS.put("special-method", "Special methods run in Python: __init__ so that the instance keeps its Python semantics, the others because the runtime calls them. "
            + "Put the logic in a regular method the special method calls.");
        HINTS.put("abstract-method", "Nothing to change: an abstract method has no body. Its implementations are compiled on their own, and a call of it runs the implementation of the object.");
        HINTS.put("overriding-java-method", "Spell the Java signature in the hints: hint each parameter with the Java parameter type, or a subtype of it the body reads the parameter as, "
            + "and leave the return unhinted, hint it object, or hint a type the Java method returns.");
        HINTS.put("overridden-method", "Give the override the signature of the method it overrides (the same parameter and return types, in the same order), "
            + "so that its generated class overrides the Java method; or keep the method in Python: the bridge dispatches to the override on the Python object.");
        HINTS.put("intercepted-method", "An advised concrete method of an introduction class runs its interceptor chain on the Python object. "
            + "Move the logic into a separate bean the method calls: the bean's methods compile.");
        HINTS.put("varargs-signature", "Replace *args, **kwargs and keyword-only parameters with named positional parameters, each with a type hint; "
            + "give an optional parameter a default value instead of collecting it.");
        HINTS.put("unhinted-parameter", "Hint the parameter with a Java type, a standard type (int, float, str, bool, list[str], dict[str, int]) or a class of the compilation.");
        HINTS.put("unhinted-return", "The return hint names no Java type or class of the compilation. Hint a Java type, a standard type or a class of the compilation, "
            + "or remove the hint: an unhinted method returns Object.");
        HINTS.put("java-reserved-name", "Rename it: a Java keyword (class, default, new, switch, ...) or a method of Object (wait, notify, hashCode, ...) cannot be declared in Java.");
        HINTS.put("unknown-type", "Give the value a static type: hint the parameter or the attribute it comes from, assign the local values of one type only and on every path "
            + "before it is read, or replace the call of a third-party Python module with a Java call.");
        HINTS.put("unknown-self-attribute", "Declare the attribute: assign it in __init__ from a hinted parameter or a Java call, or declare it as a hinted class attribute; "
            + "hint the return of a property.");
        HINTS.put("dynamic-call", "Call a Java method, a method the generated class declares, or a module-level function that compiles. A callable held in a variable, "
            + "a function that is not compiled or is in a cycle of calls with this one, and **kwargs spread into a call cannot be dispatched statically.");
        HINTS.put("ambiguous-overload", "Make the argument types decide: hint the arguments or convert them (int(x), float(x), str(x)) so that exactly one overload fits best.");
        HINTS.put("kwargs-to-java", "Pass the arguments positionally, in the order the Java method, the constructor or the builtin declares them.");
        HINTS.put("sibling-call", "Call a method the generated class declares: a public instance method with a hinted signature that is neither async, a generator nor a class method. "
            + "A nested function, or a call of something the class does not declare, stays in Python.");
        HINTS.put("python-builtin-not-lowered", "Use a compiled builtin (len, int, float, bool, abs, min, max, list, set, tuple, str.strip/split/join/upper/lower/startswith/endswith/replace, "
            + "list.append/clear, set.add/clear, dict.get/keys/values) or the Java method of the value.");
        HINTS.put("unsupported-statement", "Rewrite with the compiled statements: if/elif/else, while, for over a range, an iterable or the items of a dict, try/except of Java exceptions "
            + "with finally, raise, return and assert. There is no with, match, del, global, nonlocal, yield, else clause of a loop or a try, unpacking or chained assignment.");
        HINTS.put("unsupported-expression", "Rewrite with the compiled expressions: arithmetic and comparisons, and/or/not, x if c else y, f-strings, list, tuple, set and dict literals, "
            + "indexing, membership, calls and comprehensions with one for clause. There is no lambda, nested function, slice, starred argument, chained comparison, "
            + "generator expression outside any/all or Optional hint.");
        HINTS.put("unbounded-integer-op", "Use a literal that fits a long (64 bits), a ** with a literal exponent, or float arithmetic when the result may exceed a long; "
            + "an overflow of +, - and * raises ArithmeticException.");
        HINTS.put("equality-on-object", "Compare identifying fields (a.id == b.id), use `is` for identity, or call a.equals(b) explicitly: == between two Java objects has no static lowering.");
        HINTS.put("truthiness-of-java-object", "Test the value explicitly, with x is not None, len(x) > 0, x.isEmpty() or a boolean method, instead of if x:.");
        HINTS.put("python-exception", "Raise and catch Java exceptions (from java.lang import IllegalArgumentException) or an exception class of the compilation "
            + "extending a builtin exception (Exception, ValueError, ...), defined at the top level of its module; replace a bare except with an except of the Java "
            + "exception or of the class raised.");
        CHECKER_RULES.forEach(rule -> HINTS.put(rule, CHECKER_HINT));
    }

    private StaticCompilationHints() {
    }

    /**
     * @return The rules a hint exists for, in the order the planner checks them
     */
    public static List<String> rules() {
        return List.copyOf(HINTS.keySet());
    }

    /**
     * @param reason A reason of a decision
     * @return What to change so that the reason goes away, or {@code null} for a rule without a hint
     */
    public static @Nullable String hint(Reason reason) {
        return hint(reason.rule(), reason.message());
    }

    /**
     * @param rule    The rule of a reason
     * @param message The message of the reason, which sharpens the hint of a rule covering several situations
     * @return What to change so that the reason goes away, or {@code null} for a rule without a hint
     */
    public static @Nullable String hint(String rule, @Nullable String message) {
        String text = message == null ? "" : message;
        switch (rule) {
            case "class-not-eligible":
                if (text.contains("test module")) {
                    return "The functions of a MicronautTest module are its tests and are never compiled. Move shared logic into a module of the application.";
                }
                break;
            case "unhinted-parameter":
                if (text.contains("resolves to no Java type")) {
                    return "The hint names no Java type or class of the compilation: import the Java type it should name, or hint a class of the compilation. "
                        + "A third-party Python class has no Java type.";
                }
                break;
            case "unknown-type":
                if (text.contains("Python module outside the compilation")) {
                    return "The value comes from a Python module outside the compilation (a third-party library or the standard library), whose members have no static type. "
                        + "Call a Java library instead, or keep this function in Python and compile the functions that call it.";
                }
                if (text.contains("has no return hint")) {
                    return "Hint the return of the property.";
                }
                if (text.contains("has no hinted type")) {
                    return "Hint the attribute: declare it as a hinted class attribute, or assign it in __init__ from a hinted parameter or a Java call.";
                }
                if (text.contains("may be rebound")) {
                    return "Bind the constant once, in a statement of the module body, and never rebind it (no global declaration of it in a function); "
                        + "a value that changes while the program runs is passed as a parameter or read from an attribute.";
                }
                if (text.contains("does not return a value on every path")) {
                    return "Return a value on every path: add a return at the end of the function, or to the branch that falls through.";
                }
                if (text.contains("as the Java method declares")) {
                    return "Hint the parameter with the Java type the method declares, or a subtype of it, as the message spells it.";
                }
                break;
            case "unknown-self-attribute":
                if (text.contains("has no return hint")) {
                    return "Hint the return of the property.";
                }
                break;
            case "dynamic-call":
                if (text.contains("cycle")) {
                    return "Break the cycle of calls between the functions, or keep one of them in Python.";
                }
                if (text.contains("may be rebound")) {
                    return "Define the function once, in the module body, and do not rebind its name (no second def, no f = wrap(f), no global declaration of it): "
                        + "a call of a name the running program may rebind stays in Python.";
                }
                if (text.contains("has a decorator")) {
                    return "A decorator may replace the function with a wrapper, which a compiled call would skip. Remove the decorator, "
                        + "or call a plain function the decorated one delegates to.";
                }
                if (text.contains("resolves to no Java field")) {
                    return "The attribute names no Java field or property of the class. Assign it in __init__ from a hinted parameter or a Java call.";
                }
                break;
            case "sibling-call":
                if (text.contains("with keyword arguments")) {
                    return "Pass the arguments positionally: the stub declares no Java method a call with keyword arguments fills, "
                        + "or give the method a hinted signature without defaults so that it declares one.";
                }
                if (text.contains("calling a Python class of the compilation")) {
                    return "Construct the class with its own name (Cart(...)) or call a method on an object of it: a class of the compilation held in a value is not called statically.";
                }
                if (text.contains("advised method")) {
                    return "Give the advised method a Java layout (named positional parameters, each hinted, no *args, **kwargs or keyword-only parameters), "
                        + "so that the call runs its interceptors through the generated class.";
                }
                if (text.contains("subclass of the compilation may override")) {
                    return "Call the static method on its class (Scaler.scale(n)) when the class's own one is meant, or keep the call in Python: "
                        + "self.scale(n) runs the override of a subclass.";
                }
                if (text.contains("class method")) {
                    return "Make the class method a @staticmethod or an instance method: a class method takes the class and is only bridged.";
                }
                if (text.contains("async or generator")) {
                    return "Call a plain synchronous method instead: an async or generator method runs in Python.";
                }
                break;
            case "unsupported-statement":
                if (text.contains("assigned on some paths only")) {
                    return "Assign the local before the branch, or on every path before it is read: Python raises UnboundLocalError on the path that skips the assignment.";
                }
                if (text.contains("frozen dataclass")) {
                    return "Create a new object instead of assigning an attribute of a frozen dataclass (dataclasses.replace in Python, or the constructor), "
                        + "or make the dataclass mutable.";
                }
                if (text.contains("already catches it")) {
                    return "Put the clause for the subclass before the clause for its base: Java refuses a catch that an earlier one already covers.";
                }
                if (text.contains("more than once")) {
                    return "Define the function once: rename one of the definitions.";
                }
                if (text.contains("ConcurrentModificationException")) {
                    return "Iterate over a copy (for x in list(xs)) when the body changes the collection, or collect the changes and apply them after the loop: "
                        + "Python goes on iterating a list the body changes, where the Java iterator fails.";
                }
                if (text.contains("checked exception")) {
                    return "Raise and catch unchecked Java exceptions, or catch a checked one only around the Java call that declares it: "
                        + "javac refuses a checked exception a body throws undeclared, and a catch of one its try block cannot throw.";
                }
                break;
            case "python-exception":
                if (text.contains("the Java call [")) {
                    return "Move the Java call out of the try block, or keep the method in Python: an exception of the class that crossed Java code "
                        + "(a Java method calling back into Python) is the generated Java class in Python, which the clause does not catch there.";
                }
                if (text.contains("inside an except or finally block") || text.contains("again outside the except clause")) {
                    return "Raise the exception after the try statement (note the failure in a local and raise when it is set), or raise the caught "
                        + "exception again only in its own except clause: Python chains the exception being handled as the context of the new one.";
                }
                if (text.contains("is a nested class")) {
                    return "Define the exception class at the top level of its module: the runtime finds the generated class of a Python exception "
                        + "by the name of its class, which a nested class does not give.";
                }
                if (text.contains("with keyword arguments") || text.contains("starred argument") || text.contains("only a str, an int, a float, a bool or None")) {
                    return "Pass the exception class str, int, float, bool or None values, positionally: a compiled raise hands them to the Python class "
                        + "as the values Python passes; build a message from anything else with an f-string.";
                }
                if (text.contains("would catch [")) {
                    return "Catch the exception classes of the compilation by name before a clause for RuntimeException, Exception or Throwable, "
                        + "or catch a narrower Java exception: the Java catch would also catch the Python exception classes, which Python lets through.";
                }
                if (text.contains("where Python raises an exception of its own")) {
                    return "Catch a narrower Java exception: a compiled body throws this one where Python raises its own exception "
                        + "(ZeroDivisionError, ValueError, ...), which the clause does not catch in Python.";
                }
                if (text.contains("a Python exception class")) {
                    return "Keep the value of a Python exception class in Python: pass or return its message or the data it carries instead, "
                        + "or use a Java exception for a value a compiled body hands on.";
                }
                if (text.contains("would not catch [")) {
                    return "Make the base the clause names the first base of the subclass, or catch the subclass by name too: "
                        + "the generated class of a subclass extends its first base only.";
                }
                break;
            case "python-builtin-not-lowered":
                if (text.contains("dict.get without a default")) {
                    return "Pass a default to dict.get (d.get(key, 0)), or hint the values of the dict as objects.";
                }
                break;
            case "unsupported-expression":
                if (text.contains("Optional")) {
                    return "Replace Optional[T] or T | None with T and test for None in the body.";
                }
                if (text.contains("type arguments")) {
                    return "Hint the return with the raw type: the generated class of a subclass extends the raw base.";
                }
                if (text.contains("a comprehension after an evaluation") || text.contains("evaluated lazily or repeatedly")) {
                    return "Assign the comprehension to a local in a statement of its own, before the statement that uses it, "
                        + "when evaluating it first gives the same result.";
                }
                if (text.contains("Python compares numbers across their types")) {
                    return "Give the keys or elements one type of number (hint the dict or set and convert with int() or float()), or use str keys: "
                        + "Python holds 1 == 1.0 == True in a dict or set, which Java tells apart.";
                }
                if (text.contains("boxes a Python number as an Integer or a Double")) {
                    return "Copy the Java collection of Longs, Shorts, Bytes or Floats into a Python one first, or keep the function in Python: "
                        + "a compiled body looks a Python int up as an Integer, which such a collection does not hold.";
                }
                if (text.contains("result of a call with None")) {
                    return "Hint the return of the method T | None when it may return None, or drop the test for None of a method that never returns it.";
                }
                if (text.contains("a Java method or field returns")) {
                    return "Pass the message through a str the body builds (an f-string, str(), a literal), or test the Java value for None first.";
                }
                break;
            default:
                break;
        }
        return HINTS.get(rule);
    }
}
