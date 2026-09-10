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
import org.jspecify.annotations.Nullable;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReferenceArray;
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
     * File extension for OpenDocument spreadsheets.
     */
    public static final String EXTENSION_ODS = "ods";

    /**
     * File extension used for Microsoft Word Open XML document (DOCX).
     */
    public static final String EXTENSION_DOCX = "docx";

    /**
     * File extension for Microsoft Word document files in use between 97-2003.
     */
    public static final String EXTENSION_DOC = "doc";

    /**
     * File extension for OpenDocument text files.
     */
    public static final String EXTENSION_ODT = "odt";

    /**
     * File extension used for Microsoft Powerpoint Open XML document (PPTX).
     */
    public static final String EXTENSION_PPTX = "pptx";

    /**
     * File extension for Microsoft Powerpoint files in use between 97-2003.
     */
    public static final String EXTENSION_PPT = "ppt";

    /**
     * File extension for OpenDocument presentation files.
     */
    public static final String EXTENSION_ODP = "odp";

    /**
     * File extension for GPS Exchange Format files.
     */
    public static final String EXTENSION_GPX = "gpx";

    /**
     * File extension for ZIP archive files.
     */
    public static final String EXTENSION_ZIP = "zip";

    /**
     * File extension for GZIP compressed files.
     */
    public static final String EXTENSION_GZIP = "gz";

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
     * Multi part form data: multipart/form-data.
     */
    public static final String MULTIPART_FORM_DATA = "multipart/form-data";

    /**
     * Multi part form data: multipart/form-data.
     */
    public static final MediaType MULTIPART_FORM_DATA_TYPE = new MediaType(MULTIPART_FORM_DATA);

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
     * JSON Schema: application/schema+json.
     */
    public static final String APPLICATION_JSON_SCHEMA = "application/schema+json";

    /**
     * JSON Schema: application/schema+json.
     */
    public static final MediaType APPLICATION_JSON_SCHEMA_TYPE = new MediaType(MediaType.APPLICATION_JSON_SCHEMA);

    /**
     * JSON Schema: application/scim+json.
     */
    public static final String APPLICATION_SCIM_JSON = "application/scim+json";

    /**
     * JSON Schema: application/scim+json.
     */
    public static final MediaType APPLICATION_SCIM_JSON_TYPE = new MediaType(MediaType.APPLICATION_SCIM_JSON);

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
     * OpenDocument spreadsheet: application/vnd.oasis.opendocument.spreadsheet.
     */
    public static final String OPEN_DOCUMENT_SPREADSHEET = "application/vnd.oasis.opendocument.spreadsheet";

    /**
     * OpenDocument spreadsheet: application/vnd.oasis.opendocument.spreadsheet.
     */
    public static final MediaType OPEN_DOCUMENT_SPREADSHEET_TYPE = new MediaType(OPEN_DOCUMENT_SPREADSHEET, EXTENSION_ODS);

    /**
     * XML: Microsoft Word Open XML (DOCX).
     */
    public static final String MICROSOFT_WORD_OPEN_XML = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /**
     * XML: Microsoft Word Open XML (DOCX).
     */
    public static final MediaType MICROSOFT_WORD_OPEN_XML_TYPE = new MediaType(MICROSOFT_WORD_OPEN_XML, EXTENSION_DOCX);

    /**
     * Microsoft Word files in use between 97-2003.
     */
    public static final String MICROSOFT_WORD = "application/msword";

    /**
     * Microsoft Word files in use between 97-2003.
     */
    public static final MediaType MICROSOFT_WORD_TYPE = new MediaType(MICROSOFT_WORD, EXTENSION_DOC);

    /**
     * OpenDocument text: application/vnd.oasis.opendocument.text.
     */
    public static final String OPEN_DOCUMENT_TEXT = "application/vnd.oasis.opendocument.text";

    /**
     * OpenDocument text: application/vnd.oasis.opendocument.text.
     */
    public static final MediaType OPEN_DOCUMENT_TEXT_TYPE = new MediaType(OPEN_DOCUMENT_TEXT, EXTENSION_ODT);

    /**
     * XML: Microsoft Powerpoint XML (PPTX).
     */
    public static final String MICROSOFT_POWERPOINT_OPEN_XML = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    /**
     * XML: Microsoft Powerpoint Open XML (PPTX).
     */
    public static final MediaType MICROSOFT_POWERPOINT_OPEN_XML_TYPE = new MediaType(MICROSOFT_POWERPOINT_OPEN_XML, EXTENSION_PPTX);

    /**
     * Microsoft Powerpoint files in use between 97-2003.
     */
    public static final String MICROSOFT_POWERPOINT = "application/vnd.ms-powerpoint";

    /**
     * Microsoft Powerpoint files in use between 97-2003.
     */
    public static final MediaType MICROSOFT_POWERPOINT_TYPE = new MediaType(MICROSOFT_POWERPOINT, EXTENSION_PPT);

    /**
     * OpenDocument presentation: application/vnd.oasis.opendocument.presentation.
     */
    public static final String OPEN_DOCUMENT_PRESENTATION = "application/vnd.oasis.opendocument.presentation";

    /**
     * OpenDocument presentation: application/vnd.oasis.opendocument.presentation.
     */
    public static final MediaType OPEN_DOCUMENT_PRESENTATION_TYPE = new MediaType(OPEN_DOCUMENT_PRESENTATION, EXTENSION_ODP);

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
     * GPS Exchange Format: application/gpx+xml.
     */
    public static final String APPLICATION_GPX_XML = "application/gpx+xml";

    /**
     * GPS Exchange Format: application/gpx+xml.
     */
    public static final MediaType GPX_XML_TYPE = new MediaType(APPLICATION_GPX_XML, EXTENSION_GPX);

    /**
     * ZIP archive format: application/zip.
     */
    public static final String APPLICATION_ZIP = "application/zip";

    /**
     * ZIP archive format: application/zip.
     */
    public static final MediaType ZIP_TYPE = new MediaType(APPLICATION_ZIP);

    /**
     * GZip compressed data: application/gzip.
     */
    public static final String APPLICATION_GZIP = "application/gzip";

    /**
     * GZip compressed data: application/gzip.
     */
    public static final MediaType GZIP_TYPE = new MediaType(APPLICATION_GZIP);

    /**
     * YANG format data: application/yang.
     */
    public static final String APPLICATION_YANG = "application/yang";

    /**
     * YANG format data: application/yang.
     */
    public static final MediaType YANG_TYPE = new MediaType(APPLICATION_YANG);

    /**
     * CUE format data: application/x-cue.
     */
    public static final String APPLICATION_CUE = "application/x-cue";

    /**
     * CUE format data: application/x-cue.
     */
    public static final MediaType CUE_TYPE = new MediaType(APPLICATION_CUE);

    /**
     * TOML format data: application/toml.
     */
    public static final String APPLICATION_TOML = "application/toml";

    /**
     * TOML format data: application/toml.
     */
    public static final MediaType TOML_TYPE = new MediaType(APPLICATION_TOML);

    /**
     * RTF format data: application/rtf.
     */
    public static final String APPLICATION_RTF = "application/rtf";

    /**
     * RTF format data: application/rtf.
     */
    public static final MediaType RTF_TYPE = new MediaType(APPLICATION_RTF);

    /**
     * Zlib compressed data: application/zlib.
     */
    public static final String APPLICATION_ZLIB = "application/zlib";

    /**
     * Zlib compressed data: application/zlib.
     */
    public static final MediaType ZLIB_TYPE = new MediaType(APPLICATION_ZLIB);

    /**
     * Zstd compressed data: application/zstd.
     */
    public static final String APPLICATION_ZSTD = "application/zstd";

    /**
     * Zstd compressed data: application/zstd.
     */
    public static final MediaType ZSTD_TYPE = new MediaType(APPLICATION_ZSTD);

    /**
     * PDF: application/pdf.
     */
    public static final String APPLICATION_PDF = "application/pdf";

    /**
     * PDF: application/pdf.
     */
    public static final MediaType APPLICATION_PDF_TYPE = new MediaType(APPLICATION_PDF);

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
     * CSS: text/css.
     */
    public static final String TEXT_CSS = "text/css";

    /**
     * CSS: text/css.
     */
    public static final MediaType TEXT_CSS_TYPE = new MediaType(TEXT_CSS);

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
     * Text javascript: text/javascript.
     */
    public static final String TEXT_JAVASCRIPT = "text/javascript";

    /**
     * Text javascript: text/javascript.
     */
    public static final MediaType TEXT_JAVASCRIPT_TYPE = new MediaType(TEXT_JAVASCRIPT);

    /**
     * Text ecmascript: text/ecmascript.
     */
    public static final String TEXT_ECMASCRIPT = "text/ecmascript";

    /**
     * Text ecmascript: text/ecmascript.
     */
    public static final MediaType TEXT_ECMASCRIPT_TYPE = new MediaType(TEXT_ECMASCRIPT);

    /**
     * Plain Text: text/plain.
     */
    public static final String TEXT_PLAIN = "text/plain";

    /**
     * Plain Text: text/plain.
     */
    public static final MediaType TEXT_PLAIN_TYPE = new MediaType(TEXT_PLAIN);

    /**
     * Markdown: text/markdown.
     */
    public static final String TEXT_MARKDOWN = "text/markdown";

    /**
     * Markdown: text/markdown.
     */
    public static final MediaType TEXT_MARKDOWN_TYPE = new MediaType(TEXT_MARKDOWN);

    /**
     * Server Sent Event: text/event-stream.
     */
    public static final String TEXT_EVENT_STREAM = "text/event-stream";

    /**
     * Server Sent Event: text/event-stream.
     */
    public static final MediaType TEXT_EVENT_STREAM_TYPE = new MediaType(TEXT_EVENT_STREAM);

    /**
     * Animated Portable Network Graphics (APNG): image/apng.
     */
    public static final String IMAGE_APNG = "image/apng";

    /**
     * Animated Portable Network Graphics (APNG): image/apng.
     */
    public static final MediaType IMAGE_APNG_TYPE = new MediaType(IMAGE_APNG);

    /**
     * Bitmap file: image/bmp.
     */
    public static final String IMAGE_BMP = "image/bmp";

    /**
     * Bitmap file: image/bmp.
     */
    public static final MediaType IMAGE_BMP_TYPE = new MediaType(IMAGE_BMP);

    /**
     * Microsoft Icon: image/x-icon.
     */
    public static final String IMAGE_X_ICON = "image/x-icon";

    /**
     * Microsoft Icon: image/x-icon.
     */
    public static final MediaType IMAGE_X_ICON_TYPE = new MediaType(IMAGE_X_ICON);

    /**
     * Tagged Image File Format: image/tiff.
     */
    public static final String IMAGE_TIFF = "image/tiff";

    /**
     * Tagged Image File Format: image/tiff.
     */
    public static final MediaType IMAGE_TIFF_TYPE = new MediaType(IMAGE_TIFF);

    /**
     * AV1 Image File Format (AVIF): image/avif.
     */
    public static final String IMAGE_AVIF = "image/avif";

    /**
     * AV1 Image File Format (AVIF): image/avif.
     */
    public static final MediaType IMAGE_AVIF_TYPE = new MediaType(IMAGE_AVIF);

    /**
     * Scalable Vector Graphics (SVG): image/svg+xml.
     */
    public static final String IMAGE_SVG = "image/svg+xml";

    /**
     * Scalable Vector Graphics (SVG): image/svg+xml.
     */
    public static final MediaType IMAGE_SVG_TYPE = new MediaType(IMAGE_SVG);

    /**
     * X Window System Bitmap file (XBM): image/xbm.
     */
    public static final String IMAGE_XBM = "image/xbm";

    /**
     * X Window System Bitmap file (XBM): image/xbm.
     */
    public static final MediaType IMAGE_XBM_TYPE = new MediaType(IMAGE_XBM);

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
     * WMF Image: image/wmf.
     */
    public static final String IMAGE_WMF = "image/wmf";

    /**
     * WMF Image: image/wmf.
     */
    public static final MediaType IMAGE_WMF_TYPE = new MediaType(IMAGE_WMF);

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
    private static final String WILDCARD = "*";

    /**
     * The longest header value that {@link #orderedOf(List)} will cache. Real world {@code Accept}
     * headers are well under this; anything longer is parsed on every call rather than taking up a
     * cache slot.
     */
    private static final int MAX_CACHED_HEADER_LENGTH = 256;

    /**
     * The number of entries held by {@link #ORDERED_CACHE}. The table is allocated once with
     * exactly this many slots and never grows, which is what bounds the cache.
     */
    private static final int MAX_CACHED_HEADERS = 256;

    /**
     * The number of slots a header value can be stored in. A value is looked up in one set of this
     * many adjacent slots, chosen by its hash, so a lookup reads at most this many slots and an
     * insertion considers at most this many entries as the one to replace.
     */
    private static final int ORDERED_CACHE_WAYS = 2;

    /**
     * Mask selecting the set of {@link #ORDERED_CACHE_WAYS} slots a hash maps to.
     */
    private static final int ORDERED_CACHE_SET_MASK = MAX_CACHED_HEADERS / ORDERED_CACHE_WAYS - 1;

    /**
     * The most credit an entry can accumulate by being read again. An entry with this much credit
     * survives this many insertions of other values that map to the same set before it can be
     * replaced.
     */
    private static final int MAX_ORDERED_CACHE_CREDIT = 16;

    /**
     * Cache of parsed and sorted media type lists, keyed by the single header value they were
     * parsed from. A handful of distinct {@code Accept} header values typically recur for the
     * whole life of a process, so parsing them once is worth a small table. Header values naming a
     * single media type are served by the fast path in {@link #orderedOf(List)} instead and never
     * reach the cache.
     *
     * <p>The table is a fixed array of {@link #MAX_CACHED_HEADERS} slots, treated as
     * {@code MAX_CACHED_HEADERS / ORDERED_CACHE_WAYS} sets of {@link #ORDERED_CACHE_WAYS} slots. A
     * value only ever occupies a slot of the one set its hash maps to, so the bound is structural:
     * the table cannot hold more than it was allocated with no matter how many threads insert at
     * once, and no size check, counter or lock is needed to keep it.</p>
     *
     * <p>Entries are admitted on probation and earn the right to stay by being read again. A newly
     * admitted entry carries no credit; every read gives it one more, up to
     * {@link #MAX_ORDERED_CACHE_CREDIT}. Inserting a value whose set is full takes one credit from
     * the poorer of the two entries and replaces it once it has none left. An entry never read
     * again therefore yields its slot to the next value that wants it, which is what keeps values
     * seen once from holding the cache against values that recur; an entry that keeps being read
     * holds its slot against a stream of one-off values, because each of them costs it one credit
     * and every read gives one back. Eviction costs a single compare and set of one slot, on the
     * miss path only, next to a parse that is far more expensive.</p>
     *
     * <p>Reading an entry is a volatile array read, a hash comparison and a string comparison, and
     * allocates nothing; only admitting a value allocates, and only after it has been parsed. Credit
     * stops being given once an entry reaches the cap, so the read of a header that recurs for the
     * life of the process writes nothing at all after its first few reads and cannot bounce the
     * entry's cache line between cores. The
     * volatile read also guarantees that a thread observing an entry observes everything the
     * inserting thread did beforehand, which is what makes it safe to share the parsed
     * {@link MediaType} instances across threads.</p>
     */
    private static final AtomicReferenceArray<CachedMediaTypes> ORDERED_CACHE = new AtomicReferenceArray<>(MAX_CACHED_HEADERS);

    @SuppressWarnings("ConstantName")
    private static final String MIME_TYPES_FILE_NAME = "META-INF/http/mime.types";
    // Sonar java:S3077: the table is an immutable map, a Map.copyOf of the parsed table or an empty map
    // when the load fails, assigned once under the double checked lock in getMediaTypeFileExtensions.
    @SuppressWarnings("java:S3077")
    private static volatile @Nullable Map<String, String> mediaTypeFileExtensions;
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
    public MediaType(String name, @Nullable Map<String, String> params) {
        this(name, null, params);
    }

    /**
     * Constructs a new media type for the given string and extension.
     *
     * @param name      The name of the media type. For example application/json
     * @param extension The extension of the file using this media type if it differs from the subtype
     */
    public MediaType(String name, @Nullable String extension) {
        this(name, extension, Collections.emptyMap());
    }

    /**
     * Constructs a new media type for the given string and extension.
     *
     * @param name      The name of the media type. For example application/json
     * @param extension The extension of the file using this media type if it differs from the subtype
     * @param params    The parameters
     */
    public MediaType(String name, @Nullable String extension, @Nullable Map<String, String> params) {
        if (name == null) {
            throw new IllegalArgumentException("Argument [name] cannot be null");
        }
        name = name.trim();
        String withoutArgs;
        if (name.indexOf(SEMICOLON) == -1) {
            withoutArgs = name;
            if (params == null) {
                this.parameters = Collections.emptyMap();
            } else {
                this.parameters = (Map) params;
            }
        } else {
            String[] parsedType = new String[1];
            Map<CharSequence, String> parsedParameters = new LinkedHashMap<>();
            new ParameterParser() {
                @Override
                void visitType(String type) {
                    parsedType[0] = type.trim();
                }

                @Override
                boolean visitAttribute(String attribute) {
                    return !attribute.trim().isEmpty();
                }

                @Override
                void visitAttributeValue(String attribute, String value) {
                    String normalizedAttribute = attribute.trim();
                    String normalizedValue = value.trim();
                    if ("q".equals(normalizedAttribute)) {
                        qualityNumberField = new BigDecimal(unquoteParameterValue(normalizedValue));
                    }
                    parsedParameters.put(normalizedAttribute, normalizedValue);
                }
            }.run(name);
            withoutArgs = parsedType[0];
            if (parsedParameters.isEmpty()) {
                if (params == null) {
                    this.parameters = Collections.emptyMap();
                } else {
                    this.parameters = (Map) params;
                }
            } else {
                this.parameters = parsedParameters;
            }
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
        if (params == null || params.isEmpty()) {
            this.strRepr = name;
        } else {
            this.strRepr = toString0();
        }
    }

    /**
     * Create a new or get a {@link MediaType} from the given text.
     *
     * @param mediaType The text
     * @return The {@link MediaType}
     */
    public static MediaType of(String mediaType) {
        return switch (mediaType) {
            case ALL -> ALL_TYPE;
            case APPLICATION_FORM_URLENCODED -> APPLICATION_FORM_URLENCODED_TYPE;
            case APPLICATION_XHTML -> APPLICATION_XHTML_TYPE;
            case APPLICATION_XML -> APPLICATION_XML_TYPE;
            case APPLICATION_JSON -> APPLICATION_JSON_TYPE;
            case APPLICATION_JSON_FEED -> APPLICATION_JSON_FEED_TYPE;
            case APPLICATION_JSON_GITHUB -> APPLICATION_JSON_GITHUB_TYPE;
            case APPLICATION_JSON_PATCH -> APPLICATION_JSON_PATCH_TYPE;
            case APPLICATION_JSON_MERGE_PATCH -> APPLICATION_JSON_MERGE_PATCH_TYPE;
            case APPLICATION_JSON_PROBLEM -> APPLICATION_JSON_PROBLEM_TYPE;
            case APPLICATION_JSON_SCHEMA -> APPLICATION_JSON_SCHEMA_TYPE;
            case APPLICATION_SCIM_JSON -> APPLICATION_SCIM_JSON_TYPE;
            case APPLICATION_YAML -> APPLICATION_YAML_TYPE;
            case APPLICATION_HAL_JSON -> APPLICATION_HAL_JSON_TYPE;
            case APPLICATION_HAL_XML -> APPLICATION_HAL_XML_TYPE;
            case APPLICATION_ATOM_XML -> APPLICATION_ATOM_XML_TYPE;
            case APPLICATION_VND_ERROR -> APPLICATION_VND_ERROR_TYPE;
            case APPLICATION_JSON_STREAM -> APPLICATION_JSON_STREAM_TYPE;
            case APPLICATION_OCTET_STREAM -> APPLICATION_OCTET_STREAM_TYPE;
            case APPLICATION_GRAPHQL -> APPLICATION_GRAPHQL_TYPE;
            case APPLICATION_PDF -> APPLICATION_PDF_TYPE;
            case APPLICATION_GPX_XML -> GPX_XML_TYPE;
            case APPLICATION_GZIP -> GZIP_TYPE;
            case APPLICATION_ZIP -> ZIP_TYPE;
            case MICROSOFT_EXCEL_OPEN_XML -> MICROSOFT_EXCEL_OPEN_XML_TYPE;
            case MICROSOFT_EXCEL -> MICROSOFT_EXCEL_TYPE;
            case OPEN_DOCUMENT_SPREADSHEET -> OPEN_DOCUMENT_SPREADSHEET_TYPE;
            case MICROSOFT_WORD_OPEN_XML -> MICROSOFT_WORD_OPEN_XML_TYPE;
            case MICROSOFT_WORD -> MICROSOFT_WORD_TYPE;
            case OPEN_DOCUMENT_TEXT -> OPEN_DOCUMENT_TEXT_TYPE;
            case MICROSOFT_POWERPOINT -> MICROSOFT_POWERPOINT_TYPE;
            case MICROSOFT_POWERPOINT_OPEN_XML -> MICROSOFT_POWERPOINT_OPEN_XML_TYPE;
            case OPEN_DOCUMENT_PRESENTATION -> OPEN_DOCUMENT_PRESENTATION_TYPE;
            case APPLICATION_YANG -> YANG_TYPE;
            case APPLICATION_CUE -> CUE_TYPE;
            case APPLICATION_TOML -> TOML_TYPE;
            case APPLICATION_RTF -> RTF_TYPE;
            case APPLICATION_ZLIB -> ZLIB_TYPE;
            case APPLICATION_ZSTD -> ZSTD_TYPE;
            case MULTIPART_FORM_DATA -> MULTIPART_FORM_DATA_TYPE;
            case TEXT_HTML -> TEXT_HTML_TYPE;
            case TEXT_CSV -> TEXT_CSV_TYPE;
            case TEXT_XML -> TEXT_XML_TYPE;
            case TEXT_JSON -> TEXT_JSON_TYPE;
            case TEXT_PLAIN -> TEXT_PLAIN_TYPE;
            case TEXT_EVENT_STREAM -> TEXT_EVENT_STREAM_TYPE;
            case TEXT_MARKDOWN -> TEXT_MARKDOWN_TYPE;
            case TEXT_CSS -> TEXT_CSS_TYPE;
            case TEXT_JAVASCRIPT -> TEXT_JAVASCRIPT_TYPE;
            case TEXT_ECMASCRIPT -> TEXT_ECMASCRIPT_TYPE;
            case IMAGE_APNG -> IMAGE_APNG_TYPE;
            case IMAGE_BMP -> IMAGE_BMP_TYPE;
            case IMAGE_X_ICON -> IMAGE_X_ICON_TYPE;
            case IMAGE_TIFF -> IMAGE_TIFF_TYPE;
            case IMAGE_AVIF -> IMAGE_AVIF_TYPE;
            case IMAGE_SVG -> IMAGE_SVG_TYPE;
            case IMAGE_XBM -> IMAGE_XBM_TYPE;
            case IMAGE_PNG -> IMAGE_PNG_TYPE;
            case IMAGE_JPEG -> IMAGE_JPEG_TYPE;
            case IMAGE_GIF -> IMAGE_GIF_TYPE;
            case IMAGE_WEBP -> IMAGE_WEBP_TYPE;
            case IMAGE_WMF -> IMAGE_WMF_TYPE;
            default -> new MediaType(mediaType);
        };
    }

    /**
     * Determine if this requested content type can be satisfied by a given content type. e.g. text/* will be satisfied by test/html.
     *
     * @param expectedContentType   Content type to match against
     * @return if successful match
     */
    public boolean matches(MediaType expectedContentType) {
        //noinspection ConstantConditions
        if (expectedContentType == null) {
            return false;
        }
        if (expectedContentType == this) {
            return true;
        }
        return matchesType(expectedContentType.getType()) && matchesSubtype(expectedContentType.getSubtype());
    }

    /**
     * Check if the subtype matches.
     *
     * @param matchSubtype The subtype to match
     * @return true if matches
     * @since 4.6.3
     */
    public boolean matchesSubtype(String matchSubtype) {
        return subtype.equals(WILDCARD) || subtype.equalsIgnoreCase(matchSubtype);
    }

    /**
     * Check if the type matches.
     * @param matchType The type to match
     * @return true if matches
     * @since 4.6.3
     */
    public boolean matchesType(String matchType) {
        return type.equals(WILDCARD) || type.equalsIgnoreCase(matchType);
    }

    /**
     * Check if the extension matches.
     * @param matchExtension The extension to match
     * @return true if matches
     * @since 4.7.0
     */
    public boolean matchesAllOrWildcardOrExtension(String matchExtension) {
        return extension.equalsIgnoreCase(ALL_TYPE.extension) || extension.equals(WILDCARD) || matchesExtension(matchExtension);
    }

    /**
     * Check if the extension matches.
     * @param matchExtension The extension to match
     * @return true if matches
     * @since 4.6.3
     */
    public boolean matchesExtension(String matchExtension) {
        return extension.equalsIgnoreCase(matchExtension);
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
     * @return The parameters of the media type
     */
    public OptionalValues<String> getParameters() {
        return OptionalValues.of(String.class, parameters);
    }

    /**
     * @return The parameters map of the media type
     * @since 4.8
     */
    public Map<CharSequence, String> getParametersMap() {
        if (parameters == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(parameters);
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
    public @Nullable String getVersion() {
        return parameters.getOrDefault(V_PARAMETER, null);
    }

    /**
     * @return The charset of the media type if specified
     */
    public Optional<Charset> getCharset() {
        String charset = parameters.get(CHARSET_PARAMETER);
        if (charset == null) {
            return Optional.empty();
        }

        return Optional.of(Charset.forName(unquoteParameterValue(charset)));
    }

    private static String unquoteParameterValue(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            StringBuilder unescaped = new StringBuilder(value.length() - 2);
            boolean escaped = false;
            for (int i = 1; i < value.length() - 1; i++) {
                char c = value.charAt(i);
                if (escaped) {
                    unescaped.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else {
                    unescaped.append(c);
                }
            }
            if (escaped) {
                unescaped.append('\\');
            }
            return unescaped.toString();
        }
        return value;
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
            matches = subtype.equalsIgnoreCase("json")
                    || subtype.equalsIgnoreCase("xml")
                    || subtype.equalsIgnoreCase("yaml")
                    || subtype.equalsIgnoreCase("graphql")
                    || subtype.equalsIgnoreCase("yang")
                    || subtype.equalsIgnoreCase("toml")
                    || subtype.equalsIgnoreCase("x-cue")
            ;
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
                sb.append(';');
                sb.append(name);
                sb.append('=');
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
            String singleHeader = values.get(0).toString();
            if (singleHeader.indexOf(',') == -1) {
                // fast path for single header with single media type
                try {
                    return List.of(MediaType.of(singleHeader));
                } catch (IllegalArgumentException ignored) {
                }
            } else if (singleHeader.length() <= MAX_CACHED_HEADER_LENGTH) {
                // a single header listing several media types has to be split, parsed and sorted,
                // which is what the cache is for. It is also the only case whose raw text can be
                // used as a key without building one.
                return orderedOfCached(singleHeader, values);
            }
        }
        return parseOrdered(values);
    }

    /**
     * Return the parsed media types of a single header value, parsing it only if it is not already
     * cached.
     *
     * @param singleHeader The header value, also the cache key
     * @param values       The header values, a singleton list of {@code singleHeader}
     * @return The media types, ordered
     */
    private static List<MediaType> orderedOfCached(String singleHeader, List<? extends CharSequence> values) {
        int hash = spread(singleHeader.hashCode());
        int firstSlot = (hash & ORDERED_CACHE_SET_MASK) * ORDERED_CACHE_WAYS;
        for (int way = 0; way < ORDERED_CACHE_WAYS; way++) {
            CachedMediaTypes entry = ORDERED_CACHE.get(firstSlot + way);
            if (holds(entry, hash, singleHeader)) {
                int credit = entry.credit;
                if (credit < MAX_ORDERED_CACHE_CREDIT) {
                    // a lost increment only costs the entry a little of its protection, so this
                    // deliberately does not pay for an atomic update
                    entry.credit = credit + 1;
                }
                return entry.mediaTypes;
            }
        }
        return admit(singleHeader, hash, firstSlot, parseOrdered(values));
    }

    /**
     * Offer a freshly parsed header value to the cache, and return the list to hand to the caller:
     * the given one, or the one another thread cached for the same value first.
     *
     * <p>The value takes a free slot of its set if there is one. If there is none it replaces the
     * poorer of the entries already there when that entry has no credit left, and otherwise takes
     * one credit from it and is not cached this time round. Nothing is retried: a value that loses
     * a race is simply parsed again the next time it is seen.</p>
     *
     * @param singleHeader The header value, also the cache key
     * @param hash         The spread hash of the header value
     * @param firstSlot    The first slot of the set the header value maps to
     * @param parsed       The media types parsed from the header value
     * @return The media types, ordered
     */
    private static List<MediaType> admit(String singleHeader, int hash, int firstSlot, List<MediaType> parsed) {
        CachedMediaTypes poorest = null;
        int poorestSlot = -1;
        int poorestCredit = Integer.MAX_VALUE;
        for (int way = 0; way < ORDERED_CACHE_WAYS; way++) {
            int slot = firstSlot + way;
            CachedMediaTypes entry = ORDERED_CACHE.get(slot);
            if (entry == null) {
                if (ORDERED_CACHE.compareAndSet(slot, null, new CachedMediaTypes(hash, singleHeader, parsed))) {
                    return parsed;
                }
                // another thread filled the slot while this one was parsing
                entry = ORDERED_CACHE.get(slot);
            }
            if (holds(entry, hash, singleHeader)) {
                // another thread parsed the same value first, so share the list it cached rather
                // than handing out a second copy of it
                return entry.mediaTypes;
            }
            if (entry != null && entry.credit < poorestCredit) {
                poorest = entry;
                poorestSlot = slot;
                poorestCredit = entry.credit;
            }
        }
        if (poorest != null) {
            if (poorestCredit > 0) {
                poorest.credit = poorestCredit - 1;
            } else {
                ORDERED_CACHE.compareAndSet(poorestSlot, poorest, new CachedMediaTypes(hash, singleHeader, parsed));
            }
        }
        return parsed;
    }

    /**
     * Whether the given entry is the cached parse of the given header value.
     *
     * @param entry        The entry, {@code null} for an empty slot
     * @param hash         The spread hash of the header value
     * @param singleHeader The header value
     * @return {@code true} if the entry holds the header value
     */
    private static boolean holds(@Nullable CachedMediaTypes entry, int hash, String singleHeader) {
        return entry != null && entry.hash == hash && entry.header.equals(singleHeader);
    }

    /**
     * Spread the bits of a header value's hash code, so that values differing only in their high
     * bits do not all map to the same set of the cache.
     *
     * @param hash The hash code
     * @return The spread hash, never negative
     */
    private static int spread(int hash) {
        return (hash ^ (hash >>> 16)) & Integer.MAX_VALUE;
    }

    /**
     * Parse and sort the media types of the given header values. The returned list is immutable and,
     * because the list it wraps is not published anywhere else, safe to hand out repeatedly.
     *
     * @param values The header values
     * @return The media types, ordered
     */
    private static List<MediaType> parseOrdered(List<? extends CharSequence> values) {
        var mediaTypes = new ArrayList<MediaType>(values.size());
        for (CharSequence value : values) {
            for (String token : StringUtils.splitOmitEmptyStrings(value, ',')) {
                try {
                    mediaTypes.add(MediaType.of(token));
                } catch (IllegalArgumentException e) {
                    // ignore
                }
            }
        }
        mediaTypes.sort(MediaType::naturalSort);
        return Collections.unmodifiableList(mediaTypes);
    }

    /**
     * The number of occupied slots of the ordered media type cache. Internal test hook, not API:
     * it exists so that a test can assert the cache stays within its bound and is not part of the
     * behaviour of this class.
     *
     * @return The cache size
     */
    @Internal
    static int orderedCacheSize() {
        int size = 0;
        for (int slot = 0; slot < MAX_CACHED_HEADERS; slot++) {
            if (ORDERED_CACHE.get(slot) != null) {
                size++;
            }
        }
        return size;
    }

    /**
     * Empty the ordered media type cache. Internal test hook, not API: it exists so that a test can
     * start from a known state and is not part of the behaviour of this class.
     */
    @Internal
    static void clearOrderedCache() {
        for (int slot = 0; slot < MAX_CACHED_HEADERS; slot++) {
            ORDERED_CACHE.set(slot, null);
        }
    }

    private static int naturalSort(MediaType o1, MediaType o2)  {
        //The */* type is always last
        boolean fullWildcard1 = o1.type.equals(WILDCARD);
        boolean fullWildcard2 = o2.type.equals(WILDCARD);
        if (fullWildcard1 && fullWildcard2) {
            return 0;
        } else if (fullWildcard1) {
            return 1;
        } else if (fullWildcard2) {
            return -1;
        }
        if (o2.subtype.equals(WILDCARD) && !o1.subtype.equals(WILDCARD)) {
            return -1;
        } else if (o1.subtype.equals(WILDCARD) && !o2.subtype.equals(WILDCARD)) {
            return 1;
        }
        return o2.getQualityAsNumber().compareTo(o1.getQualityAsNumber());
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
        var types = new MediaType[mediaType.length];
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
            String type = getMediaTypeFileExtensions().get(extension);
            if (type != null) {
                return Optional.of(new MediaType(type, extension));
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

    private static Map<String, String> getMediaTypeFileExtensions() {
        Map<String, String> extensions = mediaTypeFileExtensions;
        if (extensions == null) {
            synchronized (MediaType.class) { // double check
                extensions = mediaTypeFileExtensions;
                if (extensions == null) {
                    extensions = loadMimeTypesReporting(MediaType.class.getClassLoader());
                    // An empty table is cached like any other. The class path does not change for the
                    // life of the JVM, so retrying the load on every lookup would only fail again; the
                    // warning logged by the failing branch is what explains the missing detection.
                    mediaTypeFileExtensions = extensions;
                }
            }
        }
        return extensions;
    }

    /**
     * Reads the table and, as a safety net, reports and swallows any unexpected failure of the parse
     * itself. {@link #loadMimeTypes(ClassLoader)} already recovers from the failures it expects, so
     * dropping this would be defensible, but an unexpected one would then be thrown out of
     * {@link #forExtension(String)}, which sits on request paths, rather than degrading quietly.
     * Package private so that a test can reach the branch without touching the cache.
     *
     * @param classLoader The class loader to read the resource from, {@code null} for the bootstrap loader
     * @return The table, or an empty map if it could not be loaded
     */
    static Map<String, String> loadMimeTypesReporting(@Nullable ClassLoader classLoader) {
        try {
            return loadMimeTypes(classLoader);
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(MediaType.class)
                .warn("Failed to load {}, media type detection by file extension is disabled", MIME_TYPES_FILE_NAME, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Reads the file extension to media type table from the given class loader. Package private so that
     * a test can supply a class loader that does not carry the resource, without touching the cache.
     *
     * @param classLoader The class loader to read the resource from, {@code null} for the bootstrap loader
     * @return The table, or an empty map if the resource could not be found or read
     */
    @SuppressWarnings("MagicNumber")
    static Map<String, String> loadMimeTypes(@Nullable ClassLoader classLoader) {
        // A repackaged or shaded jar can drop the resource, and a class loader that cannot see it
        // returns null rather than failing, so this has to be checked before the stream is read.
        InputStream resource = classLoader == null
            ? ClassLoader.getSystemResourceAsStream(MIME_TYPES_FILE_NAME)
            : classLoader.getResourceAsStream(MIME_TYPES_FILE_NAME);
        if (resource == null) {
            Logger logger = LoggerFactory.getLogger(MediaType.class);
            if (logger.isWarnEnabled()) {
                logger.warn("Cannot find {} on the class path, media type detection by file extension is disabled", MIME_TYPES_FILE_NAME);
            }
            return Collections.emptyMap();
        }
        try (InputStream is = resource) {
            var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.US_ASCII));
            var result = new LinkedHashMap<String, String>(100);
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
            return Map.copyOf(result);
        } catch (IOException ex) {
            Logger logger = LoggerFactory.getLogger(MediaType.class);
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to read {}, media type detection by file extension is disabled", MIME_TYPES_FILE_NAME, ex);
            }
        }

        return Collections.emptyMap();
    }

    /**
     * An entry of {@link #ORDERED_CACHE}: the media types parsed from a single header value,
     * together with what that value has earned by being read again.
     */
    private static final class CachedMediaTypes {

        /**
         * The spread hash of {@link #header}, compared before the header itself so that a slot
         * holding a different value is usually rejected without a string comparison.
         */
        private final int hash;

        /**
         * The header value these media types were parsed from.
         */
        private final String header;

        /**
         * The parsed media types. Immutable and handed to every caller that reads this entry.
         */
        private final List<MediaType> mediaTypes;

        /**
         * How many insertions into this entry's set it survives before it can be replaced. Read and
         * written without synchronization on purpose: it only steers replacement, an int is never
         * seen half written, and a lost update costs the entry a little protection rather than
         * correctness. It is deliberately not {@code volatile}, so that reading a cached value
         * stays as cheap as reading the slot.
         */
        private int credit;

        private CachedMediaTypes(int hash, String header, List<MediaType> mediaTypes) {
            this.hash = hash;
            this.header = header;
            this.mediaTypes = mediaTypes;
        }
    }

    /**
     * Adapted from Netty's {@code ParmParser}.
     *
     * Source: https://github.com/netty-contrib/codec-multipart/blob/1.0/multipart-core/src/main/java/io/netty/contrib/multipart/ParmParser.java
     */
    private abstract static class ParameterParser {
        abstract void visitType(String type);

        abstract boolean visitAttribute(String attribute);

        abstract void visitAttributeValue(String attribute, String value);

        final void run(String headerValue) {
            int typeEnd = headerValue.indexOf(';');
            if (typeEnd == -1) {
                visitType(headerValue);
                return;
            }
            visitType(headerValue.substring(0, typeEnd));
            for (int parameterStart = typeEnd + 1; parameterStart < headerValue.length(); ) {
                int attributeEnd = headerValue.indexOf('=', parameterStart);
                if (attributeEnd == -1) {
                    break;
                }
                while (parameterStart < headerValue.length() && Character.isWhitespace(headerValue.charAt(parameterStart))) {
                    parameterStart++;
                }
                String attribute = headerValue.substring(parameterStart, attributeEnd);
                boolean needParameterValue = visitAttribute(attribute);

                String parameterValue = null;
                int parameterValueEnd = attributeEnd + 1;
                if (parameterValueEnd < headerValue.length() && headerValue.charAt(parameterValueEnd) == '"') {
                    StringBuilder valueBuilder = needParameterValue ? new StringBuilder() : null;
                    boolean quoted = false;
                    while (parameterValueEnd < headerValue.length()) {
                        char c = headerValue.charAt(parameterValueEnd++);
                        if (c == '"') {
                            quoted = !quoted;
                        } else {
                            if (!quoted && c == ';') {
                                parameterValueEnd--;
                                break;
                            } else if (quoted && c == '\\' && parameterValueEnd < headerValue.length()) {
                                if (needParameterValue && valueBuilder != null) {
                                    valueBuilder.append(headerValue.charAt(parameterValueEnd));
                                }
                                parameterValueEnd++;
                            } else if (needParameterValue && valueBuilder != null) {
                                valueBuilder.append(c);
                            }
                        }
                    }
                    if (needParameterValue && valueBuilder != null) {
                        parameterValue = valueBuilder.toString();
                    }
                } else {
                    parameterValueEnd = headerValue.indexOf(';', parameterValueEnd);
                    if (parameterValueEnd == -1) {
                        parameterValueEnd = headerValue.length();
                    }
                    if (needParameterValue) {
                        parameterValue = headerValue.substring(attributeEnd + 1, parameterValueEnd);
                    }
                }
                if (parameterValue != null) {
                    visitAttributeValue(attribute, parameterValue);
                }
                parameterStart = parameterValueEnd + 1;
            }
        }

    }
}
