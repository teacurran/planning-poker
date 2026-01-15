package com.scrumpoker.logging;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.matchesPattern;

/**
 * Integration test for {@link CorrelationIdFilter}.
 * <p>
 * Verifies that:
 * <ul>
 *   <li>Correlation IDs are automatically generated for requests without X-Correlation-ID header</li>
 *   <li>Correlation IDs are preserved from incoming X-Correlation-ID header</li>
 *   <li>Correlation IDs are included in response headers</li>
 * </ul>
 * </p>
 */
@QuarkusTest
class CorrelationIdFilterTest {

    /**
     * Test that a correlation ID is automatically generated and returned in response headers
     * when the request does not include an X-Correlation-ID header.
     */
    @Test
    void testCorrelationIdGeneratedAutomatically() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"title\":\"Correlation Test Room\"}")
        .when()
                .post("/api/v1/rooms")
        .then()
                .statusCode(201)
                .header(LoggingConstants.CORRELATION_ID_HEADER, notNullValue())
                // Verify it's a UUID format (8-4-4-4-12 hexadecimal pattern)
                .header(LoggingConstants.CORRELATION_ID_HEADER,
                        matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    /**
     * Test that an existing correlation ID from the request header is preserved
     * and returned in the response.
     */
    @Test
    void testCorrelationIdPreservedFromRequest() {
        String customCorrelationId = "test-correlation-id-12345";

        given()
                .contentType(ContentType.JSON)
                .header(LoggingConstants.CORRELATION_ID_HEADER, customCorrelationId)
                .body("{\"title\":\"Correlation Header Test\"}")
        .when()
                .post("/api/v1/rooms")
        .then()
                .statusCode(201)
                .header(LoggingConstants.CORRELATION_ID_HEADER, customCorrelationId);
    }

    /**
     * Test that correlation ID works for API endpoints (not just health checks).
     * <p>
     * Note: This test uses /api/v1/rooms/{roomId} endpoint which returns 404 for nonexistent rooms,
     * but the correlation ID should still be present even on error responses.
     * </p>
     */
    @Test
    void testCorrelationIdOnApiEndpoint() {
        given()
                .when()
                .get("/api/v1/rooms/nonexistent")
                .then()
                .statusCode(404)
                .header(LoggingConstants.CORRELATION_ID_HEADER, notNullValue());
    }
}
