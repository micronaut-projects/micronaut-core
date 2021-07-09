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

import com.sun.source.tree.ErroneousTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;

import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import java.io.File;
import static java.lang.Boolean.TRUE;


/**
 * Methods to parse Java source files.
 * NOTE: Forked from Google Compile Testing Project
 *
 **/
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
                    for (Tree node : nodes) {
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

}
