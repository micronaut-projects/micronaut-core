/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.inject.beans.visitor;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import org.objectweb.asm.ClassWriter;

/**
 * ClassWriter implementation that uses the visitor context for {@link #getCommonSuperClass(String, String)}.
 */
final class AptClassWriter extends ClassWriter {
    private final VisitorContext visitorContext;

    public AptClassWriter(int flags, VisitorContext visitorContext) {
        super(flags);
        this.visitorContext = visitorContext;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        // this is basically the same as the supermethod, just with Class.forName replaced

        ClassElement cl1 = loadClass(type1);
        ClassElement cl2 = loadClass(type1);
        if (cl2.isAssignable(cl1)) {
            return type1;
        }
        if (cl1.isAssignable(cl2)) {
            return type2;
        }
        if (cl1.isInterface() || cl2.isInterface()) {
            return "java/lang/Object";
        } else {
            do {
                // type2 should always be assignable to Object, the only type where this can be empty
                cl1 = cl1.getSuperType().orElseThrow();
            } while (!cl2.isAssignable(cl1));
            return cl1.getName().replace('.', '/');
        }
    }

    private ClassElement loadClass(String binaryName) {
        return visitorContext.getClassElement(binaryName.replace('/', '.'))
                .orElseThrow(() -> new TypeNotPresentException(binaryName, null));
    }
}
