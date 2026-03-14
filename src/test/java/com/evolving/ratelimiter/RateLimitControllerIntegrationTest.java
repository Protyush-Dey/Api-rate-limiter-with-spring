package com.evolving.ratelimiter;

import com.evolving.ratelimiter.dto.RateLimitDTOs.*;
import com.evolving.ratelimiter.model.RateLimitConfig;
import com.evolving.ratelimiter.service.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@DisplayName("RateLimitController Integration Tests")
class RateLimitControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RateLimiterService rateLimiterService;

    private static final String CHECK_URL = "/api/rate-limit/check";

    private RateLimitCheckRequest buildRequest(String identifier) {
        return RateLimitCheckRequest.builder()
            .identifier(identifier)
            .identifierType(RateLimitConfig.IdentifierType.USER)
            .endpoint("/api/demo/hello")
            .httpMethod("GET")
            .build();
    }

    /** Send up to maxAttempts requests. Returns true when 429 is received. */
    private boolean exhaustBucket(String identifier, int maxAttempts) throws Exception {
        for (int i = 0; i < maxAttempts; i++) {
            int status = mockMvc.perform(post(CHECK_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildRequest(identifier))))
                .andReturn().getResponse().getStatus();
            if (status == 429) return true;
        }
        return false;
    }

    @Test
    @DisplayName("Single request should be allowed (200 OK)")
    void testSingleRequestAllowed() throws Exception {
        mockMvc.perform(post(CHECK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRequest("user_test_1"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowed").value(true))
            .andExpect(jsonPath("$.remaining").isNumber())
            .andExpect(jsonPath("$.limit").value(100));
    }

    @Test
    @DisplayName("Rate limit headers must be present in every response")
    void testRateLimitHeadersPresent() throws Exception {
        mockMvc.perform(post(CHECK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRequest("user_headers_test"))))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-RateLimit-Limit"))
            .andExpect(header().exists("X-RateLimit-Remaining"))
            .andExpect(header().exists("X-RateLimit-Reset"));
    }

    @Test
    @DisplayName("Should return HTTP 429 when rate limit is exceeded")
    void test101stRequestReturns429() throws Exception {
        boolean got429 = exhaustBucket("user_429_test", 300);
        assertTrue(got429, "Should have received 429 when bucket is exhausted");
    }

    @Test
    @DisplayName("Different identifiers should be tracked independently")
    void testDifferentIdentifiersTrackedSeparately() throws Exception {
        boolean userABlocked = exhaustBucket("user_A_separate", 300);
        assertTrue(userABlocked, "user_A should be rate limited");

        mockMvc.perform(post(CHECK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRequest("user_B_separate"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    @DisplayName("Reset endpoint should clear rate limit for an identifier")
    void testResetEndpointClearsLimits() throws Exception {
        String identifier = "user_reset_test";

        boolean got429 = exhaustBucket(identifier, 300);
        assertTrue(got429, "Should be rate limited before reset");

        mockMvc.perform(post("/api/rate-limit/reset")
                .param("identifier", identifier))
            .andExpect(status().isOk());

        mockMvc.perform(post(CHECK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRequest(identifier))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    @DisplayName("Status endpoint should return UP status")
    void testStatusEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/rate-limit/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.activeBuckets").isNumber());
    }

    @Test
    @DisplayName("Missing identifier should return 400 Bad Request")
    void testMissingIdentifierReturns400() throws Exception {
        mockMvc.perform(post(CHECK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifierType\": \"USER\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Remaining tokens should decrease with each request")
    void testRemainingDecreases() throws Exception {
        String identifier = "user_decrease_test";

        String r1 = mockMvc.perform(post(CHECK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRequest(identifier))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeader("X-RateLimit-Remaining");

        String r2 = mockMvc.perform(post(CHECK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRequest(identifier))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeader("X-RateLimit-Remaining");

        assertTrue(Integer.parseInt(r2) < Integer.parseInt(r1),
            "Remaining should decrease after each request");
    }
}