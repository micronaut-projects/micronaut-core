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

    static {
        HINTS.put("class-not-eligible", "Static compilation needs an ordinary generated Java class, which a test module, an enum, a protocol, an interface, "
            + "a class served by a context pool or a class declaring __slots__ or __getattr__ does not have. Move the logic into a plain class or a module-level function.");
        HINTS.put("pooled-module", "The module has no module-level annotation, so the runtime serves it from a pool of Python contexts and its generated class runs without one. "
            + "Keep the function to Java calls and the generated classes of the compilation, pass the bean it needs as a parameter, "
            + "or give the module a module-level annotation so it is served as one object.");
        HINTS.put("static-method", "A @classmethod takes the class and is only bridged. Make it a @staticmethod, which compiles into the static Java method the stub declares, or an instance method.");
        HINTS.put("async-function", "An async function runs on the Python event loop and is never compiled. Move the synchronous work into a plain method the async function calls: that method compiles.");
        HINTS.put("generator-function", "A generator keeps its frame alive between calls. Build a list in a loop and return it instead of yielding.");
        HINTS.put("special-method", "Special methods run in Python: __init__ so that the instance keeps its Python semantics, the others because the runtime calls them. "
            + "Put the logic in a regular method the special method calls.");
        HINTS.put("abstract-method", "Nothing to change: an abstract method has no body. Its implementations are compiled on their own, and a call of it runs the implementation of the object.");
        HINTS.put("overriding-java-method", "Spell the Java signature in the hints: hint each parameter with the Java parameter type, or a subtype of it the body reads the parameter as, "
            + "and leave the return unhinted, hint it object, or hint a type the Java method returns.");
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
        HINTS.put("python-exception", "Raise and catch Java exceptions (from java.lang import IllegalArgumentException) or an exception class of the compilation extending Exception; "
            + "replace a bare except with an except of the Java exception or of the class raised.");
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
                if (text.contains("third-party")) {
                    return "The value comes from a third-party Python module, whose members have no static type. Call a Java library instead, "
                        + "or keep this function in Python and compile the functions that call it.";
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
                if (text.contains("resolves to no Java field")) {
                    return "The attribute names no Java field or property of the class. Assign it in __init__ from a hinted parameter or a Java call.";
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
                break;
            default:
                break;
        }
        return HINTS.get(rule);
    }
}
