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
##
XXXV. An Electric Storm
Friday, August 21.⁠—On the morrow the magnificent geyser has disappeared. The wind has 
risen, and has rapidly carried us away from Axel Island. The roarings become lost in the 
distance.
The weather⁠—if we may use that term⁠—will change before long. The atmosphere is charged 
with vapours, pervaded with the electricity generated by the evaporation of saline waters. The 
clouds are sinking lower, and assume an olive hue. The electric light can scarcely penetrate 
through the dense curtain which has dropped over the theatre on which the battle of the 
elements is about to be waged. 
I feel peculiar sensations, like many creatures on earth at the approach of violent atmospheric 
changes. The heavily voluted cumulus clouds lower gloomily and threateningly; they wear 
that implacable look which I have sometimes noticed at the outbreak of a great storm. The air 
is heavy; the sea is calm.
In the distance the clouds resemble great bales of cotton, piled up in picturesque disorder. By 
degrees they dilate, and gain in huge size what they lose in number. Such is their ponderous 
weight that they cannot rise from the horizon; but, obeying an impulse from higher currents, 
their dense consistency slowly yields. The gloom upon them deepens; and they soon present 
to our view a ponderous mass of almost level surface. From time to time a fleecy tuft of mist, 
with yet some gleaming light left upon it, drops down upon the dense floor of grey, and loses 
itself in the opaque and impenetrable mass. 
The atmosphere is evidently charged and surcharged with electricity. My whole body is 
saturated; my hair bristles just as when you stand upon an insulated stool under the action of 
an electrical machine. It seems to me as if my companions, the moment they touched me, 
would receive a severe shock like that from an electric eel.
At ten in the morning the symptoms of storm become aggravated. The wind never lulls but to 
acquire increased strength; the vast bank of heavy clouds is a huge reservoir of fearful windy 
gusts and rushing storms. 
I am loth to believe these atmospheric menaces, and yet I cannot help muttering: 
“Here’s some very bad weather coming on.” 
The Professor made no answer. His temper is awful, to judge from the working of his 
features, as he sees this vast length of ocean unrolling before him to an indefinite extent. He 
can only spare time to shrug his shoulders viciously. 
“There’s a heavy storm coming on,” I cried, pointing towards the horizon. “Those clouds 
seem as if they were going to crush the sea.”
A deep silence falls on all around. The lately roaring winds are hushed into a dead calm; 
nature seems to breathe no more, and to be sinking into the stillness of death. On the mast 
already I see the light play of a lambent St. Elmo’s fire; the outstretched sail catches not a 
breath of wind, and hangs like a sheet of lead. The rudder stands motionless in a sluggish, 
waveless sea. But if we have now ceased to advance why do we yet leave that sail loose, 
which at the first shock of the tempest may capsize us in a moment?
“Let us reef the sail and cut the mast down!” I cried. “That will be safest.”
118 “No, no! Never!” shouted my impetuous uncle. “Never! Let the wind catch us if it will! What 
I want is to get the least glimpse of rock or shore, even if our raft should be smashed into 
shivers!”
The words were hardly out of his mouth when a sudden change took place in the southern 
sky. The piled-up vapours condense into water; and the air, put into violent action to supply 
the vacuum left by the condensation of the mists, rouses itself into a whirlwind. It rushes on 
from the farthest recesses of the vast cavern. The darkness deepens; scarcely can I jot down a 
few hurried notes. The helm makes a bound. My uncle falls full length; I creep close to him. 
He has laid a firm hold upon a rope, and appears to watch with grim satisfaction this awful 
display of elemental strife.
Hans stirs not. His long hair blown by the pelting storm, and laid flat across his immovable 
countenance, makes him a strange figure; for the end of each lock of loose flowing hair is 
tipped with little luminous radiations. This frightful mask of electric sparks suggests to me, 
even in this dizzy excitement, a comparison with pre-adamite man, the contemporary of the 
ichthyosaurus and the Megatherium.12F
13 
The mast yet holds firm. The sail stretches tight like a bubble ready to burst. The raft flies at a 
rate that I cannot reckon, but not so fast as the foaming clouds of spray which it dashes from 
side to side in its headlong speed. 
“The sail! the sail!” I cry, motioning to lower it.
“No!” replies my uncle.
“Nej!” repeats Hans, leisurely shaking his head. 
But now the rain forms a rushing cataract in front of that horizon toward which we are 
running with such maddening speed. But before it has reached us the rain cloud parts 
asunder, the sea boils, and the electric fires are brought into violent action by a mighty 
chemical power that descends from the higher regions. The most vivid flashes of lightning 
are mingled with the violent crash of continuous thunder. Ceaseless fiery arrows dart in and 
out amongst the flying thunderclouds; the vaporous mass soon glows with incandescent heat; 
hailstones rattle fiercely down, and as they dash upon our iron tools they too emit gleams and 
flashes of lurid light. The heaving waves resemble fiery volcanic hills, each belching forth its 
own interior flames, and every crest is plumed with dancing fire. My eyes fail under the 
dazzling light, my ears are stunned with the incessant crash of thunder. I must be bound to the 
mast, which bows like a reed before the mighty strength of the storm. 
(Here my notes become vague and indistinct. I have only been able to find a few which I 
seem to have jotted down almost unconsciously. But their very brevity and their obscurity 
reveal the intensity of the excitement which dominated me, and describe the actual position 
even better than my memory could do.) 
Sunday, 23.—Where are we? Driven forward with a swiftness that cannot be measured.
The night was fearful; no abatement of the storm. The din and uproar are incessant; our ears 
are bleeding; to exchange a word is impossible. 
The lightning flashes with intense brilliancy, and never seems to cease for a moment. Zigzag 
streams of bluish white fire dash down upon the sea and rebound, and then take an upward 
flight till they strike the granite vault that overarches our heads. Suppose that solid roof 
should crumble down upon our heads! Other flashes with incessant play cross their vivid 
13 Rather of the mammoth and the mastodon. —⁠Tr.
119 fires, while others again roll themselves into balls of living fire which explode like 
bombshells, but the music of which scarcely adds to the din of the battle strife that almost 
deprives us of our senses of hearing and sight; the limit of intense loudness has been passed 
within which the human ear can distinguish one sound from another. If all the powder 
magazines in the world were to explode at once, we should hear no more than we do now. 
From the under surface of the clouds there are continual emissions of lurid light; electric 
matter is in continual evolution from their component molecules; the gaseous elements of the 
air need to be slaked with moisture; for innumerable columns of water rush upwards into the 
air and fall back again in white foam.
Whither are we flying? My uncle lies full length across the raft.
The heat increases. I refer to the thermometer; it indicates⁠ ⁠… (the figure is obliterated).
Monday, August 24.—Will there be an end to it? Is the atmospheric condition, having once 
reached this density, to become final? 
We are prostrated and worn out with fatigue. But Hans is as usual. The raft bears on still to 
the southeast. We have made two hundred leagues since we left Axel Island. 
At noon the violence of the storm redoubles. We are obliged to secure as fast as possible 
every article that belongs to our cargo. Each of us is lashed to some part of the raft. The 
waves rise above our heads. 
For three days we have never been able to make each other hear a word. Our mouths open, 
our lips move, but not a word can be heard. We cannot even make ourselves heard by 
approaching our mouth close to the ear. 
My uncle has drawn nearer to me. He has uttered a few words. They seem to be “We are 
lost;” but I am not sure. 
At last I write down the words: “Let us lower the sail.”
He nods his consent. 
Scarcely has he lifted his head again before a ball of fire has bounded over the waves and 
lighted on board our raft. Mast and sail flew up in an instant together, and I saw them carried 
up to prodigious height, resembling in appearance a pterodactyle, one of those strong birds of 
the infant world. 
We lay there, our blood running cold with unspeakable terror. The fireball, half of it white, 
half azure blue, and the size of a ten-inch shell, moved slowly about the raft, but revolving on 
its own axis with astonishing velocity, as if whipped round by the force of the whirlwind. 
Here it comes, there it glides, now it is up the ragged stump of the mast, thence it lightly leaps 
on the provision bag, descends with a light bound, and just skims the powder magazine. 
Horrible! we shall be blown up; but no, the dazzling disk of mysterious light nimbly leaps 
aside; it approaches Hans, who fixes his blue eye upon it steadily; it threatens the head of my 
uncle, who falls upon his knees with his head down to avoid it. And now my turn comes; pale 
and trembling under the blinding splendour and the melting heat, it drops at my feet, spinning 
silently round upon the deck; I try to move my foot away, but cannot. 
A suffocating smell of nitrogen fills the air, it enters the throat, it fills the lungs. We suffer 
stifling pains.
Why am I unable to move my foot? Is it riveted to the planks? Alas! the fall upon our fated 
raft of this electric globe has magnetised every iron article on board. The instruments, the 
tools, our guns, are clashing and clanking violently in their collisions with each other; the 
120 nails of my boots cling tenaciously to a plate of iron let into the timbers, and I cannot draw 
my foot away from the spot. At last by a violent effort I release myself at the instant when the 
ball in its gyrations was about to seize upon it, and carry me off my feet⁠ ⁠…
Ah! what a flood of intense and dazzling light! the globe has burst, and we are deluged with 
tongues of fire! 
Then all the light disappears. I could just see my uncle at full length on the raft, and Hans still 
at his helm and spitting fire under the action of the electricity which has saturated him.
But where are we going to? Where?
Tuesday, August 25.—I recover from a long swoon. The storm continues to roar and rage; the 
lightnings dash hither and thither, like broods of fiery serpents filling all the air. Are we still 
under the sea? Yes, we are borne at incalculable speed. We have been carried under England, 
under the channel, under France, perhaps under the whole of Europe. 
***** 
A fresh noise is heard! Surely it is the sea breaking upon the rocks! But then⁠ ⁠…
##
121 XXXVI. Calm Philosophic Discussions
Here I end what I may call my log, happily saved from the wreck, and I resume my narrative 
as before.
What happened when the raft was dashed upon the rocks is more than I can tell. I felt myself 
hurled into the waves; and if I escaped from death, and if my body was not torn over the 
sharp edges of the rocks, it was because the powerful arm of Hans came to my rescue.
The brave Icelander carried me out of the reach of the waves, over a burning sand where I 
found myself by the side of my uncle. 
Then he returned to the rocks, against which the furious waves were beating, to save what he 
could. I was unable to speak. I was shattered with fatigue and excitement; I wanted a whole 
hour to recover even a little.
But a deluge of rain was still falling, though with that violence which generally denotes the 
near cessation of a storm. A few overhanging rocks afforded us some shelter from the storm. 
Hans prepared some food, which I could not touch; and each of us, exhausted with three 
sleepless nights, fell into a broken and painful sleep. 
The next day the weather was splendid. The sky and the sea had sunk into sudden repose. 
Every trace of the awful storm had disappeared. The exhilarating voice of the Professor fell 
upon my ears as I awoke; he was ominously cheerful. 
“Well, my boy,” he cried, “have you slept well?” 
Would not anyone have thought that we were still in our cheerful little house on the 
Königstrasse and that I was only just coming down to breakfast, and that I was to be married 
to Gräuben that day? 
Alas! if the tempest had but sent the raft a little more east, we should have passed under 
Germany, under my beloved town of Hamburg, under the very street where dwelt all that I 
loved most in the world. Then only forty leagues would have separated us! But they were 
forty leagues perpendicular of solid granite wall, and in reality we were a thousand leagues 
asunder! 
All these painful reflections rapidly crossed my mind before I could answer my uncle’s 
question. 
“Well, now,” he repeated, “won’t you tell me how you have slept?”
“Oh, very well,” I said. “I am only a little knocked up, but I shall soon be better.” 
“Oh,” says my uncle, “that’s nothing to signify. You are only a little bit tired.” 
“But you, uncle, you seem in very good spirits this morning.” 
“Delighted, my boy, delighted. We have got there.” 
“To our journey’s end?” 
“No; but we have got to the end of that endless sea. Now we shall go by land, and really 
begin to go down! down! down!” 
“But, my dear uncle, do let me ask you one question.” 
“Of course, Axel.”
122 “How about returning?” 
“Returning? Why, you are talking about the return before the arrival.” 
“No, I only want to know how that is to be managed.” 
“In the simplest way possible. When we have reached the centre of the globe, either we shall 
find some new way to get back, or we shall come back like decent folks the way we came. I 
feel pleased at the thought that it is sure not to be shut against us.” 
“But then we shall have to refit the raft.” 
“Of course.”
“Then, as to provisions, have we enough to last?” 
“Yes; to be sure we have. Hans is a clever fellow, and I am sure he must have saved a large 
part of our cargo. But still let us go and make sure.” 
We left this grotto which lay open to every wind. At the same time I cherished a trembling 
hope which was a fear as well. It seemed to me impossible that the terrible wreck of the raft 
should not have destroyed everything on board. On my arrival on the shore I found Hans 
surrounded by an assemblage of articles all arranged in good order. My uncle shook hands 
with him with a lively gratitude. This man, with almost superhuman devotion, had been at 
work all the while that we were asleep, and had saved the most precious of the articles at the 
risk of his life.
Not that we had suffered no losses. For instance, our firearms; but we might do without them. 
Our stock of powder had remained uninjured after having risked blowing up during the 
storm.
“Well,” cried the Professor, “as we have no guns we cannot hunt, that’s all.” 
“Yes, but how about the instruments?”
“Here is the aneroid, the most useful of all, and for which I would have given all the others. 
By means of it I can calculate the depth and know when we have reached the centre; without 
it we might very likely go beyond, and come out at the antipodes!” 
Such high spirits as these were rather too strong. 
“But where is the compass?” I asked.
“Here it is, upon this rock, in perfect condition, as well as the thermometers and the 
chronometer. The hunter is a splendid fellow.” 
There was no denying it. We had all our instruments. As for tools and appliances, there they 
all lay on the ground⁠—ladders, ropes, picks, spades, etc.
Still there was the question of provisions to be settled, and I asked⁠—“How are we off for 
provisions?” 
The boxes containing these were in a line upon the shore, in a perfect state of preservation; 
for the most part the sea had spared them, and what with biscuits, salt meat, spirits, and salt 
fish, we might reckon on four months’ supply. 
“Four months!” cried the Professor. “We have time to go and to return; and with what is left I 
will give a grand dinner to my friends at the Johannæum.” 
I ought by this time to have been quite accustomed to my uncle’s ways; yet there was always 
something fresh about him to astonish me. 
123 “Now,” said he, “we will replenish our supply of water with the rain which the storm has left 
in all these granite basins; therefore we shall have no reason to fear anything from thirst. As 
for the raft, I will recommend Hans to do his best to repair it, although I don’t expect it will 
be of any further use to us.” 
“How so?” I cried.
“An idea of my own, my lad. I don’t think we shall come out by the way that we went in.” 
I stared at the Professor with a good deal of mistrust. I asked, was he not touched in the 
brain? And yet there was method in his madness. 
“And now let us go to breakfast,” said he. 
I followed him to a headland, after he had given his instructions to the hunter. There 
preserved meat, biscuit, and tea made us an excellent meal, one of the best I ever remember. 
Hunger, the fresh air, the calm quiet weather, after the commotions we had gone through, all 
contributed to give me a good appetite. 
Whilst breakfasting I took the opportunity to put to my uncle the question where we were 
now. 
“That seems to me,” I said, “rather difficult to make out.” 
“Yes, it is difficult,” he said, “to calculate exactly; perhaps even impossible, since during 
these three stormy days I have been unable to keep any account of the rate or direction of the 
raft; but still we may get an approximation.”
“The last observation,” I remarked, “was made on the island, when the geyser was⁠—” 
“You mean Axel Island. Don’t decline the honour of having given your name to the first 
island ever discovered in the central parts of the globe.” 
“Well,” said I, “let it be Axel Island. Then we had cleared two hundred and seventy leagues 
of sea, and we were six hundred leagues from Iceland.” 
“Very well,” answered my uncle; “let us start from that point and count four days’ storm, 
during which our rate cannot have been less than eighty leagues in the twenty-four hours.” 
“That is right; and this would make three hundred leagues more.” 
“Yes, and the Liedenbrock sea would be six hundred leagues from shore to shore. Surely, 
Axel, it may vie in size with the Mediterranean itself.”
“Especially,” I replied, “if it happens that we have only crossed it in its narrowest part. And it 
is a curious circumstance,” I added, “that if my computations are right, and we are nine 
hundred leagues from Reykjavík, we have now the Mediterranean above our head.” 
“That is a good long way, my friend. But whether we are under Turkey or the Atlantic 
depends very much upon the question in what direction we have been moving. Perhaps we 
have deviated.” 
“No, I think not. Our course has been the same all along, and I believe this shore is southeast 
of Port Gräuben.” 
“Well,” replied my uncle, “we may easily ascertain this by consulting the compass. Let us go 
and see what it says.”
The Professor moved towards the rock upon which Hans had laid down the instruments. He 
was gay and full of spirits; he rubbed his hands, he studied his attitudes. I followed him, 
curious to know if I was right in my estimate. As soon as we had arrived a