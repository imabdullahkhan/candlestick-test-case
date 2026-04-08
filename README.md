# Candlestick Backend Service

Backend Java service (Spring Boot 4) that ingests a continuous stream of simulated bid/ask market data, aggregates it into candlestick (OHLC) chart format across multiple symbols and timeframes, and exposes a TradingView-compatible history REST API.

## How It Works

The application follows a **stream -> aggregate -> store -> serve** pipeline:

```
 BidAskEvent stream              BucketStore (Redis)         PostgreSQL
┌─────────────────┐    ┌──────────────────────────────┐    ┌──────────────────┐
│ MarketData      │───▶│ CandleIngestionService       │───▶│ candles table    │
│ Simulator       │    │                              │    │ (upsert on       │
│ (configurable)  │    │  Redis HASH per active candle │    │  conflict)       │
└─────────────────┘    │  bucket:BTC-USD:1s            │    └──────────────────┘
                       │    O:64000  H:64010           │             ▲
                       │    L:63990  C:64005  V:42     │             │
                       │                              │        on bucket
                       │  Lua script for atomic OHLC   │        close or
                       │  updates across pods          │        shutdown
                       └──────────────────────────────┘
                                                           ┌──────────────────┐
                                                           │ Flyway migration │
                                                           │ creates schema   │
                                                           └──────────────────┘
```

**1. Event Ingestion:** `MarketDataSimulator` generates `BidAskEvent(symbol, bid, ask, timestamp)` records at a configurable rate and pushes them into `CandleIngestionService.onEvent()`.

**2. Bucket Alignment:** Each event's timestamp is aligned to a bucket boundary using `CandleInterval.bucketStart()`. For example, with a `1m` interval, timestamp `1620000035` aligns to bucket `1620000000`. This is how events are grouped into candles.

**3. Active Candle Aggregation via BucketStore:** The active (currently open) candle for each `(symbol, interval)` pair is managed by a `BucketStore` implementation. When a new event arrives:
- **Same bucket** -- update high/low/close/volume atomically (Lua script in Redis, or ConcurrentHashMap in-memory).
- **New bucket** -- finalize the current candle, persist it, and start a new one.
- **Late event** (timestamp falls in an already-closed bucket) -- update the historical candle.

Two implementations are provided via the `BucketStore` interface:
- **`RedisBucketStore`** (default) -- active candles live in Redis HASHes, surviving pod crashes and enabling multi-pod scaling.
- **`InMemoryBucketStore`** (fallback) -- used when Redis is unavailable, or in unit tests.

**4. Deferred DB Persistence:** When a candle is finalized, it is written to PostgreSQL using an `INSERT ... ON CONFLICT DO UPDATE` (upsert). DB writes are wrapped in try-catch so a failure never crashes the aggregation loop.

**5. API Read Path with Redis Cache:**

```
Client request                Redis                    PostgreSQL
     │                          │                          │
     ▼                          ▼                          ▼
GET /history ──▶ Check Redis ──▶ HIT? ──yes──▶ Return cached JSON
     ?symbol=BTC-USD             │
     &interval=1s                no
     &from=0                     │
     &to=999999          Query PostgreSQL ◀─────────────────┘
                                 │
                         Merge with active
                         candle from BucketStore
                                 │
                         Store result in Redis
                         (TTL = 3 seconds)
                                 │
                         Return HistoryResponse
                         {"s":"ok","t":[...],...}
```

When a client calls `/history`, `CandleQueryService` first checks Redis for a cached response. On a cache miss, it queries PostgreSQL for finalized candles, merges in the current active candle (via `CandleIngestionService`), sorts by time, caches the result in Redis with a 3-second TTL, and returns the TradingView-compatible JSON response. If Redis or PostgreSQL is unavailable, the service degrades gracefully without failing.

## Implemented Features

### Stream Ingestion
- Simulated generator emits `BidAskEvent(symbol, bid, ask, timestamp)` at a configurable rate.
- Symbols and tick rate are configurable via `application.properties`.
- Decoupled from aggregation logic -- can be replaced with Kafka, WebSocket, or any real data source.

### Candlestick Aggregation
- Supported intervals: `1s`, `5s`, `1m`, `15m`, `1h`.
- Aggregates by `(symbol, interval, bucket_start)` using `BucketStore` interface (Redis or in-memory).
- Candle structure: `Candle(time, open, high, low, close, volume)`.
- Price basis: mid-price `(bid + ask) / 2`.
- Volume: number of ticks in the bucket.
- Late events for already-closed buckets are handled by updating historical candles.
- Active candles survive pod crashes when using Redis bucket store.

### Data Storage
- Finalized candles are persisted to PostgreSQL using `ON CONFLICT ... DO UPDATE` (upsert).
- Flyway migration applied automatically at startup (`db/migration/V1__create_candles_table.sql`).
- HikariCP connection pooling (auto-configured by Spring Boot).
- All DB operations wrapped in try-catch with structured logging.

### History Cache
- `/history` responses are cached in Redis with a 3-second TTL.
- Cache failures (Redis down, serialization errors) are logged but never break the primary API path.

### History API
- **Endpoint:** `GET /history?symbol=BTC-USD&interval=1m&from=1620000000&to=1620000600`
- **Success response:**
  ```json
  {
    "s": "ok",
    "t": [1620000000, 1620000060],
    "o": [29500.5, 29501.0],
    "h": [29510.0, 29505.0],
    "l": [29490.0, 29500.0],
    "c": [29505.0, 29502.0],
    "v": [10, 8]
  }
  ```
- **No data response:** `{"s": "no_data", "t": [], "o": [], ...}`
- **Error response:** `{"s": "error", "errmsg": "Unsupported interval: 2s"}`

### Error Handling
- `@RestControllerAdvice` returns structured TradingView-compatible error responses for:
  - Invalid interval (`400`), invalid range (`400`), missing parameters (`400`), type mismatches (`400`).
  - Unexpected server errors (`500`) -- logged with full stack trace, generic message returned to client.
- DB and Redis failures in the query path are caught and logged, never surfaced as raw exceptions.

### Observability
- **Health check:** `GET /actuator/health` -- reports PostgreSQL, Redis, disk space, liveness, and readiness status.
- **Metrics:** `GET /actuator/metrics` -- Micrometer metrics exposed.
- **Logging:** Structured logging across all layers (controller, aggregation, repository, cache) at appropriate levels (trace/debug/info/warn/error).

### Reliability
- Thread-safe aggregation using `BucketStore` implementations (Redis Lua script or `ConcurrentHashMap`).
- Active candles stored in Redis survive pod crashes and support multi-pod deployments.
- Graceful shutdown: stops the simulator, flushes all active candles to the database, then exits.
- `server.shutdown=graceful` with a 10-second drain timeout.

## Tech Stack

- Java 17
- Spring Boot 4.0.5
- Maven Wrapper (`mvnw`)
- PostgreSQL 16 (persistent candle storage)
- Redis 7 (active candle buckets + history cache)
- Flyway (database migrations)
- HikariCP (connection pooling)
- Spring Boot Actuator (health, info, metrics)
- JUnit 5 (51 unit tests)
- Docker Compose (PostgreSQL + Redis)

## Project Structure

```
src/main/java/com/example/hellospringapi/
  HelloSpringApiApplication.java          # Application entry point
  config/
    FlywayConfig.java                     # Flyway migration setup
    JacksonConfig.java                    # ObjectMapper bean
  market/
    model/                                # Pure data types
      BidAskEvent.java                    # Incoming event record
      Candle.java                         # OHLCV candle record
      CandleInterval.java                 # Interval enum with bucket alignment
      HistoryResponse.java                # API success response record
      ErrorResponse.java                  # API error response record
    aggregation/                          # Write + Read orchestration
      CandleIngestionService.java         # Write path: event -> aggregate -> DB
      CandleQueryService.java             # Read path: cache -> DB -> merge active
      bucket/                             # Active candle storage strategy
        BucketStore.java                  # Interface (Dependency Inversion)
        BucketStoreConfig.java            # Spring wiring (Redis preferred, in-memory fallback)
        InMemoryBucketStore.java          # Single-JVM implementation
        RedisBucketStore.java             # Multi-pod Redis implementation (Lua script)
    repository/                           # Data access layer
      CandleStorageRepository.java        # PostgreSQL persistence (JDBC)
    cache/                                # Cache layer
      HistoryCacheService.java            # Redis response cache
    controller/                           # REST layer
      HistoryController.java              # /history endpoint
      GlobalExceptionHandler.java         # @ControllerAdvice for structured errors
      BadRequestException.java            # Validation exception
    simulator/                            # Data generation
      MarketDataSimulator.java            # Simulated bid/ask event generator
      MarketDataProperties.java           # Externalized configuration
```

## Run Locally

### 1) Start dependencies

```bash
docker compose up -d
```

### 2) Start the application

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

### 3) Test endpoints

```bash
curl "http://localhost:8080/history?symbol=BTC-USD&interval=1s&from=0&to=9999999999"
curl "http://localhost:8080/actuator/health"
```

## Run Tests

```bash
./mvnw test
```

51 unit tests covering:
- `CandleAggregatorServiceTest` (12 tests) -- OHLC aggregation, bucket closing, late events, multi-symbol, multi-interval, range queries.
- `CandleIntervalTest` (19 tests) -- `fromValue`, `bucketStart` alignment for all intervals, edge cases.
- `HistoryControllerTest` (9 tests) -- valid/invalid requests, error response structure, no-data handling.
- `MarketDataPropertiesTest` (10 tests) -- default values and setter overrides.
- `HelloSpringApiApplicationTests` (1 test) -- application class existence.

## Docker Setup (PostgreSQL + Redis)

`docker-compose.yml` is included to spin up PostgreSQL and Redis locally.

```bash
docker compose up -d    # start
docker compose down     # stop
```

### Service details

| Service    | Host        | Port   | Database      | User          |
|------------|-------------|--------|---------------|---------------|
| PostgreSQL | `localhost` | `5433` | `candlestick` | `candlestick` |
| Redis      | `localhost` | `6380` | --            | --            |

## Assumptions and Trade-offs

- **Mid-price over last-traded:** Uses `(bid + ask) / 2` as the candle price since the simulator produces bid/ask pairs, not trade executions.
- **Tick-count volume:** Volume represents the number of events per bucket, not trade size, since events are synthetic.
- **3-second Redis TTL:** Short enough to keep data fresh for active charts, long enough to reduce DB load under repeated queries.
- **Redis active candles:** Active candles are stored in Redis HASHes (not JVM memory), so they survive pod crashes and support horizontal scaling across multiple instances.
- **Deferred persistence:** DB writes happen after bucket aggregation completes, wrapped in try-catch so failures never crash the ingestion loop.
- **BucketStore interface:** Dependency Inversion allows swapping between Redis and in-memory implementations without changing business logic. Unit tests use `InMemoryBucketStore` with no infrastructure required.
- **PostgreSQL over TimescaleDB:** Chose standard PostgreSQL for simplicity. TimescaleDB hypertables would improve query performance at scale and could be added without changing the application code.
- **No authentication:** The API is unauthenticated, suitable for internal/demo use.

## Extensibility

- **New intervals:** Add entries to the `CandleInterval` enum -- no other code changes needed.
- **New symbols:** Add to `market.symbols` in `application.properties` -- picked up at startup.
- **Real data sources:** Replace `MarketDataSimulator` with a Kafka consumer or WebSocket client that publishes `BidAskEvent` into `CandleIngestionService.onEvent()`.
- **Multi-pod scaling:** Already supported via `RedisBucketStore` -- all pods share the same active candle state in Redis.
- **TimescaleDB:** Swap to TimescaleDB hypertables for better time-series query performance -- the `CandleStorageRepository` SQL is already compatible.
