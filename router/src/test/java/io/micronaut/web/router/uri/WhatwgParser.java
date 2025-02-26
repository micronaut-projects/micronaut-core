package io.micronaut.web.router.uri;

import com.ibm.icu.text.IDNA;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

final class WhatwgParser {
    static final Set<String> SPECIAL_SCHEMES = Set.of("http", "https", "ftp", "file", "ws", "wss");

    private State stateOverride = null;
    private String input;
    private WhatwgUrl baseUrl = null;
    private Charset encoding = StandardCharsets.UTF_8;
    private int pointer = 0;
    private State state = State.SCHEME_START;
    private boolean atSignSeen = false;
    private boolean insideBrackets = false;
    private boolean passwordTokenSeen = false;
    private boolean abort = false;

    private boolean schemeMixedCase = false;
    private final StringBuilder buffer = new StringBuilder();

    private @NonNull String scheme = "";
    private @NonNull String username = "";
    private @NonNull String password = "";
    private @Nullable String host = null;
    private @Nullable Integer port = null;
    private final @NonNull StringBuilder path = new StringBuilder();
    private boolean opaquePath = false;
    private @Nullable StringBuilder query = null;
    private @Nullable StringBuilder fragment = null;

    WhatwgParser(String input) {
        this.input = input;
    }

    public void setBaseUrl(WhatwgUrl baseUrl) {
        this.baseUrl = baseUrl;
    }

    void setInputUrl(WhatwgUrl inputUrl) {
        this.scheme = inputUrl.scheme;
        this.username = inputUrl.username;
        this.password = inputUrl.password;
        this.host = inputUrl.host;
        this.port = inputUrl.port;
        setPath(inputUrl.path, inputUrl.opaquePath);
        setQuery(inputUrl.query);
        setFragment(inputUrl.fragment);
    }

    void setStateOverride(State stateOverride) {
        this.stateOverride = stateOverride;
        this.state = stateOverride;
    }

    private void setPath(String path, boolean opaque) {
        this.path.setLength(0);
        this.path.append(path);
        this.opaquePath = opaque;
    }

    private void setQuery(@Nullable String query) {
        if (query == null) {
            this.query = null;
        } else if (this.query != null) {
            this.query.setLength(0);
            this.query.append(query);
        } else {
            this.query = new StringBuilder(query);
        }
    }

    private void setFragment(@Nullable String fragment) {
        if (fragment == null) {
            this.fragment = null;
        } else if (this.fragment != null) {
            this.fragment.setLength(0);
            this.fragment.append(fragment);
        } else {
            this.fragment = new StringBuilder(fragment);
        }
    }

    WhatwgUrl toUrl() {
        return new WhatwgUrl(
            scheme,
            username,
            password,
            host,
            port,
            path.toString(),
            opaquePath,
            query == null ? null : query.toString(),
            fragment == null ? null : fragment.toString()
        );
    }

    void parse() {
        trimWhitespace();

        while (pointer <= input.length() && !abort) {
            boolean eof = pointer == input.length();
            int c;
            int cLen;
            if (eof) {
                c = -1;
                cLen = 1;
            } else {
                c = input.charAt(pointer);
                if (Character.isSurrogate((char) c)) {
                    if (Character.isHighSurrogate((char) c) && pointer + 1 < input.length()) {
                        c = Character.toCodePoint((char) c, input.charAt(pointer + 1));
                        cLen = 2;
                    } else {
                        throw failure("Unpaired surrogate");
                    }
                } else {
                    cLen = 1;
                }
            }
            switch (state) {
                case SCHEME_START -> {
                    if (isAsciiLowerAlpha(c)) {
                        state = State.SCHEME;
                    } else if (isAsciiUpperAlpha(c)) {
                        schemeMixedCase = true;
                        state = State.SCHEME;
                    } else if (stateOverride == null) {
                        state = State.NO_SCHEME;
                        pointer -= cLen;
                    } else {
                        throw failure("Invalid scheme while state override is given");
                    }
                }
                case SCHEME -> {
                    if (isAsciiLowerAlpha(c) || isAsciiDigit(c) || c == '+' || c == '-' || c == '.') {
                        break;
                    } else if (isAsciiUpperAlpha(c)) {
                        schemeMixedCase = true;
                    } else if (c == ':') {
                        String newScheme = input.substring(0, pointer);
                        if (schemeMixedCase) {
                            newScheme = newScheme.toLowerCase(Locale.ROOT);
                        }
                        if (stateOverride != null) {
                            if (SPECIAL_SCHEMES.contains(this.scheme) != SPECIAL_SCHEMES.contains(newScheme)) {
                                abort = true;
                                break;
                            }
                            if (newScheme.equals("file") && (port != null || !username.isEmpty() || !password.isEmpty())) {
                                abort = true;
                                break;
                            }
                            if ("file".equals(this.scheme) && "".equals(host)) {
                                abort = true;
                                break;
                            }
                        }
                        scheme = newScheme;
                        if (stateOverride != null) {
                            if (Objects.equals(port, getDefaultPort(scheme))) {
                                port = null;
                            }
                            abort = true;
                            break;
                        }
                        if (scheme.equals("file")) {
                            if (input.length() <= pointer + 2 || input.charAt(pointer + 1) != '/' || input.charAt(pointer + 2) != '/') {
                                validationError(ValidationError.SPECIAL_SCHEME_MISSING_FOLLOWING_SOLIDUS);
                            }
                            state = State.FILE;
                        } else if (SPECIAL_SCHEMES.contains(scheme) && baseUrl != null && scheme.equals(baseUrl.scheme)) {
                            state = State.SPECIAL_RELATIVE_OR_AUTHORITY;
                        } else if (SPECIAL_SCHEMES.contains(scheme)) {
                            state = State.SPECIAL_AUTHORITY_SLASHES;
                        } else if (input.length() > pointer + 1 && input.charAt(pointer + 1) == '/') {
                            pointer++;
                            state = State.PATH_OR_AUTHORITY;
                        } else {
                            path.setLength(0);
                            opaquePath = true;
                            state = State.OPAQUE_PATH;
                        }
                    } else if (stateOverride == null) {
                        state = State.NO_SCHEME;
                        pointer = -1;
                    } else {
                        throw failure("Invalid character in scheme");
                    }
                }
                case NO_SCHEME -> {
                    if (baseUrl == null || (c != '#' && baseUrl.opaquePath)) {
                        throw fatalValidationError(ValidationError.MISSING_SCHEME_NON_RELATIVE_URL);
                    }
                    if (baseUrl.opaquePath && c == '#') {
                        this.scheme = baseUrl.scheme;
                        setPath(baseUrl.path, baseUrl.opaquePath);
                        setQuery(baseUrl.query);
                        setFragment("");
                        state = State.FRAGMENT;
                    } else if (!"file".equals(baseUrl.scheme)) {
                        state = State.RELATIVE;
                        pointer -= cLen;
                    } else {
                        state = State.FILE;
                        pointer -= cLen;
                    }
                }
                case SPECIAL_RELATIVE_OR_AUTHORITY -> {
                    if (c == '/' && pointer + 1 < input.length() && input.charAt(pointer + 1) == '/') {
                        state = State.SPECIAL_AUTHORITY_IGNORE_SLASHES;
                        pointer++;
                    } else {
                        validationError(ValidationError.SPECIAL_SCHEME_MISSING_FOLLOWING_SOLIDUS);
                        state = State.RELATIVE;
                        pointer -= cLen;
                    }
                }
                case PATH_OR_AUTHORITY -> {
                    if (c == '/') {
                        state = State.AUTHORITY;
                    } else {
                        state = State.PATH;
                        pointer -= cLen;
                    }
                }
                case RELATIVE -> {
                    assert baseUrl != null;
                    assert !"file".equals(baseUrl.scheme);
                    this.scheme = baseUrl.scheme;
                    if (c == '/') {
                        state = State.RELATIVE_SLASH;
                    } else if (SPECIAL_SCHEMES.contains(baseUrl.scheme) && c == '\\') {
                        validationError(ValidationError.INVALID_REVERSE_SOLIDUS);
                        state = State.RELATIVE_SLASH;
                    } else {
                        this.username = baseUrl.username;
                        this.password = baseUrl.password;
                        this.host = baseUrl.host;
                        this.port = baseUrl.port;
                        setPath(baseUrl.path, baseUrl.opaquePath);
                        setQuery(baseUrl.query);

                        if (c == '?') {
                            setQuery("");
                            state = State.QUERY;
                        } else if (c == '#') {
                            setFragment("");
                            state = State.FRAGMENT;
                        } else if (!eof) {
                            setQuery(null);
                            shortenPath();
                            state = State.PATH;
                            pointer -= cLen;
                        }
                    }
                }
                case RELATIVE_SLASH -> {
                    if (SPECIAL_SCHEMES.contains(scheme) && (c == '\\' || c == '/')) {
                        if (c == '\\') {
                            validationError(ValidationError.INVALID_REVERSE_SOLIDUS);
                        }
                        state = State.SPECIAL_AUTHORITY_IGNORE_SLASHES;
                    } else if (c == '/') {
                        state = State.AUTHORITY;
                    } else {
                        this.username = baseUrl.username;
                        this.password = baseUrl.password;
                        this.host = baseUrl.host;
                        this.port = baseUrl.port;
                        state = State.PATH;
                        pointer -= cLen;
                    }
                }
                case SPECIAL_AUTHORITY_SLASHES -> {
                    if (c == '/' && pointer + 1 < input.length() && input.charAt(pointer + 1) == '/') {
                        state = State.SPECIAL_AUTHORITY_IGNORE_SLASHES;
                        pointer++;
                    } else {
                        validationError(ValidationError.SPECIAL_SCHEME_MISSING_FOLLOWING_SOLIDUS);
                        state = State.SPECIAL_AUTHORITY_IGNORE_SLASHES;
                        pointer -= cLen;
                    }
                }
                case SPECIAL_AUTHORITY_IGNORE_SLASHES -> {
                    if (c != '/' && c != '\\') {
                        state = State.AUTHORITY;
                        pointer -= cLen;
                    } else {
                        validationError(ValidationError.SPECIAL_SCHEME_MISSING_FOLLOWING_SOLIDUS);
                    }
                }
                case AUTHORITY -> {
                    if (c == '@') {
                        validationError(ValidationError.INVALID_CREDENTIALS);
                        if (atSignSeen) {
                            buffer.insert(0, "%40");
                        }
                        atSignSeen = true;
                        StringBuilder dst = passwordTokenSeen ? new StringBuilder(password) : new StringBuilder(username);
                        for (int i = 0; i < buffer.length(); i++) {
                            int cp = buffer.charAt(i);
                            if (Character.isHighSurrogate((char) cp)) {
                                cp = buffer.codePointAt(i);
                                i++;
                            }
                            if (cp == ':' && !passwordTokenSeen) {
                                passwordTokenSeen = true;
                                username = dst.toString();
                                dst = new StringBuilder(password);
                                continue;
                            }
                            PercentEncoder.USERINFO.encodeUtf8(dst, cp);
                        }
                        if (passwordTokenSeen) {
                            password = dst.toString();
                        } else {
                            username = dst.toString();
                        }
                        buffer.setLength(0);
                    } else if (eof || c == '/' || c == '?' || c == '#' || (SPECIAL_SCHEMES.contains(scheme) && c == '\\')) {
                        if (atSignSeen && buffer.isEmpty()) {
                            throw fatalValidationError(ValidationError.HOST_MISSING);
                        } else {
                            pointer -= buffer.length() + 1;
                            buffer.setLength(0);
                            state = State.HOST;
                        }
                    } else {
                        buffer.appendCodePoint(c);
                    }
                }
                case HOST, HOSTNAME -> {
                    if (stateOverride != null && scheme.equals("file")) {
                        pointer -= cLen;
                        state = State.FILE_HOST;
                    } else if (c == ':' && !insideBrackets) {
                        if (buffer.isEmpty()) {
                            throw fatalValidationError(ValidationError.HOST_MISSING);
                        }
                        if (stateOverride == State.HOSTNAME) {
                            abort = true;
                            break;
                        }
                        host = hostParse(buffer, !SPECIAL_SCHEMES.contains(scheme));
                        buffer.setLength(0);
                        state = State.PORT;
                    } else if (eof || c == '/' || c == '?' || c == '#' || (SPECIAL_SCHEMES.contains(scheme) && c == '\\')) {
                        pointer -= cLen;
                        if (SPECIAL_SCHEMES.contains(scheme) && buffer.isEmpty()) {
                            throw fatalValidationError(ValidationError.HOST_MISSING);
                        } else if (stateOverride != null && buffer.isEmpty() && (!username.isEmpty() || !password.isEmpty() || port != null)) {
                            abort = true;
                            break;
                        }
                        host = hostParse(buffer, !SPECIAL_SCHEMES.contains(scheme));
                        buffer.setLength(0);
                        state = State.PATH_START;
                        if (stateOverride != null) {
                            abort = true;
                        }
                    } else {
                        if (c == '[') {
                            insideBrackets = true;
                        } else if (c == ']') {
                            insideBrackets = false;
                        }
                        buffer.appendCodePoint(c);
                    }
                }
                case PORT -> {
                    if (isAsciiDigit(c)) {
                        buffer.appendCodePoint(c);
                    } else if (eof || c == '/' || c == '?' || c == '#' || (SPECIAL_SCHEMES.contains(scheme) && c == '\\') || stateOverride != null) {
                        if (!buffer.isEmpty()) {
                            int p;
                            try {
                                p = Integer.parseInt(buffer.toString());
                            } catch (NumberFormatException e) {
                                // only happens on out-of-range
                                throw fatalValidationError(ValidationError.PORT_OUT_OF_RANGE);
                            }
                            if (p > 0xffff) {
                                throw fatalValidationError(ValidationError.PORT_OUT_OF_RANGE);
                            }
                            if (Objects.equals(getDefaultPort(scheme), p)) {
                                this.port = null;
                            } else {
                                this.port = p;
                            }
                            buffer.setLength(0);
                        }
                        if (stateOverride != null) {
                            abort = true;
                        }
                        state = State.PATH_START;
                        pointer -= cLen;
                    } else {
                        throw fatalValidationError(ValidationError.PORT_INVALID);
                    }
                }
                case FILE -> {
                    scheme = "file";
                    host = "";
                    if (c == '/' || c == '\\') {
                        if (c == '\\') {
                            validationError(ValidationError.INVALID_REVERSE_SOLIDUS);
                        }
                        state = State.FILE_SLASH;
                    } else if (baseUrl != null && "file".equals(baseUrl.scheme)) {
                        this.host = baseUrl.host;
                        setPath(baseUrl.path, baseUrl.opaquePath);
                        setQuery(baseUrl.query);

                        if (c == '?') {
                            setQuery("");
                            state = State.QUERY;
                        } else if (c == '#') {
                            setFragment("");
                            state = State.FRAGMENT;
                        } else if (!eof) {
                            setQuery(null);
                            if (!startsWithWindowsDriveLetter(input, pointer)) {
                                shortenPath();
                            } else {
                                validationError(ValidationError.FILE_INVALID_WINDOWS_DRIVE_LETTER);
                                setPath("", false);
                            }
                            state = State.PATH;
                            pointer -= cLen;
                        }
                    } else {
                        state = State.PATH;
                        pointer -= cLen;
                    }
                }
                case FILE_SLASH -> {
                    if (c == '/' || c == '\\') {
                        if (c == '\\') {
                            validationError(ValidationError.INVALID_REVERSE_SOLIDUS);
                        }
                        state = State.FILE_HOST;
                    } else {
                        if (baseUrl != null && "file".equals(baseUrl.scheme)) {
                            this.host = baseUrl.host;
                            if (!startsWithWindowsDriveLetter(input, pointer)) {
                                String basePath = baseUrl.path;
                                int i = basePath.indexOf('/', 1);
                                int end = i == -1 ? basePath.length() : i;
                                if (isNormalizedWindowsDriveLetter(basePath, 1, end)) {
                                    appendToPath(basePath, 1, end);
                                }
                            }
                        }
                        state = State.PATH;
                        pointer -= cLen;
                    }
                }
                case FILE_HOST -> {
                    if (eof || c == '/' || c == '\\' || c == '?' || c == '#') {
                        pointer -= cLen;
                        if (stateOverride == null && isWindowsDriveLetter(buffer)) {
                            validationError(ValidationError.FILE_INVALID_WINDOWS_DRIVE_LETTER_HOST);
                            state = State.PATH;
                        } else if (buffer.isEmpty()) {
                            host = "";
                            if (stateOverride != null) {
                                abort = true;
                            }
                            state = State.PATH_START;
                        } else {
                            host = hostParse(buffer, !SPECIAL_SCHEMES.contains(scheme));
                            if (host.equals("localhost")) {
                                host = "";
                            }
                            if (stateOverride != null) {
                                abort = true;
                            }
                            buffer.setLength(0);
                            state = State.PATH_START;
                        }
                    } else {
                        buffer.appendCodePoint(c);
                    }
                }
                case PATH_START -> {
                    if (SPECIAL_SCHEMES.contains(scheme)) {
                        if (c == '\\') {
                            validationError(ValidationError.INVALID_REVERSE_SOLIDUS);
                        }
                        state = State.PATH;
                        if (c != '/' && c != '\\') {
                            pointer -= cLen;
                        }
                    } else if (stateOverride == null && c == '?') {
                        setQuery("");
                        state = State.QUERY;
                    } else if (stateOverride == null && c == '#') {
                        setFragment("");
                        state = State.FRAGMENT;
                    } else if (!eof) {
                        state = State.PATH;
                        if (c != '/') {
                            pointer -= cLen;
                        }
                    } else if (stateOverride != null && host != null) {
                        appendToPath("");
                    }
                }
                case PATH -> {
                    if (eof || c == '/' || (SPECIAL_SCHEMES.contains(scheme) && c == '\\') || (stateOverride == null && (c == '?' || c == '#'))) {
                        if (SPECIAL_SCHEMES.contains(scheme) && c == '\\') {
                            validationError(ValidationError.INVALID_REVERSE_SOLIDUS);
                        }
                        if (isDoubleDotUrlPathSegment(buffer)) {
                            shortenPath();
                            if (c != '/' && (c != '\\' || !SPECIAL_SCHEMES.contains(scheme))) {
                                appendToPath("");
                            }
                        } else if (isSingleDotUrlPathSegment(buffer) && c != '/' && (c != '\\' || !SPECIAL_SCHEMES.contains(scheme))) {
                            appendToPath("");
                        } else if (!isSingleDotUrlPathSegment(buffer)) {
                            if (scheme.equals("file") && path.isEmpty() && isWindowsDriveLetter(buffer)) {
                                buffer.setCharAt(1, ':');
                            }
                            appendToPath(buffer);
                        }
                        buffer.setLength(0);
                        if (c == '?') {
                            setQuery("");
                            state = State.QUERY;
                        } else if (c == '#') {
                            setFragment("");
                            state = State.FRAGMENT;
                        }
                    } else {
                        validateCodePoint(c);
                        PercentEncoder.PATH.encodeUtf8(buffer, c);
                    }
                }
                case OPAQUE_PATH -> {
                    if (c == '?') {
                        setQuery("");
                        state = State.QUERY;
                    } else if (c == '#') {
                        setFragment("");
                        state = State.FRAGMENT;
                    } else if (!eof) {
                        validateCodePoint(c);
                        PercentEncoder.C0.encodeUtf8(path, c);
                    }
                }
                case QUERY -> {
                    if (!encoding.equals(StandardCharsets.UTF_8) && (!SPECIAL_SCHEMES.contains(scheme) || scheme.equals("ws") || scheme.equals("wss"))) {
                        encoding = StandardCharsets.UTF_8;
                    }
                    if (eof || (stateOverride == null && c == '#')) {
                        PercentEncoder queryPercentEncodeSet = SPECIAL_SCHEMES.contains(scheme) ? PercentEncoder.SPECIAL_QUERY : PercentEncoder.QUERY;
                        percentEncodeAfterEncoding(query, encoding, buffer, queryPercentEncodeSet, false);
                        buffer.setLength(0);
                        if (c == '#') {
                            setFragment("");
                            state = State.FRAGMENT;
                        }
                    } else {
                        validateCodePoint(c);
                        buffer.appendCodePoint(c);
                    }
                }
                case FRAGMENT -> {
                    if (!eof) {
                        validateCodePoint(c);
                        PercentEncoder.FRAGMENT.encodeUtf8(fragment, c);
                    }
                }
                default -> throw new AssertionError("Unexpected value: " + state);
            }
            pointer += cLen;
        }
    }

    private void validateCodePoint(int c) {
        if (!isUrlCodePoint(c) && c != '%') {
            validationError(ValidationError.INVALID_URL_UNIT);
        }
        if (c == '%' && (pointer + 2 >= input.length() || !isAsciiHexDigit(input.charAt(pointer + 1)) || !isAsciiHexDigit(input.charAt(pointer + 2)))) {
            validationError(ValidationError.INVALID_URL_UNIT);
        }
    }

    private void appendToPath(CharSequence s) {
        appendToPath(s, 0, s.length());
    }

    private void appendToPath(CharSequence s, int start, int end) {
        if (!opaquePath) {
            path.append('/');
        }
        path.append(s, start, end);
    }

    private void trimWhitespace() {
        StringBuilder trimmed = null;
        if (!input.isEmpty() && (isC0OrSpace(input.charAt(0)) || isC0OrSpace(input.charAt(input.length() - 1)))) {
            validationError(ValidationError.INVALID_URL_UNIT);

            trimmed = new StringBuilder(input);
            // remove trailing, O(n)
            while (!trimmed.isEmpty() && isC0OrSpace(trimmed.charAt(trimmed.length() - 1))) {
                trimmed.setLength(trimmed.length() - 1);
            }
            // remove leading, O(n)
            int n = 0;
            while (n < trimmed.length() && isC0OrSpace(trimmed.charAt(n))) {
                n++;
            }
            trimmed.delete(0, n);
        }
        if (trimmed == null ? hasTabOrNewline(input) : hasTabOrNewline(trimmed)) {
            validationError(ValidationError.INVALID_URL_UNIT);

            CharSequence source = trimmed == null ? input : trimmed;
            // can't do this in the existing `trimmed` StringBuilder because it would be O(n²)
            StringBuilder trimmed2 = new StringBuilder(source.length());
            for (int i = 0; i < source.length(); i++) {
                char c = source.charAt(i);
                if (!isTabOrNewline(c)) {
                    trimmed2.append(c);
                }
            }
            input = trimmed2.toString();
        } else if (trimmed != null) {
            input = trimmed.toString();
        }
    }

    private void validationError(ValidationError error) {

    }

    private String hostParse(StringBuilder buffer, boolean isOpaque) {
        if (!buffer.isEmpty() && buffer.charAt(0) == '[') {
            if (buffer.charAt(buffer.length() - 1) != ']') {
                throw fatalValidationError(ValidationError.IPV6_UNCLOSED);
            }
            buffer.setLength(buffer.length() - 1);
            StringBuilder out = new StringBuilder("[");
            ipv6Serialize(out, ipv6Parse(buffer, 1));
            out.append(']');
            return out.toString();
        }
        if (isOpaque) {
            StringBuilder out = new StringBuilder(buffer.length());
            for (int i = 0; i < buffer.length(); ) {
                int c = buffer.codePointAt(i);
                if (isForbiddenHostCodePoint(c)) {
                    throw fatalValidationError(ValidationError.HOST_INVALID_CODE_POINT);
                }
                validateCodePoint(c);
                PercentEncoder.C0.encodeUtf8(out, c);
                i += Character.charCount(c);
            }
            return out.toString();
        }
        assert !buffer.isEmpty();
        StringBuilder decoded = new StringBuilder(buffer.length());
        PercentDecoder.decode(decoded, buffer, null);
        String asciiDomain = domainToAscii(decoded.toString(), false);
        if (endsInANumber(asciiDomain)) {
            StringBuilder out = new StringBuilder();
            ipv4Serialize(out, ipv4Parse(asciiDomain));
            return out.toString();
        } else {
            return asciiDomain;
        }
    }

    private static boolean isForbiddenHostCodePoint(int c) {
        return c == 0 || c == '\t' || c == '\r' || c == '\n' || c == ' ' || c == '#' ||
            c == '/' || c == ':' || c == '<' || c == '>' || c == '?' || c == '@' ||
            c == '[' || c == '\\' || c == ']' || c == '^' || c == '|';
    }

    private static boolean isForbiddenDomainCodePoint(int c) {
        return isForbiddenHostCodePoint(c) || isC0(c) || c == '%' || c == 0x7f;
    }

    private String domainToAscii(String s, boolean beStrict) {
        StringBuilder dest = new StringBuilder();
        idnaToAscii(dest, s, beStrict);
        // todo: these two checks should be unnecessary for beStrict
        if (dest.isEmpty()) {
            throw fatalValidationError(ValidationError.DOMAIN_TO_ASCII);
        }
        for (int i = 0; i < dest.length(); i++) {
            // only some ascii chars are forbidden, so we don't have to use codePointAt here
            if (isForbiddenDomainCodePoint(dest.charAt(i))) {
                throw fatalValidationError(ValidationError.DOMAIN_INVALID_CODE_POINT);
            }
        }
        return dest.toString();
    }

    void idnaToAscii(StringBuilder dest, String input, boolean beStrict) {
        int flags = IDNA.CHECK_BIDI | IDNA.CHECK_CONTEXTJ | IDNA.NONTRANSITIONAL_TO_ASCII;
        if (beStrict) {
            flags |= IDNA.USE_STD3_RULES;
        }
        IDNA idna = IDNA.getUTS46Instance(flags);
        IDNA.Info info = new IDNA.Info();
        idna.nameToASCII(input, dest, info);
        if (info.hasErrors()) {
            for (IDNA.Error error : info.getErrors()) {
                switch (error) {
                    case LEADING_HYPHEN, TRAILING_HYPHEN, HYPHEN_3_4, LABEL_TOO_LONG,
                         DOMAIN_NAME_TOO_LONG -> {
                        if (beStrict) {
                            throw fatalValidationError(ValidationError.DOMAIN_TO_ASCII);
                        }
                    }
                    case BIDI, CONTEXTJ, PUNYCODE, LABEL_HAS_DOT, LEADING_COMBINING_MARK,
                         DISALLOWED, INVALID_ACE_LABEL ->
                        throw fatalValidationError(ValidationError.DOMAIN_TO_ASCII);
                    case CONTEXTO_PUNCTUATION, CONTEXTO_DIGITS ->
                        throw new AssertionError("ContextO checking should not be enabled");
                    case EMPTY_LABEL -> {
                    }
                }
            }
        }
    }

    private static boolean endsInANumber(String s) {
        if (s.isEmpty()) {
            return false;
        }
        int from = s.lastIndexOf('.');
        int to = s.length();
        if (from == s.length() - 1) {
            from = s.lastIndexOf('.', from - 1);
            to--;
        }
        from++;
        if (from == to) {
            return false;
        }
        return isValidIpv4Number(s, from, to);
    }

    private static boolean isValidIpv4Number(String s, int from, int to) {
        boolean decimal = true;
        for (int i = from; i < to; i++) {
            decimal &= isAsciiDigit(s.charAt(i));
        }
        if (decimal) {
            return true;
        }
        // try hex parsing
        if (to - from < 2 || s.charAt(from) != '0' || (s.charAt(from + 1) != 'x' && s.charAt(from + 1) != 'X')) {
            return false;
        }
        for (int i = from + 2; i < to; i++) {
            if (!isAsciiHexDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private int ipv4Parse(String s) {
        int[] dotIndices = new int[4];
        int pieceCount;
        dotIndices[0] = s.indexOf('.');
        dotIndices[1] = s.indexOf('.', dotIndices[0] + 1);
        dotIndices[2] = s.indexOf('.', dotIndices[1] + 1);
        dotIndices[3] = s.indexOf('.', dotIndices[2] + 1);
        if (dotIndices[1] == -1 || dotIndices[2] == -1 || dotIndices[3] == -1) {
            if (dotIndices[0] == -1) {
                pieceCount = 1;
            } else if (dotIndices[1] == -1) {
                pieceCount = 2;
            } else if (dotIndices[2] == -1) {
                pieceCount = 3;
            } else {
                pieceCount = 4;
            }
            dotIndices[pieceCount - 1] = s.length();
        } else {
            pieceCount = 5;
        }
        if (pieceCount > 1 && dotIndices[pieceCount - 2] == s.length() - 1) {
            validationError(ValidationError.IPV4_EMPTY_PART);
            pieceCount--;
        } else if (pieceCount > 4) {
            throw fatalValidationError(ValidationError.IPV4_TOO_MANY_PARTS);
        }
        int ipv4 = ipv4NumberParse(s, pieceCount == 1 ? 0 : dotIndices[pieceCount - 2] + 1, dotIndices[pieceCount - 1]);
        if (pieceCount > 1 && (ipv4 >>> (8 * (5 - pieceCount))) != 0) {
            throw fatalValidationError(ValidationError.IPV4_OUT_OF_RANGE_PART);
        }
        for (int i = 0; i < pieceCount - 1; i++) {
            int part = ipv4NumberParse(s, i == 0 ? 0 : dotIndices[i - 1] + 1, dotIndices[i]);
            if (part < 0 || part >= 256) {
                throw fatalValidationError(ValidationError.IPV4_OUT_OF_RANGE_PART);
            }
            ipv4 |= part << (8 * (3 - i));
        }
        return ipv4;
    }

    private int ipv4NumberParse(String s, int from, int to) {
        if (!isValidIpv4Number(s, from, to) || from == to) {
            throw failure("Invalid IPv4 number");
        }
        // the strict syntax checks are in isValidIpv4Number
        int radix;
        if (to - from >= 2 && (s.charAt(from + 1) == 'x' || s.charAt(from + 1) == 'X')) {
            from += 2;
            radix = 16;
        } else if (to - from >= 2 && s.charAt(from) == '0') {
            from++;
            radix = 8;
        } else {
            radix = 10;
        }
        if (to <= from) {
            validationError(ValidationError.IPV4_NON_DECIMAL_PART);
            return 0;
        }
        try {
            return Integer.parseUnsignedInt(s.substring(from, to), radix);
        } catch (NumberFormatException e) {
            throw failure("Failed to parse IPv4: " + e.getMessage());
        }
    }

    private short[] ipv6Parse(CharSequence cs, int ptr) {
        short[] pieces = new short[8];
        int pieceIndex = 0;
        int compress = -1;
        if (cs.length() > ptr && cs.charAt(ptr) == ':') {
            if (cs.length() == ptr + 1 || cs.charAt(ptr + 1) != ':') {
                throw fatalValidationError(ValidationError.IPV6_INVALID_COMPRESSION);
            }
            ptr += 2;
            pieceIndex = 1;
            compress = pieceIndex;
        }
        while (ptr < cs.length()) {
            if (pieceIndex >= 8) {
                throw fatalValidationError(ValidationError.IPV6_TOO_MANY_PIECES);
            }
            char c = cs.charAt(ptr);
            if (c == ':') {
                if (compress != -1) {
                    throw fatalValidationError(ValidationError.IPV6_MULTIPLE_COMPRESSION);
                }
                ptr++;
                pieceIndex++;
                compress = pieceIndex;
                continue;
            }
            int value = 0;
            int length = 0;
            while (length < 4 && isAsciiHexDigit(c)) {
                value = value * 0x10 + Integer.parseInt(Character.toString(c), 16);
                ptr++;
                length++;
                c = ptr >= cs.length() ? 0 : cs.charAt(ptr);
            }
            if (c == '.') {
                if (length == 0) {
                    throw fatalValidationError(ValidationError.IPV4_IN_IPV6_INVALID_CODE_POINT);
                }
                ptr -= length;
                c = cs.charAt(ptr);
                if (pieceIndex > 6) {
                    throw fatalValidationError(ValidationError.IPV4_IN_IPV6_TOO_MANY_PIECES);
                }
                int numbersSeen = 0;
                while (ptr < cs.length()) {
                    int ipv4Piece = -1;
                    if (numbersSeen > 0) {
                        if (c == '.' && numbersSeen < 4) {
                            ptr++;
                            c = ptr >= cs.length() ? 0 : cs.charAt(ptr);
                        } else {
                            throw fatalValidationError(ValidationError.IPV4_IN_IPV6_INVALID_CODE_POINT);
                        }
                    }
                    if (!isAsciiDigit(c)) {
                        throw fatalValidationError(ValidationError.IPV4_IN_IPV6_INVALID_CODE_POINT);
                    }
                    do {
                        int number = c - '0';
                        if (ipv4Piece == -1) {
                            ipv4Piece = number;
                        } else if (ipv4Piece == 0) {
                            throw fatalValidationError(ValidationError.IPV4_IN_IPV6_INVALID_CODE_POINT);
                        } else {
                            ipv4Piece = ipv4Piece * 10 + number;
                        }
                        if (ipv4Piece > 255) {
                            throw fatalValidationError(ValidationError.IPV4_IN_IPV6_OUT_OF_RANGE_PART);
                        }
                        ptr++;
                        c = ptr >= cs.length() ? 0 : cs.charAt(ptr);
                    } while (isAsciiDigit(c));
                    pieces[pieceIndex] = (short) (pieces[pieceIndex] * 0x100 + ipv4Piece);
                    numbersSeen++;
                    if (numbersSeen == 2 || numbersSeen == 4) {
                        pieceIndex++;
                    }
                }
                if (numbersSeen != 4) {
                    throw fatalValidationError(ValidationError.IPV4_IN_IPV6_TOO_FEW_PARTS);
                }
                break;
            } else if (c == ':') {
                ptr++;
                if (ptr >= cs.length()) {
                    throw fatalValidationError(ValidationError.IPV6_INVALID_CODE_POINT);
                }
                c = cs.charAt(ptr);
            } else if (ptr < cs.length()) {
                throw fatalValidationError(ValidationError.IPV6_INVALID_CODE_POINT);
            }
            pieces[pieceIndex++] = (short) value;
        }
        if (compress != -1) {
            int swaps = pieceIndex - compress;
            pieceIndex = 7;
            while (pieceIndex != 0 && swaps > 0) {
                short a = pieces[pieceIndex];
                pieces[pieceIndex] = pieces[compress + swaps - 1];
                pieces[compress + swaps - 1] = a;
                pieceIndex--;
                swaps--;
            }
        } else {
            if (pieceIndex != 8) {
                throw fatalValidationError(ValidationError.IPV6_TOO_FEW_PIECES);
            }
        }
        return pieces;
    }

    private static void ipv4Serialize(StringBuilder dest, int ipv4) {
        for (int i = 0; i < 4; i++) {
            if (i != 0) {
                dest.append('.');
            }
            dest.append((ipv4 >>> 8 * (3 - i)) & 0xff);
        }
    }

    private static void ipv6Serialize(StringBuilder dest, short[] ipv6) {
        int compress = findCompressedPieceIndex(ipv6);
        boolean ignore0 = false;
        for (int i = 0; i < ipv6.length; i++) {
            if (ignore0 && ipv6[i] == 0) {
                continue;
            }
            ignore0 = false;
            if (compress == i) {
                dest.append(i == 0 ? "::" : ":");
                ignore0 = true;
                continue;
            }
            dest.append(Integer.toHexString(ipv6[i] & 0xffff));
            if (i != 7) {
                dest.append(":");
            }
        }
    }

    private static int findCompressedPieceIndex(short[] ipv6) {
        int longestIndex = -1;
        int longestSize = 1;
        int foundIndex = -1;
        int foundSize = 0;
        for (int i = 0; i < ipv6.length; i++) {
            if (ipv6[i] != 0) {
                if (foundSize > longestSize) {
                    longestIndex = foundIndex;
                    longestSize = foundSize;
                }
                foundIndex = -1;
                foundSize = 0;
            } else {
                if (foundIndex == -1) {
                    foundIndex = i;
                }
                foundSize++;
            }
        }
        return foundSize > longestSize ? foundIndex : longestIndex;
    }

    private void shortenPath() {
        assert !opaquePath;
        if (!scheme.equals("file") || !isNormalizedWindowsDriveLetter(path, 1, path.length())) {
            int i = path.lastIndexOf("/");
            if (i != -1) {
                path.setLength(i);
            }
        }
    }

    private IllegalArgumentException fatalValidationError(ValidationError error) {
        validationError(error);
        return new IllegalArgumentException(error.toString());
    }

    private IllegalArgumentException failure(String message) {
        return new IllegalArgumentException(message);
    }

    private static boolean isC0(int c) {
        return c <= 0x1f;
    }

    private static boolean isC0OrSpace(char c) {
        return isC0(c) || c == ' ';
    }

    private static boolean isControl(char c) {
        return isC0(c) || (c >= 0x7f && c <= 0x9f);
    }

    private static boolean isAsciiDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAsciiUpperHexDigit(int c) {
        return isAsciiDigit(c) || (c >= 'A' && c <= 'F');
    }

    private static boolean isAsciiLowerHexDigit(int c) {
        return isAsciiDigit(c) || (c >= 'a' && c <= 'f');
    }

    static boolean isAsciiHexDigit(int c) {
        return isAsciiLowerHexDigit(c) || isAsciiUpperHexDigit(c);
    }

    private static boolean isAsciiUpperAlpha(int c) {
        return c >= 'A' && c <= 'Z';
    }

    private static boolean isAsciiLowerAlpha(int c) {
        return c >= 'a' && c <= 'z';
    }

    private static boolean isAsciiAlpha(char c) {
        return isAsciiUpperAlpha(c) || isAsciiLowerAlpha(c);
    }

    private static boolean isAsciiAlphanumeric(char c) {
        return isAsciiAlpha(c) || isAsciiDigit(c);
    }

    private static boolean isTabOrNewline(char c) {
        return c == '\t' || c == '\r' || c == '\n';
    }

    private static boolean hasTabOrNewline(CharSequence input) {
        for (int i = 0; i < input.length(); i++) {
            if (isTabOrNewline(input.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Integer getDefaultPort(String scheme) {
        return switch (scheme) {
            case "ftp" -> 21;
            case "http", "ws" -> 80;
            case "https", "wss" -> 443;
            default -> null;
        };
    }

    private static boolean isWindowsDriveLetter(CharSequence input) {
        return input.length() == 2 && isAsciiAlpha(input.charAt(0)) && (input.charAt(1) == ':' || input.charAt(1) == '|');
    }

    private static boolean isNormalizedWindowsDriveLetter(CharSequence input, int start, int end) {
        return end == 2 + start && isAsciiAlpha(input.charAt(start)) && input.charAt(start + 1) == ':';
    }

    private static boolean startsWithWindowsDriveLetter(CharSequence input, int start) {
        if (input.length() - start < 2) {
            return false;
        }
        if (input.length() - start >= 3) {
            char third = input.charAt(start + 2);
            if (third != '/' && third != '\\' && third != '?' && third != '#') {
                return false;
            }
        }
        char c = input.charAt(start + 1);
        return isAsciiAlpha(input.charAt(start)) && (c == ':' || c == '|');
    }

    private static boolean isDoubleDotUrlPathSegment(CharSequence input) {
        if (input.length() == 2) {
            return "..".contentEquals(input);
        } else if (input.length() == 4) {
            return ".%2e".contentEquals(input) || ".%2E".contentEquals(input) ||
                "%2e.".contentEquals(input) || "%2E.".contentEquals(input);
        } else if (input.length() == 6) {
            return "%2e%2e".contentEquals(input) || "%2e%2E".contentEquals(input) ||
                "%2E%2e".contentEquals(input) || "%2E%2E".contentEquals(input);
        } else {
            return false;
        }
    }

    private static boolean isSingleDotUrlPathSegment(CharSequence input) {
        return ".".contentEquals(input) || "%2e".contentEquals(input) || "%2E".contentEquals(input);
    }

    private static boolean isUrlCodePoint(int codePoint) {
        if (codePoint < 0xa0) {
            char c = (char) codePoint;
            if (isAsciiAlphanumeric(c)) {
                return true;
            }
            return "!$&'()*+,-./:;=?@_~".indexOf(c) != -1;
        } else {
            if (codePoint > 0x10fff) {
                return false;
            }
            if (codePoint <= Character.MAX_VALUE) {
                char c = (char) codePoint;
                if (Character.isSurrogate(c)) {
                    return false;
                }
            }
            if ((codePoint >= 0xfdd0 && codePoint <= 0xfdef) ||
                (codePoint >= 0xfffe && ((codePoint & 0xfff) == 0xffe || (codePoint & 0xfff) == 0xfff))) {
                return false;
            }
            return true;
        }
    }

    private static void percentEncodeAfterEncoding(StringBuilder dest, Charset charset, CharSequence input, PercentEncoder percentEncoder, boolean spaceAsPlus) {
        CharsetEncoder encoder = charset.newEncoder();
        CharBuffer inputBuffer = CharBuffer.wrap(input);
        ByteBuffer outputBuffer = ByteBuffer.allocate(1024);
        while (true) {
            CoderResult result = encoder.encode(inputBuffer, outputBuffer, true);
            outputBuffer.flip();
            while (outputBuffer.hasRemaining()) {
                byte b = outputBuffer.get();
                if (b == ' ' && spaceAsPlus) {
                    dest.append('+');
                } else {
                    percentEncoder.encodeByte(dest, b);
                }
            }
            outputBuffer.flip();
            if (result == CoderResult.OVERFLOW) {
                continue;
            }
            if (result.isError()) {
                dest.append("%26%23");
                char high = inputBuffer.get();
                if (Character.isHighSurrogate(high)) {
                    dest.append(Character.toCodePoint(high, inputBuffer.get()));
                } else {
                    dest.append((int) high);
                }
                dest.append("%3B");
            }
            break;
        }
    }

    private enum ValidationError {
        DOMAIN_TO_ASCII,
        DOMAIN_INVALID_CODE_POINT,
        DOMAIN_TO_UNICODE,

        HOST_INVALID_CODE_POINT,
        IPV4_EMPTY_PART,
        IPV4_TOO_MANY_PARTS,
        IPV4_NON_NUMERIC_PART,
        IPV4_NON_DECIMAL_PART,
        IPV4_OUT_OF_RANGE_PART,
        IPV6_UNCLOSED,
        IPV6_INVALID_COMPRESSION,
        IPV6_TOO_MANY_PIECES,
        IPV6_MULTIPLE_COMPRESSION,
        IPV6_INVALID_CODE_POINT,
        IPV6_TOO_FEW_PIECES,
        IPV4_IN_IPV6_TOO_MANY_PIECES,
        IPV4_IN_IPV6_INVALID_CODE_POINT,
        IPV4_IN_IPV6_OUT_OF_RANGE_PART,
        IPV4_IN_IPV6_TOO_FEW_PARTS,

        INVALID_URL_UNIT,
        SPECIAL_SCHEME_MISSING_FOLLOWING_SOLIDUS,
        MISSING_SCHEME_NON_RELATIVE_URL,
        INVALID_REVERSE_SOLIDUS,
        INVALID_CREDENTIALS,
        HOST_MISSING,
        PORT_OUT_OF_RANGE,
        PORT_INVALID,
        FILE_INVALID_WINDOWS_DRIVE_LETTER,
        FILE_INVALID_WINDOWS_DRIVE_LETTER_HOST,
    }

    enum State {
        SCHEME_START,
        SCHEME,
        NO_SCHEME,
        SPECIAL_RELATIVE_OR_AUTHORITY,
        PATH_OR_AUTHORITY,
        RELATIVE,
        RELATIVE_SLASH,
        SPECIAL_AUTHORITY_SLASHES,
        SPECIAL_AUTHORITY_IGNORE_SLASHES,
        AUTHORITY,
        HOST,
        HOSTNAME,
        PORT,
        FILE,
        FILE_SLASH,
        FILE_HOST,
        PATH_START,
        PATH,
        OPAQUE_PATH,
        QUERY,
        FRAGMENT,
    }
}
