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

import io.micronaut.python.processing.staticcompile.StaticCompilationDecision.Reason;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class StaticCompilationHintsTest {

    /**
     * The Python sources of the planner, read from the class path (the resources of the processor) whatever the working directory.
     */
    private static final String PLANNER = "GRAALPY-VFS/io.micronaut/micronaut-inject-python/src/";

    @Test
    void everyRuleThePlannerGivesHasAHint() throws IOException {
        // the rules are the first argument of _refuse(...) in the lowering, the first element of the
        // reason tuples the planner appends, and the rules of the problems the type checker reports in a
        // body, which the planner takes as the reasons of the decision (rules.problems)
        Pattern refusal = Pattern.compile("(?:_refuse\\(|_record\\(|reasons\\.append\\(\\(|reasons = \\[\\(|reason = \\()\\s*f?\"([^\"]+)\"");
        Pattern problem = Pattern.compile("_report\\(\\s*f?\"([^\"]+)\"");
        TreeSet<String> rules = new TreeSet<>();
        collect(refusal, "micronaut_static.py", rules);
        collect(refusal, "micronaut_lowering.py", rules);
        collect(problem, "micronaut_typecheck.py", rules);
        assertTrue(rules.size() > 30, rules.toString());
        // a rule given through a variable or an f-string would escape the literal match above
        for (String file : List.of("micronaut_static.py", "micronaut_lowering.py")) {
            String source = source(file);
            // the lowering records a reason with _record too, the planner records a decision with its own _record
            String calls = file.equals("micronaut_lowering.py") ? "self\\._(?:refuse|record)\\(\\s*(?!\"|rule, message)" : "self\\._refuse\\(\\s*(?!\")";
            Matcher dynamic = Pattern.compile(calls).matcher(source);
            if (dynamic.find()) {
                fail(file + " gives a rule that is not a literal: " + source.substring(dynamic.start(), Math.min(source.length(), dynamic.start() + 80)));
            }
        }
        for (String each : rules) {
            String hint = StaticCompilationHints.hint(each, "");
            assertTrue(hint != null && !hint.isBlank(), "no hint for [" + each + "]");
        }
        assertTrue(StaticCompilationHints.rules().containsAll(rules), rules.toString());
    }

    private static void collect(Pattern pattern, String file, Set<String> rules) throws IOException {
        Matcher matcher = pattern.matcher(source(file));
        while (matcher.find()) {
            String rule = matcher.group(1);
            assertTrue(rule.matches("[a-z][a-z0-9-]+"), "the rule [" + rule + "] of " + file + " is no rule name");
            rules.add(rule);
        }
    }

    private static String source(String file) throws IOException {
        try (InputStream stream = StaticCompilationHintsTest.class.getClassLoader().getResourceAsStream(PLANNER + file)) {
            assertNotNull(stream, PLANNER + file);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theMessageSharpensTheHintOfARuleCoveringSeveralSituations() {
        // the messages the lowering gives; test_micronaut_static.HintTest checks the planner gives them
        String general = StaticCompilationHints.hint("unknown-type", "the local [x] has no static type");
        String thirdParty = StaticCompilationHints.hint(new Reason("unknown-type", "[inflect] is a Python module outside the compilation (a third-party library or the standard library); its members have no static type", null));
        String paths = StaticCompilationHints.hint("unknown-type", "the function does not return a value on every path");
        assertNotEquals(general, thirdParty);
        assertNotEquals(general, paths);
        assertTrue(thirdParty.contains("outside the compilation"), thirdParty);
        String keywords = StaticCompilationHints.hint("sibling-call", "calling [_helper] with keyword arguments has no static lowering: the stub declares no Java method the call fills");
        String construction = StaticCompilationHints.hint("sibling-call", "calling a Python class of the compilation has no static lowering yet");
        String advised = StaticCompilationHints.hint("sibling-call", "the advised method [log] has no Java layout: Python calls it on self directly, without its interceptors");
        assertEquals(3, Set.of(keywords, construction, advised).size());
        assertNotEquals(StaticCompilationHints.hint("sibling-call", ""), keywords);
        assertEquals(StaticCompilationHints.hint("python-arity", ""), StaticCompilationHints.hint("unknown-method", ""), "the rules of the type checker share a hint");
        assertTrue(paths.contains("Return a value on every path"), paths);
        assertEquals("Pass a default to dict.get (d.get(key, 0)), or hint the values of the dict as objects.",
            StaticCompilationHints.hint("python-builtin-not-lowered", "dict.get without a default may answer None, which a [long] cannot hold"));
        assertEquals("Write the hint as T | None instead of Optional[T].", StaticCompilationHints.hint("unsupported-expression", "an Optional hint has no static lowering yet"));
        assertTrue(StaticCompilationHints.hint("unsupported-expression", "a Lambda node has no static lowering").startsWith("Rewrite with the compiled expressions"));
        // the refusals of the Java iterators and of Python's numeric equality in dicts and sets
        assertTrue(StaticCompilationHints.hint("unsupported-statement", "the loop body may change the [xs] it iterates, which Python goes on iterating while the Java iterator throws ConcurrentModificationException")
            .startsWith("Iterate over a copy"));
        String numbers = StaticCompilationHints.hint("unsupported-expression", "looking up a key of a [java.lang.Long] among [java.lang.Object] values has no static lowering: Python compares numbers across their types");
        assertTrue(numbers.startsWith("Give the keys or elements one type of number"), numbers);
        assertEquals(numbers, StaticCompilationHints.hint("unsupported-expression", "a membership test of a [java.lang.Long] among [java.lang.Double] values has no static lowering: Python compares numbers across their types"));
        assertTrue(StaticCompilationHints.hint("python-exception", "the except clause for [java.lang.ArithmeticException] catches the [java.lang.ArithmeticException] a compiled body throws where Python raises an exception of its own")
            .startsWith("Catch a narrower Java exception"));
        assertTrue(StaticCompilationHints.hint("unsupported-statement", "the except clause for the checked exception [java.io.IOException] has no Java call of the try block that declares it")
            .startsWith("Raise and catch unchecked Java exceptions"));
        assertTrue(StaticCompilationHints.hint("python-exception", "parameter [error] is a [Rejected], a Python exception class; a value of it has no Java layout")
            .startsWith("Keep the value of a Python exception class in Python"));
        // the refusals of the compiled raise and catch of the exception classes of the compilation
        assertTrue(StaticCompilationHints.hint("python-exception", "an except clause for [Rejected] would catch the [app.Rejected] the Java call [java.util.Objects.nonNull] of the try block throws, which Python lets through: a Python exception that crossed Java code is the Java class in Python")
            .startsWith("Move the Java call out of the try block"));
        String context = StaticCompilationHints.hint("python-exception", "raising [Coded] inside an except or finally block has no static lowering: Python chains the exception being handled as its context");
        assertTrue(context.startsWith("Raise the exception after the try statement"), context);
        assertEquals(context, StaticCompilationHints.hint("python-exception", "raising [rejected] again outside the except clause that caught it has no static lowering: Python would chain the exception handled there as its context"));
        assertTrue(StaticCompilationHints.hint("python-exception", "[Outer.Nested] is a nested class: the runtime does not wrap a Python exception of it in its generated class")
            .startsWith("Define the exception class at the top level"));
        String arguments = StaticCompilationHints.hint("python-exception", "constructing [Rejected] with a [java.util.List] has no static lowering: only a str, an int, a float, a bool or None reaches the Python class as the value Python passes");
        assertTrue(arguments.startsWith("Pass the exception class str, int, float, bool or None values"), arguments);
        assertEquals(arguments, StaticCompilationHints.hint("python-exception", "constructing [Coded] with keyword arguments has no static lowering: a compiled raise calls the Python class with positional arguments"));
        assertTrue(StaticCompilationHints.hint("python-exception", "raising a [Rejected] that the raise statement does not construct, nor an except clause around it catch, has no static lowering: a value of a Python exception class has no Java layout")
            .startsWith("Keep the value of a Python exception class in Python"));
        assertTrue(StaticCompilationHints.hint("overridden-method", "[IntHolder] overrides the method with a signature its generated class cannot declare in Java; the bridge of this class dispatches to the Python object for both")
            .startsWith("Give the override the signature"));
    }

    @Test
    void anUnknownRuleHasNoHint() {
        assertNull(StaticCompilationHints.hint("no-such-rule", "whatever"));
        assertNull(StaticCompilationHints.hint("no-such-rule", null));
    }
}
