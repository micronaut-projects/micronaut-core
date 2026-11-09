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
package io.micronaut.dev.compile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.attribute.SourceDebugExtensionAttribute;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassDependencyIndexTest {

    @TempDir
    Path directory;

    @Test
    void aClassFileIsReadInAFewLargeReadsRatherThanOneForEachField() throws IOException {
        byte[] classFile = compile("example.Reader", """
            package example;
            public class Reader {
                public static final int LIMIT = 3;
                public java.util.List<String> names() { return java.util.List.of("a", "b"); }
                public Late late() { return new Late(); }
            }
            class Late { }
            """);
        CountingStream in = new CountingStream(new ByteArrayInputStream(classFile));

        ClassDependencyIndex.ClassInfo info = ClassDependencyIndex.readClassFile(in);

        assertTrue(info.references().contains("example.Late"));
        assertTrue(info.declaresConstants());
        // the index runs over every class file of the output on each compilation: one read of the file system
        // for each byte or short of a class file cost about a quarter of a second for a few hundred classes
        assertTrue(in.reads <= 2, "a class file of " + classFile.length + " bytes took " + in.reads + " reads");
    }

    @Test
    void aClassFileLargerThanTheBufferIsReadWhole() throws IOException {
        StringBuilder strings = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            strings.append("\"constant-number-").append(i).append("\", ");
        }
        byte[] classFile = compile("example.Large", """
            package example;
            public class Large {
                public Object[] strings() { return new Object[] { %s }; }
                public example.Later later() { return null; }
                public static final String LAST = "last";
            }
            class Later { }
            """.formatted(strings));
        assertTrue(classFile.length > 3 * 16 * 1024, "the constant pool spans several buffers: " + classFile.length);

        ClassDependencyIndex.ClassInfo info = ClassDependencyIndex.readClassFile(new ByteArrayInputStream(classFile));

        assertTrue(info.references().contains("example.Later"));
        assertTrue(info.declaresConstants(), "the constant field after the constant pool is seen");
    }

    @Test
    void theClassesWhoseCodeWasInlinedAreReferencesFromTheSourceDebugExtensionAttribute() throws IOException {
        byte[] compiled = compile("example.Caller", """
            package example;
            public class Caller {
                public static final int LIMIT = 3;
                public String name() { return "inlined"; }
            }
            """);
        // as kotlinc writes it for a call of an inline function of util/Inline.kt, without the annotation that repeats it
        String smap = """
            SMAP
            Caller.kt
            Kotlin
            *S Kotlin
            *F
            + 1 Caller.kt
            example/Caller
            + 2 Inline.kt
            example/util/InlineKt
            + 3 Wide.kt
            example/util/Wide\uD835\uDCB3Kt
            *L
            1#1,10:1
            3#2:11
            *E
            """;
        // the class file format writes the attribute in modified UTF-8, as it writes its constants
        ByteArrayOutputStream modifiedUtf8 = new ByteArrayOutputStream();
        new DataOutputStream(modifiedUtf8).writeUTF(smap);
        byte[] attribute = Arrays.copyOfRange(modifiedUtf8.toByteArray(), 2, modifiedUtf8.size());
        byte[] classFile = ClassFile.of().transformClass(ClassFile.of().parse(compiled),
            ClassTransform.endHandler(builder -> builder.with(SourceDebugExtensionAttribute.of(attribute))));

        ClassDependencyIndex.ClassInfo info = ClassDependencyIndex.readClassFile(new ByteArrayInputStream(classFile));

        assertTrue(info.references().contains("example.util.InlineKt"), "references " + info.references());
        assertTrue(info.references().contains("example.util.Wide\uD835\uDCB3Kt"), "references " + info.references());
        assertTrue(info.declaresConstants());
    }

    private byte[] compile(String className, String source) throws IOException {
        Path sources = Files.createDirectories(directory.resolve("src/example"));
        Path classes = Files.createDirectories(directory.resolve("classes"));
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        Path file = Files.writeString(sources.resolve(simpleName + ".java"), source);
        int status = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-d", classes.toString(), file.toString());
        assertEquals(0, status);
        return Files.readAllBytes(classes.resolve(className.replace('.', '/') + ".class"));
    }

    /**
     * Counts the reads that reach the stream underneath, as each would reach the file system.
     */
    private static final class CountingStream extends FilterInputStream {

        private int reads;

        CountingStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            reads++;
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            reads++;
            return super.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            reads++;
            return super.skip(n);
        }
    }
}
