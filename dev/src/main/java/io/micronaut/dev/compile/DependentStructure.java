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

import io.micronaut.dev.change.ClassStructure;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;
import java.util.Arrays;
import java.util.List;

/**
 * Whether two versions of a class are the same to javac compiling a class that uses it: their {@link ClassStructure}
 * without the entries of the {@code InnerClasses} attribute about the nested classes of other classes. A method body
 * that concatenates strings or makes a lambda adds one for {@code MethodHandles.Lookup}, which says nothing about the
 * class itself. Its own nested classes, anonymous and local ones included, stay. The line numbers and local variables of
 * the static initializer, whose code the structure keeps, are left out too: a line added above a static field moves
 * them, and javac never reads them compiling a dependent. The code itself stays, so a changed static initializer is still
 * a change, as it is to the redefinition that {@link ClassStructure#bodyOnlyChange} decides, which the JVM would not
 * run again.
 *
 * @author graemerocher
 * @since 5.3.0
 */
final class DependentStructure {

    private static final ClassFile CLASS_FILE = ClassFile.of(ClassFile.ConstantPoolSharingOption.NEW_POOL);
    private static final String STATIC_INITIALIZER = "<clinit>";
    private static final ClassTransform WITHOUT_STATIC_INITIALIZER_DEBUG = ClassTransform.transformingMethodBodies(
        method -> method.methodName().equalsString(STATIC_INITIALIZER),
        (CodeTransform) (builder, element) -> {
            if (!(element instanceof LineNumber || element instanceof LocalVariable || element instanceof LocalVariableType)) {
                builder.with(element);
            }
        });

    private DependentStructure() {
    }

    /**
     * @param before A class file of the output
     * @param after The class file compiled again
     * @return True if a class that uses it compiles the same against either; false otherwise, or for bytes that are not class files
     */
    static boolean same(byte[] before, byte[] after) {
        if (Arrays.equals(before, after)) {
            return true;
        }
        try {
            return Arrays.equals(withoutForeignNestedClasses(ClassStructure.structure(before)), withoutForeignNestedClasses(ClassStructure.structure(after)));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] withoutForeignNestedClasses(byte[] structure) {
        ClassModel model = CLASS_FILE.parse(structure);
        String self = model.thisClass().asInternalName();
        String topLevel = topLevel(self);
        ClassTransform ownNestedClasses = (builder, element) -> {
            if (element instanceof InnerClassesAttribute innerClasses) {
                List<InnerClassInfo> own = innerClasses.classes().stream()
                    .filter(info -> topLevel(info.innerClass().asInternalName()).equals(topLevel))
                    .toList();
                if (!own.isEmpty()) {
                    builder.with(InnerClassesAttribute.of(own));
                }
            } else {
                builder.with(element);
            }
        };
        return CLASS_FILE.transformClass(model, ownNestedClasses.andThen(WITHOUT_STATIC_INITIALIZER_DEBUG));
    }

    /**
     * The top-level class of a nested class by its binary name, {@code a/B} for {@code a/B$C$1}: the classes of one
     * source and its nest.
     */
    private static String topLevel(String internalName) {
        int slash = internalName.lastIndexOf('/');
        int dollar = internalName.indexOf('$', slash + 1);
        return dollar > slash + 1 ? internalName.substring(0, dollar) : internalName;
    }
}
