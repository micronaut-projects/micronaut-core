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
package io.micronaut.dev.change;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.SourceDebugExtensionAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.util.Arrays;

/**
 * Tells a change to method bodies from a change to a class's structure.
 *
 * <p>The structure of a class is the class file with every {@code Code} attribute dropped, and with
 * it the line numbers, local variables and stack maps inside, the bootstrap methods that only code
 * refers to, and the source file name. Both versions are written again with a fresh constant pool,
 * so that the constants a body change adds or reorders do not count, and compared byte for byte:
 * the members, their descriptors, the annotations, the constant values, the nest, the inner classes
 * and the signatures must all be the same, and so must the static initializer, which the JVM would
 * not run again. Anything else is a structural change.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class ClassStructure {

    private static final String STATIC_INITIALIZER = "<clinit>";
    private static final ClassFile CLASS_FILE = ClassFile.of(ClassFile.ConstantPoolSharingOption.NEW_POOL);
    private static final ClassTransform STRUCTURE_ONLY = ClassTransform
        // the bootstrap methods are not an element to drop: the writer emits them for the invokedynamic
        // constants the code refers to, and with the code gone there are none
        .dropping(element -> element instanceof SourceFileAttribute || element instanceof SourceDebugExtensionAttribute)
        // the static initializer is kept: the JVM does not run it again after a redefinition, so a change to it
        // is a change the running class would never see
        .andThen(ClassTransform.transformingMethods(method -> !method.methodName().equalsString(STATIC_INITIALIZER),
            MethodTransform.dropping(element -> element instanceof CodeAttribute)));

    private ClassStructure() {
    }

    /**
     * The structure of a class file: what remains once the method bodies are gone.
     *
     * @param classFile The class file
     * @return The structure, as class file bytes written with a fresh constant pool
     * @throws IllegalArgumentException if the bytes are not a class file
     */
    public static byte[] structure(byte[] classFile) {
        ClassModel model = CLASS_FILE.parse(classFile);
        return CLASS_FILE.transformClass(model, STRUCTURE_ONLY);
    }

    /**
     * Whether two versions of a class differ in their method bodies only, so that the JVM can
     * redefine the class in place.
     *
     * @param before The class file of the retired version
     * @param after The class file of the new version
     * @return True if the structure is the same and only bodies changed; false for a structural change, or bytes that are not class files
     */
    public static boolean bodyOnlyChange(byte[] before, byte[] after) {
        if (Arrays.equals(before, after)) {
            return true;
        }
        try {
            return Arrays.equals(structure(before), structure(after));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
