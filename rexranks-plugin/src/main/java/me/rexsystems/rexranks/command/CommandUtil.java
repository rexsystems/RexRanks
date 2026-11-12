package me.rexsystems.rexranks.command;

import java.util.List;

public final class CommandUtil {
    private CommandUtil() {
    }

    public static String trackPath(List<String> groups, int current) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) {
                sb.append(" <dark_gray>→ ");
            }
            if (i == current) {
                sb.append("<gold>").append(groups.get(i)).append("<gray>");
            } else {
                sb.append("<gray>").append(groups.get(i));
            }
        }
        return sb.toString();
    }
}
