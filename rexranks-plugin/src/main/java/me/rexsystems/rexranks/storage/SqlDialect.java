package me.rexsystems.rexranks.storage;

import java.util.Locale;

public enum SqlDialect {
    H2,
    SQLITE,
    MYSQL;

    public static SqlDialect from(String type) {
        String n = type.toLowerCase(Locale.ROOT);
        return switch (n) {
            case "h2" -> H2;
            case "sqlite", "sqlite3" -> SQLITE;
            case "mysql", "mariadb", "maria" -> MYSQL;
            default -> throw new IllegalArgumentException("Unknown SQL dialect: " + type);
        };
    }

    public String autoIncrementId() {
        return switch (this) {
            case H2 -> "BIGINT AUTO_INCREMENT PRIMARY KEY";
            case SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT";
            case MYSQL -> "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY";
        };
    }

    public String booleanType() {
        return switch (this) {
            case SQLITE -> "INTEGER";
            default -> "BOOLEAN";
        };
    }

    public String textType() {
        return switch (this) {
            case MYSQL -> "TEXT";
            default -> "CLOB";
        };
    }

    /**
     * TEXT is portable enough: H2 accepts TEXT as an alias for CLOB/VARCHAR.
     */
    public String longText() {
        return "TEXT";
    }

    public String quote(String ident) {
        return switch (this) {
            case MYSQL -> "`" + ident + "`";
            default -> "\"" + ident + "\"";
        };
    }
}
