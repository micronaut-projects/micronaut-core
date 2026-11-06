package io.micronaut.dev.test;

import java.util.ArrayList;
import java.util.List;

/**
 * A small terminal emulator for the console's tests: what a terminal shows of an output.
 */
final class TerminalScreen {

    private TerminalScreen() {
    }

    /**
     * @param out The output
     * @return What a terminal shows of it: the cursor movements and clearing applied, the colours and links dropped,
     * and the trailing blank lines left out
     */
    static String render(String out) {
        List<StringBuilder> lines = new ArrayList<>();
        lines.add(new StringBuilder());
        int row = 0;
        int column = 0;
        int i = 0;
        while (i < out.length()) {
            char ch = out.charAt(i);
            if (ch == '\033' && i + 1 < out.length() && out.charAt(i + 1) == ']') {
                // OSC 8: up to the string terminator
                i = out.indexOf("\033\\", i) + 2;
                continue;
            }
            if (ch == '\033') {
                int end = i + 2;
                while (!Character.isLetter(out.charAt(end))) {
                    end++;
                }
                String parameter = out.substring(i + 2, end);
                switch (out.charAt(end)) {
                    case 'A' -> row = Math.max(0, row - Integer.parseInt(parameter));
                    case 'J' -> {
                        StringBuilder line = lines.get(row);
                        line.setLength(Math.min(line.length(), column));
                        while (lines.size() > row + 1) {
                            lines.remove(lines.size() - 1);
                        }
                    }
                    case 'K' -> lines.get(row).setLength(0);
                    default -> {
                        // colours
                    }
                }
                i = end + 1;
                continue;
            }
            if (ch == '\r') {
                column = 0;
            } else if (ch == '\n') {
                row++;
                column = 0;
                if (lines.size() <= row) {
                    lines.add(new StringBuilder());
                }
            } else {
                StringBuilder line = lines.get(row);
                while (line.length() < column) {
                    line.append(' ');
                }
                if (column < line.length()) {
                    line.setCharAt(column, ch);
                } else {
                    line.append(ch);
                }
                column++;
            }
            i++;
        }
        List<String> text = new ArrayList<>();
        for (StringBuilder line : lines) {
            text.add(line.toString().stripTrailing());
        }
        while (!text.isEmpty() && text.get(text.size() - 1).isEmpty()) {
            text.remove(text.size() - 1);
        }
        return String.join("\n", text);
    }
}
