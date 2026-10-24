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

import io.micronaut.core.annotation.Internal;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.jspecify.annotations.Nullable;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.instruction.LineNumber;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Python positions of the Java a stub with compiled bodies is compiled to, so that a stack
 * trace or a debugger shows the Python file and line of a compiled statement rather than the
 * generated Java.
 *
 * <p>A stack trace element and the default stratum of a debugger take the file from the
 * {@code SourceFile} attribute of the class and the line from the {@code LineNumberTable} of the
 * method: a JSR-45 {@code SourceDebugExtension} is read by debuggers only. So the class files
 * compiled from such a stub are rewritten: {@code SourceFile} names the Python file and each line
 * of a compiled method is the Python line of the statement it was generated from, or the line of
 * the {@code def} for the code the stub adds around the statements. The lines of the other
 * methods of the stub, which bridge to Python, have no Python statement: they are dropped, and the
 * frames of those methods show the Python file without a line.
 *
 * <p>The positions travel in the generated Java source itself: the {@code Compiled from} javadoc
 * of a compiled method gives the file and the {@code def} line, and a {@code // Python line N}
 * comment the line of the statements that follow it, up to the next comment at the same or a
 * lower indentation. SourceGen writes no comments: the body generator writes a marker statement
 * ({@link #marker(int)}), which the stub generator turns into the comment once the source is
 * written ({@link #withLineComments(String)}). A compiler that writes the class files of the
 * stubs reads the source the class was compiled from ({@link #of(CharSequence)}) and rewrites
 * each class file ({@link #apply(byte[], String)}).
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
public final class StaticLineMap {

    private static final String MARKER_METHOD = "__mnPythonLine";
    private static final String COMPILED_FROM = "Compiled from ";
    private static final Pattern MARKER = Pattern.compile(
        "^([ \\t]*)(?:java\\.lang\\.)?Object\\." + MARKER_METHOD + "(\\d+)\\(\\);[ \\t]*$", Pattern.MULTILINE);
    private static final Pattern COMPILED_FROM_LINE = Pattern.compile("^\\s*\\* " + COMPILED_FROM + "(.+):(\\d+):(\\d+)\\s*$");
    private static final Pattern LINE_COMMENT = Pattern.compile("^(\\s*)// Python line (\\d+)\\s*$");
    // a resolver that knows no class: the class files are rewritten without computing stack maps,
    // which would need the class hierarchy of the compilation; when they cannot be kept, the class
    // file stays as javac wrote it
    private static final ClassFile CLASS_FILE = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(classDesc -> null));

    private final String sourceFile;
    private final int[] pythonLines;

    private StaticLineMap(String sourceFile, int[] pythonLines) {
        this.sourceFile = sourceFile;
        this.pythonLines = pythonLines;
    }

    /**
     * The statement marking the Java statements that follow as generated from a Python line, until
     * the next marker of the same block. It is replaced by a comment when the source is written.
     *
     * @param line The Python line
     * @return The marker statement
     */
    static StatementDef marker(int line) {
        return ClassTypeDef.of(Object.class).invokeStatic(MARKER_METHOD + line, TypeDef.VOID);
    }

    /**
     * @param javaSource The Java source SourceGen wrote for a stub
     * @return The source with each line marker replaced by its comment
     */
    public static String withLineComments(String javaSource) {
        if (!javaSource.contains(MARKER_METHOD)) {
            return javaSource;
        }
        return MARKER.matcher(javaSource).replaceAll("$1" + Matcher.quoteReplacement("// Python line ") + "$2");
    }

    /**
     * Reads the Python positions of a Java source.
     *
     * @param javaSource The Java source a class was compiled from
     * @return The positions, or {@code null} when the source has no compiled method or its
     * compiled methods come from more than one Python file
     */
    public static @Nullable StaticLineMap of(CharSequence javaSource) {
        String source = javaSource.toString();
        if (!source.contains(COMPILED_FROM)) {
            return null;
        }
        String[] javaLines = source.split("\n", -1);
        int[] pythonLines = new int[javaLines.length + 1];
        String file = null;
        String pendingPath = null;
        int pendingLine = 0;
        // the indentation of the compiled method being read, -1 outside one
        int methodIndent = -1;
        int defLine = 0;
        // the Python lines of the enclosing comments: indentation and line
        Deque<int[]> regions = new ArrayDeque<>();
        for (int i = 0; i < javaLines.length; i++) {
            String text = javaLines[i];
            if (methodIndent < 0) {
                Matcher compiledFrom = COMPILED_FROM_LINE.matcher(text);
                if (compiledFrom.matches()) {
                    pendingPath = compiledFrom.group(1);
                    pendingLine = Integer.parseInt(compiledFrom.group(2));
                } else if (pendingPath != null && "*/".equals(text.trim())) {
                    String name = fileName(pendingPath);
                    if (file == null) {
                        file = name;
                    } else if (!file.equals(name)) {
                        return null;
                    }
                    // the method declaration is one column left of the javadoc's closing */
                    methodIndent = Math.max(0, indent(text) - 1);
                    defLine = pendingLine;
                    pendingPath = null;
                    regions.clear();
                }
                continue;
            }
            Matcher comment = LINE_COMMENT.matcher(text);
            if (comment.matches()) {
                int indent = comment.group(1).length();
                while (!regions.isEmpty() && regions.peek()[0] >= indent) {
                    regions.pop();
                }
                regions.push(new int[] {indent, Integer.parseInt(comment.group(2))});
                continue;
            }
            if (text.isBlank()) {
                continue;
            }
            int indent = indent(text);
            while (!regions.isEmpty() && regions.peek()[0] > indent) {
                regions.pop();
            }
            pythonLines[i + 1] = regions.isEmpty() ? defLine : regions.peek()[1];
            if (indent == methodIndent && "}".equals(text.trim())) {
                methodIndent = -1;
                regions.clear();
            }
        }
        return file == null ? null : new StaticLineMap(file, pythonLines);
    }

    /**
     * @return The name of the Python file, as the {@code SourceFile} attribute gives it
     */
    public String sourceFile() {
        return sourceFile;
    }

    /**
     * @param javaLine A line of the Java source
     * @return The Python line, or 0 when the Java line is outside the compiled methods
     */
    public int pythonLine(int javaLine) {
        return javaLine > 0 && javaLine < pythonLines.length ? pythonLines[javaLine] : 0;
    }

    /**
     * Rewrites a class file compiled from the source to the Python positions. A class file whose
     * {@code SourceFile} attribute does not name the source (one compiled without debugging
     * information, or not compiled from it) is left as it is, and so is one that cannot be rewritten.
     *
     * @param classFile      The class file javac wrote
     * @param javaSourceFile The name of the Java source file the positions were read from
     * @return The rewritten class file, or the given one
     */
    public byte[] apply(byte[] classFile, String javaSourceFile) {
        try {
            ClassModel model = CLASS_FILE.parse(classFile);
            if (!model.findAttribute(Attributes.sourceFile()).map(attribute -> attribute.sourceFile().equalsString(javaSourceFile)).orElse(false)) {
                return classFile;
            }
            return CLASS_FILE.transformClass(model, (builder, element) -> {
                if (element instanceof SourceFileAttribute) {
                    builder.with(SourceFileAttribute.of(sourceFile));
                } else if (element instanceof MethodModel method && method.code().isPresent()) {
                    builder.transformMethod(method, MethodTransform.transformingCode(lines(method.code().get())));
                } else {
                    builder.with(element);
                }
            });
        } catch (RuntimeException e) {
            return classFile;
        }
    }

    /**
     * The lines of a method: each one its Python line. A method with no Python line loses its
     * lines; in a method with some, a line without one is given the method's first Python line,
     * since a dropped entry would attribute its instructions to the entry before it.
     */
    private CodeTransform lines(CodeModel code) {
        int first = 0;
        for (var element : code) {
            if (element instanceof LineNumber lineNumber && pythonLine(lineNumber.line()) > 0) {
                first = pythonLine(lineNumber.line());
                break;
            }
        }
        int fallback = first;
        return (builder, element) -> {
            if (element instanceof LineNumber lineNumber) {
                int line = pythonLine(lineNumber.line());
                if (line > 0 || fallback > 0) {
                    builder.lineNumber(line > 0 ? line : fallback);
                }
            } else {
                builder.with(element);
            }
        };
    }

    private static String fileName(String path) {
        int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return separator < 0 ? path : path.substring(separator + 1);
    }

    private static int indent(String text) {
        int indent = 0;
        while (indent < text.length() && (text.charAt(indent) == ' ' || text.charAt(indent) == '\t')) {
            indent++;
        }
        return indent;
    }
}
