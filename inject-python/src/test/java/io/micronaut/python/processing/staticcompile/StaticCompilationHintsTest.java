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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticCompilationHintsTest {

    private static final Path PLANNER = Path.of("src/main/resources/GRAALPY-VFS/io.micronaut/micronaut-inject-python/src");

    @Test
    void everyRuleThePlannerGivesHasAHint() throws IOException {
        // the rules are the first argument of _refuse(...) in the lowering and the first element of
        // the reason tuples the planner appends
        Pattern rule = Pattern.compile("(?:_refuse\\(|reasons\\.append\\(\\(|reason = \\()\\s*\"([a-z][a-z0-9-]+)\"");
        TreeSet<String> rules = new TreeSet<>();
        for (String file : List.of("micronaut_static.py", "micronaut_lowering.py")) {
            Matcher matcher = rule.matcher(Files.readString(PLANNER.resolve(file)));
            while (matcher.find()) {
                rules.add(matcher.group(1));
            }
        }
        assertTrue(rules.size() > 20, rules.toString());
        for (String each : rules) {
            String hint = StaticCompilationHints.hint(each, "");
            assertTrue(hint != null && !hint.isBlank(), "no hint for [" + each + "]");
        }
        assertTrue(StaticCompilationHints.rules().containsAll(rules), rules.toString());
    }

    @Test
    void theMessageSharpensTheHintOfARuleCoveringSeveralSituations() {
        String general = StaticCompilationHints.hint("unknown-type", "the local [x] has no static type");
        String thirdParty = StaticCompilationHints.hint(new Reason("unknown-type", "[inflect] is a third-party Python module; its members have no static type", null));
        String paths = StaticCompilationHints.hint("unknown-type", "the function does not return a value on every path");
        assertNotEquals(general, thirdParty);
        assertNotEquals(general, paths);
        assertTrue(thirdParty.contains("third-party Python module"), thirdParty);
        assertTrue(paths.contains("Return a value on every path"), paths);
        assertEquals("Pass a default to dict.get (d.get(key, 0)), or hint the values of the dict as objects.",
            StaticCompilationHints.hint("python-builtin-not-lowered", "dict.get without a default may answer None, which a [long] cannot hold"));
        assertTrue(StaticCompilationHints.hint("unsupported-expression", "an Optional hint has no static lowering yet").startsWith("Replace Optional[T]"));
        assertTrue(StaticCompilationHints.hint("unsupported-expression", "a Lambda node has no static lowering").startsWith("Rewrite with the compiled expressions"));
    }

    @Test
    void anUnknownRuleHasNoHint() {
        assertNull(StaticCompilationHints.hint("no-such-rule", "whatever"));
        assertNull(StaticCompilationHints.hint("no-such-rule", null));
    }
}
