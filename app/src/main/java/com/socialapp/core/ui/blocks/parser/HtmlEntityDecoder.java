package com.socialapp.core.ui.blocks.parser;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Single-pass HTML character entity decoder (named, decimal &#...;, hex &#x...;).
 * Pure Java, zero regex.
 */
public final class HtmlEntityDecoder {

    private static final Map<String, String> NAMED_ENTITIES = new HashMap<>();

    static {
        NAMED_ENTITIES.put("amp", "&");
        NAMED_ENTITIES.put("lt", "<");
        NAMED_ENTITIES.put("gt", ">");
        NAMED_ENTITIES.put("quot", "\"");
        NAMED_ENTITIES.put("apos", "'");
        NAMED_ENTITIES.put("nbsp", " ");
        NAMED_ENTITIES.put("ndash", "–");
        NAMED_ENTITIES.put("mdash", "—");
        NAMED_ENTITIES.put("hellip", "…");
        NAMED_ENTITIES.put("lsquo", "‘");
        NAMED_ENTITIES.put("rsquo", "’");
        NAMED_ENTITIES.put("ldquo", "“");
        NAMED_ENTITIES.put("rdquo", "”");
        NAMED_ENTITIES.put("copy", "©");
        NAMED_ENTITIES.put("reg", "®");
        NAMED_ENTITIES.put("trade", "™");
        NAMED_ENTITIES.put("times", "×");
        NAMED_ENTITIES.put("divide", "÷");
        NAMED_ENTITIES.put("middot", "·");
        NAMED_ENTITIES.put("bull", "•");
    }

    private HtmlEntityDecoder() {
    }

    @NonNull
    public static String decode(@NonNull String input) {
        if (input.indexOf('&') < 0) {
            return input;
        }
        int len = input.length();
        StringBuilder out = new StringBuilder(len);
        int i = 0;
        while (i < len) {
            char ch = input.charAt(i);
            if (ch == '&') {
                int semi = input.indexOf(';', i + 1);
                if (semi > i + 1 && semi - i <= 12) {
                    String entity = input.substring(i + 1, semi);
                    String resolved = resolveEntity(entity);
                    if (resolved != null) {
                        out.append(resolved);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static String resolveEntity(@NonNull String entity) {
        if (entity.startsWith("#x") || entity.startsWith("#X")) {
            if (entity.length() <= 2) return null;
            try {
                int cp = Integer.parseInt(entity.substring(2), 16);
                if (Character.isValidCodePoint(cp)) {
                    return cp == 160 ? " " : new String(Character.toChars(cp));
                }
            } catch (NumberFormatException ignored) {
            }
            return null;
        }
        if (entity.startsWith("#")) {
            if (entity.length() <= 1) return null;
            try {
                int cp = Integer.parseInt(entity.substring(1), 10);
                if (Character.isValidCodePoint(cp)) {
                    return cp == 160 ? " " : new String(Character.toChars(cp));
                }
            } catch (NumberFormatException ignored) {
            }
            return null;
        }
        return NAMED_ENTITIES.get(entity);
    }
}
