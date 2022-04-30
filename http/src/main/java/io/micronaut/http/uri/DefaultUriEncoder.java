package io.micronaut.http.uri;

import io.micronaut.core.convert.value.MutableConvertibleMultiValues;
import io.micronaut.core.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Dan Hollingsworth
 */
class DefaultUriEncoder implements UriEncoder {

    @Override
    public String encode(String scheme, String userInfo, String host, int port, String path, MutableConvertibleMultiValues<String> queryParams, String fragment, Map<String, ? super Object> templateParams) {
        scheme = isTemplate(scheme, templateParams) ? UriTemplate.of(scheme).expand(templateParams) : scheme;
        userInfo = expandOrEncodeUserInfo(userInfo, templateParams);
        host = expandOrEncodeForm(host, templateParams);
        path = expandOrEncodePath(path, templateParams);
        String queryString = buildQueryParams(queryParams, templateParams);
        fragment = expandOrEncodeFragment(fragment, templateParams);
        return concat(scheme, userInfo, host, port, path, queryString, fragment);
    }

    private String concat(String scheme, String userInfo, String host, int port,
                          String path, String queryString, String fragment) {
        /*
         * The is no one correct way to construct a URI. We drop elements
         * from the URI when they would be invalid such as userInfo without
         * an authority. And we delimit the tokens by taking into account several
         * specs and what's most compatible between them:
         *   https://datatracker.ietf.org/doc/html/rfc1738#section-3.3
         *   https://datatracker.ietf.org/doc/html/rfc2396#section-3
         *   https://datatracker.ietf.org/doc/html/rfc3986#section-3
         */
        StringBuilder uriText = new StringBuilder();
        if (StringUtils.isNotEmpty(scheme)) {
            uriText.append(scheme).append(":");
        }
        if (StringUtils.isNotEmpty(host)) {
            if (StringUtils.isNotEmpty(userInfo)) {
                uriText.append(userInfo).append('@');
            }
            uriText.append("//").append(host);

            if (port != -1) {
                uriText.append(':').append(port);
            }
        }
        if (StringUtils.isEmpty(path)) {
            uriText.append('/');
        } else {
            if (uriText.length() > 0 && path.charAt(0) != '/') {
                uriText.append('/');
            }
            uriText.append(path);
        }
        if (StringUtils.isNotEmpty(queryString)) {
            uriText.append('?').append(queryString);
        }
        if (StringUtils.isNotEmpty(fragment)) {
            if (fragment.charAt(0) != '#') {
                uriText.append('#');
            }
            uriText.append(fragment);
        }
        return uriText.toString();
    }

    private boolean isTemplate(String value, Map<String, ? super Object> templateParams) {
        return templateParams != null && value != null && value.indexOf('{') > -1;
    }

    private String buildQueryParams(MutableConvertibleMultiValues<String> queryParams, Map<String, ? super Object> templateParams) {
        if (!queryParams.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            final Iterator<Map.Entry<String, List<String>>> nameIterator = queryParams.iterator();
            while (nameIterator.hasNext()) {
                Map.Entry<String, List<String>> entry = nameIterator.next();
                String rawName = entry.getKey();
                String name = expandOrEncodeForm(rawName, templateParams);

                final Iterator<String> i = entry.getValue().iterator();
                while (i.hasNext()) {
                    String v = expandOrEncodeForm(i.next(), templateParams);
                    builder.append(name).append('=').append(v);
                    if (i.hasNext()) {
                        builder.append('&');
                    }
                }
                if (nameIterator.hasNext()) {
                    builder.append('&');
                }

            }
            return builder.toString();
        }
        return null;
    }

    private String expandOrEncodeUserInfo(String userInfo, Map<String, ? super Object> values) {
        if (StringUtils.isNotEmpty(userInfo)) {
            if (userInfo.contains(":")) {
                final String[] sa = userInfo.split(":");
                userInfo = expandOrEncodeForm(sa[0], values) + ":" + expandOrEncodeForm(sa[1], values);
            } else {
                userInfo = expandOrEncodeForm(userInfo, values);
            }
        }
        return userInfo;
    }

    private String expandOrEncodeForm(String value, Map<String, ? super Object> values) {
        try {
            if (StringUtils.isEmpty(value)) {
                return null;
            }
            return isTemplate(value, values) ? UriTemplate.of(value).expand(values) : URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("No available charset: " + e.getMessage());
        }
    }

    private String expandOrEncodePath(String path, Map<String, ? super Object> values) {
        return isTemplate(path, values) ? UriTemplate.of(path).expand(values) : encode(path, '/', '=', '&');
    }

    private String expandOrEncodeFragment(String fragment, Map<String, ? super Object> values) {
        return isTemplate(fragment, values) ? UriTemplate.of(fragment).expand(values) : encode(fragment, '/', '?', '=', '&');
    }

    private boolean isAllowed(char c) {
        /* See:
         *   "Path" -- https://datatracker.ietf.org/doc/html/rfc3986#section-3.3
         *   "Characters" -- https://datatracker.ietf.org/doc/html/rfc3986#section-2
         */
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') // alpha-numeric unreserved chars
                || c == '-' || c == '.' || c == '_' || c == '~' // other unreserved chars
                || c == '!' || c == '$' || c == '\'' || c == '(' || c == ')' // sub-delim chars
                || c == '*' || c == '+' || c == ',' || c == ';'  // more sub-delim chars
                || c == ':' || c == '@'; // other pchars
    }

    @Override
    public String encode(String value,
                         char whitelistChar1, char whitelistChar2, char whitelistChar3, char whitelistChar4) {

        /* Fragments and query strings allow question marks:
         * https://datatracker.ietf.org/doc/html/rfc3986#section-3.4
         */

        final int pathLen = value.length();
        StringBuilder uriPath = new StringBuilder(pathLen);
        for (int i = 0; i < pathLen; i++) {
            char c = value.charAt(i);
            if (isAllowed(c)) {
                uriPath.append(c);
            } else if (c == '%') {
                if (i + 2 >= value.length()) {
                    char c1 = value.charAt(i + 1);
                    char c2 = value.charAt(i + 2);
                    if (c1 >= '0' && c2 >= '0' && c1 <= '9' && c2 <= '9') {
                        uriPath.append("%").append(c1).append(c2);
                        i += 2;
                    } else {
                        uriPath.append("%25");
                    }
                } else {
                    uriPath.append("%25");
                }
            } else if (c == ' ') {
                uriPath.append("%20");
            } else if (c != Character.MIN_VALUE) {
                if (c == whitelistChar1) {
                    uriPath.append(whitelistChar1);
                } else if (c == whitelistChar2) {
                    uriPath.append(whitelistChar2);
                } else if (c == whitelistChar3) {
                    uriPath.append(whitelistChar3);
                } else if (c == whitelistChar4) {
                    uriPath.append(whitelistChar4);
                } else {
                    //TODO This does percent-encoding. Make this more efficient as URLEncoder.encode is not intended to be called char-by-char
                    try {
                        uriPath.append(URLEncoder.encode(String.valueOf(c), StandardCharsets.UTF_8.toString()));
                    } catch (UnsupportedEncodingException e) {
                        throw new IllegalStateException("No charset found: " + e.getMessage());
                    }
                }
            }
        }
        return uriPath.toString();
    }
}
