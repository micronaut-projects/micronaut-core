/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.openapi.util;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.format.InputAccessor;
import com.fasterxml.jackson.core.format.MatchStrength;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.yaml.PackageVersion;
import com.fasterxml.jackson.dataformat.yaml.UTF8Reader;
import com.fasterxml.jackson.dataformat.yaml.UTF8Writer;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import org.yaml.snakeyaml.DumperOptions;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Copied from {@link com.fasterxml.jackson.dataformat.yaml.YAMLFactory} to support snakeyaml >= 1.20.
 */
class YAMLFactory extends JsonFactory
{

    /**
     * Name used to identify YAML format.
     * (and returned by {@link #getFormatName()}
     */
    private static final String FORMAT_NAME_YAML = "YAML";

    /**
     * Bitfield (set of flags) of all parser features that are enabled
     * by default.
     */
    private static final int DEFAULT_YAML_PARSER_FEATURE_FLAGS = YAMLParser.Feature.collectDefaults();

    /**
     * Bitfield (set of flags) of all generator features that are enabled
     * by default.
     */
    private static final int DEFAULT_YAML_GENERATOR_FEATURE_FLAGS = YAMLGenerator.Feature.collectDefaults();

    private static final byte UTF8_BOM_1 = (byte) 0xEF;
    private static final long serialVersionUID = 1L;
    private static final byte UTF8_BOM_2 = (byte) 0xBB;
    private static final byte UTF8_BOM_3 = (byte) 0xBF;

    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

    protected int _yamlParserFeatures;

    protected int _yamlGeneratorFeatures;

    /*
    /**********************************************************************
    /* Factory construction, configuration
    /**********************************************************************
     */

    protected DumperOptions.Version _version;

    /**
     * Default constructor used to create factory instances.
     * Creation of a factory instance is a light-weight operation,
     * but it is still a good idea to reuse limited number of
     * factory instances (and quite often just a single instance):
     * factories are used as context for storing some reused
     * processing objects (such as symbol tables parsers use)
     * and this reuse only works within context of a single
     * factory instance.
     */
    public YAMLFactory() { this(null); }

    public YAMLFactory(ObjectCodec oc)
    {
        super(oc);
        _yamlParserFeatures = DEFAULT_YAML_PARSER_FEATURE_FLAGS;
        _yamlGeneratorFeatures = DEFAULT_YAML_GENERATOR_FEATURE_FLAGS;
        /* 26-Jul-2013, tatu: Seems like we should force output as 1.1 but
         *   that adds version declaration which looks ugly...
         */
        //_version = DumperOptions.Version.V1_1;
        _version = null;
    }

    /**
     * @since 2.2.1
     */
    public YAMLFactory(YAMLFactory src, ObjectCodec oc)
    {
        super(src, oc);
        _version = src._version;
        _yamlParserFeatures = src._yamlParserFeatures;
        _yamlGeneratorFeatures = src._yamlGeneratorFeatures;
    }

    @Override
    public YAMLFactory copy()
    {
        _checkInvalidCopy(YAMLFactory.class);
        return new YAMLFactory(this, null);
    }

    /*
    /**********************************************************
    /* Serializable overrides
    /**********************************************************
     */

    /**
     * Method that we need to override to actually make restoration go
     * through constructors etc.
     * Also: must be overridden by sub-classes as well.
     */
    @Override
    protected Object readResolve() {
        return new YAMLFactory(this, _objectCodec);
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
    /* Capability introspection
    /**********************************************************
     */

    // No, we can't make use of char[] optimizations
    @Override
    public boolean canUseCharArrays() { return false; }

    // Add these in 2.7:

    /*
    @Override
    public Class<YAMLParser.Feature> getFormatReadFeatureType() {
        return YAMLParser.Feature.class;
    }

    @Override
    public Class<YAMLGenerator.Feature> getFormatWriteFeatureType() {
        return YAMLGenerator.Feature.class;
    }
    */

    /*
    /**********************************************************
    /* Format detection functionality
    /**********************************************************
     */

    @Override
    public String getFormatName() {
        return FORMAT_NAME_YAML;
    }

    /**
     * Sub-classes need to override this method (as of 1.8)
     */
    @Override
    public MatchStrength hasFormat(InputAccessor acc) throws IOException
    {
        /* Actually quite possible to do, thanks to (optional) "---"
         * indicator we may be getting...
         */
        if (!acc.hasMoreBytes()) {
            return MatchStrength.INCONCLUSIVE;
        }
        byte b = acc.nextByte();
        // Very first thing, a UTF-8 BOM?
        if (b == UTF8_BOM_1) { // yes, looks like UTF-8 BOM
            if (!acc.hasMoreBytes()) {
                return MatchStrength.INCONCLUSIVE;
            }
            if (acc.nextByte() != UTF8_BOM_2) {
                return MatchStrength.NO_MATCH;
            }
            if (!acc.hasMoreBytes()) {
                return MatchStrength.INCONCLUSIVE;
            }
            if (acc.nextByte() != UTF8_BOM_3) {
                return MatchStrength.NO_MATCH;
            }
            if (!acc.hasMoreBytes()) {
                return MatchStrength.INCONCLUSIVE;
            }
            b = acc.nextByte();
        }
        // as far as I know, leading space is NOT allowed before "---" marker?
        if (b == '-' && (acc.hasMoreBytes() && acc.nextByte() == '-')
                && (acc.hasMoreBytes() && acc.nextByte() == '-')) {
            return MatchStrength.FULL_MATCH;
        }
        return MatchStrength.INCONCLUSIVE;
    }

    /*
    /**********************************************************
    /* Configuration, parser settings
    /**********************************************************
     */

    /**
     * Method for enabling or disabling specified parser feature
     * (check {@link YAMLParser.Feature} for list of features)
     */
    public final YAMLFactory configure(YAMLParser.Feature f, boolean state)
    {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }

    /**
     * Method for enabling specified parser feature
     * (check {@link YAMLParser.Feature} for list of features)
     */
    public YAMLFactory enable(YAMLParser.Feature f) {
        _yamlParserFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified parser features
     * (check {@link YAMLParser.Feature} for list of features)
     */
    public YAMLFactory disable(YAMLParser.Feature f) {
        _yamlParserFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Checked whether specified parser feature is enabled.
     */
    public final boolean isEnabled(YAMLParser.Feature f) {
        return (_yamlParserFeatures & f.getMask()) != 0;
    }

    /*
    /**********************************************************
    /* Configuration, generator settings
    /**********************************************************
     */

    /**
     * Method for enabling or disabling specified generator feature
     * (check {@link YAMLGenerator.Feature} for list of features)
     */
    public final YAMLFactory configure(YAMLGenerator.Feature f, boolean state) {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }


    /**
     * Method for enabling specified generator features
     * (check {@link YAMLGenerator.Feature} for list of features)
     */
    public YAMLFactory enable(YAMLGenerator.Feature f) {
        _yamlGeneratorFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified generator feature
     * (check {@link YAMLGenerator.Feature} for list of features)
     */
    public YAMLFactory disable(YAMLGenerator.Feature f) {
        _yamlGeneratorFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Check whether specified generator feature is enabled.
     */
    public final boolean isEnabled(YAMLGenerator.Feature f) {
        return (_yamlGeneratorFeatures & f.getMask()) != 0;
    }

    /*
    /**********************************************************
    /* Overridden parser factory methods (for 2.1)
    /**********************************************************
     */

    @Override
    public YAMLParser createParser(String content) throws IOException {
        return createParser(new StringReader(content));
    }

    @Override
    public YAMLParser createParser(File f) throws IOException {
        IOContext ctxt = _createContext(f, true);
        return _createParser(_decorate(new FileInputStream(f), ctxt), ctxt);
    }

    @Override
    public YAMLParser createParser(URL url) throws IOException
    {
        IOContext ctxt = _createContext(url, true);
        return _createParser(_decorate(_optimizedStreamFromURL(url), ctxt), ctxt);
    }

    @Override
    public YAMLParser createParser(InputStream in) throws IOException
    {
        IOContext ctxt = _createContext(in, false);
        return _createParser(_decorate(in, ctxt), ctxt);
    }

    @Override
    public YAMLParser createParser(Reader r) throws IOException
    {
        IOContext ctxt = _createContext(r, false);
        return _createParser(_decorate(r, ctxt), ctxt);
    }

    @Override // since 2.4
    public YAMLParser createParser(char[] data) throws IOException {
        return createParser(new CharArrayReader(data, 0, data.length));
    }

    @Override // since 2.4
    public YAMLParser createParser(char[] data, int offset, int len) throws IOException {
        return createParser(new CharArrayReader(data, offset, len));
    }

    @Override
    public YAMLParser createParser(byte[] data) throws IOException
    {
        IOContext ctxt = _createContext(data, true);
        // [JACKSON-512]: allow wrapping with InputDecorator
        if (_inputDecorator != null) {
            InputStream in = _inputDecorator.decorate(ctxt, data, 0, data.length);
            if (in != null) {
                return _createParser(in, ctxt);
            }
        }
        return _createParser(data, 0, data.length, ctxt);
    }

    @Override
    public YAMLParser createParser(byte[] data, int offset, int len) throws IOException
    {
        IOContext ctxt = _createContext(data, true);
        // [JACKSON-512]: allow wrapping with InputDecorator
        if (_inputDecorator != null) {
            InputStream in = _inputDecorator.decorate(ctxt, data, offset, len);
            if (in != null) {
                return _createParser(in, ctxt);
            }
        }
        return _createParser(data, offset, len, ctxt);
    }

    /*
    /**********************************************************
    /* Overridden generator factory methods (2.1)
    /**********************************************************
     */

    @Override
    public YAMLGenerator createGenerator(OutputStream out, JsonEncoding enc) throws IOException
    {
        // false -> we won't manage the stream unless explicitly directed to
        IOContext ctxt = _createContext(out, false);
        ctxt.setEncoding(enc);
        return _createGenerator(_createWriter(_decorate(out, ctxt), enc, ctxt), ctxt);
    }

    @Override
    public YAMLGenerator createGenerator(OutputStream out) throws IOException
    {
        // false -> we won't manage the stream unless explicitly directed to
        IOContext ctxt = _createContext(out, false);
        return _createGenerator(_createWriter(_decorate(out, ctxt),
                JsonEncoding.UTF8, ctxt), ctxt);
    }

    @Override
    public YAMLGenerator createGenerator(Writer out) throws IOException
    {
        IOContext ctxt = _createContext(out, false);
        return _createGenerator(_decorate(out, ctxt), ctxt);
    }

    @Override
    public JsonGenerator createGenerator(File f, JsonEncoding enc) throws IOException
    {
        OutputStream out = new FileOutputStream(f);
        // true -> yes, we have to manage the stream since we created it
        IOContext ctxt = _createContext(f, true);
        ctxt.setEncoding(enc);
        return _createGenerator(_createWriter(_decorate(out, ctxt), enc, ctxt), ctxt);
    }

    /*
    /******************************************************
    /* Overridden internal factory methods
    /******************************************************
     */

    //protected IOContext _createContext(Object srcRef, boolean resourceManaged)

    @Override
    protected YAMLParser _createParser(InputStream in, IOContext ctxt) throws IOException {
        return new YAMLParser(ctxt, _getBufferRecycler(), _parserFeatures, _yamlParserFeatures,
                _objectCodec, _createReader(in, null, ctxt));
    }

    @Override
    protected YAMLParser _createParser(Reader r, IOContext ctxt) throws IOException {
        return new YAMLParser(ctxt, _getBufferRecycler(), _parserFeatures, _yamlParserFeatures,
                _objectCodec, r);
    }

    // since 2.4
    @Override
    protected YAMLParser _createParser(char[] data, int offset, int len, IOContext ctxt,
                                       boolean recyclable) throws IOException {
        return new YAMLParser(ctxt, _getBufferRecycler(), _parserFeatures, _yamlParserFeatures,
                _objectCodec, new CharArrayReader(data, offset, len));
    }

    @Override
    protected YAMLParser _createParser(byte[] data, int offset, int len, IOContext ctxt) throws IOException {
        return new YAMLParser(ctxt, _getBufferRecycler(), _parserFeatures, _yamlParserFeatures,
                _objectCodec, _createReader(data, offset, len, null, ctxt));
    }

    @Override
    protected YAMLGenerator _createGenerator(Writer out, IOContext ctxt) throws IOException {
        int feats = _yamlGeneratorFeatures;
        YAMLGenerator gen = new YAMLGenerator(ctxt, _generatorFeatures, feats,
                _objectCodec, out, _version);
        // any other initializations? No?
        return gen;
    }

    @Override
    protected YAMLGenerator _createUTF8Generator(OutputStream out, IOContext ctxt) throws IOException {
        // should never get called; ensure
        throw new IllegalStateException();
    }

    @Override
    protected Writer _createWriter(OutputStream out, JsonEncoding enc, IOContext ctxt) throws IOException {
        if (enc == JsonEncoding.UTF8) {
            return new UTF8Writer(out);
        }
        return new OutputStreamWriter(out, enc.getJavaName());
    }

    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */

    protected final Charset UTF8 = Charset.forName("UTF-8");

    protected Reader _createReader(InputStream in, JsonEncoding enc, IOContext ctxt) throws IOException
    {
        if (enc == null) {
            enc = JsonEncoding.UTF8;
        }
        // default to UTF-8 if encoding missing
        if (enc == JsonEncoding.UTF8) {
            boolean autoClose = ctxt.isResourceManaged() || isEnabled(JsonParser.Feature.AUTO_CLOSE_SOURCE);
            return new UTF8Reader(in, autoClose);
//          return new InputStreamReader(in, UTF8);
        }
        return new InputStreamReader(in, enc.getJavaName());
    }

    protected Reader _createReader(byte[] data, int offset, int len,
                                   JsonEncoding enc, IOContext ctxt) throws IOException
    {
        if (enc == null) {
            enc = JsonEncoding.UTF8;
        }
        // default to UTF-8 if encoding missing
        if (enc == null || enc == JsonEncoding.UTF8) {
            return new UTF8Reader(data, offset, len, true);
        }
        ByteArrayInputStream in = new ByteArrayInputStream(data, offset, len);
        return new InputStreamReader(in, enc.getJavaName());
    }
}
