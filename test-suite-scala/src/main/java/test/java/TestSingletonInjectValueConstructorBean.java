package test.java;

import io.micronaut.context.annotation.Value;

import javax.inject.Singleton;

@Singleton
public class TestSingletonInjectValueConstructorBean {
    public final String injectedString;
    public final byte injectedByte;
    public final short injectedShort;
    public final int injectedInt;
    public final long injectedLong;
    public final float injectedFloat;
    public final double injectedDouble;
    public final char injectedChar;
    public final boolean injectedBoolean;
    public final int lookUpInteger;
    @Value("46") public int injectIntField;

    public TestSingletonInjectValueConstructorBean(
            @Value("injected String") String injectedString,
            @Value("41") byte injectedByte,
            @Value("42") short injectedShort,
            @Value("43") int injectedInt,
            @Value("44") long injectedLong,
            @Value("44.1f") float injectedFloat,
            @Value("44.2f") double injectedDouble,
            @Value("#") char injectedChar,
            @Value("true") boolean injectedBoolean,
            @Value("${lookup.integer}") int lookUpInteger
    ) {
        this.injectedString = injectedString;
        this.injectedByte = injectedByte;
        this.injectedShort = injectedShort;
        this.injectedInt = injectedInt;
        this.injectedLong = injectedLong;
        this.injectedFloat = injectedFloat;
        this.injectedDouble = injectedDouble;
        this.injectedChar = injectedChar;
        this.injectedBoolean = injectedBoolean;
        this.lookUpInteger = lookUpInteger;
    }
}
