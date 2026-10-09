package com.socialapp.core.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class ApiErrorParserTest {

    @Test
    public void testParse401Unauthorized() {
        ResponseBody body = ResponseBody.create(
                MediaType.parse("application/json"),
                "{\"message\": \"Unauthenticated.\"}"
        );
        Response<?> response = Response.error(401, body);

        AppError error = ApiErrorParser.parse(response);

        assertEquals(401, error.getStatusCode());
        assertEquals(AppError.ErrorType.UNAUTHORIZED, error.getType());
        assertEquals("Unauthenticated.", error.getMessage());
    }

    @Test
    public void testParse422ValidationErrorWithFieldErrors() {
        String json = "{\n" +
                "  \"message\": \"The given data was invalid.\",\n" +
                "  \"errors\": {\n" +
                "    \"email\": [\"The email has already been taken.\"],\n" +
                "    \"password\": [\"The password must be at least 8 characters.\"]\n" +
                "  }\n" +
                "}";

        ResponseBody body = ResponseBody.create(MediaType.parse("application/json"), json);
        Response<?> response = Response.error(422, body);

        AppError error = ApiErrorParser.parse(response);

        assertEquals(422, error.getStatusCode());
        assertEquals(AppError.ErrorType.VALIDATION_ERROR, error.getType());
        assertEquals("The given data was invalid.", error.getMessage());
        assertNotNull(error.getFieldErrors());
        assertEquals("The email has already been taken.", error.getFirstFieldError("email"));
        assertEquals("The password must be at least 8 characters.", error.getFirstFieldError("password"));
    }

    @Test
    public void testParse500ServerError() {
        ResponseBody body = ResponseBody.create(
                MediaType.parse("application/json"),
                "{\"message\": \"Server Error\"}"
        );
        Response<?> response = Response.error(500, body);

        AppError error = ApiErrorParser.parse(response);

        assertEquals(500, error.getStatusCode());
        assertEquals(AppError.ErrorType.SERVER_ERROR, error.getType());
    }
}
