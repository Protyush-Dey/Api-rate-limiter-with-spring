# 🚦 API Rate Limiter Service
**Evolving Systems Ltd — Internship Technical Assessment (Project 3)**

A production-grade API rate limiting service built with **Java 17 + Spring Boot 3**.
Implements the **Token Bucket** algorithm with **Multi-Tier support (FREE / PRO / ENTERPRISE / UNLIMITED)**.

---

## 📋 Table of Contents
- [Architecture Overview](#architecture-overview)
- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Phase 1: Core Features](#phase-1-core-features)
- [Phase 2: Multiple Tiers](#phase-2-multiple-tiers)
- [API Reference](#api-reference)
- [Testing](#testing)
- [Configuration](#configuration)

---

## 🏗 Architecture Overview

```
HTTP Request
     │
     ▼
RateLimitFilter (middleware — intercepts ALL requests)
     │  checks identifier: X-API-Key > X-User-ID > IP
     │
     ▼
RateLimiterService
     │  looks up / creates TokenBucket in ConcurrentHashMap (O(1))
     │
     ▼
TokenBucket.tryConsume()
     │  ✅ allowed → add headers, pass through
     │  ❌ rejected → 429 Too Many Requests
     │
     ▼
RateLimitEvent saved to H2 DB (for analytics)
```

### Token Bucket Algorithm
```
Capacity: 100 tokens  │████████████████████│ full
Each request: -1 token │███████████████░░░░░│ 75 remaining
Refill: +10/second    │████████████████████│ refills over time
If empty: HTTP 429    │                    │ 0 tokens → rejected
```

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+

### Run the Application
```bash
# Clone / navigate to project
cd rate-limiter

# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run

# Or run the jar
java -jar target/rate-limiter-1.0.0.jar
```

Server starts at: **http://localhost:8080**
H2 Console: **http://localhost:8080/h2-console**
- JDBC URL: `jdbc:h2:mem:ratelimiterdb`
- Username: `sa` | Password: *(empty)*

---

## 📁 Project Structure

```
rate-limiter/
├── src/main/java/com/evolving/ratelimiter/
│   ├── RateLimiterApplication.java        # Main entry point
│   ├── algorithm/
│   │   └── TokenBucket.java               # ⭐ Core algorithm
│   ├── config/
│   │   └── TierConfig.java                # Tier capacity/rate mappings
│   ├── controller/
│   │   ├── RateLimitController.java       # /api/rate-limit/**
│   │   ├── AdminController.java           # /api/admin/**
│   │   └── DemoController.java            # /api/demo/** (for testing)
│   ├── dto/
│   │   └── RateLimitDTOs.java             # Request/Response objects
│   ├── exception/
│   │   ├── RateLimitExceededException.java
│   │   └── GlobalExceptionHandler.java
│   ├── filter/
│   │   └── RateLimitFilter.java           # ⭐ HTTP middleware filter
│   ├── model/
│   │   ├── RateLimitConfig.java           # DB entity: identifier configs
│   │   └── RateLimitEvent.java            # DB entity: audit log
│   ├── repository/
│   │   ├── RateLimitConfigRepository.java
│   │   └── RateLimitEventRepository.java
│   ├── service/
│   │   └── RateLimiterService.java        # ⭐ Core business logic
│   └── tier/
│       └── SubscriptionTier.java          # FREE/PRO/ENTERPRISE/UNLIMITED enum
├── src/main/resources/
│   └── application.properties
├── src/test/java/com/evolving/ratelimiter/
│   ├── TokenBucketTest.java               # Unit tests (10 tests)
│   ├── RateLimitControllerIntegrationTest.java  # Integration tests (8 tests)
│   └── TierManagementTest.java            # Tier tests (5 tests)
├── postman_collection.json               # 22 ready-to-run Postman requests
└── pom.xml
```

---

## ✅ Phase 1: Core Features

### Token Bucket Algorithm
| Parameter | Value |
|-----------|-------|
| Capacity  | 100 tokens (configurable) |
| Refill Rate | 10 tokens/second |
| Identifier Types | USER, IP, API_KEY |
| Storage | ConcurrentHashMap (O(1) lookup) |

### Rate Limit Headers (every response)
```
X-RateLimit-Limit:     100
X-RateLimit-Remaining: 87
X-RateLimit-Reset:     1
Retry-After:           1   (only on 429)
```

### Success Criteria Verification
| Criteria | How to Test |
|----------|-------------|
| Submit 100 → succeed, 101st → 429 | Run `2. Drain Bucket` 100x in Postman Runner, then `3. Trigger 429` |
| Wait for refill → new requests succeed | After 429, wait 10 seconds, retry |
| Different identifiers tracked separately | Use different `identifier` values |
| Headers present in all responses | Check any response headers |
| Reset endpoint clears limits | Run `4. Reset Bucket` after draining |

---

## 🎯 Phase 2: Multiple Tiers

### Tier Limits
| Tier | Capacity | Refill Rate |
|------|----------|-------------|
| FREE | 100 | 10/sec |
| PRO | 1,000 | 100/sec |
| ENTERPRISE | 10,000 | 1,000/sec |
| UNLIMITED | ∞ | ∞ |

### How Tiers Work
1. New identifiers auto-assigned to **FREE**
2. Upgrade via `/api/admin/tier/assign` or `/api/admin/tier/upgrade`
3. Upgrade takes effect **immediately** (bucket rebuilt with new limits)
4. Optional `expiresAt` field for temporary upgrades → auto-downgrades to FREE

### Testing Tiers
```bash
# 1. Assign PRO tier
POST /api/admin/tier/assign
{ "identifier": "user_123", "identifierType": "USER", "tier": "PRO" }

# 2. Verify limit is now 1000
POST /api/rate-limit/check
{ "identifier": "user_123", "identifierType": "USER" }
# Response: { "limit": 1000, ... }

# 3. Quick upgrade shortcut
POST /api/admin/tier/upgrade?identifier=user_123&type=USER&newTier=ENTERPRISE
```

---

## 📡 API Reference

### Core Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/rate-limit/check` | Check if request is allowed |
| GET | `/api/rate-limit/check-ip` | Auto-check by requester IP |
| GET | `/api/rate-limit/status` | Service health + stats |
| POST | `/api/rate-limit/reset` | Reset bucket (testing) |

### Admin Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/admin/tier/assign` | Assign tier to identifier |
| POST | `/api/admin/tier/upgrade` | Quick tier upgrade |
| GET | `/api/admin/tier/{identifier}` | Get tier info |
| GET | `/api/admin/tiers` | List all tier configs |
| GET | `/api/admin/configs` | List all identifier configs |
| GET | `/api/admin/buckets` | Live bucket token counts |

### Request/Response Examples

**Check Rate Limit:**
```json
POST /api/rate-limit/check
{
  "identifier": "user_123",
  "identifierType": "USER",
  "endpoint": "/api/data",
  "httpMethod": "GET"
}

// 200 OK (allowed)
{
  "allowed": true,
  "identifier": "user_123",
  "remaining": 99,
  "limit": 100,
  "resetInSeconds": 1,
  "message": "Request allowed"
}

// 429 Too Many Requests
{
  "allowed": false,
  "remaining": 0,
  "limit": 100,
  "resetInSeconds": 1,
  "message": "Rate limit exceeded. Retry after 1 seconds."
}
```

### HTTP Filter Usage (Middleware Mode)
Any request to `/api/demo/**` (or any non-excluded path) is **automatically rate limited**:
```bash
# Rate limited by IP (default)
curl http://localhost:8080/api/demo/hello

# Rate limited by User ID
curl -H "X-User-ID: user_123" http://localhost:8080/api/demo/hello

# Rate limited by API Key
curl -H "X-API-Key: my-key-abc" http://localhost:8080/api/demo/hello
```

---

## 🧪 Testing

### Run All Tests
```bash
mvn test
```

### Test Summary
| Test Class | Tests | Coverage |
|------------|-------|----------|
| TokenBucketTest | 10 | Algorithm unit tests |
| RateLimitControllerIntegrationTest | 8 | API end-to-end |
| TierManagementTest | 5 | Phase 2 tier features |
| **Total** | **23** | **>= 5 required by spec** |

### Test Scenarios Covered
- ✅ Bucket starts full
- ✅ First 100 requests succeed
- ✅ 101st request returns 429
- ✅ Reset clears limits
- ✅ Different identifiers tracked separately
- ✅ Headers present in all responses
- ✅ Token refill after waiting
- ✅ Thread safety (concurrent requests)
- ✅ FREE tier defaults
- ✅ Upgrade FREE → PRO increases limits
- ✅ ENTERPRISE tier 10,000 capacity

### Postman Collection
Import `postman_collection.json` into Postman.
22 pre-built requests with automated test scripts.

---

## ⚙️ Configuration

Edit `src/main/resources/application.properties`:

```properties
# Change default limits
ratelimiter.tier.free.capacity=100
ratelimiter.tier.free.refill-rate=10
ratelimiter.tier.pro.capacity=1000
ratelimiter.tier.pro.refill-rate=100
ratelimiter.tier.enterprise.capacity=10000
ratelimiter.tier.enterprise.refill-rate=1000

# Cleanup interval (ms)
ratelimiter.cleanup.interval-ms=60000
```

---

## 🔮 Phase 3 Extensions (Future Work)
- **High Performance**: AtomicLong lock-free operations, Caffeine cache, k6 load testing
- **Circuit Breaker**: Auto-block abusive users with escalating durations
- **Whitelist/Blacklist**: Bypass or permanently block identifiers
- **Cost-Based Limiting**: Assign "points" per endpoint (AI calls = 100pts, reads = 1pt)
