package me.rexsystems.rexranks.engine;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Matches a permission against a node and returns a specificity score.
 * Higher scores beat lower ones. {@code -1} means no match.
 *
 * <ul>
 *   <li>Exact match: {@code 1_000_000 + length}</li>
 *   <li>Regex match: {@code 100_000 + pattern length}</li>
 *   <li>{@code foo.bar.*} matching {@code foo.bar.baz}: {@code 10_000 + prefix length}</li>
 *   <li>Standalone {@code *}: {@code 1}</li>
 * </ul>
 */
public final class WildcardMatcher {
    private WildcardMatcher() {
    }

    public static int score(String nodeKey, String permission) {
        return score(nodeKey, permission, false);
    }

    public static int score(String nodeKey, String permission, boolean regex) {
        String key = nodeKey.toLowerCase(Locale.ROOT);
        String perm = permission.toLowerCase(Locale.ROOT);
        if (regex) {
            return regexScore(key, perm);
        }
        if (key.equals(perm)) {
            return 1_000_000 + key.length();
        }
        if (key.equals("*")) {
            return 1;
        }
        if (key.endsWith(".*")) {
            String prefix = key.substring(0, key.length() - 1); // keep trailing dot: "foo.bar."
            if (perm.startsWith(prefix) && perm.length() > prefix.length()) {
                return 10_000 + prefix.length();
            }
            return -1;
        }
        // trailing wildcard without a preceding dot, e.g. "foo.bar*"
        if (key.endsWith("*") && key.length() > 1) {
            String prefix = key.substring(0, key.length() - 1);
            if (perm.startsWith(prefix)) {
                return 9_000 + prefix.length();
            }
        }
        return -1;
    }

    private static int regexScore(String pattern, String permission) {
        String body = pattern;
        if (body.startsWith("r=")) {
            body = body.substring(2);
        }
        try {
            if (Pattern.compile(body, Pattern.CASE_INSENSITIVE).matcher(permission).matches()) {
                return 100_000 + body.length();
            }
        } catch (PatternSyntaxException ignored) {
            return -1;
        }
        return -1;
    }
}
