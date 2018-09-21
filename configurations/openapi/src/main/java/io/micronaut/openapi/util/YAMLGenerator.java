/*
This copy of Jackson JSON processor YAML module is licensed under the
Apache (Software) License, version 2.0 ("the License").
See the License for details about distribution rights, and the
specific rights regarding derivate works.

You may obtain a copy of the License at:

http://www.apache.org/licenses/LICENSE-2.0
 */

package io.micronaut.openapi.util;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.events.*;
import org.yaml.snakeyaml.nodes.Tag;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.GeneratorBase;
import com.fasterxml.jackson.core.json.JsonWriteContext;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.yaml.PackageVersion;

/**
 * Copied from {@link com.fasterxml.jackson.dataformat.yaml.YAMLGenerator} to support snakeyaml >= 1.2.
 */
public class YAMLGenerator extends GeneratorBase {

    /*
    /**********************************************************
    /* Internal constants
    /**********************************************************
     */

    private static final long MIN_INT_AS_LONG = (long) Integer.MIN_VALUE;
    private static final long MAX_INT_AS_LONG = (long) Integer.MAX_VALUE;
    private static final Pattern PLAIN_NUMBER_P = Pattern.compile("[0-9]*(\\.[0-9]*)?");
    private static final String TAG_BINARY = Tag.BINARY.toString();


    // Implicit means that (type) tags won't be shown, right?
    private static final ImplicitTuple NO_TAGS = new ImplicitTuple(true, true);

    // ... and sometimes we specifically DO want explicit tag:
    private static final ImplicitTuple EXPLICIT_TAGS = new ImplicitTuple(false, false);

    // for field names, leave out quotes
    private static final DumperOptions.ScalarStyle STYLE_NAME = DumperOptions.ScalarStyle.PLAIN;

    // numbers, booleans, should use implicit
    private static final DumperOptions.ScalarStyle STYLE_SCALAR = DumperOptions.ScalarStyle.PLAIN;
    // Strings quoted for fun
    private static final DumperOptions.ScalarStyle STYLE_QUOTED = DumperOptions.ScalarStyle.DOUBLE_QUOTED;
    // Strings in literal (block) style
    private static final DumperOptions.ScalarStyle STYLE_LITERAL = DumperOptions.ScalarStyle.LITERAL;

    // Which flow style to use for Base64? Maybe basic quoted?
    // 29-Nov-2017, tatu: Actually SnakeYAML uses block style so:
    private static final DumperOptions.ScalarStyle STYLE_BASE64 = STYLE_LITERAL;

    private static final DumperOptions.ScalarStyle STYLE_PLAIN = DumperOptions.ScalarStyle.PLAIN;

    /**
     * Enumeration that defines all togglable features for YAML generators.
     */
    // since 2.9
    public enum Feature implements FormatFeature {
        /**
         * Whether we are to write an explicit document start marker ("---")
         * or not.
         *
         * @since 2.3
         */
        WRITE_DOC_START_MARKER(true),

        /**
         * Whether to use YAML native Object Id construct for indicating type (true);
         * or "generic" Object Id mechanism (false). Former works better for systems that
         * are YAML-centric; latter may be better choice for interoperability, when
         * converting between formats or accepting other formats.
         *
         * @since 2.5
         */
        USE_NATIVE_OBJECT_ID(true),

        /**
         * Whether to use YAML native Type Id construct for indicating type (true);
         * or "generic" type property (false). Former works better for systems that
         * are YAML-centric; latter may be better choice for interoperability, when
         * converting between formats or accepting other formats.
         *
         * @since 2.5
         */
        USE_NATIVE_TYPE_ID(true),

        /**
         * Do we try to force so-called canonical output or not.
         */
        CANONICAL_OUTPUT(false),

        /**
         * Options passed to SnakeYAML that determines whether longer textual content
         * gets automatically split into multiple lines or not.
         * <p>
         * Feature is enabled by default to conform to SnakeYAML defaults as well as
         * backwards compatibility with 2.5 and earlier versions.
         *
         * @since 2.6
         */
        SPLIT_LINES(true),

        /**
         * Whether strings will be rendered without quotes (true) or
         * with quotes (false, default).
         * <p>
         * Minimized quote usage makes for more human readable output; however, content is
         * limited to printable characters according to the rules of
         * <a href="http://www.yaml.org/spec/1.2/spec.html#style/block/literal">literal block style</a>.
         *
         * @since 2.7
         */
        MINIMIZE_QUOTES(false),

        /**
         * Whether numbers stored as strings will be rendered with quotes (true) or
         * without quotes (false, default) when MINIMIZE_QUOTES is enabled.
         * <p>
         * Minimized quote usage makes for more human readable output; however, content is
         * limited to printable characters according to the rules of
         * <a href="http://www.yaml.org/spec/1.2/spec.html#style/block/literal">literal block style</a>.
         *
         * @since 2.8.2
         */
        ALWAYS_QUOTE_NUMBERS_AS_STRINGS(false),

        /**
         * Whether for string containing newlines a <a href="http://www.yaml.org/spec/1.2/spec.html#style/block/literal">literal block style</a>
         * should be used. This automatically enabled when {@link #MINIMIZE_QUOTES} is set.
         * <p>
         * The content of such strings is limited to printable characters according to the rules of
         * <a href="http://www.yaml.org/spec/1.2/spec.html#style/block/literal">literal block style</a>.
         *
         * @since 2.9
         */
        LITERAL_BLOCK_STYLE(false),

        /**
         * Feature enabling of which adds indentation for array entry generation
         * (default indentation being 2 spaces).
         * <p>
         * Default value is `false` for backwards compatibility
         *
         * @since 2.9
         */
        INDENT_ARRAYS(false);

        protected final boolean _defaultState;
        protected final int _mask;

        private Feature(boolean defaultState) {
            _defaultState = defaultState;
            _mask = (1 << ordinal());
        }

        /**
         * Method that calculates bit set (flags) of all features that
         * are enabled by default.
         *
         * @return The flag
         */
        public static int collectDefaults() {
            int flags = 0;
            for (Feature f : values()) {
                if (f.enabledByDefault()) {
                    flags |= f.getMask();
                }
            }
            return flags;
        }

        @Override
        public boolean enabledByDefault() {
            return _defaultState;
        }

        @Override
        public boolean enabledIn(int flags) {
            return (flags & _mask) != 0;
        }

        @Override
        public int getMask() {
            return _mask;
        }
    }



    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    /**
     * Bit flag composed of bits that indicate which
     * {@link YAMLGenerator.Feature}s
     * are enabled.
     */
    private int _formatFeatures;

    private Writer _writer;

    private DumperOptions _outputOptions;



    /*
    /**********************************************************
    /* Output state
    /**********************************************************
     */

    private Emitter _emitter;

    /**
     * YAML supports native Object identifiers, so databinder may indicate
     * need to output one.
     */
    private String _objectId;

    /**
     * YAML supports native Type identifiers, so databinder may indicate
     * need to output one.
     */
    private String _typeId;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    /**
     * Default constructor.
     *
     * @param ctxt         The context
     * @param jsonFeatures the features
     * @param yamlFeatures the yaml features
     * @param codec        The codec
     * @param out          The writer
     * @param version      The version
     * @throws IOException When an error occurs
     */
    YAMLGenerator(IOContext ctxt, int jsonFeatures, int yamlFeatures,
                  ObjectCodec codec, Writer out,
                  DumperOptions.Version version)
            throws IOException {
        super(jsonFeatures, codec);
        _formatFeatures = yamlFeatures;
        _writer = out;

        _outputOptions = buildDumperOptions();

        _emitter = new Emitter(_writer, _outputOptions);
        // should we start output now, or try to defer?
        _emitter.emit(new StreamStartEvent(null, null));
        Map<String, String> noTags = Collections.emptyMap();

        boolean startMarker = Feature.WRITE_DOC_START_MARKER.enabledIn(yamlFeatures);

        _emitter.emit(new DocumentStartEvent(null, null, startMarker,
                version, // for 1.10 was: ((version == null) ? null : version.getArray()),
                noTags));
    }

    private DumperOptions buildDumperOptions() {
        DumperOptions opt = new DumperOptions();
        // would we want canonical?
        if (Feature.CANONICAL_OUTPUT.enabledIn(_formatFeatures)) {
            opt.setCanonical(true);
        } else {
            opt.setCanonical(false);
            // if not, MUST specify flow styles
            opt.setDefaultFlowStyle(FlowStyle.BLOCK);
        }
        // split-lines for text blocks?
        opt.setSplitLines(Feature.SPLIT_LINES.enabledIn(_formatFeatures));
        // array indentation?
        if (Feature.INDENT_ARRAYS.enabledIn(_formatFeatures)) {
            // But, wrt [dataformats-text#34]: need to set both to diff values to work around bug
            // (otherwise indentation level is "invisible". Note that this should NOT be necessary
            // but is needed up to at least SnakeYAML 1.18.
            // Also looks like all kinds of values do work, except for both being 2... weird.
            opt.setIndicatorIndent(1);
            opt.setIndent(2);
        }
        return opt;
    }

    /*
    /**********************************************************
    /* Versioned
    /**********************************************************
     */

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************
    /* Overridden methods, configuration
    /**********************************************************
     */

    /**
     * Not sure what to do here; could reset indentation to some value maybe?.
     */
    @Override
    public YAMLGenerator useDefaultPrettyPrinter() {
        return this;
    }

    /**
     * Not sure what to do here; will always indent, but uses
     * YAML-specific settings etc.
     */
    @Override
    public YAMLGenerator setPrettyPrinter(PrettyPrinter pp) {
        return this;
    }

    @Override
    public Object getOutputTarget() {
        return _writer;
    }

    /**
     * SnakeYAML does not expose buffered content amount, so we can only return
     * <code>-1</code> from here.
     */
    @Override
    public int getOutputBuffered() {
        return -1;
    }

    @Override
    public int getFormatFeatures() {
        return _formatFeatures;
    }

    @Override
    public JsonGenerator overrideFormatFeatures(int values, int mask) {
        // 14-Mar-2016, tatu: Should re-configure, but unfortunately most
        //    settings passed via options passed to constructor of Emitter
        _formatFeatures = (_formatFeatures & ~mask) | (values & mask);
        return this;
    }

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return false;
    }

    @Override
    public boolean canWriteFormattedNumbers() {
        return true;
    }

    /*
    /**********************************************************************
    /* Overridden methods; writing field names
    /**********************************************************************
     */

    /* And then methods overridden to make final, streamline some
     * aspects...
     */

    @Override
    public final void writeFieldName(String name) throws IOException {
        if (_writeContext.writeFieldName(name) == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Can not write a field name, expecting a value");
        }
        writeFieldNameInternal(name);
    }

    @Override
    public final void writeFieldName(SerializableString name)
            throws IOException {
        // Object is a value, need to verify it's allowed
        if (_writeContext.writeFieldName(name.getValue()) == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Can not write a field name, expecting a value");
        }
        writeFieldNameInternal(name.getValue());
    }

    @Override
    public final void writeStringField(String fieldName, String value)
            throws IOException {
        if (_writeContext.writeFieldName(fieldName) == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Can not write a field name, expecting a value");
        }
        writeFieldNameInternal(fieldName);
        writeString(value);
    }

    private void writeFieldNameInternal(String name)
            throws IOException {
        writeScalar(name, STYLE_NAME);
    }

    /*
    /**********************************************************
    /* Public API: low-level I/O
    /**********************************************************
     */

    @Override
    public final void flush() throws IOException {
        _writer.flush();
    }

    @Override
    public void close() throws IOException {
        if (!isClosed()) {
            _emitter.emit(new DocumentEndEvent(null, null, false));
            _emitter.emit(new StreamEndEvent(null, null));
            super.close();
            _writer.close();
        }
    }

    /*
    /**********************************************************
    /* Public API: structural output
    /**********************************************************
     */

    @Override
    public final void writeStartArray() throws IOException {
        _verifyValueWrite("start an array");
        _writeContext = _writeContext.createChildArrayContext();
        FlowStyle style = _outputOptions.getDefaultFlowStyle();
        String yamlTag = _typeId;
        boolean implicit = (yamlTag == null);
        String anchor = _objectId;
        if (anchor != null) {
            _objectId = null;
        }
        _emitter.emit(new SequenceStartEvent(anchor, yamlTag,
                implicit, null, null, style));
    }

    @Override
    public final void writeEndArray() throws IOException {
        if (!_writeContext.inArray()) {
            _reportError("Current context not Array but " + _writeContext.typeDesc());
        }
        // just to make sure we don't "leak" type ids
        _typeId = null;
        _writeContext = _writeContext.getParent();
        _emitter.emit(new SequenceEndEvent(null, null));
    }

    @Override
    public final void writeStartObject() throws IOException {
        _verifyValueWrite("start an object");
        _writeContext = _writeContext.createChildObjectContext();
        FlowStyle style = _outputOptions.getDefaultFlowStyle();
        String yamlTag = _typeId;
        boolean implicit = (yamlTag == null);
        String anchor = _objectId;
        if (anchor != null) {
            _objectId = null;
        }
        _emitter.emit(new MappingStartEvent(anchor, yamlTag,
                implicit, null, null, style));
    }

    @Override
    public final void writeEndObject() throws IOException {
        if (!_writeContext.inObject()) {
            _reportError("Current context not Object but " + _writeContext.typeDesc());
        }
        // just to make sure we don't "leak" type ids
        _typeId = null;
        _writeContext = _writeContext.getParent();
        _emitter.emit(new MappingEndEvent(null, null));
    }

    /*
    /**********************************************************
    /* Output method implementations, textual
    /**********************************************************
     */

    @Override
    public void writeString(String text) throws IOException {
        if (text == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write String value");
        DumperOptions.ScalarStyle style = STYLE_QUOTED;
        if (Feature.MINIMIZE_QUOTES.enabledIn(_formatFeatures) && !isBooleanContent(text)) {
            // If this string could be interpreted as a number, it must be quoted.
            if (Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS.enabledIn(_formatFeatures)
                    && PLAIN_NUMBER_P.matcher(text).matches()) {
                style = STYLE_QUOTED;
            } else if (text.indexOf('\n') >= 0) {
                style = STYLE_LITERAL;
            } else {
                style = STYLE_PLAIN;
            }
        } else if (Feature.LITERAL_BLOCK_STYLE.enabledIn(_formatFeatures) && text.indexOf('\n') >= 0) {
            style = STYLE_LITERAL;
        }
        writeScalar(text, style);
    }

    private boolean isBooleanContent(String text) {
        return text.equals("true") || text.equals("false");
    }

    @Override
    public void writeString(char[] text, int offset, int len) throws IOException {
        writeString(new String(text, offset, len));
    }

    @Override
    public final void writeString(SerializableString sstr)
            throws IOException {
        writeString(sstr.toString());
    }

    @Override
    public void writeRawUTF8String(byte[] text, int offset, int len) {
        _reportUnsupportedOperation();
    }

    @Override
    public final void writeUTF8String(byte[] text, int offset, int len)
            throws IOException {
        writeString(new String(text, offset, len, "UTF-8"));
    }

    /*
    /**********************************************************
    /* Output method implementations, unprocessed ("raw")
    /**********************************************************
     */

    @Override
    public void writeRaw(String text) {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(String text, int offset, int len) {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(char[] text, int offset, int len) {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(char c) {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(String text) {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(String text, int offset, int len) {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(char[] text, int offset, int len) {
        _reportUnsupportedOperation();
    }

    /*
    /**********************************************************
    /* Output method implementations, base64-encoded binary
    /**********************************************************
     */

    @Override
    public void writeBinary(Base64Variant b64variant, byte[] data, int offset, int len) throws IOException {
        if (data == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write Binary value");
        if (offset > 0 || (offset + len) != data.length) {
            data = Arrays.copyOfRange(data, offset, offset + len);
        }
        writeScalarBinary(b64variant, data);
    }

    /*
    /**********************************************************
    /* Output method implementations, scalars
    /**********************************************************
     */

    @Override
    public void writeBoolean(boolean state) throws IOException {
        _verifyValueWrite("write boolean value");
        writeScalar(state ? "true" : "false", STYLE_SCALAR);
    }

    @Override
    public void writeNumber(int i) throws IOException {
        _verifyValueWrite("write number");
        writeScalar(String.valueOf(i), STYLE_SCALAR);
    }

    @Override
    public void writeNumber(long l) throws IOException {
        // First: maybe 32 bits is enough?
        if (l <= MAX_INT_AS_LONG && l >= MIN_INT_AS_LONG) {
            writeNumber((int) l);
            return;
        }
        _verifyValueWrite("write number");
        writeScalar(String.valueOf(l), STYLE_SCALAR);
    }

    @Override
    public void writeNumber(BigInteger v) throws IOException {
        if (v == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write number");
        writeScalar(String.valueOf(v.toString()), STYLE_SCALAR);
    }

    @Override
    public void writeNumber(double d) throws IOException {
        _verifyValueWrite("write number");
        writeScalar(String.valueOf(d), STYLE_SCALAR);
    }

    @Override
    public void writeNumber(float f) throws IOException {
        _verifyValueWrite("write number");
        writeScalar(String.valueOf(f), STYLE_SCALAR);
    }

    @Override
    public void writeNumber(BigDecimal dec) throws IOException {
        if (dec == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write number");
        String str = isEnabled(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN) ? dec.toPlainString() : dec.toString();
        writeScalar(str, STYLE_SCALAR);
    }

    @Override
    public void writeNumber(String encodedValue) throws IOException, UnsupportedOperationException {
        if (encodedValue == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write number");
        writeScalar(encodedValue, STYLE_SCALAR);
    }

    @Override
    public void writeNull() throws IOException {
        _verifyValueWrite("write null value");
        // no real type for this, is there?
        writeScalar("null", STYLE_SCALAR);
    }

    /*
    /**********************************************************
    /* Public API, write methods, Native Ids
    /**********************************************************
     */

    @Override
    public boolean canWriteObjectId() {
        // yes, YAML does support Native Type Ids!
        // 10-Sep-2014, tatu: Except as per [#23] might not want to...
        return Feature.USE_NATIVE_OBJECT_ID.enabledIn(_formatFeatures);
    }

    @Override
    public boolean canWriteTypeId() {
        // yes, YAML does support Native Type Ids!
        // 10-Sep-2014, tatu: Except as per [#22] might not want to...
        return Feature.USE_NATIVE_TYPE_ID.enabledIn(_formatFeatures);
    }

    @Override
    public void writeTypeId(Object id) {
        // should we verify there's no preceding type id?
        _typeId = String.valueOf(id);
    }

    @Override
    public void writeObjectRef(Object id)
            throws IOException {
        _verifyValueWrite("write Object reference");
        AliasEvent evt = new AliasEvent(String.valueOf(id), null, null);
        _emitter.emit(evt);
    }

    @Override
    public void writeObjectId(Object id) {
        // should we verify there's no preceding id?
        _objectId = String.valueOf(id);
    }

    /*
    /**********************************************************
    /* Implementations for methods from base class
    /**********************************************************
     */

    @Override
    protected final void _verifyValueWrite(String typeMsg)
            throws IOException {
        int status = _writeContext.writeValue();
        if (status == JsonWriteContext.STATUS_EXPECT_NAME) {
            _reportError("Can not " + typeMsg + ", expecting field name");
        }
    }

    @Override
    protected void _releaseBuffers() {
        // nothing special to do...
    }

    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */
    private void writeScalar(String value, DumperOptions.ScalarStyle style) throws IOException {
        _emitter.emit(scalarEvent(value, style));
    }

    private void writeScalarBinary(Base64Variant b64variant,
                                   byte[] data) throws IOException {
        // 15-Dec-2017, tatu: as per [dataformats-text#62], can not use SnakeYAML's internal
        //    codec. Also: force use of linefeed variant if using default
        if (b64variant == Base64Variants.getDefaultVariant()) {
            b64variant = Base64Variants.MIME;
        }
        String encoded = b64variant.encode(data);
        _emitter.emit(new ScalarEvent(null, TAG_BINARY, EXPLICIT_TAGS, encoded,
                null, null, STYLE_BASE64));
    }

    private ScalarEvent scalarEvent(String value, DumperOptions.ScalarStyle style) {
        String yamlTag = _typeId;
        if (yamlTag != null) {
            _typeId = null;
        }
        String anchor = _objectId;
        if (anchor != null) {
            _objectId = null;
        }
        // 29-Nov-2017, tatu: Not 100% sure why we don't force explicit tags for
        //    type id, but trying to do so seems to double up tag output...
        return new ScalarEvent(anchor, yamlTag, NO_TAGS, value,
                null, null, style);
    }
}
