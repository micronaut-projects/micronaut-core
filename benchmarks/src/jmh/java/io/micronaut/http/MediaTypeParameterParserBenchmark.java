package io.micronaut.http;

import io.micronaut.core.util.StringUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@State(Scope.Benchmark)
public class MediaTypeParameterParserBenchmark {
    private static final String NO_PARAMETERS = "application/json";
    private static final String SIMPLE_PARAMETERS = "text/plain; charset=utf-8";
    private static final String QUOTED_PARAMETERS = "text/plain; charset=\"utf-8\"";
    private static final String QUOTED_SEMICOLON_PARAMETERS = "text/plain; foo=\"a;b\"; charset=\"utf-8\"";

    @Benchmark
    public void oldUpstreamNoParameters(Blackhole blackhole) {
        blackhole.consume(parseWithOldUpstream(NO_PARAMETERS));
    }

    @Benchmark
    public void newImplementationNoParameters(Blackhole blackhole) {
        blackhole.consume(parseWithNewImplementation(NO_PARAMETERS));
    }

    @Benchmark
    public void oldUpstreamSimpleParameters(Blackhole blackhole) {
        blackhole.consume(parseWithOldUpstream(SIMPLE_PARAMETERS));
    }

    @Benchmark
    public void newImplementationSimpleParameters(Blackhole blackhole) {
        blackhole.consume(parseWithNewImplementation(SIMPLE_PARAMETERS));
    }

    @Benchmark
    public void oldUpstreamQuotedParameters(Blackhole blackhole) {
        blackhole.consume(parseWithOldUpstream(QUOTED_PARAMETERS));
    }

    @Benchmark
    public void newImplementationQuotedParameters(Blackhole blackhole) {
        blackhole.consume(parseWithNewImplementation(QUOTED_PARAMETERS));
    }

    @Benchmark
    public void oldUpstreamQuotedSemicolonParameters(Blackhole blackhole) {
        blackhole.consume(parseWithOldUpstream(QUOTED_SEMICOLON_PARAMETERS));
    }

    @Benchmark
    public void newImplementationQuotedSemicolonParameters(Blackhole blackhole) {
        blackhole.consume(parseWithNewImplementation(QUOTED_SEMICOLON_PARAMETERS));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(".*" + MediaTypeParameterParserBenchmark.class.getSimpleName() + ".*")
            .warmupIterations(3)
            .measurementIterations(5)
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    private static ParsedMediaType parseWithOldUpstream(String name) {
        name = name.trim();
        String withoutArgs;
        Iterator<String> splitIt = StringUtils.splitOmitEmptyStringsIterator(name, ';');
        Map<CharSequence, String> parameters = Collections.emptyMap();
        if (splitIt.hasNext()) {
            withoutArgs = splitIt.next();
            if (splitIt.hasNext()) {
                Map<CharSequence, String> parsedParameters = null;
                while (splitIt.hasNext()) {
                    String paramExpression = splitIt.next();
                    int i = paramExpression.indexOf('=');
                    if (i > -1) {
                        String paramName = paramExpression.substring(0, i).trim();
                        String paramValue = paramExpression.substring(i + 1).trim();
                        if (parsedParameters == null) {
                            parsedParameters = new LinkedHashMap<>();
                        }
                        parsedParameters.put(paramName, paramValue);
                    }
                }
                parameters = parsedParameters == null ? Collections.emptyMap() : parsedParameters;
            }
        } else {
            withoutArgs = name;
        }
        return new ParsedMediaType(withoutArgs, parameters);
    }

    private static ParsedMediaType parseWithNewImplementation(String name) {
        name = name.trim();
        if (name.indexOf(';') == -1) {
            return new ParsedMediaType(name, Collections.emptyMap());
        }

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
                    new BigDecimal(unquoteParameterValue(normalizedValue));
                }
                parsedParameters.put(normalizedAttribute, normalizedValue);
            }
        }.run(name);
        return new ParsedMediaType(
            parsedType[0],
            parsedParameters.isEmpty() ? Collections.emptyMap() : parsedParameters
        );
    }

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
                                if (needParameterValue) {
                                    valueBuilder.append(headerValue.charAt(parameterValueEnd));
                                }
                                parameterValueEnd++;
                            } else {
                                if (needParameterValue) {
                                    valueBuilder.append(c);
                                }
                            }
                        }
                    }
                    if (needParameterValue) {
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

    private record ParsedMediaType(String type, Map<CharSequence, String> parameters) {
    }
}
