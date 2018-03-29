/* Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.cli.interactive.completers;

import jline.console.completer.Completer;

import java.util.*;

import static jline.internal.Preconditions.checkNotNull;

/**
 * A completer that completes based on a collection of Strings
 *
 * @author Graeme Rocher
 * @since 3.0
 */
public class StringsCompleter
    implements Completer
{
    private SortedSet<String> strings = new TreeSet<String>();

    public StringsCompleter() {
        // empty
    }

    public StringsCompleter(final Collection<String> strings) {
        checkNotNull(strings);
        getStrings().addAll(strings);
    }

    public StringsCompleter(final String... strings) {
        this(Arrays.asList(strings));
    }

    public SortedSet<String> getStrings() {
        return strings;
    }


    public void setStrings(SortedSet<String> strings) {
        this.strings = strings;
    }

    public int complete(final String buffer, final int cursor, final List<CharSequence> candidates) {
        // buffer could be null
        checkNotNull(candidates);

        if (buffer == null) {
            candidates.addAll(getStrings());
        }
        else {
            for (String match : getStrings().tailSet(buffer)) {
                if (!match.startsWith(buffer)) {
                    break;
                }

                candidates.add(match);
            }
        }

        if (candidates.size() == 1) {
            candidates.set(0, candidates.get(0) + " ");
        }

        return candidates.isEmpty() ? -1 : 0;
    }
}