package io.micronaut.inject.failures

final class LineEndingStripper {
    static String strip(String s) {
        return s.replace("\r", "" ).replace("\n", "")
    }
}
