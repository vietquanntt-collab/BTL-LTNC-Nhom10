package com.btl.team10.server.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

final class SqlScriptRunner {
    private SqlScriptRunner() {
    }

    static void executeFile(Connection connection, Path script) throws IOException, SQLException {
        String sql = Files.readString(script);
        for (String statementText : splitStatements(sql)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(statementText);
            }
        }
    }

    private static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char character = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (character == '\n' || character == '\r') {
                    inLineComment = false;
                    current.append(character);
                }
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote && character == '-' && next == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (character == '\'' && !inDoubleQuote) {
                if (inSingleQuote && next == '\'') {
                    current.append(character).append(next);
                    i++;
                    continue;
                }
                inSingleQuote = !inSingleQuote;
            } else if (character == '"' && !inSingleQuote) {
                if (inDoubleQuote && next == '"') {
                    current.append(character).append(next);
                    i++;
                    continue;
                }
                inDoubleQuote = !inDoubleQuote;
            }

            if (character == ';' && !inSingleQuote && !inDoubleQuote) {
                String statement = current.toString().trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                current.setLength(0);
            } else {
                current.append(character);
            }
        }

        String last = current.toString().trim();
        if (!last.isEmpty()) {
            statements.add(last);
        }
        return statements;
    }
}
