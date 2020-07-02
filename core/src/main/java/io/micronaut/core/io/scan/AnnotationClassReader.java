/***
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.micronaut.core.io.scan;

import io.micronaut.core.annotation.Internal;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;

/**
 * A limited class parser that parses the class-level annotations for each class only.
 * This class parses a byte array conforming to the Java class file format and
 * calls the appropriate visit methods of a given class visitor for each field,
 * method and bytecode instruction encountered.
 *
 * @author Eric Bruneton
 * @author Eugene Kuleshov
 * @author Graeme Rocher
 */
@SuppressWarnings("MagicNumber")
@Internal
class AnnotationClassReader {

    /**
     * Flag to skip the debug information in the class. If this flag is set the
     * debug information of the class is not visited, i.e. the
     * {@link org.objectweb.asm.MethodVisitor#visitLocalVariable visitLocalVariable} and
     * {@link org.objectweb.asm.MethodVisitor#visitLineNumber visitLineNumber} methods will not be
     * called.
     */
    public static final int SKIP_DEBUG = 2;

    /**
     * Pseudo access flag to distinguish between the synthetic attribute and the
     * synthetic access flag.
     */
    static final int ACC_SYNTHETIC_ATTRIBUTE = 0x40000;

    /**
     * The type of CONSTANT_Class constant pool items.
     */
    static final int CLASS = 7;

    /**
     * The type of CONSTANT_Fieldref constant pool items.
     */
    static final int FIELD = 9;

    /**
     * The type of CONSTANT_Methodref constant pool items.
     */
    static final int METH = 10;

    /**
     * The type of CONSTANT_InterfaceMethodref constant pool items.
     */
    static final int IMETH = 11;

    /**
     * The type of CONSTANT_String constant pool items.
     */
    static final int STR = 8;

    /**
     * The type of CONSTANT_Integer constant pool items.
     */
    static final int INT = 3;

    /**
     * The type of CONSTANT_Float constant pool items.
     */
    static final int FLOAT = 4;

    /**
     * The type of CONSTANT_Long constant pool items.
     */
    static final int LONG = 5;

    /**
     * The type of CONSTANT_Double constant pool items.
     */
    static final int DOUBLE = 6;

    /**
     * The type of CONSTANT_NameAndType constant pool items.
     */
    static final int NAME_TYPE = 12;

    /**
     * The type of CONSTANT_Utf8 constant pool items.
     */
    static final int UTF8 = 1;

    /**
     * The type of CONSTANT_MethodType constant pool items.
     */
    static final int MTYPE = 16;

    /**
     * The type of CONSTANT_MethodHandle constant pool items.
     */
    static final int HANDLE = 15;

    /**
     * The type of CONSTANT_InvokeDynamic constant pool items.
     */
    static final int INDY = 18;

    /**
     * True to enable signatures support.
     */
    static final boolean SIGNATURES = true;

    /**
     * True to enable annotations support.
     */
    static final boolean ANNOTATIONS = true;

    /**
     * Start index of the class header information (access, name...) in
     * {@link #b b}.
     */
    final int header;

    /**
     * The class to be parsed. <i>The content of this array must not be
     * modified. This field is intended for {@link Attribute} sub classes, and
     * is normally not needed by class generators or adapters.</i>
     */
    final byte[] b;

    /**
     * The start index of each constant pool item in {@link #b b}, plus one. The
     * one byte offset skips the constant pool item tag that indicates its type.
     */
    private final int[] items;

    /**
     * The String objects corresponding to the CONSTANT_Utf8 items. This cache
     * avoids multiple parsing of a given CONSTANT_Utf8 constant pool item,
     * which GREATLY improves performances (by a factor 2 to 3). This caching
     * strategy could be extended to all constant pool items, but its benefit
     * would not be so great for these items (because they are much less
     * expensive to parse than CONSTANT_Utf8 items).
     */
    private final String[] strings;

    /**
     * Maximum length of the strings contained in the constant pool of the
     * class.
     */
    private final int maxStringLength;

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    /**
     * Constructs a new {@link org.objectweb.asm.ClassReader} object.
     *
     * @param b the bytecode of the class to be read.
     */
    private AnnotationClassReader(final byte[] b) {
        this(b, 0, b.length);
    }

    /**
     * Constructs a new {@link org.objectweb.asm.ClassReader} object.
     *
     * @param is an input stream from which to read the class.
     * @throws IOException if a problem occurs during reading.
     */
    public AnnotationClassReader(final InputStream is) throws IOException {
        this(readClass(is, false));
    }

    /**
     * Constructs a new {@link org.objectweb.asm.ClassReader} object.
     *
     * @param name the binary qualified name of the class to be read.
     * @throws IOException if an exception occurs during reading.
     */
    public AnnotationClassReader(final String name) throws IOException {
        this(readClass(
            ClassLoader.getSystemResourceAsStream(name.replace('.', '/')
                + ".class"), true));
    }

    /**
     * Constructs a new {@link org.objectweb.asm.ClassReader} object.
     *
     * @param b   the bytecode of the class to be read.
     * @param off the start offset of the class data.
     * @param len the length of the class data.
     */
    AnnotationClassReader(final byte[] b, final int off, final int len) {
        this.b = b;
        // checks the class version
        /* SPRING PATCH: REMOVED FOR FORWARD COMPATIBILITY WITH JDK 9
        if (readShort(off + 6) > Opcodes.V1_8) {
            throw new IllegalArgumentException();
        }
        */
        // parses the constant pool
        items = new int[readUnsignedShort(off + 8)];
        int n = items.length;
        strings = new String[n];
        int max = 0;
        int index = off + 10;
        for (int i = 1; i < n; ++i) {
            items[i] = index + 1;
            int size;
            switch (b[index]) {
                case FIELD:
                case METH:
                case IMETH:
                case INT:
                case FLOAT:
                case NAME_TYPE:
                case INDY:
                    size = 5;
                    break;
                case LONG:
                case DOUBLE:
                    size = 9;
                    ++i;
                    break;
                case UTF8:
                    size = 3 + readUnsignedShort(index + 1);
                    if (size > max) {
                        max = size;
                    }
                    break;
                case HANDLE:
                    size = 4;
                    break;
                // case ClassWriter.CLASS:
                // case ClassWriter.STR:
                // case ClassWriter.MTYPE
                default:
                    size = 3;
                    break;
            }
            index += size;
        }
        maxStringLength = max;
        // the class header information starts just after the constant pool
        header = index;
    }

    /**
     * Returns the class's access flags (see {@link Opcodes}). This value may
     * not reflect Deprecated and Synthetic flags when bytecode is before 1.5
     * and those flags are represented by attributes.
     *
     * @return the class access flags
     * @see ClassVisitor#visit(int, int, String, String, String, String[])
     */
    public int getAccess() {
        return readUnsignedShort(header);
    }

    /**
     * Returns the internal name of the class (see
     * {@link Type#getInternalName() getInternalName}).
     *
     * @return the internal class name
     * @see ClassVisitor#visit(int, int, String, String, String, String[])
     */
    public String getClassName() {
        return readClass(header + 2, new char[maxStringLength]);
    }

    /**
     * Returns the internal of name of the super class (see
     * {@link Type#getInternalName() getInternalName}). For interfaces, the
     * super class is {@link Object}.
     *
     * @return the internal name of super class, or <tt>null</tt> for
     * {@link Object} class.
     * @see ClassVisitor#visit(int, int, String, String, String, String[])
     */
    public String getSuperName() {
        return readClass(header + 4, new char[maxStringLength]);
    }

    /**
     * Returns the internal names of the class's interfaces (see
     * {@link Type#getInternalName() getInternalName}).
     *
     * @return the array of internal names for all implemented interfaces or
     * <tt>null</tt>.
     * @see ClassVisitor#visit(int, int, String, String, String, String[])
     */
    public String[] getInterfaces() {
        int index = header + 6;
        int n = readUnsignedShort(index);
        String[] interfaces = new String[n];
        if (n > 0) {
            char[] buf = new char[maxStringLength];
            for (int i = 0; i < n; ++i) {
                index += 2;
                interfaces[i] = readClass(index, buf);
            }
        }
        return interfaces;
    }

    /**
     * Reads the bytecode of a class.
     *
     * @param is    an input stream from which to read the class.
     * @param close true to close the input stream after reading.
     * @return the bytecode read from the given input stream.
     * @throws IOException if a problem occurs during reading.
     */
    private static byte[] readClass(final InputStream is, boolean close)
        throws IOException {
        if (is == null) {
            throw new IOException("Class not found");
        }
        try {
            byte[] b = new byte[is.available()];
            int len = 0;
            while (true) {
                int n = is.read(b, len, b.length - len);
                if (n == -1) {
                    if (len < b.length) {
                        byte[] c = new byte[len];
                        System.arraycopy(b, 0, c, 0, len);
                        b = c;
                    }
                    return b;
                }
                len += n;
                if (len == b.length) {
                    int last = is.read();
                    if (last < 0) {
                        return b;
                    }
                    byte[] c = new byte[b.length + 1000];
                    System.arraycopy(b, 0, c, 0, len);
                    c[len++] = (byte) last;
                    b = c;
                }
            }
        } finally {
            if (close) {
                is.close();
            }
        }
    }

    // ------------------------------------------------------------------------
    // Public methods
    // ------------------------------------------------------------------------

    /**
     * Makes the given visitor visit the Java class of this {@link org.objectweb.asm.ClassReader}
     * . This class is the one specified in the constructor (see
     * {@link #AnnotationClassReader(byte[]) ClassReader}).
     *
     * @param classVisitor the visitor that must visit this class.
     * @param flags        the class access flags
     */
    public void accept(final ClassVisitor classVisitor, final int flags) {
        accept(classVisitor, new Attribute[0], flags);
    }

    /**
     * Makes the given visitor visit the Java class of this {@link org.objectweb.asm.ClassReader}.
     * This class is the one specified in the constructor (see
     * {@link #AnnotationClassReader(byte[]) ClassReader}).
     *
     * @param classVisitor the visitor that must visit this class.
     * @param attrs        prototypes of the attributes that must be parsed during the
     *                     visit of the class. Any attribute whose type is not equal to
     *                     the type of one the prototypes will not be parsed: its byte
     *                     array value will be passed unchanged to the ClassWriter.
     *                     <i>This may corrupt it if this value contains references to
     *                     the constant pool, or has syntactic or semantic links with a
     *                     class element that has been transformed by a class adapter
     *                     between the reader and the writer</i>.
     * @param flags        the class access flags
     */
    public void accept(final ClassVisitor classVisitor,
                       final Attribute[] attrs, final int flags) {
        int u = header; // current offset in the class file
        char[] c = new char[maxStringLength]; // buffer used to read strings

        Context context = new Context();
        context.attrs = attrs;
        context.flags = flags;
        context.buffer = c;

        // reads the class declaration
        int access = readUnsignedShort(u);
        String name = readClass(u + 2, c);
        String superClass = readClass(u + 4, c);
        String[] interfaces = new String[readUnsignedShort(u + 6)];
        u += 8;
        for (int i = 0; i < interfaces.length; ++i) {
            interfaces[i] = readClass(u, c);
            u += 2;
        }

        // reads the class attributes
        String signature = null;
        String sourceFile = null;
        String sourceDebug = null;
        String enclosingOwner = null;
        String enclosingName = null;
        String enclosingDesc = null;
        int anns = 0;
        int ianns = 0;
        int tanns = 0;
        int itanns = 0;
        int innerClasses = 0;
        Attribute attributes = null;

        u = getAttributes();
        for (int i = readUnsignedShort(u); i > 0; --i) {
            String attrName = readUTF8(u + 2, c);
            // tests are sorted in decreasing frequency order
            // (based on frequencies observed on typical classes)
            if ("SourceFile".equals(attrName)) {
                sourceFile = readUTF8(u + 8, c);
            } else if ("InnerClasses".equals(attrName)) {
                innerClasses = u + 8;
            } else if ("EnclosingMethod".equals(attrName)) {
                enclosingOwner = readClass(u + 8, c);
                int item = readUnsignedShort(u + 10);
                if (item != 0) {
                    enclosingName = readUTF8(items[item], c);
                    enclosingDesc = readUTF8(items[item] + 2, c);
                }
            } else if (SIGNATURES && "Signature".equals(attrName)) {
                signature = readUTF8(u + 8, c);
            } else if (ANNOTATIONS
                && "RuntimeVisibleAnnotations".equals(attrName)) {
                anns = u + 8;
            } else if (ANNOTATIONS
                && "RuntimeVisibleTypeAnnotations".equals(attrName)) {
                tanns = u + 8;
            } else if ("Deprecated".equals(attrName)) {
                access |= Opcodes.ACC_DEPRECATED;
            } else if ("Synthetic".equals(attrName)) {
                access |= Opcodes.ACC_SYNTHETIC
                    | ACC_SYNTHETIC_ATTRIBUTE;
            } else if ("SourceDebugExtension".equals(attrName)) {
                int len = readInt(u + 4);
                sourceDebug = readUTF(u + 8, len, new char[len]);
            } else if (ANNOTATIONS
                && "RuntimeInvisibleAnnotations".equals(attrName)) {
                ianns = u + 8;
            } else if (ANNOTATIONS
                && "RuntimeInvisibleTypeAnnotations".equals(attrName)) {
                itanns = u + 8;
            } else if ("BootstrapMethods".equals(attrName)) {
                int[] bootstrapMethods = new int[readUnsignedShort(u + 8)];
                for (int j = 0, v = u + 10; j < bootstrapMethods.length; j++) {
                    bootstrapMethods[j] = v;
                    v += 2 + readUnsignedShort(v + 2) << 1;
                }
                context.bootstrapMethods = bootstrapMethods;
            } else {
                Attribute attr = readAttribute(attrs, attrName, u + 8,
                    readInt(u + 4), c, -1, null);
                if (attr != null) {
                    attr.next = attributes;
                    attributes = attr;
                }
            }
            u += 6 + readInt(u + 4);
        }

        // visits the class declaration
        classVisitor.visit(readInt(items[1] - 7), access, name, signature,
            superClass, interfaces);

        // visits the source and debug info
        if ((flags & SKIP_DEBUG) == 0
            && (sourceFile != null || sourceDebug != null)) {
            classVisitor.visitSource(sourceFile, sourceDebug);
        }

        // visits the outer class
        if (enclosingOwner != null) {
            classVisitor.visitOuterClass(enclosingOwner, enclosingName,
                enclosingDesc);
        }

        // visits the class annotations and type annotations
        if (ANNOTATIONS && anns != 0) {
            for (int i = readUnsignedShort(anns), v = anns + 2; i > 0; --i) {
                v = readAnnotationValues(v + 2, c, true,
                    classVisitor.visitAnnotation(readUTF8(v, c), true));
            }
        }
        if (ANNOTATIONS && ianns != 0) {
            for (int i = readUnsignedShort(ianns), v = ianns + 2; i > 0; --i) {
                v = readAnnotationValues(v + 2, c, true,
                    classVisitor.visitAnnotation(readUTF8(v, c), false));
            }
        }

        // visits the end of the class
        classVisitor.visitEnd();
    }

    /**
     * Reads the values of an annotation and makes the given visitor visit them.
     *
     * @param v     the start offset in {@link #b b} of the values to be read
     *              (including the unsigned short that gives the number of
     *              values).
     * @param buf   buffer to be used to call {@link #readUTF8 readUTF8},
     *              {@link #readClass(int, char[]) readClass} or {@link #readConst
     *              readConst}.
     * @param named if the annotation values are named or not.
     * @param av    the visitor that must visit the values.
     * @return the end offset of the annotation values.
     */
    private int readAnnotationValues(int v, final char[] buf,
                                     final boolean named, final AnnotationVisitor av) {
        int i = readUnsignedShort(v);
        v += 2;
        if (named) {
            for (; i > 0; --i) {
                v = readAnnotationValue(v + 2, buf, readUTF8(v, buf), av);
            }
        } else {
            for (; i > 0; --i) {
                v = readAnnotationValue(v, buf, null, av);
            }
        }
        if (av != null) {
            av.visitEnd();
        }
        return v;
    }

    /**
     * Reads a value of an annotation and makes the given visitor visit it.
     *
     * @param v    the start offset in {@link #b b} of the value to be read
     *             (<i>not including the value name constant pool index</i>).
     * @param buf  buffer to be used to call {@link #readUTF8 readUTF8},
     *             {@link #readClass(int, char[]) readClass} or {@link #readConst
     *             readConst}.
     * @param name the name of the value to be read.
     * @param av   the visitor that must visit the value.
     * @return the end offset of the annotation value.
     */
    private int readAnnotationValue(int v, final char[] buf, final String name,
                                    final AnnotationVisitor av) {
        if (av == null) {
            switch (b[v] & 0xFF) {
                case 'e': // enum_const_value
                    return v + 5;
                case '@': // annotation_value
                    return readAnnotationValues(v + 3, buf, true, null);
                case '[': // array_value
                    return readAnnotationValues(v + 1, buf, false, null);
                default:
                    return v + 3;
            }
        }
        int i;
        switch (b[v++] & 0xFF) {
            case 'I': // pointer to CONSTANT_Integer
            case 'J': // pointer to CONSTANT_Long
            case 'F': // pointer to CONSTANT_Float
            case 'D': // pointer to CONSTANT_Double
                av.visit(name, readConst(readUnsignedShort(v), buf));
                v += 2;
                break;
            case 'B': // pointer to CONSTANT_Byte
                av.visit(name, (byte) readInt(items[readUnsignedShort(v)]));
                v += 2;
                break;
            case 'Z': // pointer to CONSTANT_Boolean
                av.visit(name, readInt(items[readUnsignedShort(v)]) == 0 ? Boolean.FALSE : Boolean.TRUE);
                v += 2;
                break;
            case 'S': // pointer to CONSTANT_Short
                av.visit(name, (short) readInt(items[readUnsignedShort(v)]));
                v += 2;
                break;
            case 'C': // pointer to CONSTANT_Char
                av.visit(name, (char) readInt(items[readUnsignedShort(v)]));
                v += 2;
                break;
            case 's': // pointer to CONSTANT_Utf8
                av.visit(name, readUTF8(v, buf));
                v += 2;
                break;
            case 'e': // enum_const_value
                av.visitEnum(name, readUTF8(v, buf), readUTF8(v + 2, buf));
                v += 4;
                break;
            case 'c': // class_info
                av.visit(name, Type.getType(readUTF8(v, buf)));
                v += 2;
                break;
            case '@': // annotation_value
                v = readAnnotationValues(v + 2, buf, true,
                    av.visitAnnotation(name, readUTF8(v, buf)));
                break;
            case '[': // array_value
                int size = readUnsignedShort(v);
                v += 2;
                if (size == 0) {
                    return readAnnotationValues(v - 2, buf, false,
                        av.visitArray(name));
                }
                switch (this.b[v++] & 0xFF) {
                    case 'B':
                        byte[] bv = new byte[size];
                        for (i = 0; i < size; i++) {
                            bv[i] = (byte) readInt(items[readUnsignedShort(v)]);
                            v += 3;
                        }
                        av.visit(name, bv);
                        --v;
                        break;
                    case 'Z':
                        boolean[] zv = new boolean[size];
                        for (i = 0; i < size; i++) {
                            zv[i] = readInt(items[readUnsignedShort(v)]) != 0;
                            v += 3;
                        }
                        av.visit(name, zv);
                        --v;
                        break;
                    case 'S':
                        short[] sv = new short[size];
                        for (i = 0; i < size; i++) {
                            sv[i] = (short) readInt(items[readUnsignedShort(v)]);
                            v += 3;
                        }
                        av.visit(name, sv);
                        --v;
                        break;
                    case 'C':
                        char[] cv = new char[size];
                        for (i = 0; i < size; i++) {
                            cv[i] = (char) readInt(items[readUnsignedShort(v)]);
                            v += 3;
                        }
                        av.visit(name, cv);
                        --v;
                        break;
                    case 'I':
                        int[] iv = new int[size];
                        for (i = 0; i < size; i++) {
                            iv[i] = readInt(items[readUnsignedShort(v)]);
                            v += 3;
                        }
                        av.visit(name, iv);
                        --v;
                        break;
                    case 'J':
                        long[] lv = new long[size];
                        for (i = 0; i < size; i++) {
                            lv[i] = readLong(items[readUnsignedShort(v)]);
                            v += 3;
                        }
                        av.visit(name, lv);
                        --v;
                        break;
                    case 'F':
                        float[] fv = new float[size];
                        for (i = 0; i < size; i++) {
                            fv[i] = Float
                                .intBitsToFloat(readInt(items[readUnsignedShort(v)]));
                            v += 3;
                        }
                        av.visit(name, fv);
                        --v;
                        break;
                    case 'D':
                        double[] dv = new double[size];
                        for (i = 0; i < size; i++) {
                            dv[i] = Double
                                .longBitsToDouble(readLong(items[readUnsignedShort(v)]));
                            v += 3;
                        }
                        av.visit(name, dv);
                        --v;
                        break;
                    default:
                        v = readAnnotationValues(v - 3, buf, false, av.visitArray(name));
                }
            default:
                // no-op
        }
        return v;
    }

    /**
     * Returns the label corresponding to the given offset. The default
     * implementation of this method creates a label for the given offset if it
     * has not been already created.
     *
     * @param offset a bytecode offset in a method.
     * @param labels the already created labels, indexed by their offset. If a
     *               label already exists for offset this method must not create a
     *               new one. Otherwise it must store the new label in this array.
     * @return a non null Label, which must be equal to labels[offset].
     */
    protected Label readLabel(int offset, Label[] labels) {
        // SPRING PATCH: leniently handle offset mismatch
        if (offset >= labels.length) {
            return new Label();
        }
        // END OF PATCH
        if (labels[offset] == null) {
            labels[offset] = new Label();
        }
        return labels[offset];
    }

    /**
     * Returns the start index of the attribute_info structure of this class.
     *
     * @return the start index of the attribute_info structure of this class.
     */
    private int getAttributes() {
        // skips the header
        int u = header + 8 + readUnsignedShort(header + 6) * 2;
        // skips fields and methods
        for (int i = readUnsignedShort(u); i > 0; --i) {
            for (int j = readUnsignedShort(u + 8); j > 0; --j) {
                u += 6 + readInt(u + 12);
            }
            u += 8;
        }
        u += 2;
        for (int i = readUnsignedShort(u); i > 0; --i) {
            for (int j = readUnsignedShort(u + 8); j > 0; --j) {
                u += 6 + readInt(u + 12);
            }
            u += 8;
        }
        // the attribute_info structure starts just after the methods
        return u + 2;
    }

    /**
     * Reads an attribute in {@link #b b}.
     *
     * @param attrs   prototypes of the attributes that must be parsed during the
     *                visit of the class. Any attribute whose type is not equal to
     *                the type of one the prototypes is ignored (i.e. an empty
     *                {@link Attribute} instance is returned).
     * @param type    the type of the attribute.
     * @param off     index of the first byte of the attribute's content in
     *                {@link #b b}. The 6 attribute header bytes, containing the
     *                type and the length of the attribute, are not taken into
     *                account here (they have already been read).
     * @param len     the length of the attribute's content.
     * @param buf     buffer to be used to call {@link #readUTF8 readUTF8},
     *                {@link #readClass(int, char[]) readClass} or {@link #readConst
     *                readConst}.
     * @param codeOff index of the first byte of code's attribute content in
     *                {@link #b b}, or -1 if the attribute to be read is not a code
     *                attribute. The 6 attribute header bytes, containing the type
     *                and the length of the attribute, are not taken into account
     *                here.
     * @param labels  the labels of the method's code, or <tt>null</tt> if the
     *                attribute to be read is not a code attribute.
     * @return the attribute that has been read, or <tt>null</tt> to skip this
     * attribute.
     */
    private Attribute readAttribute(final Attribute[] attrs, final String type,
                                    final int off, final int len, final char[] buf, final int codeOff,
                                    final Label[] labels) {
        for (int i = 0; i < attrs.length; ++i) {
            if (attrs[i].type.equals(type)) {
                return attrs[i].read(this, off, len, buf, codeOff, labels);
            }
        }
        return new Attribute(type).read(this, off, len, null, -1, null);
    }

    /**
     * Reads a byte value in {@link #b b}. <i>This method is intended for
     * {@link Attribute} sub classes, and is normally not needed by class
     * generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public int readByte(final int index) {
        return b[index] & 0xFF;
    }

    /**
     * Reads an unsigned short value in {@link #b b}. <i>This method is intended
     * for {@link Attribute} sub classes, and is normally not needed by class
     * generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public int readUnsignedShort(final int index) {
        byte[] b = this.b;
        return ((b[index] & 0xFF) << 8) | (b[index + 1] & 0xFF);
    }

    /**
     * Reads a signed short value in {@link #b b}. <i>This method is intended
     * for {@link Attribute} sub classes, and is normally not needed by class
     * generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public short readShort(final int index) {
        byte[] b = this.b;
        return (short) (((b[index] & 0xFF) << 8) | (b[index + 1] & 0xFF));
    }

    /**
     * Reads a signed int value in {@link #b b}. <i>This method is intended for
     * {@link Attribute} sub classes, and is normally not needed by class
     * generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public int readInt(final int index) {
        byte[] b = this.b;
        return ((b[index] & 0xFF) << 24) | ((b[index + 1] & 0xFF) << 16)
            | ((b[index + 2] & 0xFF) << 8) | (b[index + 3] & 0xFF);
    }

    /**
     * Reads a signed long value in {@link #b b}. <i>This method is intended for
     * {@link Attribute} sub classes, and is normally not needed by class
     * generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public long readLong(final int index) {
        long l1 = readInt(index);
        long l0 = readInt(index + 4) & 0xFFFFFFFFL;
        return (l1 << 32) | l0;
    }

    /**
     * Reads an UTF8 string constant pool item in {@link #b b}. <i>This method
     * is intended for {@link Attribute} sub classes, and is normally not needed
     * by class generators or adapters.</i>
     *
     * @param index the start index of an unsigned short value in {@link #b b},
     *              whose value is the index of an UTF8 constant pool item.
     * @param buf   buffer to be used to read the item. This buffer must be
     *              sufficiently large. It is not automatically resized.
     * @return the String corresponding to the specified UTF8 item.
     */
    public String readUTF8(int index, final char[] buf) {
        int item = readUnsignedShort(index);
        if (index == 0 || item == 0) {
            return null;
        }
        String s = strings[item];
        if (s != null) {
            return s;
        }
        index = items[item];
        strings[item] = readUTF(index + 2, readUnsignedShort(index), buf);
        return strings[item];
    }

    /**
     * Reads UTF8 string in {@link #b b}.
     *
     * @param index  start offset of the UTF8 string to be read.
     * @param utfLen length of the UTF8 string to be read.
     * @param buf    buffer to be used to read the string. This buffer must be
     *               sufficiently large. It is not automatically resized.
     * @return the String corresponding to the specified UTF8 string.
     */
    private String readUTF(int index, final int utfLen, final char[] buf) {
        int endIndex = index + utfLen;
        byte[] b = this.b;
        int strLen = 0;
        int c;
        int st = 0;
        char cc = 0;
        while (index < endIndex) {
            c = b[index++];
            switch (st) {
                case 0:
                    c = c & 0xFF;
                    if (c < 0x80) { // 0xxxxxxx
                        buf[strLen++] = (char) c;
                    } else if (c < 0xE0 && c > 0xBF) { // 110x xxxx 10xx xxxx
                        cc = (char) (c & 0x1F);
                        st = 1;
                    } else { // 1110 xxxx 10xx xxxx 10xx xxxx
                        cc = (char) (c & 0x0F);
                        st = 2;
                    }
                    break;

                case 1: // byte 2 of 2-byte char or byte 3 of 3-byte char
                    buf[strLen++] = (char) ((cc << 6) | (c & 0x3F));
                    st = 0;
                    break;

                case 2: // byte 2 of 3-byte char
                    cc = (char) ((cc << 6) | (c & 0x3F));
                    st = 1;
                    break;
                default:
                    // no-op
            }
        }
        return new String(buf, 0, strLen);
    }

    /**
     * Reads a class constant pool item in {@link #b b}. <i>This method is
     * intended for {@link Attribute} sub classes, and is normally not needed by
     * class generators or adapters.</i>
     *
     * @param index the start index of an unsigned short value in {@link #b b},
     *              whose value is the index of a class constant pool item.
     * @param buf   buffer to be used to read the item. This buffer must be
     *              sufficiently large. It is not automatically resized.
     * @return the String corresponding to the specified class item.
     */
    public String readClass(final int index, final char[] buf) {
        // computes the start index of the CONSTANT_Class item in b
        // and reads the CONSTANT_Utf8 item designated by
        // the first two bytes of this CONSTANT_Class item
        return readUTF8(items[readUnsignedShort(index)], buf);
    }

    /**
     * Reads a numeric or string constant pool item in {@link #b b}. <i>This
     * method is intended for {@link Attribute} sub classes, and is normally not
     * needed by class generators or adapters.</i>
     *
     * @param item the index of a constant pool item.
     * @param buf  buffer to be used to read the item. This buffer must be
     *             sufficiently large. It is not automatically resized.
     * @return the {@link Integer}, {@link Float}, {@link Long}, {@link Double},
     * {@link String}, {@link Type} or {@link Handle} corresponding to
     * the given constant pool item.
     */
    public Object readConst(final int item, final char[] buf) {
        int index = items[item];
        switch (b[index - 1]) {
            case INT:
                return readInt(index);
            case FLOAT:
                return Float.intBitsToFloat(readInt(index));
            case LONG:
                return readLong(index);
            case DOUBLE:
                return Double.longBitsToDouble(readLong(index));
            case CLASS:
                return Type.getObjectType(readUTF8(index, buf));
            case STR:
                return readUTF8(index, buf);
            case MTYPE:
                return Type.getMethodType(readUTF8(index, buf));
            default: // case ClassWriter.HANDLE_BASE + [1..9]:
                int tag = readByte(index);
                int[] items = this.items;
                int cpIndex = items[readUnsignedShort(index + 1)];
                String owner = readClass(cpIndex, buf);
                cpIndex = items[readUnsignedShort(cpIndex + 2)];
                String name = readUTF8(cpIndex, buf);
                String desc = readUTF8(cpIndex + 2, buf);
                return new Handle(tag, owner, name, desc, tag == Opcodes.H_INVOKEINTERFACE);
        }
    }
}
