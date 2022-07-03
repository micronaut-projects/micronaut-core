package io.micronaut.context.banner;

import java.io.PrintStream;

import io.micronaut.core.version.VersionUtils;

/**
 * Implementation of {@link Banner} that prints the Micronaut version after main banner.
 */
public class MicronautVersionBanner implements Banner {
    private static final String MICRONAUT = "  Micronaut";

    private final PrintStream out;

    /**
     * Constructor.
     *
     * @param out The print stream
     */
    public MicronautVersionBanner(PrintStream out) {
        this.out = out;
    }


    @Override
    public void print() {
        String version = VersionUtils.getMicronautVersion();
        version = (version != null) ? " (v" + version + ")" : "";
        out.println(MICRONAUT + version + "\n");
    }
}
