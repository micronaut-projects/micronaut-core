/*
 * Copyright 2017 original authors
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
package org.particleframework.http;

import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.value.OptionalValues;
import org.particleframework.http.annotation.Produces;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents a media type. See https://www.iana.org/assignments/media-types/media-types.xhtml and https://tools.ietf.org/html/rfc2046
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class MediaType implements CharSequence {

    static {
        ConversionService.SHARED.addConverter(CharSequence.class, MediaType.class, (Function<CharSequence, MediaType>) charSequence ->
                new MediaType(charSequence.toString())
        );
    }

    public static final MediaType[] EMPTY_ARRAY = new MediaType[0];

    /**
     * A wildcard media type representing all types
     */
    public static final String ALL = "*/*";

    /**
     * A wildcard media type representing all types
     */
    public static final MediaType ALL_TYPE = new MediaType(ALL, "all");

    /**
     * Form encoded data: application/x-www-form-urlencoded
     */
    public static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";
    /**
     * Form encoded data: application/x-www-form-urlencoded
     */
    public static final MediaType APPLICATION_FORM_URLENCODED_TYPE = new MediaType(APPLICATION_FORM_URLENCODED);

    /**
     * Multi part form data: multipart/form-data
     */
    public static final String MULTIPART_FORM_DATA = "multipart/form-data";
    /**
     * Multi part form data: multipart/form-data
     */
    public static final MediaType MULTIPART_FORM_DATA_TYPE = new MediaType(MULTIPART_FORM_DATA);

    /**
     * HTML: text/html
     */
    public static final String TEXT_HTML = "text/html";
    /**
     * HTML: text/html
     */
    public static final MediaType TEXT_HTML_TYPE = new MediaType(TEXT_HTML);
    /**
     * XHTML: application/xhtml+xml
     */
    public static final String APPLICATION_XHTML = "application/xhtml+xml";
    /**
     * XHTML: application/xhtml+xml
     */
    public static final MediaType APPLICATION_XHTML_TYPE = new MediaType(APPLICATION_XHTML, "html");
    /**
     * XML: application/xml
     */
    public static final String APPLICATION_XML = "application/xml";
    /**
     * XML: application/xml
     */
    public static final MediaType APPLICATION_XML_TYPE = new MediaType(APPLICATION_XML);
    /**
     * JSON: application/json
     */
    public static final String APPLICATION_JSON = "application/json";
    /**
     * JSON: application/json
     */
    public static final MediaType APPLICATION_JSON_TYPE = new MediaType(MediaType.APPLICATION_JSON);
    /**
     * XML: text/xml
     */
    public static final String TEXT_XML = "text/xml";
    /**
     * XML: text/xml
     */
    public static final MediaType TEXT_XML_TYPE = new MediaType(TEXT_XML);
    /**
     * JSON: text/json
     */
    public static final String TEXT_JSON = "text/json";
    /**
     * JSON: text/json
     */
    public static final MediaType TEXT_JSON_TYPE = new MediaType(TEXT_JSON);

    /**
     * JSON: text/plain
     */
    public static final String TEXT_PLAIN = "text/plain";
    /**
     * JSON: text/plain
     */
    public static final MediaType TEXT_PLAIN_TYPE = new MediaType(TEXT_PLAIN);
    /**
     * HAL JSON: application/hal+json
     */
    public static final String APPLICATION_HAL_JSON = "application/hal+json";
    /**
     * HAL JSON: application/hal+json
     */
    public static final MediaType APPLICATION_HAL_JSON_TYPE = new MediaType(APPLICATION_HAL_JSON);
    /**
     * HAL XML: application/hal+xml
     */
    public static final String APPLICATION_HAL_XML = "application/hal+xml";
    /**
     * HAL XML: application/hal+xml
     */
    public static final MediaType APPLICATION_HAL_XML_TYPE = new MediaType(APPLICATION_HAL_XML);
    /**
     * Atom: application/atom+xml
     */
    public static final String APPLICATION_ATOM_XML = "application/atom+xml";
    /**
     * Atom: application/atom+xml
     */
    public static final MediaType APPLICATION_ATOM_XML_TYPE = new MediaType(APPLICATION_ATOM_XML);
    /**
     * VND Error: application/vnd.error+json
     */
    public static final String APPLICATION_VND_ERROR = "application/vnd.error+json";
    /**
     * VND Error: application/vnd.error+json
     */
    public static final MediaType APPLICATION_VND_ERROR_TYPE = new MediaType(APPLICATION_VND_ERROR);

    /**
     * Server Sent Event: text/event-stream
     */
    public final static String TEXT_EVENT_STREAM = "text/event-stream";

    /**
     * Server Sent Event: text/event-stream
     */
    public static final MediaType TEXT_EVENT_STREAM_TYPE = new MediaType(TEXT_EVENT_STREAM);

    /**
     * JSON Stream: application/x-json-stream
     */
    public final static String APPLICATION_JSON_STREAM = "application/x-json-stream";

    /**
     * JSON Stream: application/x-json-stream
     */
    public final static MediaType APPLICATION_JSON_STREAM_TYPE = new MediaType("application/x-json-stream");

    public static final String CHARSET_PARAMETER = "charset";
    public static final String Q_PARAMETER = "q";
    public static final String V_PARAMETER = "v";

    private static final BigDecimal QUALITY_RATING_NUMBER = new BigDecimal("1.0");
    private static final String QUALITY_RATING = "1.0";
    private static final String SEMICOLON = ";";

    protected final String name;
    protected final String subtype;
    protected final String type;
    protected final String extension;
    protected final Map<CharSequence, String> parameters;

    private BigDecimal qualityNumberField;

    /**
     * Constructs a new media type for the given string
     *
     * @param name The name of the media type. For example application/json
     */
    public MediaType(String name) {
        this(name, null, Collections.emptyMap());
    }

    /**
     * Constructs a new media type for the given string and parameters
     *
     * @param name The name of the media type. For example application/json
     * @param params The parameters
     */
    public MediaType(String name, Map<String, String> params) {
        this(name, null, params);
    }

    /**
     * Constructs a new media type for the given string and extension
     *
     * @param name The name of the media type. For example application/json
     * @param extension The extension of the file using this media type if it differs from the subtype
     */
    public MediaType(String name, String extension) {
        this(name, extension, Collections.emptyMap());
    }

    /**
     * Constructs a new media type for the given string and extension
     *
     * @param name The name of the media type. For example application/json
     * @param extension The extension of the file using this media type if it differs from the subtype
     */
    public MediaType(String name, String extension, Map<String, String> params) {

        if(name == null) {
            throw new IllegalArgumentException("Argument [name] cannot be null");
        }
        String withoutArgs;
        if(name.contains(SEMICOLON)) {
            this.parameters = new LinkedHashMap<>();
            String[] tokenWithArgs = name.split(SEMICOLON);
            withoutArgs = tokenWithArgs[0];
            String[] paramsList = Arrays.copyOfRange(tokenWithArgs, 1, tokenWithArgs.length);
            for(String param : paramsList) {
                int i = param.indexOf('=');
                if (i > -1) {
                    parameters.put(param.substring(0, i).trim(), param.substring(i+1).trim() );
                }
            }
        }
        else {
            this.parameters = Collections.emptyMap();
            withoutArgs = name;
        }
        this.name = withoutArgs;
        int i = withoutArgs.indexOf('/');
        if(i > -1) {
            this.type = withoutArgs.substring(0, i);
            this.subtype = withoutArgs.substring(i + 1, withoutArgs.length());
        }
        else {
            throw new IllegalArgumentException("Invalid mime type: " + name);
        }

        if(extension != null) {
            this.extension = extension;
        }
        else {
            int j = subtype.indexOf('+');
            if(j > -1) {
                this.extension = subtype.substring(j + 1);
            }
            else {
                this.extension = subtype;
            }
        }
        if(params != null) {
            parameters.putAll(params);
        }
    }

    /**
     * @return The name of the mime type without any parameters
     */
    public String getName() {
        return name;
    }

    /**
     * @return The type of the media type. For example for application/hal+json this would return "application"
     */
    public String getType() {
        return this.type;
    }

    /**
     * @return The subtype. For example for application/hal+json this would return "hal+json"
     */
    public String getSubtype() {
        return this.subtype;
    }

    /**
     * @return The extension. For example for application/hal+json this would return "json"
     */
    public String getExtension() {
        return extension;
    }

    /**
     * @return The parameters to the media type
     */
    public OptionalValues<String> getParameters() {
        return OptionalValues.of(String.class, parameters);
    }

    /**
     * @return The quality of the Mime type
     */
    public String getQuality() {
        return parameters.getOrDefault("q", QUALITY_RATING);
    }

    /**
     * @return The quality in BigDecimal form
     */
    public BigDecimal getQualityAsNumber() {
        if(this.qualityNumberField == null) {
            this.qualityNumberField = getOrConvertQualityParameterToBigDecimal(this);
        }
        return this.qualityNumberField;
    }

    /**
     * @return The version of the Mime type
     */
    String getVersion() {
        return parameters.getOrDefault(V_PARAMETER, null);
    }


    @Override
    public int length() {
        return toString().length();
    }

    @Override
    public char charAt(int index) {
        return toString().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return toString().subSequence(start, end);
    }

    public String toString() {
        if(parameters.isEmpty()) {
            return name;
        }
        else {
            return name + ";" + parameters.entrySet().stream().map(Object::toString)
                    .collect(Collectors.joining(";"));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MediaType mediaType = (MediaType) o;

        return name.equals(mediaType.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Create a new {@link MediaType} from the given text
     *
     * @param mediaType The text
     * @return The {@link MediaType}
     */
    public static MediaType of(CharSequence mediaType) {
        return new MediaType(mediaType.toString());
    }

    /**
     * Create a new {@link MediaType} from the given text
     *
     * @param mediaType The text
     * @return The {@link MediaType}
     */
    public static MediaType[] of(CharSequence... mediaType) {
        return Arrays.stream(mediaType).map(txt -> new MediaType(txt.toString())).toArray(MediaType[]::new);
    }

    /**
     * Resolve the {@link MediaType} produced by the given type based on the {@link Produces} annotation
     * @param type The type
     * @return An {@link Optional} {@link MediaType}
     */
    public static Optional<MediaType> fromType(Class<?> type) {
        Produces producesAnn = type.getAnnotation(Produces.class);
        if(producesAnn != null) {
            return Arrays.stream(producesAnn.value()).findFirst().map(MediaType::new);
        }
        return Optional.empty();
    }

    private BigDecimal getOrConvertQualityParameterToBigDecimal(MediaType mt) {
        BigDecimal bd;
        try {
            String q = mt.parameters.getOrDefault(Q_PARAMETER, null);
            if(q == null) return QUALITY_RATING_NUMBER;
            else {
                bd = new BigDecimal(q);
            }
            return bd;
        } catch (NumberFormatException e) {
            bd = QUALITY_RATING_NUMBER;
            return bd;
        }
    }
}
