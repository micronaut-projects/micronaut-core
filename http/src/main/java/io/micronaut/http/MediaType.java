/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ImmutableArgumentConversionContext;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.http.annotation.Produces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Represents a media type.
 * See https://www.iana.org/assignments/media-types/media-types.xhtml and https://tools.ietf.org/html/rfc2046
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@TypeHint(value = MediaType[].class)
public class MediaType implements CharSequence {

    /**
     * Default file extension used for JSON.
     */
    public static final String EXTENSION_JSON = "json";

    /**
     * Default file extension used for XML.
     */
    public static final String EXTENSION_XML = "xml";

    /**
     * Default file extension used for PDF.
     */
    public static final String EXTENSION_PDF = "pdf";

    /**
     * File extension used for Microsoft Excel Open XML Spreadsheet (XLSX).
     */
    public static final String EXTENSION_XLSX = "xlsx";

    /**
     * File extension for Microsoft Excel's workbook files in use between 97-2003.
     */
    public static final String EXTENSION_XLS = "xls";

    /**
     * Default empty media type array.
     */
    public static final MediaType[] EMPTY_ARRAY = new MediaType[0];

    /**
     * A wildcard media type representing all types.
     */
    public static final String ALL = "*/*";

    /**
     * A wildcard media type representing all types.
     */
    public static final MediaType ALL_TYPE = new MediaType(ALL, "all");

    /**
     * Form encoded data: application/x-www-form-urlencoded.
     */
    public static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";

    /**
     * Form encoded data: application/x-www-form-urlencoded.
     */
    public static final MediaType APPLICATION_FORM_URLENCODED_TYPE = new MediaType(APPLICATION_FORM_URLENCODED);

    /**
     * Shortcut for {@link #APPLICATION_FORM_URLENCODED_TYPE}.
     */
    public static final MediaType FORM = APPLICATION_FORM_URLENCODED_TYPE;

    /**
     * Multi part form data: multipart/form-data.
     */
    public static final String MULTIPART_FORM_DATA = "multipart/form-data";

    /**
     * Multi part form data: multipart/form-data.
     */
    public static final MediaType MULTIPART_FORM_DATA_TYPE = new MediaType(MULTIPART_FORM_DATA);

    /**
     * HTML: text/html.
     */
    public static final String TEXT_HTML = "text/html";

    /**
     * HTML: text/html.
     */
    public static final MediaType TEXT_HTML_TYPE = new MediaType(TEXT_HTML);

    /**
     * CSV: text/csv.
     */
    public static final String TEXT_CSV = "text/csv";

    /**
     * CSV: text/csv.
     */
    public static final MediaType TEXT_CSV_TYPE = new MediaType(TEXT_CSV);

    /**
     * XHTML: application/xhtml+xml.
     */
    public static final String APPLICATION_XHTML = "application/xhtml+xml";

    /**
     * XHTML: application/xhtml+xml.
     */
    public static final MediaType APPLICATION_XHTML_TYPE = new MediaType(APPLICATION_XHTML, "html");

    /**
     * XML: application/xml.
     */
    public static final String APPLICATION_XML = "application/xml";

    /**
     * XML: application/xml.
     */
    public static final MediaType APPLICATION_XML_TYPE = new MediaType(APPLICATION_XML);

    /**
     * JSON GitHub: application/vnd.github+json.
     */
    public static final String APPLICATION_JSON_GITHUB = "application/vnd.github+json";

    /**
     * JSON GitHub: application/vnd.github+json.
     */
    public static final MediaType APPLICATION_JSON_GITHUB_TYPE = new MediaType(MediaType.APPLICATION_JSON_GITHUB);

    /**
     * JSON Feed: application/feed+json.
     */
    public static final String APPLICATION_JSON_FEED = "application/feed+json";

    /**
     * JSON Feed: application/feed+json.
     */
    public static final MediaType APPLICATION_JSON_FEED_TYPE = new MediaType(MediaType.APPLICATION_JSON_FEED);

    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6902/">JSON Patch</a>
     * JSON Patch: application/json-patch+json.
     */
    public static final String APPLICATION_JSON_PATCH = "application/json-patch+json";

    /**
     * JSON Patch: application/json-patch+json.
     */
    public static final MediaType APPLICATION_JSON_PATCH_TYPE = new MediaType(MediaType.APPLICATION_JSON_PATCH);

    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7386">JSON Merge Patch</a>
     * JSON Merge Patch: application/merge-patch+json
     */
    public static final String APPLICATION_JSON_MERGE_PATCH = "application/merge-patch+json";

    /**
     * JSON Merge Patch: application/merge-patch+json.
     */
    public static final MediaType APPLICATION_JSON_MERGE_PATCH_TYPE = new MediaType(MediaType.APPLICATION_JSON_MERGE_PATCH);

    /**
     * JSON Feed: application/problem+json.
     */
    public static final String APPLICATION_JSON_PROBLEM = "application/problem+json";

    /**
     * JSON Feed: application/problem+json.
     */
    public static final MediaType APPLICATION_JSON_PROBLEM_TYPE = new MediaType(MediaType.APPLICATION_JSON_PROBLEM);

    /**
     * JSON: application/json.
     */
    public static final String APPLICATION_JSON = "application/json";

    /**
     * JSON: application/json.
     */
    public static final MediaType APPLICATION_JSON_TYPE = new MediaType(MediaType.APPLICATION_JSON);

    /**
     * YAML: application/yaml.
     */
    public static final String APPLICATION_YAML = "application/yaml";

    /**
     * YAML: application/yaml.
     */
    public static final MediaType APPLICATION_YAML_TYPE = new MediaType(MediaType.APPLICATION_YAML);

    /**
     * XML: Microsoft Excel Open XML Spreadsheet (XLSX).
     */
    public static final String MICROSOFT_EXCEL_OPEN_XML = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * XML: Microsoft Excel Open XML Spreadsheet (XLSX).
     */
    public static final MediaType MICROSOFT_EXCEL_OPEN_XML_TYPE = new MediaType(MICROSOFT_EXCEL_OPEN_XML, EXTENSION_XLSX);

    /**
     * Microsoft Excel's workbook files in use between 97-2003.
     */
    public static final String MICROSOFT_EXCEL = "application/vnd.ms-excel";

    /**
     * Microsoft Excel's workbook files in use between 97-2003.
     */
    public static final MediaType MICROSOFT_EXCEL_TYPE = new MediaType(MICROSOFT_EXCEL, EXTENSION_XLS);

    /**
     * XML: text/xml.
     */
    public static final String TEXT_XML = "text/xml";

    /**
     * XML: text/xml.
     */
    public static final MediaType TEXT_XML_TYPE = new MediaType(TEXT_XML);

    /**
     * JSON: text/json.
     */
    public static final String TEXT_JSON = "text/json";

    /**
     * JSON: text/json.
     */
    public static final MediaType TEXT_JSON_TYPE = new MediaType(TEXT_JSON);

    /**
     * Plain Text: text/plain.
     */
    public static final String TEXT_PLAIN = "text/plain";

    /**
     * Plain Text: text/plain.
     */
    public static final MediaType TEXT_PLAIN_TYPE = new MediaType(TEXT_PLAIN);

    /**
     * HAL JSON: application/hal+json.
     */
    public static final String APPLICATION_HAL_JSON = "application/hal+json";

    /**
     * HAL JSON: application/hal+json.
     */
    public static final MediaType APPLICATION_HAL_JSON_TYPE = new MediaType(APPLICATION_HAL_JSON);

    /**
     * HAL XML: application/hal+xml.
     */
    public static final String APPLICATION_HAL_XML = "application/hal+xml";

    /**
     * HAL XML: application/hal+xml.
     */
    public static final MediaType APPLICATION_HAL_XML_TYPE = new MediaType(APPLICATION_HAL_XML);

    /**
     * Atom: application/atom+xml.
     */
    public static final String APPLICATION_ATOM_XML = "application/atom+xml";

    /**
     * Atom: application/atom+xml.
     */
    public static final MediaType APPLICATION_ATOM_XML_TYPE = new MediaType(APPLICATION_ATOM_XML);

    /**
     * VND Error: application/vnd.error+json.
     */
    public static final String APPLICATION_VND_ERROR = "application/vnd.error+json";

    /**
     * VND Error: application/vnd.error+json.
     */
    public static final MediaType APPLICATION_VND_ERROR_TYPE = new MediaType(APPLICATION_VND_ERROR);

    /**
     * Server Sent Event: text/event-stream.
     */
    public static final String TEXT_EVENT_STREAM = "text/event-stream";

    /**
     * Server Sent Event: text/event-stream.
     */
    public static final MediaType TEXT_EVENT_STREAM_TYPE = new MediaType(TEXT_EVENT_STREAM);

    /**
     * JSON Stream: application/x-json-stream.
     */
    public static final String APPLICATION_JSON_STREAM = "application/x-json-stream";

    /**
     * JSON Stream: application/x-json-stream.
     */
    public static final MediaType APPLICATION_JSON_STREAM_TYPE = new MediaType(APPLICATION_JSON_STREAM);

    /**
     * BINARY: application/octet-stream.
     */
    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    /**
     * BINARY: application/octet-stream.
     */
    public static final MediaType APPLICATION_OCTET_STREAM_TYPE = new MediaType(APPLICATION_OCTET_STREAM);

    /**
     * GraphQL: application/graphql.
     */
    public static final String APPLICATION_GRAPHQL = "application/graphql";

    /**
     * GraphQL: application/graphql.
     */
    public static final MediaType APPLICATION_GRAPHQL_TYPE = new MediaType(APPLICATION_GRAPHQL);

    /**
     * PDF: application/pdf.
     */
    public static final String APPLICATION_PDF = "application/pdf";

    /**
     * PDF: application/pdf.
     */
    public static final MediaType APPLICATION_PDF_TYPE = new MediaType(APPLICATION_PDF);

    /**
     * Png Image: image/png.
     */
    public static final String IMAGE_PNG = "image/png";

    /**
     * Png Image: image/png.
     */
    public static final MediaType IMAGE_PNG_TYPE = new MediaType(IMAGE_PNG);

    /**
     * Jpeg Image: image/jpeg.
     */
    public static final String IMAGE_JPEG = "image/jpeg";

    /**
     * Jpeg Image: image/jpeg.
     */
    public static final MediaType IMAGE_JPEG_TYPE = new MediaType(IMAGE_JPEG);

    /**
     * Gif Image: image/gif.
     */
    public static final String IMAGE_GIF = "image/gif";

    /**
     * Gif Image: image/gif.
     */
    public static final MediaType IMAGE_GIF_TYPE = new MediaType(IMAGE_GIF);

    /**
     * Webp Image: image/webp.
     */
    public static final String IMAGE_WEBP = "image/webp";

    /**
     * Webp Image: image/webp.
     */
    public static final MediaType IMAGE_WEBP_TYPE = new MediaType(IMAGE_WEBP);

    /**
     * Parameter {@code "charset"}.
     */
    public static final String CHARSET_PARAMETER = "charset";

    /**
     * Parameter {@code "q"}.
     */
    public static final String Q_PARAMETER = "q";

    /**
     * Parameter {@code "v"}.
     */
    public static final String V_PARAMETER = "v";

    @Internal
    static final Argument<MediaType> ARGUMENT = Argument.of(MediaType.class);

    @Internal
    static final ArgumentConversionContext<MediaType> CONVERSION_CONTEXT = ImmutableArgumentConversionContext.of(ARGUMENT);

    private static final char SEMICOLON = ';';

    @SuppressWarnings("ConstantName")
    private static final String MIME_TYPES_FILE_NAME = "META-INF/http/mime.types";
    private static Map<String, String> mediaTypeFileExtensions;
    @SuppressWarnings("ConstantName")
    private static final List<Pattern> textTypePatterns = new ArrayList<>(4);

    protected final String name;
    protected final String subtype;
    protected final String type;
    protected final String extension;
    protected final Map<CharSequence, String> parameters;
    private final String strRepr;
    private final String lowerName;

    private BigDecimal qualityNumberField = BigDecimal.ONE;

    private boolean valid;

    static {
        textTypePatterns.add(Pattern.compile("^text/.*$"));
        textTypePatterns.add(Pattern.compile("^.*\\+json$"));
        textTypePatterns.add(Pattern.compile("^.*\\+text$"));
        textTypePatterns.add(Pattern.compile("^.*\\+xml$"));
        textTypePatterns.add(Pattern.compile("^application/javascript$"));
    }

    /**
     * Constructs a new media type for the given string.
     *
     * @param name The name of the media type. For example application/json
     */
    public MediaType(String name) {
        this(name, null, Collections.emptyMap());
    }

    /**
     * Constructs a new media type for the given string and parameters.
     *
     * @param name   The name of the media type. For example application/json
     * @param params The parameters
     */
    public MediaType(String name, Map<String, String> params) {
        this(name, null, params);
    }

    /**
     * Constructs a new media type for the given string and extension.
     *
     * @param name      The name of the media type. For example application/json
     * @param extension The extension of the file using this media type if it differs from the subtype
     */
    public MediaType(String name, String extension) {
        this(name, extension, Collections.emptyMap());
    }

    /**
     * Constructs a new media type for the given string and extension.
     *
     * @param name      The name of the media type. For example application/json
     * @param extension The extension of the file using this media type if it differs from the subtype
     * @param params    The parameters
     */
    public MediaType(String name, String extension, Map<String, String> params) {
        if (name == null) {
            throw new IllegalArgumentException("Argument [name] cannot be null");
        }
        name = name.trim();
        String withoutArgs;
        Iterator<String> splitIt = StringUtils.splitOmitEmptyStringsIterator(name, SEMICOLON);
        if (splitIt.hasNext()) {
            withoutArgs = splitIt.next();
            if (splitIt.hasNext()) {
                Map<CharSequence, String> parameters = null;
                while (splitIt.hasNext()) {
                    String paramExpression = splitIt.next();
                    int i = paramExpression.indexOf('=');
                    if (i > -1) {
                        String paramName = paramExpression.substring(0, i).trim();
                        String paramValue = paramExpression.substring(i + 1).trim();
                        if ("q".equals(paramName)) {
                            qualityNumberField = new BigDecimal(paramValue);
                        }
                        if (parameters == null) {
                            parameters = new LinkedHashMap<>();
                        }
                        parameters.put(paramName, paramValue);
                    }
                }
                if (parameters == null) {
                    parameters = Collections.emptyMap();
                }
                this.parameters = parameters;
            } else if (params == null) {
                this.parameters = Collections.emptyMap();
            } else {
                this.parameters = (Map) params;
            }
        } else {
            if (params == null) {
                this.parameters = Collections.emptyMap();
            } else {
                this.parameters = (Map) params;
            }
            withoutArgs = name;
        }
        this.name = withoutArgs;
        this.lowerName = withoutArgs.toLowerCase(Locale.ROOT);
        int i = withoutArgs.indexOf('/');
        if (i > -1) {
            this.type = withoutArgs.substring(0, i);
            this.subtype = withoutArgs.substring(i + 1);
        } else {
            throw new IllegalArgumentException("Invalid mime type: " + name);
        }

        if (extension != null) {
            this.extension = extension;
        } else {
            int j = subtype.indexOf('+');
            if (j > -1) {
                this.extension = subtype.substring(j + 1);
            } else {
                this.extension = subtype;
            }
        }
        this.strRepr = toString0();
    }

    /**
     * Create a new or get a {@link MediaType} from the given text.
     *
     * @param mediaType The text
     * @return The {@link MediaType}
     */
    public static MediaType of(String mediaType) {
        switch (mediaType) {
            case ALL:
                return ALL_TYPE;
            case APPLICATION_FORM_URLENCODED:
                return APPLICATION_FORM_URLENCODED_TYPE;
            case MULTIPART_FORM_DATA:
                return MULTIPART_FORM_DATA_TYPE;
            case TEXT_HTML:
                return TEXT_HTML_TYPE;
            case TEXT_CSV:
                return TEXT_CSV_TYPE;
            case APPLICATION_XHTML:
                return APPLICATION_XHTML_TYPE;
            case APPLICATION_XML:
                return APPLICATION_XML_TYPE;
            case APPLICATION_JSON:
                return APPLICATION_JSON_TYPE;
            case APPLICATION_JSON_FEED:
                return APPLICATION_JSON_FEED_TYPE;
            case APPLICATION_JSON_GITHUB:
                return APPLICATION_JSON_GITHUB_TYPE;
            case APPLICATION_JSON_PATCH:
                return APPLICATION_JSON_PATCH_TYPE;
            case APPLICATION_JSON_MERGE_PATCH:
                return APPLICATION_JSON_MERGE_PATCH_TYPE;
            case APPLICATION_JSON_PROBLEM:
                return APPLICATION_JSON_PROBLEM_TYPE;
            case APPLICATION_YAML:
                return APPLICATION_YAML_TYPE;
            case TEXT_XML:
                return TEXT_XML_TYPE;
            case TEXT_JSON:
                return TEXT_JSON_TYPE;
            case TEXT_PLAIN:
                return TEXT_PLAIN_TYPE;
            case APPLICATION_HAL_JSON:
                return APPLICATION_HAL_JSON_TYPE;
            case APPLICATION_HAL_XML:
                return APPLICATION_HAL_XML_TYPE;
            case APPLICATION_ATOM_XML:
                return APPLICATION_ATOM_XML_TYPE;
            case APPLICATION_VND_ERROR:
                return APPLICATION_VND_ERROR_TYPE;
            case TEXT_EVENT_STREAM:
                return TEXT_EVENT_STREAM_TYPE;
            case APPLICATION_JSON_STREAM:
                return APPLICATION_JSON_STREAM_TYPE;
            case APPLICATION_OCTET_STREAM:
                return APPLICATION_OCTET_STREAM_TYPE;
            case APPLICATION_GRAPHQL:
                return APPLICATION_GRAPHQL_TYPE;
            case APPLICATION_PDF:
                return APPLICATION_PDF_TYPE;
            case IMAGE_PNG:
                return IMAGE_PNG_TYPE;
            case IMAGE_JPEG:
                return IMAGE_JPEG_TYPE;
            case IMAGE_GIF:
                return IMAGE_GIF_TYPE;
            case IMAGE_WEBP:
                return IMAGE_WEBP_TYPE;
            default:
                return new MediaType(mediaType);
        }
    }

    /**
     * Determine if this requested content type can be satisfied by a given content type. e.g. text/* will be satisfied by test/html.
     *
     * @param expectedContentType   Content type to match against
     * @return if successful match
     */
    public boolean matches(@NonNull MediaType expectedContentType) {
        //noinspection ConstantConditions
        if (expectedContentType == null) {
            return false;
        }
        if (expectedContentType == this) {
            return true;
        }
        String expectedType = expectedContentType.getType();
        String expectedSubtype = expectedContentType.getSubtype();
        boolean typeMatch = type.equals("*") || type.equalsIgnoreCase(expectedType);
        boolean subtypeMatch = subtype.equals("*") || subtype.equalsIgnoreCase(expectedSubtype);
        return typeMatch && subtypeMatch;
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
        return qualityNumberField.toString();
    }

    /**
     * @return The quality in BigDecimal form
     */
    public BigDecimal getQualityAsNumber() {
        return this.qualityNumberField;
    }

    /**
     * @return The version of the Mime type
     */
    public String getVersion() {
        return parameters.getOrDefault(V_PARAMETER, null);
    }

    /**
     * @return The charset of the media type if specified
     */
    public Optional<Charset> getCharset() {
        return getParameters().get(CHARSET_PARAMETER).map(Charset::forName);
    }

    @Override
    public int length() {
        return strRepr.length();
    }

    @Override
    public char charAt(int index) {
        return strRepr.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return strRepr.subSequence(start, end);
    }

    /**
     * @return Whether the media type is text based
     */
    public boolean isTextBased() {
        boolean matches = textTypePatterns.stream().anyMatch(p -> p.matcher(name).matches());
        if (!matches) {
            matches = subtype.equalsIgnoreCase("json") || subtype.equalsIgnoreCase("xml") || subtype.equalsIgnoreCase("yaml");
        }
        return matches;
    }

    /**
     * @param contentType The content type
     * @return Whether the content type is text based
     */
    public static boolean isTextBased(String contentType) {
        if (StringUtils.isEmpty(contentType)) {
            return false;
        }
        try {
            return of(contentType).isTextBased();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Validate this media type for sending as an HTTP header. This is an optimization to only run
     * the validation once if possible. If the validation function does not throw, future calls to
     * this method will not call the validation function again.
     *
     * @param r Validation function
     */
    @Internal
    public void validate(Runnable r) {
        if (!valid) {
            r.run();
            valid = true;
        }
    }

    @Override
    public String toString() {
        return strRepr;
    }

    private String toString0() {
        if (parameters.isEmpty()) {
            return name;
        } else {
            StringBuilder sb = new StringBuilder(name);
            parameters.forEach((name, value) -> {
                sb.append(";");
                sb.append(name);
                sb.append("=");
                sb.append(value);
            });
            return sb.toString();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Only the name is matched. Parameters are not included.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MediaType mediaType = (MediaType) o;

        return lowerName.equals(mediaType.lowerName);
    }

    @Override
    public int hashCode() {
        return lowerName.hashCode();
    }

    /**
     * Returns the ordered media types for the given values.
     * @param values The values
     * @return The media types.
     * @since 1.3.3
     */
    public static List<MediaType> orderedOf(CharSequence... values) {
        return orderedOf(Arrays.asList(values));
    }

    /**
     * Returns the ordered media types for the given values.
     * @param values The values
     * @return The media types.
     * @since 1.3.3
     */
    public static List<MediaType> orderedOf(List<? extends CharSequence> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        int headerCount = values.size();
        if (headerCount == 0) {
            return Collections.emptyList();
        }
        if (headerCount == 1) {
            // fast path for single header with single media type
            String singleHeader = values.get(0).toString();
            if (singleHeader.indexOf(',') == -1) {
                try {
                    return List.of(MediaType.of(singleHeader));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        List<MediaType> mediaTypes = new ArrayList<>();
        for (CharSequence value : values) {
            for (String token : StringUtils.splitOmitEmptyStrings(value, ',')) {
                try {
                    mediaTypes.add(MediaType.of(token));
                } catch (IllegalArgumentException e) {
                    // ignore
                }
            }
        }
        mediaTypes.sort((o1, o2) -> {
            //The */* type is always last
            boolean fullWildcard1 = o1.type.equals("*");
            boolean fullWildcard2 = o2.type.equals("*");
            if (fullWildcard1 && fullWildcard2) {
                return 0;
            } else if (fullWildcard1) {
                return 1;
            } else if (fullWildcard2) {
                return -1;
            }
            if (o2.subtype.equals("*") && !o1.subtype.equals("*")) {
                return -1;
            } else if (o1.subtype.equals("*") && !o2.subtype.equals("*")) {
                return 1;
            }
            return o2.getQualityAsNumber().compareTo(o1.getQualityAsNumber());
        });
        return Collections.unmodifiableList(mediaTypes);
    }

    /**
     * Create a new {@link MediaType} from the given text.
     *
     * @param mediaType The text
     * @return The {@link MediaType}
     */
    public static MediaType of(CharSequence mediaType) {
        return MediaType.of(mediaType.toString());
    }

    /**
     * Create a new {@link MediaType} from the given text.
     *
     * @param mediaType The text
     * @return The {@link MediaType}
     */
    public static MediaType[] of(CharSequence... mediaType) {
        MediaType[] types = new MediaType[mediaType.length];
        for (int i = 0; i < mediaType.length; i++) {
            types[i] = MediaType.of(mediaType[i].toString());
        }
        return types;
    }

    /**
     * Resolve the {@link MediaType} produced by the given type based on the {@link Produces} annotation.
     *
     * @param type The type
     * @return An {@link Optional} {@link MediaType}
     */
    public static Optional<MediaType> fromType(Class<?> type) {
        Produces producesAnn = type.getAnnotation(Produces.class);
        if (producesAnn != null) {
            String[] value = producesAnn.value();
            if (ArrayUtils.isNotEmpty(value)) {
                return Optional.of(MediaType.of(value[0]));
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve the {@link MediaType} for the given file extension.
     *
     * @param extension The file extension
     * @return The {@link MediaType}
     */
    public static Optional<MediaType> forExtension(String extension) {
        if (StringUtils.isNotEmpty(extension)) {
            Map<String, String> extensions = getMediaTypeFileExtensions();
            if (extensions != null) {
                String type = extensions.get(extension);
                if (type != null) {
                    return Optional.of(new MediaType(type, extension));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve the {@link MediaType} for the given file name. Defaults
     * to text/plain.
     *
     * @param filename The file name
     * @return The {@link MediaType}
     */
    public static MediaType forFilename(String filename) {
        if (StringUtils.isNotEmpty(filename)) {
            return forExtension(NameUtils.extension(filename)).orElse(MediaType.TEXT_PLAIN_TYPE);
        }
        return MediaType.TEXT_PLAIN_TYPE;
    }

    @SuppressWarnings("MagicNumber")
    private static Map<String, String> getMediaTypeFileExtensions() {
        Map<String, String> extensions = mediaTypeFileExtensions;
        if (extensions == null) {
            synchronized (MediaType.class) { // double check
                extensions = mediaTypeFileExtensions;
                if (extensions == null) {
                    try {
                        extensions = loadMimeTypes();
                        mediaTypeFileExtensions = extensions;
                    } catch (Exception e) {
                        mediaTypeFileExtensions = Collections.emptyMap();
                    }
                }
            }
        }
        return extensions;
    }

    @SuppressWarnings("MagicNumber")
    private static Map<String, String> loadMimeTypes() {
        try (InputStream is = MediaType.class.getClassLoader().getResourceAsStream(MIME_TYPES_FILE_NAME)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.US_ASCII));
            Map<String, String> result = new LinkedHashMap<>(100);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String formattedLine = line.trim().replaceAll("\\s{2,}", " ").replaceAll("\\s", "|");
                String[] tokens = formattedLine.split("\\|");
                for (int i = 1; i < tokens.length; i++) {
                    String fileExtension = tokens[i].toLowerCase(Locale.ENGLISH);
                    result.put(fileExtension, tokens[0]);
                }
            }
            return result;
        } catch (IOException ex) {
            Logger logger = LoggerFactory.getLogger(MediaType.class);
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to load mime types for file extension detection!");
            }
        }

        return Collections.emptyMap();
    }
}
