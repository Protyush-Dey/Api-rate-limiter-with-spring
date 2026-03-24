package com.evolving.ratelimiter;

import com.evolving.ratelimiter.dto.RateLimitDTOs.*;
import com.evolving.ratelimiter.model.RateLimitConfig;
import com.evolving.ratelimiter.tier.SubscriptionTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@DisplayName("Tier Management Tests")
class TierManagementTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static final String CHECK_URL = "/api/rate-limit/check";

    private RateLimitCheckRequest buildRequest(String identifier) {
        return RateLimitCheckRequest.builder()
            .identifier(identifier)
            .identifierType(RateLimitConfig.IdentifierType.USER)
            .build();
    }

    /** Keep sending until 429 received. Returns true if bucket was exhausted. */
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
    @DisplayName("New identifiers should default to FREE tier (100 req limit)")
    void testDefaultTierIsFree() throws Exception {
        mockMvc.perform(post(CHECK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRequest("new_user_free"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(100));
    }

    @Test
    @DisplayName("FREE user should eventually be rate limited")
    void testFreeUserLimit() throws Exception {
        boolean got429 = exhaustBucket("free_limit_user", 300);
        assertTrue(got429, "FREE user should be rate limited after exhausting 100 token bucket");
    }

    @Test
    @DisplayName("Upgrading FREE to PRO should immediately increase limits to 1000")
    void testUpgradeFreeToPro() throws Exception {
        String identifier = "upgrade_test_user";

        // Exhaust FREE tier
        boolean got429 = exhaustBucket(identifier, 300);
        assertTrue(got429, "Should be rate limited on FREE tier first");

        // Upgrade to PRO
        mockMvc.perform(post("/api/admin/tier/upgrade")
                .param("identifier", identifier)
                .param("type", "USER")
                .param("newTier", "PRO"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tier").value("PRO"))
            .andExpect(jsonPath("$.capacity").value(1000));

        // Now should be allowed again with PRO limit
        mockMvc.perform(post(CHECK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRequest(identifier))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(1000));
    }

    @Test
    @DisplayName("Admin should be able to retrieve tier info for an identifier")
    void testGetTierInfo() throws Exception {
        AssignTierRequest assignReq = AssignTierRequest.builder()
            .identifier("enterprise_user")
            .identifierType(RateLimitConfig.IdentifierType.USER)
            .tier(SubscriptionTier.ENTERPRISE)
            .build();

        mockMvc.perform(post("/api/admin/tier/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignReq)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/tier/enterprise_user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tier").value("ENTERPRISE"))
            .andExpect(jsonPath("$.capacity").value(10000));
    }

    @Test
    @DisplayName("Should return all available tier configurations")
    void testListAllTiers() throws Exception {
        mockMvc.perform(get("/api/admin/tiers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.FREE").exists())
            .andExpect(jsonPath("$.PRO").exists())
            .andExpect(jsonPath("$.ENTERPRISE").exists())
            .andExpect(jsonPath("$.UNLIMITED").exists());
    }
}