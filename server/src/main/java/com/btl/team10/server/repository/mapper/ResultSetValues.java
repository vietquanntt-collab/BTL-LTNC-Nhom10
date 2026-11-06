package com.btl.team10.server.repository.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/** Small JDBC value readers shared by the explicit row mappers. */
final class ResultSetValues {
    private ResultSetValues() {
    }

    static Instant requiredInstant(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) {
            throw new SQLException("Required timestamp is null: " + column);
        }
        return parseInstant(value, column);
    }

    static Instant nullableInstant(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        return value == null || value.isBlank() ? null : parseInstant(value, column);
    }

    static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    static <E extends Enum<E>> E requiredEnum(
            ResultSet result, String column, Class<E> enumType) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) {
            throw new SQLException("Required enum value is null: " + column);
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException error) {
            throw new SQLException("Invalid " + enumType.getSimpleName() + " in " + column + ": " + value, error);
        }
    }

    private static Instant parseInstant(String value, String column) throws SQLException {
        String normalized = value.endsWith("Z") ? value : value + "Z";
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException error) {
            throw new SQLException("Invalid ISO-8601 timestamp in " + column + ": " + value, error);
        }
    }
}
