package example.pets;

import java.text.Normalizer;

public class FriendlyUrl {

    /**
     * This method transforms the text passed as an argument to a text without spaces,
     * html entities, accents, dots and extranges characters (only %,a-z,A-Z,0-9, ,_ and - are allowed).
     *
     * Borrowed from Wordpress: file wp-includes/formatting.php, function sanitize_title_with_dashes
     * http://core.svn.wordpress.org/trunk/wp-includes/formatting.php
     */
    public static String sanitizeWithDashes(String text) {

        if ( text == null ) {
            return "";
        }

        // Preserve escaped octets
        text = text.replaceAll("%([a-fA-F0-9][a-fA-F0-9])","---$1---");
        text = text.replaceAll("%","");
        text = text.replaceAll("---([a-fA-F0-9][a-fA-F0-9])---","%$1");

        // Remove accents
        text = removeAccents(text);

        // To lower case
        text = text.toLowerCase();

        // Kill entities
        text = text.replaceAll("&.+?;","");

        // Dots -> ''
        text = text.replaceAll("\\.","");

        // Remove any character except %a-zA-Z0-9 _-
        text = text.replaceAll("[^%a-zA-Z0-9 _-]", "");

        // Trim
        text = text.trim();

        // Spaces -> dashes
        text = text.replaceAll("\\s+", "-");

        // Dashes -> dash
        text = text.replaceAll("-+", "-");

        // It must end in a letter or digit, otherwise we strip the last char
        if ( !Character.isLetterOrDigit(text.substring(text.length() - 1).charAt(0)) ) {
            text = text.substring(0, text.length() - 1);
        }

        return text;
    }

    /**
     * Converts all accent characters to ASCII characters.
     *
     * If there are no accent characters, then the string given is just returned.
     *
     */
    private static String removeAccents(String  text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
