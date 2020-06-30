/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.annotation.processing.test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.testing.compile.JavaFileObjects;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ErroneousTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.util.Collections;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.lang.Boolean.TRUE;


/** Methods to parse Java source files. */
@SuppressWarnings("all")
public final class Parser {

    private static final TreeScanner<Boolean, Boolean> HAS_ERRONEOUS_NODE =
            new TreeScanner<Boolean, Boolean>() {
                @Override
                public Boolean visitErroneous(ErroneousTree node, Boolean p) {
                    return true;
                }

                @Override
                public Boolean scan(Iterable<? extends Tree> nodes, Boolean p) {
                    for (Tree node : firstNonNull(nodes, Collections.<Tree>emptyList())) {
                        if (isTrue(scan(node, p))) {
                            return true;
                        }
                    }
                    return p;
                }

                @Override
                public Boolean scan(Tree tree, Boolean p) {
                    return isTrue(p) ? p : super.scan(tree, p);
                }

                @Override
                public Boolean reduce(Boolean r1, Boolean r2) {
                    return isTrue(r1) || isTrue(r2);
                }
            };

    /**
     * Returns {@code true} if the tree contains at least one {@linkplain ErroneousTree "erroneous"}
     * node.
     */
    public static boolean hasErrorNode(Tree tree) {
        return isTrue(HAS_ERRONEOUS_NODE.scan(tree, false));
    }

    /**
     * Parses {@code sources} into {@linkplain CompilationUnitTree compilation units}. This method
     * <b>does not</b> compile the sources.
     */
    public static Iterable<? extends Element> parse(JavaFileObject... sources) {
        JavaParser javaParser = new JavaParser();
        try {
            return javaParser.parse(sources);
        } finally {
            javaParser.close();
        }
    }

    /**
     * Parses {@code sources} into {@linkplain CompilationUnitTree compilation units}. This method
     * <b>does not</b> compile the sources.
     */
    public static Iterable<? extends Element> parseLines(String className, String... lines) {
        return parse(JavaFileObjects.forSourceLines(className.replace('.', File.separatorChar) + ".java", lines));
    }

    /**
     * Parses {@code sources} into {@linkplain CompilationUnitTree compilation units}. This method
     * <b>does not</b> compile the sources.
     */
    public static Iterable<? extends JavaFileObject> generate(String className, String code) {
        return generate(JavaFileObjects.forSourceString(className, code));
    }

    /**
     * Parses {@code sources} into {@linkplain CompilationUnitTree compilation units}. This method
     * <b>does not</b> compile the sources.
     *
     * @param sources The sources
     */
    public static Iterable<? extends JavaFileObject> generate(JavaFileObject... sources) {
        final JavaParser javaParser = new JavaParser();
        try {
            return javaParser.generate(sources);
        } finally {
            javaParser.close();
        }
    }

    private static boolean isTrue(Boolean p) {
        return TRUE.equals(p);
    }

    /**
     * The diagnostic, parse trees, and {@link Trees} instance for a parse task.
     *
     * <p>Note: It is possible for the {@link Trees} instance contained within a {@code ParseResult}
     * to be invalidated by a call to {@link com.sun.tools.javac.api.JavacTaskImpl#cleanup()}. Though
     * we do not currently expose the {@link com.sun.source.util.JavacTask} used to create a {@code ParseResult} to {@code
     * cleanup()} calls on its underlying implementation, this should be acknowledged as an
     * implementation detail that could cause unexpected behavior when making calls to methods in
     * {@link Trees}.
     */
    public static final class ParseResult {


        private final ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>>
                diagnostics;
        private final ImmutableList<? extends CompilationUnitTree> compilationUnits;
        private final Trees trees;

        ParseResult(
                ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>> diagnostics,
                Iterable<? extends CompilationUnitTree> compilationUnits,
                Trees trees) {
            this.trees = trees;
            this.compilationUnits = ImmutableList.copyOf(compilationUnits);
            this.diagnostics = diagnostics;
        }

        public ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>> diagnosticsByKind() {
            return diagnostics;
        }

        public Iterable<? extends CompilationUnitTree> compilationUnits() {
            return compilationUnits;
        }

        public Trees trees() {
            return trees;
        }

    }
}
