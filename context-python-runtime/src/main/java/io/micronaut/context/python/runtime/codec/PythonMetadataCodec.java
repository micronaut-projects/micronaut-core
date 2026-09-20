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
package io.micronaut.context.python.runtime.codec;

import io.micronaut.context.python.runtime.model.AnnotationMetadataModel;
import io.micronaut.context.python.runtime.model.AnnotationValueModel;
import io.micronaut.context.python.runtime.model.ArgumentModel;
import io.micronaut.context.python.runtime.model.ArrayValueModel;
import io.micronaut.context.python.runtime.model.BeanDefinitionModel;
import io.micronaut.context.python.runtime.model.ClassModel;
import io.micronaut.context.python.runtime.model.ClassValueModel;
import io.micronaut.context.python.runtime.model.ConstructorModel;
import io.micronaut.context.python.runtime.model.ExecutableMethodModel;
import io.micronaut.context.python.runtime.model.FactoryMethodModel;
import io.micronaut.context.python.runtime.model.InjectedMethodModel;
import io.micronaut.context.python.runtime.model.InjectionPointModel;
import io.micronaut.context.python.runtime.model.IntrospectionModel;
import io.micronaut.context.python.runtime.model.MethodModel;
import io.micronaut.context.python.runtime.model.PrecalculatedInfoModel;
import io.micronaut.context.python.runtime.model.PropertyGuardModel;
import io.micronaut.context.python.runtime.model.PropertyIndexModel;
import io.micronaut.context.python.runtime.model.PropertyModel;
import io.micronaut.context.python.runtime.model.PythonMetadataModel;
import io.micronaut.context.python.runtime.model.ValueKind;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Reads and writes the resolved model as a deterministic, versioned, checksummed byte sequence. The same model always
 * encodes to the same bytes, so the identity of a model is the digest of its encoding.
 *
 * @since 5.3.0
 */
public final class PythonMetadataCodec {

    /**
     * The resource directory of saved models and catalogs.
     */
    public static final String RESOURCE_PATH = "META-INF/micronaut/python/runtime/";

    /**
     * The extension of a saved model.
     */
    public static final String MODEL_EXTENSION = ".mpym";

    private static final byte[] MAGIC = {'M', 'P', 'Y', 'M'};
    private static final int NULL_LENGTH = -1;
    private static final int MAX_COUNT = 1 << 20;
    private static final int MAX_STRING = 1 << 24;

    private PythonMetadataCodec() {
    }

    /**
     * The resource path of the saved model of a class.
     *
     * @param className The binary class name
     * @return The resource path
     */
    public static String modelResource(String className) {
        return RESOURCE_PATH + className + MODEL_EXTENSION;
    }

    /**
     * Encodes a model.
     *
     * @param model The model
     * @return The bytes
     */
    public static byte[] encode(PythonMetadataModel model) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream(1024);
        try {
            Writer writer = new Writer(new DataOutputStream(payload));
            writer.out.write(MAGIC);
            writer.out.writeInt(model.formatVersion());
            writer.string(model.producer());
            writer.string(model.sourcePath());
            writer.classModel(model.classModel());
            byte[] body = payload.toByteArray();
            CRC32 crc = new CRC32();
            crc.update(body);
            DataOutputStream trailer = new DataOutputStream(payload);
            trailer.writeInt((int) crc.getValue());
            return payload.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot encode the model of " + model.classModel().className(), e);
        }
    }

    /**
     * Decodes a model.
     *
     * @param bytes    The bytes
     * @param resource The resource the bytes were read from, for diagnostics
     * @return The model
     * @throws PythonMetadataFormatException If the bytes are not a model of a supported version
     */
    public static PythonMetadataModel decode(byte[] bytes, String resource) {
        if (bytes.length < MAGIC.length + 8) {
            throw new PythonMetadataFormatException(resource, "truncated: " + bytes.length + " bytes", null);
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (bytes[i] != MAGIC[i]) {
                throw new PythonMetadataFormatException(resource, "not a Python metadata model (bad magic)", null);
            }
        }
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length - 4);
        int recorded = ((bytes[bytes.length - 4] & 0xFF) << 24) | ((bytes[bytes.length - 3] & 0xFF) << 16)
            | ((bytes[bytes.length - 2] & 0xFF) << 8) | (bytes[bytes.length - 1] & 0xFF);
        if (recorded != (int) crc.getValue()) {
            throw new PythonMetadataFormatException(resource, "corrupt: checksum mismatch", null);
        }
        Reader reader = new Reader(new DataInputStream(new java.io.ByteArrayInputStream(bytes, MAGIC.length, bytes.length - MAGIC.length - 4)), resource);
        try {
            int version = reader.in.readInt();
            if (version != PythonMetadataModel.FORMAT_VERSION) {
                throw new PythonMetadataFormatException(resource, "format version " + version + " is not supported by this runtime (supports "
                    + PythonMetadataModel.FORMAT_VERSION + "); recompile the Python sources with a matching compiler", null);
            }
            String producer = reader.requiredString("producer");
            String sourcePath = reader.requiredString("sourcePath");
            ClassModel classModel = reader.classModel();
            if (reader.in.read() != -1) {
                throw new PythonMetadataFormatException(resource, "trailing bytes after the model", null);
            }
            return new PythonMetadataModel(version, producer, sourcePath, classModel);
        } catch (PythonMetadataFormatException e) {
            throw e;
        } catch (EOFException e) {
            throw new PythonMetadataFormatException(resource, "truncated while reading " + reader.section, e);
        } catch (IOException | RuntimeException e) {
            throw new PythonMetadataFormatException(resource, "cannot read " + reader.section + ": " + e.getMessage(), e);
        }
    }

    /**
     * Reads and decodes a model.
     *
     * @param stream   The stream
     * @param resource The resource the stream was opened from, for diagnostics
     * @return The model
     * @throws PythonMetadataFormatException If the bytes are not a model of a supported version
     */
    public static PythonMetadataModel decode(InputStream stream, String resource) {
        try (stream) {
            return decode(stream.readAllBytes(), resource);
        } catch (IOException e) {
            throw new PythonMetadataFormatException(resource, "cannot read the resource: " + e.getMessage(), e);
        }
    }

    /**
     * The identity of an encoded model: the hex SHA-256 digest of its bytes.
     *
     * @param bytes The encoded model
     * @return The identity
     */
    public static String identity(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Writer {
        private final DataOutputStream out;

        private Writer(DataOutputStream out) {
            this.out = out;
        }

        private void string(@Nullable String value) throws IOException {
            if (value == null) {
                out.writeInt(NULL_LENGTH);
                return;
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            out.writeInt(bytes.length);
            out.write(bytes);
        }

        private void strings(List<String> values) throws IOException {
            out.writeInt(values.size());
            for (String value : values) {
                string(value);
            }
        }

        private void classModel(ClassModel model) throws IOException {
            string(model.className());
            annotationMetadata(model.annotationMetadata());
            out.writeInt(model.beanDefinitions().size());
            for (BeanDefinitionModel bean : model.beanDefinitions()) {
                string(bean.definitionClassName());
                string(bean.beanTypeName());
                FactoryMethodModel factory = bean.factory();
                out.writeBoolean(factory != null);
                if (factory != null) {
                    string(factory.factoryTypeName());
                    string(factory.methodName());
                    argument(factory.returnType());
                    out.writeBoolean(factory.isStatic());
                }
                out.writeBoolean(bean.annotationMetadata() != null);
                if (bean.annotationMetadata() != null) {
                    annotationMetadata(bean.annotationMetadata());
                }
                out.writeBoolean(bean.rootAnnotationMetadata() != null);
                if (bean.rootAnnotationMetadata() != null) {
                    annotationMetadata(bean.rootAnnotationMetadata());
                }
                constructor(bean.constructor());
                out.writeInt(bean.methods().size());
                for (InjectedMethodModel method : bean.methods()) {
                    method(method.method());
                    annotationMetadata(method.annotationMetadata());
                    injectionPoints(method.injectionPoints());
                    out.writeBoolean(method.optional());
                    out.writeBoolean(method.setter());
                    out.writeBoolean(method.postConstruct());
                    out.writeBoolean(method.preDestroy());
                    out.writeBoolean(method.required());
                    out.writeBoolean(method.guard() != null);
                    if (method.guard() != null) {
                        string(method.guard().propertyPath());
                        out.writeBoolean(method.guard().multiValue());
                        string(method.guard().cliProperty());
                    }
                }
                out.writeInt(bean.executableMethods().size());
                for (ExecutableMethodModel executable : bean.executableMethods()) {
                    method(executable.method());
                    argument(executable.returnArgument());
                    annotationMetadata(executable.annotationMetadata());
                    out.writeBoolean(executable.hierarchy());
                    out.writeBoolean(executable.processOnStartup());
                    out.writeBoolean(executable.isAbstract());
                }
                PrecalculatedInfoModel info = bean.info();
                string(info.scope());
                out.writeBoolean(info.isAbstract());
                out.writeBoolean(info.isIterable());
                out.writeBoolean(info.isSingleton());
                out.writeBoolean(info.isPrimary());
                out.writeBoolean(info.isConfigurationProperties());
                out.writeBoolean(info.isContainerType());
                strings(bean.exposedTypes());
                out.writeBoolean(bean.exposedTypesDeclared());
                out.writeInt(bean.typeArguments().size());
                for (Map.Entry<String, List<ArgumentModel>> entry : bean.typeArguments().entrySet()) {
                    string(entry.getKey());
                    arguments(entry.getValue());
                }
                out.writeBoolean(bean.validated());
                out.writeBoolean(bean.postConstructValidation());
            }
            IntrospectionModel introspection = model.introspection();
            out.writeBoolean(introspection != null);
            if (introspection != null) {
                string(introspection.introspectionClassName());
                annotationMetadata(introspection.constructorAnnotationMetadata());
                arguments(introspection.constructorArguments());
                out.writeInt(introspection.properties().size());
                for (PropertyModel property : introspection.properties()) {
                    string(property.name());
                    argument(property.argument());
                    out.writeBoolean(property.readMethod() != null);
                    if (property.readMethod() != null) {
                        method(property.readMethod());
                    }
                    out.writeBoolean(property.writeMethod() != null);
                    if (property.writeMethod() != null) {
                        method(property.writeMethod());
                    }
                    out.writeBoolean(property.readOnly());
                }
                out.writeInt(introspection.indexes().size());
                for (PropertyIndexModel index : introspection.indexes()) {
                    string(index.annotationName());
                    string(index.value());
                    out.writeInt(index.propertyIndex());
                }
            }
        }

        private void constructor(ConstructorModel constructor) throws IOException {
            annotationMetadata(constructor.annotationMetadata());
            arguments(constructor.parameters());
            injectionPoints(constructor.injectionPoints());
        }

        private void injectionPoints(List<InjectionPointModel> injectionPoints) throws IOException {
            out.writeInt(injectionPoints.size());
            for (InjectionPointModel point : injectionPoints) {
                out.writeByte(point.kind().code());
                argument(point.argument());
                string(point.beanTypeName());
                string(point.propertyName());
                string(point.propertyPath());
                string(point.value());
                string(point.cliProperty());
            }
        }

        private void method(MethodModel method) throws IOException {
            string(method.declaringType());
            string(method.name());
            argument(method.returnType());
            arguments(method.parameters());
            annotationMetadata(method.annotationMetadata());
            out.writeBoolean(method.isStatic());
        }

        private void arguments(List<ArgumentModel> arguments) throws IOException {
            out.writeInt(arguments.size());
            for (ArgumentModel argument : arguments) {
                argument(argument);
            }
        }

        private void argument(ArgumentModel argument) throws IOException {
            string(argument.name());
            string(argument.typeName());
            annotationMetadata(argument.annotationMetadata());
            arguments(argument.typeArguments());
        }

        private void annotationMetadata(AnnotationMetadataModel metadata) throws IOException {
            if (metadata.isEmpty() && metadata.annotationDefaults().isEmpty() && metadata.repeatableContainers().isEmpty()) {
                out.writeBoolean(false);
                return;
            }
            out.writeBoolean(true);
            mapOfValues(metadata.declaredAnnotations());
            mapOfValues(metadata.declaredStereotypes());
            mapOfValues(metadata.allStereotypes());
            mapOfValues(metadata.allAnnotations());
            out.writeInt(metadata.annotationsByStereotype().size());
            for (Map.Entry<String, List<String>> entry : metadata.annotationsByStereotype().entrySet()) {
                string(entry.getKey());
                strings(entry.getValue());
            }
            mapOfValues(metadata.annotationDefaults());
            out.writeInt(metadata.repeatableContainers().size());
            for (Map.Entry<String, String> entry : metadata.repeatableContainers().entrySet()) {
                string(entry.getKey());
                string(entry.getValue());
            }
            out.writeBoolean(metadata.hasPropertyExpressions());
        }

        private void mapOfValues(Map<String, Map<String, Object>> map) throws IOException {
            out.writeInt(map.size());
            for (Map.Entry<String, Map<String, Object>> entry : map.entrySet()) {
                string(entry.getKey());
                values(entry.getValue());
            }
        }

        private void values(Map<String, Object> values) throws IOException {
            out.writeInt(values.size());
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                string(entry.getKey());
                value(entry.getValue());
            }
        }

        private void value(Object value) throws IOException {
            switch (value) {
                case String s -> {
                    out.writeByte(ValueKind.STRING.code());
                    string(s);
                }
                case Boolean b -> {
                    out.writeByte(ValueKind.BOOLEAN.code());
                    out.writeBoolean(b);
                }
                case Byte b -> {
                    out.writeByte(ValueKind.BYTE.code());
                    out.writeByte(b);
                }
                case Short s -> {
                    out.writeByte(ValueKind.SHORT.code());
                    out.writeShort(s);
                }
                case Integer i -> {
                    out.writeByte(ValueKind.INT.code());
                    out.writeInt(i);
                }
                case Long l -> {
                    out.writeByte(ValueKind.LONG.code());
                    out.writeLong(l);
                }
                case Float f -> {
                    out.writeByte(ValueKind.FLOAT.code());
                    out.writeFloat(f);
                }
                case Double d -> {
                    out.writeByte(ValueKind.DOUBLE.code());
                    out.writeDouble(d);
                }
                case Character c -> {
                    out.writeByte(ValueKind.CHAR.code());
                    out.writeChar(c);
                }
                case ClassValueModel c -> {
                    out.writeByte(ValueKind.CLASS.code());
                    string(c.name());
                }
                case AnnotationValueModel a -> {
                    out.writeByte(ValueKind.ANNOTATION.code());
                    string(a.annotationName());
                    values(a.values());
                }
                case ArrayValueModel a -> {
                    out.writeByte(ValueKind.ARRAY.code());
                    out.writeByte(a.componentKind().code());
                    out.writeBoolean(a.primitive());
                    out.writeInt(a.elements().size());
                    for (Object element : a.elements()) {
                        value(element);
                    }
                }
                default -> throw new IllegalArgumentException("Unsupported model value " + value.getClass().getName() + ": " + value);
            }
        }
    }

    private static final class Reader {
        private final DataInputStream in;
        private final String resource;
        private String section = "header";

        private Reader(DataInputStream in, String resource) {
            this.in = in;
            this.resource = resource;
        }

        private @Nullable String string() throws IOException {
            int length = in.readInt();
            if (length == NULL_LENGTH) {
                return null;
            }
            if (length < 0 || length > MAX_STRING) {
                throw new PythonMetadataFormatException(resource, "invalid string length " + length + " in " + section, null);
            }
            byte[] bytes = new byte[length];
            in.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private String requiredString(String what) throws IOException {
            String value = string();
            if (value == null) {
                throw new PythonMetadataFormatException(resource, "missing " + what + " in " + section, null);
            }
            return value;
        }

        private int count(String what) throws IOException {
            int count = in.readInt();
            if (count < 0 || count > MAX_COUNT) {
                throw new PythonMetadataFormatException(resource, "invalid " + what + " count " + count + " in " + section, null);
            }
            return count;
        }

        private List<String> strings() throws IOException {
            int count = count("string");
            List<String> values = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                values.add(requiredString("string"));
            }
            return List.copyOf(values);
        }

        private ClassModel classModel() throws IOException {
            section = "class";
            String className = requiredString("className");
            AnnotationMetadataModel annotationMetadata = annotationMetadata();
            int definitionCount = count("bean definition");
            List<BeanDefinitionModel> beans = new ArrayList<>(definitionCount);
            for (int d = 0; d < definitionCount; d++) {
                section = "bean definition of " + className;
                String definitionClassName = requiredString("definitionClassName");
                String beanTypeName = requiredString("beanTypeName");
                FactoryMethodModel factory = null;
                if (in.readBoolean()) {
                    factory = new FactoryMethodModel(requiredString("factoryTypeName"), requiredString("factory method"), argument(), in.readBoolean());
                }
                AnnotationMetadataModel definitionMetadata = in.readBoolean() ? annotationMetadata() : null;
                AnnotationMetadataModel rootMetadata = in.readBoolean() ? annotationMetadata() : null;
                ConstructorModel constructor = constructor();
                int methodCount = count("method");
                List<InjectedMethodModel> methods = new ArrayList<>(methodCount);
                for (int i = 0; i < methodCount; i++) {
                    methods.add(new InjectedMethodModel(method(), annotationMetadata(), injectionPoints(),
                        in.readBoolean(), in.readBoolean(), in.readBoolean(), in.readBoolean(), in.readBoolean(), guard()));
                }
                int executableCount = count("executable method");
                List<ExecutableMethodModel> executables = new ArrayList<>(executableCount);
                for (int i = 0; i < executableCount; i++) {
                    executables.add(new ExecutableMethodModel(method(), argument(), annotationMetadata(), in.readBoolean(), in.readBoolean(), in.readBoolean()));
                }
                PrecalculatedInfoModel info = new PrecalculatedInfoModel(string(), in.readBoolean(), in.readBoolean(), in.readBoolean(),
                    in.readBoolean(), in.readBoolean(), in.readBoolean());
                List<String> exposedTypes = strings();
                boolean exposedTypesDeclared = in.readBoolean();
                int typeArgumentCount = count("type argument");
                Map<String, List<ArgumentModel>> typeArguments = new LinkedHashMap<>();
                for (int i = 0; i < typeArgumentCount; i++) {
                    typeArguments.put(requiredString("type"), arguments());
                }
                beans.add(new BeanDefinitionModel(definitionClassName, beanTypeName, factory, definitionMetadata, rootMetadata, constructor,
                    List.copyOf(methods), List.copyOf(executables), info, exposedTypes, exposedTypesDeclared, typeArguments,
                    in.readBoolean(), in.readBoolean()));
            }
            IntrospectionModel introspection = null;
            if (in.readBoolean()) {
                section = "introspection of " + className;
                String introspectionClassName = requiredString("introspectionClassName");
                AnnotationMetadataModel constructorMetadata = annotationMetadata();
                List<ArgumentModel> constructorArguments = arguments();
                int propertyCount = count("property");
                List<PropertyModel> properties = new ArrayList<>(propertyCount);
                for (int i = 0; i < propertyCount; i++) {
                    String name = requiredString("property name");
                    ArgumentModel argument = argument();
                    MethodModel read = in.readBoolean() ? method() : null;
                    MethodModel write = in.readBoolean() ? method() : null;
                    properties.add(new PropertyModel(name, argument, read, write, in.readBoolean()));
                }
                int indexCount = count("index");
                List<PropertyIndexModel> indexes = new ArrayList<>(indexCount);
                for (int i = 0; i < indexCount; i++) {
                    indexes.add(new PropertyIndexModel(requiredString("indexed annotation"), string(), in.readInt()));
                }
                introspection = new IntrospectionModel(introspectionClassName, constructorMetadata, constructorArguments, List.copyOf(properties), List.copyOf(indexes));
            }
            return new ClassModel(className, annotationMetadata, List.copyOf(beans), introspection);
        }

        private ConstructorModel constructor() throws IOException {
            return new ConstructorModel(annotationMetadata(), arguments(), injectionPoints());
        }

        private List<InjectionPointModel> injectionPoints() throws IOException {
            int count = count("injection point");
            List<InjectionPointModel> points = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int code = in.readUnsignedByte();
                InjectionPointModel.Kind kind = InjectionPointModel.Kind.ofCode(code);
                if (kind == null) {
                    throw new PythonMetadataFormatException(resource, "unknown injection point kind " + code + " in " + section, null);
                }
                points.add(new InjectionPointModel(kind, argument(), string(), string(), string(), string(), string()));
            }
            return List.copyOf(points);
        }

        private @Nullable PropertyGuardModel guard() throws IOException {
            if (!in.readBoolean()) {
                return null;
            }
            return new PropertyGuardModel(requiredString("guard property"), in.readBoolean(), string());
        }

        private MethodModel method() throws IOException {
            return new MethodModel(requiredString("declaringType"), requiredString("method name"), argument(), arguments(),
                annotationMetadata(), in.readBoolean());
        }

        private List<ArgumentModel> arguments() throws IOException {
            int count = count("argument");
            List<ArgumentModel> arguments = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                arguments.add(argument());
            }
            return List.copyOf(arguments);
        }

        private ArgumentModel argument() throws IOException {
            return new ArgumentModel(requiredString("argument name"), requiredString("argument type"), annotationMetadata(), arguments());
        }

        private AnnotationMetadataModel annotationMetadata() throws IOException {
            if (!in.readBoolean()) {
                return AnnotationMetadataModel.EMPTY;
            }
            Map<String, Map<String, Object>> declaredAnnotations = mapOfValues();
            Map<String, Map<String, Object>> declaredStereotypes = mapOfValues();
            Map<String, Map<String, Object>> allStereotypes = mapOfValues();
            Map<String, Map<String, Object>> allAnnotations = mapOfValues();
            int byStereotypeCount = count("stereotype");
            Map<String, List<String>> annotationsByStereotype = new LinkedHashMap<>();
            for (int i = 0; i < byStereotypeCount; i++) {
                annotationsByStereotype.put(requiredString("stereotype"), strings());
            }
            Map<String, Map<String, Object>> defaults = mapOfValues();
            int repeatableCount = count("repeatable");
            Map<String, String> repeatable = new LinkedHashMap<>();
            for (int i = 0; i < repeatableCount; i++) {
                repeatable.put(requiredString("repeatable"), requiredString("container"));
            }
            return new AnnotationMetadataModel(declaredAnnotations, declaredStereotypes, allStereotypes, allAnnotations,
                annotationsByStereotype, defaults, repeatable, in.readBoolean());
        }

        private Map<String, Map<String, Object>> mapOfValues() throws IOException {
            int count = count("annotation");
            Map<String, Map<String, Object>> map = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                map.put(requiredString("annotation name"), values());
            }
            return map;
        }

        private Map<String, Object> values() throws IOException {
            int count = count("member");
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                values.put(requiredString("member name"), value());
            }
            return values;
        }

        private Object value() throws IOException {
            int tag = in.readUnsignedByte();
            ValueKind kind = ValueKind.ofCode(tag);
            if (kind == null) {
                throw new PythonMetadataFormatException(resource, "unknown value kind " + tag + " in " + section, null);
            }
            return switch (kind) {
                case STRING -> requiredString("string value");
                case BOOLEAN -> in.readBoolean();
                case BYTE -> in.readByte();
                case SHORT -> in.readShort();
                case INT -> in.readInt();
                case LONG -> in.readLong();
                case FLOAT -> in.readFloat();
                case DOUBLE -> in.readDouble();
                case CHAR -> in.readChar();
                case CLASS -> new ClassValueModel(requiredString("class value"));
                case ANNOTATION -> new AnnotationValueModel(requiredString("annotation value"), values());
                case ARRAY -> {
                    int component = in.readUnsignedByte();
                    ValueKind componentKind = ValueKind.ofCode(component);
                    if (componentKind == null) {
                        throw new PythonMetadataFormatException(resource, "unknown array component kind " + component + " in " + section, null);
                    }
                    boolean primitive = in.readBoolean();
                    int count = count("array element");
                    List<Object> elements = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        elements.add(value());
                    }
                    yield new ArrayValueModel(componentKind, primitive, List.copyOf(elements));
                }
                case OBJECT -> throw new PythonMetadataFormatException(resource, "an OBJECT value has no encoding", null);
            };
        }
    }
}
