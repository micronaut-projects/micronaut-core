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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Names the classes generated for evaluated expressions after what declares them: the class declaring the annotated
 * element, the element, the annotation and its member, for example {@code test.$Foo$Expr$field$name$Value$value$1a2b3c4d}.
 * The name does not depend on the other sources of the compilation or on the order elements are visited in, so the
 * compilation of a class and the compilation of a subclass that reads it from a class file give an expression the
 * same name, and a class compiled again keeps the names of its expression classes.
 *
 * <p>The element is described by the language, as segments separated by {@code $}, with the erased signature of a
 * method or constructor in parentheses: {@code type}, {@code field$name}, {@code method$name(java.lang.String,int)},
 * {@code constructor(int)$param0}. The signature only goes into the hash at the end of the name, which also covers
 * the annotation's package and the expression, so overloads, annotations of the same simple name and repeated
 * annotations give distinct names, and a class of a name holds the expression the name was given for.</p>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
public final class EvaluatedExpressionClassNames {

    /**
     * The longest simple name given, short enough for the file of the class on any file system.
     */
    static final int MAX_SIMPLE_NAME_LENGTH = 200;

    private static final String EXPR = EvaluatedExpressionReferenceCounter.EXPR_SUFFIX + '$';
    private static final Set<String> READ_FROM_CLASS_FILES = ConcurrentHashMap.newKeySet();

    private EvaluatedExpressionClassNames() {
    }

    /**
     * The name of the class of an evaluated expression.
     *
     * @param originatingClassName The binary name of the class declaring the annotated element
     * @param element The annotated element, as segments separated by {@code $} with the signature of a method in parentheses
     * @param annotationName The name of the annotation
     * @param memberName The annotation member
     * @param expression The expression, a string or an array of them
     * @return The binary name of the expression class
     */
    public static String name(String originatingClassName, String element, String annotationName, String memberName, Object expression) {
        String packageName = NameUtils.getPackageName(originatingClassName);
        String prefix = '$' + NameUtils.getSimpleName(originatingClassName) + EXPR;
        StringBuilder readable = new StringBuilder(element.length() + annotationName.length() + memberName.length());
        int depth = 0;
        for (int i = 0; i < element.length(); i++) {
            char c = element.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0) {
                readable.append(identifierPart(c));
            }
        }
        readable.append('$');
        appendIdentifier(readable, NameUtils.getSimpleName(annotationName));
        readable.append('$');
        appendIdentifier(readable, memberName);
        String hash = hash(originatingClassName + '#' + element + '@' + annotationName + '#' + memberName + '=' + expressionText(expression));
        int room = MAX_SIMPLE_NAME_LENGTH - prefix.length() - hash.length() - 1;
        String simpleName;
        if (room <= 0) {
            simpleName = prefix + hash;
        } else {
            if (readable.length() > room) {
                readable.setLength(room);
            }
            simpleName = prefix + readable + '$' + hash;
        }
        return packageName.isEmpty() ? simpleName : packageName + '.' + simpleName;
    }

    /**
     * Notes whether the element an expression class was named for was read from a class file, rather than compiled from
     * a source of the compilation: the compilation of its class wrote the expression class then, unless an older
     * version that numbered the expression classes did.
     *
     * @param expressionClassName The name of the expression class
     * @param readFromClassFile Whether the annotated element was read from a class file
     */
    public static void originatingElementRead(String expressionClassName, boolean readFromClassFile) {
        if (readFromClassFile) {
            READ_FROM_CLASS_FILES.add(expressionClassName);
        } else {
            READ_FROM_CLASS_FILES.remove(expressionClassName);
        }
    }

    /**
     * @param expressionClassName The name of an expression class
     * @return Whether the expression class was named, in this compilation, for an element read from a class file
     */
    public static boolean isReadFromClassFile(String expressionClassName) {
        return READ_FROM_CLASS_FILES.contains(expressionClassName);
    }

    /**
     * Forgets the elements noted, when a compilation finishes, as the cached annotation metadata is.
     */
    public static void reset() {
        READ_FROM_CLASS_FILES.clear();
    }

    private static String expressionText(Object expression) {
        if (expression instanceof Object[] array) {
            var text = new StringBuilder();
            for (Object o : array) {
                text.append(o).append('\0');
            }
            return text.toString();
        }
        return String.valueOf(expression);
    }

    private static void appendIdentifier(StringBuilder builder, String name) {
        for (int i = 0; i < name.length(); i++) {
            builder.append(identifierPart(name.charAt(i)));
        }
    }

    private static char identifierPart(char c) {
        return Character.isJavaIdentifierPart(c) && !Character.isIdentifierIgnorable(c) ? c : '_';
    }

    /**
     * A hash of 32 bits, as 8 hexadecimal digits, folded from the 64 bits of FNV-1a: the same in every JVM.
     *
     * @param text The text
     * @return The hash
     */
    private static String hash(String text) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            hash ^= c & 0xff;
            hash *= 0x100000001b3L;
            hash ^= c >>> 8;
            hash *= 0x100000001b3L;
        }
        int folded = (int) (hash ^ (hash >>> 32));
        String hex = Integer.toHexString(folded);
        return "00000000".substring(hex.length()) + hex;
    }
}
