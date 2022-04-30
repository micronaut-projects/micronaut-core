package io.micronaut.http.uri;

import io.micronaut.core.convert.value.MutableConvertibleMultiValues;

import java.util.Map;

/**
 * Encodes all components of a URI and transforms query parameters and template parameters maps into
 * URI strings.
 *
 * @author Dan Hollingsworth
 */
interface UriEncoder {
    String encode(String scheme, String userInfo, String host, int port, String path, MutableConvertibleMultiValues<String> queryParams, String fragment, Map<String, ? super Object> templateParams);

    /**
     * This method URL encodes a value and allows additional chars to be specified
     * which will not be escaped. This is useful so that it may encode for a variety
     * of use cases such as paths, fragments, query strings, user info, etc.
     * <p><br>
     * The characters slash, question mark, equals, and ampersand are normally going to be
     * what's supplied to the whitelist. Note that fragments and query strings do not
     * require encoding of slashes and question marks. Also note that equals and ampersand
     * do not require encoding within paths, query strings, or fragments, however
     * if you're encoding an entire query string with multiple key values already concatenated
     * then you would want to whitelist equals and ampersand.
     *
     * @param value          Value to encode
     * @param whitelistChar1 A character which should not be encoding, or {@code '\0'}
     * @param whitelistChar2 A character which should not be encoding, or {@code '\0'}
     * @param whitelistChar3 A character which should not be encoding, or {@code '\0'}
     * @param whitelistChar4 A character which should not be encoding, or {@code '\0'}
     * @return
     */
    String encode(String value, char whitelistChar1, char whitelistChar2, char whitelistChar3, char whitelistChar4);

    /**
     * Convenience method for {@link #encode(String, char, char, char, char)}.
     */
    default String encode(String value) {
        return encode(value, '\0', '\0', '\0', '\0');
    }

    /**
     * Convenience method for {@link #encode(String, char, char, char, char)}.
     */
    default String encode(String value, char whitelistChar1) {
        return encode(value, whitelistChar1, '\0', '\0', '\0');
    }

    /**
     * Convenience method for {@link #encode(String, char, char, char, char)}.
     */
    default String encode(String value, char whitelistChar1, char whitelistChar2) {
        return encode(value, whitelistChar1, whitelistChar2, '\0', '\0');
    }

    /**
     * Convenience method for {@link #encode(String, char, char, char, char)}.
     */
    default String encode(String value, char whitelistChar1, char whitelistChar2, char whitelistChar3) {
        return encode(value, whitelistChar1, whitelistChar2, whitelistChar3, '\0');
    }

    default String encodePath(String path, Map<String, ? super Object> templateParams) {
        return encode(null, null, null, -1, path, null, null, templateParams);
    }

    default String encodeQueryParams(MutableConvertibleMultiValues<String> queryParams, Map<String, ? super Object> templateParams) {
        return encode(null, null, null, -1, null, queryParams, null, templateParams);
    }

    default String encodeFragment(String fragment, Map<String, ? super Object> templateParams) {
        return encode(null, null, null, -1, null, null, fragment, templateParams);
    }
}
