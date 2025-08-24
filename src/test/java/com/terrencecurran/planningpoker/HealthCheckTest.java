package com.terrencecurran.planningpoker;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;

/**
 * Simple health check test to verify the application starts correctly
 * and all components are properly initialized.
 */
@QuarkusTest
public class HealthCheckTest {
    
    @Test
    public void testApplicationStartup() {
        // Test that the application starts and health endpoint responds
        RestAssured.given()
            .when().get("/q/health")
            .then()
            .statusCode(200);
    }
    
    @Test
    public void testLivenessEndpoint() {
        RestAssured.given()
            .when().get("/q/health/live")
            .then()
            .statusCode(200);
    }
    
    @Test
    public void testReadinessEndpoint() {
        RestAssured.given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200);
    }
}