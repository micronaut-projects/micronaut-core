package io.micronaut.http.filter

import io.micronaut.core.util.PathMatcher
import spock.lang.Specification

import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class FilterPatternMatcherSpec extends Specification {

    static final List<String> PATHS = [
            '', '/', '//', '/foo', '/foo/', '/foo/bar', '/foo//bar', 'foo', 'foo/bar', '/health', '/health/',
            '/health/liveness', '/healthliveness', '/health/readiness', '/health/other', 'health', '//health',
            '/FOO', '/foo/bar/baz.txt', '/a?b', '/%20', '?query', '/foo?x=y'
    ]

    /**
     * What the filter routes did before: the path matcher of the style called for each pattern.
     */
    static boolean matchedOneByOne(FilterPatternStyle style, List<String> patterns, String path) {
        PathMatcher pathMatcher = style.getPathMatcher()
        return patterns.any { pathMatcher.matches(it, path) }
    }

    void "ANT patterns match like the Ant path matcher: #patterns"() {
        given:
        def matcher = FilterPatternMatcher.of(FilterPatternStyle.ANT, patterns)

        expect:
        PATHS.each { path ->
            assert matcher.matches(path) == matchedOneByOne(FilterPatternStyle.ANT, patterns, path), "path [$path]"
        }
        matcher.matchesAllPaths() == matchesAll

        where:
        patterns                        | matchesAll
        ['/**']                         | true
        ['/**', '/**']                  | true
        ['/**', 'foo/**']               | false
        ['foo/**', '/**']               | false
        ['/foo/**']                     | false
        ['/foo', '/foo/*']              | false
        ['**']                          | false
        ['/*']                          | false
        ['/**/bar']                     | false
        ['/health/**', '/foo/**/baz*']  | false
        []                              | false
    }

    void "the default pattern style is ANT"() {
        given:
        def matcher = FilterPatternMatcher.of(null, ['/**'])

        expect:
        matcher.matchesAllPaths()
        matcher.matches('/foo')
        !matcher.matches('foo')
        !matcher.matches(null)
    }

    void "the match-all pattern does not match a path without a leading slash"() {
        given:
        def matcher = FilterPatternMatcher.of(FilterPatternStyle.ANT, ['/**'])

        expect:
        matcher.matches('/')
        matcher.matches('/foo/bar')
        !matcher.matches('')
        !matcher.matches('foo')
        !PathMatcher.ANT.matches('/**', '')
        !PathMatcher.ANT.matches('/**', 'foo')
    }

    void "REGEX patterns match like the regex path matcher: #patterns"() {
        given:
        def matcher = FilterPatternMatcher.of(FilterPatternStyle.REGEX, patterns)

        expect:
        PATHS.each { path ->
            assert matcher.matches(path) == matchedOneByOne(FilterPatternStyle.REGEX, patterns, path), "path [$path]"
        }
        !matcher.matchesAllPaths()

        where:
        patterns << [
                // the health endpoint filter
                ['/?/?health', '/?/?health/?liveness', '/?/?health/?readiness'],
                ['/fo(a|o)$'],
                ['/foo', '/foo/.*', '.*bar'],
                ['^/foo$', 'health|/health/.+'],
                ['/(fo)o/\\1', '/f(o)o'],
                ['(?i)/foo', '/bar'],
                ['/bar', '(?i)/foo'],
                ['(?x)/foo # a comment', '/health'],
                ['\\Q/foo', '/health'],
                ['/(?<n>f)oo', '/health'],
                ['/(?:a|b)', '/health/(?=l).*'],
                ['/[)(]', '/health'],
                ['', '/'],
                ['.*'],
                []
        ]
    }

    void "several REGEX patterns allocate a single matcher when they can be combined"() {
        given:
        def matcher = FilterPatternMatcher.of(FilterPatternStyle.REGEX, ['/?/?health', '/?/?health/?liveness', '/?/?health/?readiness'])
        Pattern regex = matcher.@regex

        expect:
        regex != null
        regex.pattern() == '(?:/?/?health)|(?:/?/?health/?liveness)|(?:/?/?health/?readiness)'
        matcher.matches('/health/liveness')
        !matcher.matches('/foo')
    }

    void "REGEX patterns that could change meaning when combined are matched one at a time: #patterns"() {
        given:
        def matcher = FilterPatternMatcher.of(FilterPatternStyle.REGEX, patterns)

        expect:
        matcher.@regex == null

        where:
        patterns << [
                ['/(fo)o/\\1', '/f(o)o'],
                ['(?i)/foo', '/bar'],
                ['(?x)/foo # a comment', '/health'],
                ['\\Q/foo', '/health'],
                ['/(?<n>f)oo', '/health'],
                ['/(?:a|b)', '/health']
        ]
    }

    void "an invalid regular expression fails when a path is matched, as before"() {
        given:
        def matcher = FilterPatternMatcher.of(FilterPatternStyle.REGEX, patterns)

        when:
        matcher.matches('/foo')

        then:
        thrown(PatternSyntaxException)

        where:
        patterns << [['/foo['], ['/bar', '/foo[']]
    }

    void "an invalid regular expression after a matching one is not compiled, as before"() {
        given:
        def matcher = FilterPatternMatcher.of(FilterPatternStyle.REGEX, ['/foo', '/bar['])

        expect:
        matcher.matches('/foo')
    }

    void "the match-all pattern is checked without the Ant path matcher"() {
        given:
        def matcher = FilterPatternMatcher.of(FilterPatternStyle.ANT, ['/**', '/foo'])

        expect:
        matcher.patterns as List == ['/foo']
        matcher.matches('/anything')
        !matcher.matches('foo')
    }

    void "the combined pattern is only used for full matches"() {
        given:
        def matcher = FilterPatternMatcher.of(FilterPatternStyle.REGEX, ['/foo', '/bar'])
        Matcher m = (matcher.@regex as Pattern).matcher('/foo/bar')

        expect:
        m.find()
        !matcher.matches('/foo/bar')
    }
}
