package ru.starik.chuvakide;

public final class ChuvakCodec {
    private static final String ALPHABET = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
    private ChuvakCodec() {}

    public static String encode(String input) {
        StringBuilder out = new StringBuilder();
        boolean firstInLine = true;
        for (int i = 0; i < input.length(); i++) {
            char original = input.charAt(i);
            if (original == '\r') continue;
            if (original == '\n') {
                out.append('\n');
                firstInLine = true;
                continue;
            }
            if (!firstInLine) out.append('|');
            firstInLine = false;
            char c = Character.toLowerCase(original);
            int idx = ALPHABET.indexOf(c);
            if (idx >= 0) {
                for (int n = 0; n <= idx; n++) {
                    if (n > 0) out.append(' ');
                    out.append("чувак");
                }
            } else {
                out.append(original);
            }
        }
        return out.toString();
    }

    public static String decode(String input) throws IllegalArgumentException {
        String[] lines = input.replace("\r", "").split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int li = 0; li < lines.length; li++) {
            if (li > 0) out.append('\n');
            String line = lines[li];
            if (!line.contains("|")) {
                out.append(decodeTokenIfPossible(line));
                continue;
            }
            String[] tokens = line.split("\\|", -1);
            for (String token : tokens) out.append(decodeTokenIfPossible(token));
        }
        return out.toString();
    }

    private static String decodeTokenIfPossible(String token) {
        String trimmed = token.trim();
        if (trimmed.isEmpty()) return token;
        String[] words = trimmed.split("\\s+");
        for (String word : words) {
            if (!"чувак".equalsIgnoreCase(word)) return token;
        }
        int count = words.length;
        if (count < 1 || count > ALPHABET.length()) {
            throw new IllegalArgumentException("Некорректная буква чувак-кода: повторов " + count + ", допустимо 1–33");
        }
        return String.valueOf(ALPHABET.charAt(count - 1));
    }

    public static boolean looksEncoded(String source) {
        return source.contains("чувак") && source.contains("|");
    }
}
