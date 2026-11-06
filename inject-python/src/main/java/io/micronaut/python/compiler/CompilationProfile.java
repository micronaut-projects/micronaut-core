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
package io.micronaut.python.compiler;

import io.micronaut.core.annotation.Internal;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The profile of one Python compilation: how long each phase of the compiler took, what it counted
 * on the way, and the inventory of what it wrote. Collected only when profiling is enabled (see
 * {@link PyronautCompiler.Builder#profileReportFile(java.io.File)}), and rendered as one block of
 * {@code key=value} lines, so that a report file holding several compilations can be compared
 * between revisions without a parser.
 *
 * @param attributes The attributes of the compilation: the JVM, the target directory, the number of
 *                   compilations of this JVM, ...
 * @param phases The phases in the order they started, with their total inclusive time and invocations
 * @param counters The counters, by name
 * @param artifacts The inventory of the output directory by kind, when the compilation wrote to disk
 * @since 5.3.0
 */
@Internal
public record CompilationProfile(
    Map<String, String> attributes,
    List<Phase> phases,
    Map<String, Long> counters,
    List<Artifact> artifacts
) {

    /**
     * The phase of the given name.
     *
     * @param name The name
     * @return The phase, or null when it did not run
     */
    public Phase phase(String name) {
        for (Phase phase : phases) {
            if (phase.name().equals(name)) {
                return phase;
            }
        }
        return null;
    }

    /**
     * The inventory of the given kind.
     *
     * @param kind The kind
     * @return The artifact, or null when nothing of the kind was written
     */
    public Artifact artifact(String kind) {
        for (Artifact artifact : artifacts) {
            if (artifact.kind().equals(kind)) {
                return artifact;
            }
        }
        return null;
    }

    /**
     * Renders the profile as a block of {@code key=value} lines, one compilation per block: the
     * attributes, then {@code phase.<name>.ms} (and {@code phase.<name>.count} when a phase ran more
     * than once) per phase in start order, then {@code counter.<name>} and
     * {@code artifact.<kind>.count} / {@code artifact.<kind>.bytes}.
     *
     * @return The rendered profile
     */
    public String render() {
        StringBuilder text = new StringBuilder("# Pyronaut compilation profile\n");
        attributes.forEach((name, value) -> text.append(name).append('=').append(value).append('\n'));
        for (Phase phase : phases) {
            text.append("phase.").append(phase.name()).append(".ms=")
                .append(String.format(Locale.ROOT, "%.1f", phase.millis())).append('\n');
            if (phase.invocations() != 1) {
                text.append("phase.").append(phase.name()).append(".count=").append(phase.invocations()).append('\n');
            }
        }
        counters.forEach((name, value) -> text.append("counter.").append(name).append('=').append(value).append('\n'));
        for (Artifact artifact : artifacts) {
            text.append("artifact.").append(artifact.kind()).append(".count=").append(artifact.count()).append('\n');
            text.append("artifact.").append(artifact.kind()).append(".bytes=").append(artifact.bytes()).append('\n');
        }
        return text.toString();
    }

    /**
     * A phase of the compilation.
     *
     * @param name The name, dotted by the component owning it ({@code javac.task}, {@code python.transform})
     * @param depth The nesting depth of the phase in the phase that was running when it started
     * @param nanos The total inclusive time of every invocation
     * @param invocations The number of invocations
     */
    public record Phase(String name, int depth, long nanos, int invocations) {

        /**
         * The total time in milliseconds.
         *
         * @return The total time in milliseconds
         */
        public double millis() {
            return nanos / 1_000_000.0;
        }
    }

    /**
     * The files of one kind in the output directory.
     *
     * @param kind The kind: {@code java-source}, {@code class}, {@code python-source}, {@code python-bytecode} or {@code resource}
     * @param count The number of files
     * @param bytes Their total size
     */
    public record Artifact(String kind, long count, long bytes) {
    }
}
