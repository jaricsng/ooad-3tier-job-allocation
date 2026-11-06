package edu.ooad.joballoc.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.ooad.joballoc.web.http.BadRequestException;

import java.io.IOException;

/** JSON (de)serialisation with Jackson. Unknown fields are rejected to keep the API contract strict. */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private Json() { }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + value.getClass().getSimpleName(), e);
        }
    }

    public static <T> T read(byte[] body, Class<T> type) {
        if (body == null || body.length == 0) {
            throw new BadRequestException("A JSON request body is required");
        }
        try {
            return MAPPER.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid JSON for " + type.getSimpleName() + ": " + e.getOriginalMessage());
        } catch (IOException e) {
            throw new BadRequestException("Unreadable JSON body");
        }
    }
}
