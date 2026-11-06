package com.btl.team10.common.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** JSON codec shared by the network client and server. */
public final class JsonCodec {
    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(Instant.class, new InstantAdapter().nullSafe())
            .create();

    private JsonCodec() {
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static <T> T fromJson(String json, Class<T> targetType) {
        Objects.requireNonNull(targetType, "targetType");
        if (json == null || json.isBlank()) {
            throw new JsonParseException("JSON input must not be blank");
        }
        return GSON.fromJson(json, targetType);
    }

    private static final class InstantAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter output, Instant value) throws IOException {
            output.value(value.toString());
        }

        @Override
        public Instant read(JsonReader input) throws IOException {
            String value = input.nextString();
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException error) {
                throw new JsonSyntaxException("Expected an ISO-8601 UTC instant", error);
            }
        }
    }
}
