package io.micronaut.http;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static io.micronaut.http.MediaType.*;
import static org.junit.jupiter.api.Assertions.*;

class MediaTypeTest {
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
}
