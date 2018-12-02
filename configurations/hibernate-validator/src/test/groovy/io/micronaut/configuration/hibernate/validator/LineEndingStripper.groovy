package io.micronaut.configuration.hibernate.validator

final class LineEndingStripper {
    static String strip(String s) {
        return s.replace("\r", "" ).replace("\n", "")
    }
}
