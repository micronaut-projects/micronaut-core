package io.micronaut.http;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.List;
import java.util.stream.Stream;

import static io.micronaut.http.MediaType.*;
import static org.junit.jupiter.api.Assertions.*;

class MediaTypeTest {
    @ParameterizedTest
    @MethodSource
    void noParameterFastPathMatchesSlowPathForValidMediaTypes(String contentType) {
        SlowPathExpectation expected = slowPathExpectation(contentType);

        MediaType mediaType = new MediaType(contentType);

        assertEquals(expected.name(), mediaType.getName());
        assertEquals(expected.type(), mediaType.getType());
        assertEquals(expected.subtype(), mediaType.getSubtype());
        assertEquals(expected.extension(), mediaType.getExtension());
        assertEquals(expected.stringRepresentation(), mediaType.toString());
        assertEquals(Map.of(), mediaType.getParametersMap());
        assertEquals(0, mediaType.getQualityAsNumber().compareTo(expected.quality()));
        assertTrue(mediaType.getCharset().isEmpty());
    }

    private static Stream<String> noParameterFastPathMatchesSlowPathForValidMediaTypes() {
        return Stream.of(
            "application/json",
            " text/plain ",
            "application/hal+json",
            "APPLICATION/JSON",
            "application/vnd.example+yaml"
        );
    }

    @ParameterizedTest
    @MethodSource
    void noParameterFastPathMatchesSlowPathForInvalidMediaTypes(String contentType) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new MediaType(contentType));

        assertEquals(slowPathFailureMessage(contentType), exception.getMessage());
    }

    private static Stream<String> noParameterFastPathMatchesSlowPathForInvalidMediaTypes() {
        return Stream.of(
            "applicationjson",
            "",
            "   ",
            "textplain"
        );
    }

    private static SlowPathExpectation slowPathExpectation(String contentType) {
        String normalized = contentType.trim();
        int slashIndex = normalized.indexOf('/');
        if (slashIndex < 0) {
            throw new IllegalArgumentException("Invalid mime type: " + normalized);
        }
        String type = normalized.substring(0, slashIndex);
        String subtype = normalized.substring(slashIndex + 1);
        int plusIndex = subtype.indexOf('+');
        String extension = plusIndex > -1 ? subtype.substring(plusIndex + 1) : subtype;
        return new SlowPathExpectation(normalized, type, subtype, extension, normalized, java.math.BigDecimal.ONE);
    }

    private static String slowPathFailureMessage(String contentType) {
        return "Invalid mime type: " + contentType.trim();
    }

    private record SlowPathExpectation(String name,
                                       String type,
                                       String subtype,
                                       String extension,
                                       String stringRepresentation,
                                       java.math.BigDecimal quality) {
    }

    @ParameterizedTest
    @MethodSource
    void isJsonTrue(MediaType mediaType) {
        assertTrue(mediaType.matchesAllOrWildcardOrExtension(MediaType.EXTENSION_JSON));
        assertTrue(mediaType.matchesExtension(MediaType.EXTENSION_JSON));
    }

    private static List<MediaType> isJsonTrue() {
        return List.of(
            APPLICATION_JSON_TYPE,
            TEXT_JSON_TYPE,
            APPLICATION_HAL_JSON_TYPE,
            APPLICATION_JSON_GITHUB_TYPE,
            APPLICATION_JSON_FEED_TYPE,
            APPLICATION_JSON_PROBLEM_TYPE,
            APPLICATION_JSON_PATCH_TYPE,
            APPLICATION_JSON_MERGE_PATCH_TYPE,
            APPLICATION_JSON_SCHEMA_TYPE,
            APPLICATION_VND_ERROR_TYPE
         );
    }

    @ParameterizedTest
    @MethodSource
    void isJsonFalse(MediaType mediaType) {
        assertFalse(mediaType.matchesExtension(MediaType.EXTENSION_JSON));
    }

    private static List<MediaType> isJsonFalse() {
        return List.of(ALL_TYPE,
            APPLICATION_FORM_URLENCODED_TYPE,
            APPLICATION_XHTML_TYPE,
            APPLICATION_XML_TYPE,
            APPLICATION_YAML_TYPE,
            APPLICATION_HAL_XML_TYPE,
            APPLICATION_ATOM_XML_TYPE,
            APPLICATION_JSON_STREAM_TYPE,
            APPLICATION_OCTET_STREAM_TYPE,
            APPLICATION_GRAPHQL_TYPE,
            APPLICATION_PDF_TYPE,
            GPX_XML_TYPE,
            GZIP_TYPE,
            ZIP_TYPE,
            MICROSOFT_EXCEL_OPEN_XML_TYPE,
            MICROSOFT_EXCEL_TYPE,
            OPEN_DOCUMENT_SPREADSHEET_TYPE,
            MICROSOFT_WORD_TYPE,
            MICROSOFT_WORD_OPEN_XML_TYPE,
            OPEN_DOCUMENT_TEXT_TYPE,
            MICROSOFT_POWERPOINT_TYPE,
            MICROSOFT_POWERPOINT_OPEN_XML_TYPE,
            OPEN_DOCUMENT_PRESENTATION_TYPE,
            YANG_TYPE,
            CUE_TYPE,
            TOML_TYPE,
            RTF_TYPE,
            ZLIB_TYPE,
            ZSTD_TYPE,
            MULTIPART_FORM_DATA_TYPE,
            TEXT_HTML_TYPE,
            TEXT_CSV_TYPE,
            TEXT_XML_TYPE,
            TEXT_PLAIN_TYPE,
            TEXT_EVENT_STREAM_TYPE,
            TEXT_MARKDOWN_TYPE,
            TEXT_CSS_TYPE,
            TEXT_JAVASCRIPT_TYPE,
            TEXT_ECMASCRIPT_TYPE,
            IMAGE_APNG_TYPE,
            IMAGE_BMP_TYPE,
            IMAGE_X_ICON_TYPE,
            IMAGE_TIFF_TYPE,
            IMAGE_AVIF_TYPE,
            IMAGE_SVG_TYPE,
            IMAGE_XBM_TYPE,
            IMAGE_PNG_TYPE,
            IMAGE_JPEG_TYPE,
            IMAGE_GIF_TYPE,
            IMAGE_WEBP_TYPE,
            IMAGE_WMF_TYPE);
    }
    @ParameterizedTest
    @MethodSource
    void parsesCharsetFromStandardizedContentTypeFormats(String contentType, String expectedCharsetName) {
        MediaType mediaType = MediaType.of(contentType);

        assertEquals(Charset.forName(expectedCharsetName), mediaType.getCharset().orElseThrow());
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> parsesCharsetFromStandardizedContentTypeFormats() {
        return Stream.of(
            org.junit.jupiter.params.provider.Arguments.of("text/plain;charset=utf-8", "utf-8"),
            org.junit.jupiter.params.provider.Arguments.of("text/plain; charset=utf-8", "utf-8"),
            org.junit.jupiter.params.provider.Arguments.of("text/plain; foo=bar; charset=utf-8", "utf-8"),
            org.junit.jupiter.params.provider.Arguments.of("text/plain; charset=\"utf-8\"", "utf-8"),
            org.junit.jupiter.params.provider.Arguments.of("text/plain; foo=bar ; charset=\"utf-8\"", "utf-8"),
            org.junit.jupiter.params.provider.Arguments.of("text/plain; charset=\"UTF-8\"", "UTF-8"),
            org.junit.jupiter.params.provider.Arguments.of("text/plain; charset=\"utf\\-8\"", "utf-8")
        );
    }


    @ParameterizedTest
    @MethodSource
    void rejectsQuotedCharsetValuesThatAreNotValidCharsets(String contentType) {
        MediaType mediaType = MediaType.of(contentType);

        assertThrows(IllegalArgumentException.class, mediaType::getCharset);
    }

    private static Stream<String> rejectsQuotedCharsetValuesThatAreNotValidCharsets() {
        return Stream.of(
            "text/plain; charset=\"utf-8;version=2\""
        );
    }

    @ParameterizedTest
    @MethodSource
    void parsesQuotedCharsetParameterValues(String contentType, String expectedParameterValue) {
        MediaType mediaType = MediaType.of(contentType);

        assertEquals(expectedParameterValue, mediaType.getParametersMap().get(MediaType.CHARSET_PARAMETER));
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> parsesQuotedCharsetParameterValues() {
        return Stream.of(
            org.junit.jupiter.params.provider.Arguments.of("text/plain; charset=\"utf-8\"", "utf-8"),
            org.junit.jupiter.params.provider.Arguments.of("text/plain; charset=\"utf-8;version=2\"", "utf-8;version=2")
        );
    }

    @ParameterizedTest
    @MethodSource
    void parsesQuotedNonCharsetParameterValues(String contentType, String parameterName, String expectedParameterValue) {
        MediaType mediaType = MediaType.of(contentType);

        assertEquals(expectedParameterValue, mediaType.getParametersMap().get(parameterName));
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> parsesQuotedNonCharsetParameterValues() {
        return Stream.of(
            org.junit.jupiter.params.provider.Arguments.of("text/plain; foo=\"a;b\"; charset=utf-8", "foo", "a;b")
        );
    }

}
