/*
 * Copyright 2017-2011 original authors
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
package io.micronaut.ast.groovy.scan;

import io.micronaut.core.annotation.Internal;
import groovyjarjarasm.asm.Label;

/**
 * A non standard class, field, method or code attribute.
 *
 * @author Eric Bruneton
 * @author Eugene Kuleshov
 */
@Internal
class Attribute {

    /**
     * The type of this attribute.
     */
    final String type;

    /**
     * The raw value of this attribute, used only for unknown attributes.
     */
    byte[] value;

    /**
     * The next attribute in this attribute list. May be <tt>null</tt>.
     */
    Attribute next;

    /**
     * Constructs a new empty attribute.
     *
     * @param type the type of the attribute.
     */
    protected Attribute(final String type) {
        this.type = type;
    }

    /**
     * Reads a {@link #type type} attribute. This method must return a
     * <i>new</i> {@link Attribute} object, of type {@link #type type},
     * corresponding to the <tt>len</tt> bytes starting at the given offset, in
     * the given class reader.
     *
     * @param cr      the class that contains the attribute to be read.
     * @param off     index of the first byte of the attribute's content in
     *                {@link groovyjarjarasm.asm.ClassReader#b cr.b}. The 6 attribute header bytes,
     *                containing the type and the length of the attribute, are not
     *                taken into account here.
     * @param len     the length of the attribute's content.
     * @param buf     buffer to be used to call {@link groovyjarjarasm.asm.ClassReader#readUTF8
     *                readUTF8}, {@link groovyjarjarasm.asm.ClassReader#readClass(int, char[]) readClass}
     *                or {@link groovyjarjarasm.asm.ClassReader#readConst readConst}.
     * @param codeOff index of the first byte of code's attribute content in
     *                {@link groovyjarjarasm.asm.ClassReader#b cr.b}, or -1 if the attribute to be read
     *                is not a code attribute. The 6 attribute header bytes,
     *                containing the type and the length of the attribute, are not
     *                taken into account here.
     * @param labels  the labels of the method's code, or <tt>null</tt> if the
     *                attribute to be read is not a code attribute.
     * @return a <i>new</i> {@link Attribute} object corresponding to the given
     * bytes.
     */
    protected Attribute read(final AnnotationClassReader cr, final int off,
                             final int len, final char[] buf, final int codeOff,
                             final Label[] labels) {
        Attribute attr = new Attribute(type);
        attr.value = new byte[len];
        System.arraycopy(cr.b, off, attr.value, 0, len);
        return attr;
    }
}
