# Trading Desk

A concurrent stock-trading backend built with Spring Boot 3. It manages order placement, filling, and cancellation with portfolio tracking, enforcing business rules safely under parallel load.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Running the Application](#running-the-application)
- [Running the Tests](#running-the-tests)
- [API Reference](#api-reference)
- [Architecture](#architecture)
- [Concurrency Design](#concurrency-design)
- [Design Decisions](#design-decisions)
- [Trade-offs](#trade-offs)

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.8+ |
| Oracle Database | XE 21c (or any Oracle 12c+ instance) |

---

## Running the Application

### 1. Create the Oracle schema

Connect as a DBA and create the application user:

```sql
CREATE USER trading_desk IDENTIFIED BY trading_desk;
GRANT CONNECT, RESOURCE TO trading_desk;
GRANT CREATE SESSION TO trading_desk;
```

Then create the tables under that user:

```sql
CREATE TABLE traders (
    id   VARCHAR2(50)  PRIMARY KEY,
    name VARCHAR2(255) NOT NULL
);

CREATE TABLE orders (
    id         RAW(16)      DEFAULT SYS_GUID() PRIMARY KEY,
    trader_id  VARCHAR2(50) NOT NULL REFERENCES traders(id),
    stock      VARCHAR2(20) NOT NULL,
    sector     VARCHAR2(50) NOT NULL,
    quantity   NUMBER(10)   NOT NULL,
    side       VARCHAR2(10) NOT NULL,
    status     VARCHAR2(20) NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    version    NUMBER(19)   DEFAULT 0,
    CONSTRAINT orders_side_chk   CHECK (side   IN ('BUY','SELL')),
    CONSTRAINT orders_status_chk CHECK (status IN ('PENDING','FILLED','CANCELLED'))
);

CREATE TABLE portfolio (
    id        RAW(16)      DEFAULT SYS_GUID() PRIMARY KEY,
    trader_id VARCHAR2(50) NOT NULL REFERENCES traders(id),
    stock     VARCHAR2(20) NOT NULL,
    sector    VARCHAR2(50) NOT NULL,
    quantity  NUMBER(10)   NOT NULL,
    CONSTRAINT portfolio_uq UNIQUE (trader_id, stock)
);
```

### 2. Configure the connection

Edit `src/main/resources/application.properties` to match your Oracle instance:

```properties
spring.datasource.url=jdbc:oracle:thin:@localhost:1521/XEPDB1
spring.datasource.username=trading_desk
spring.datasource.password=trading_desk
```

The service name (`XEPDB1`) can be found in SQL Developer under your connection's **Service name** field. Common alternatives are `XE` (older installs) or a custom PDB name.

### 3. Seed traders

The application runs `data.sql` on startup (controlled by `spring.sql.init.mode=always`), which inserts three traders using Oracle `MERGE` — safe to re-run:

| ID | Name |
|----|------|
| T001 | Alice |
| T002 | Bob |
| T003 | Charlie |

### 4. Build and run

```bash
mvn clean package -DskipTests
java -jar target/trading-desk-1.0.0.jar
```

Or directly via Maven:

```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.

---

## Running the Tests

Tests run against an **H2 in-memory database** — no Oracle instance required.

```bash
mvn test
```

| Test class | Type | What it covers |
|---|---|---|
| `OrderServiceTest` | Unit (Mockito) | All order placement, fill, and cancel business rules |
| `SectorAnalysisServiceTest` | Unit | Overlap formula, risk-flag boundaries, the spec's worked example |
| `OrderConcurrencyTest` | Integration (H2) | Pending-order cap under 6 parallel threads; concurrent fill of the same order |

---

## API Reference

### Orders

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/orders` | Place a new order |
| `POST` | `/api/orders/{orderId}/fill` | Fill a pending order |
| `POST` | `/api/orders/{orderId}/cancel` | Cancel a pending order |

**Place order — request body:**
```json
{
  "traderId": "T001",
  "stock": "AAPL",
  "sector": "TECH",
  "quantity": 10,
  "side": "BUY"
}
```

Business rules enforced at placement:
- Max **3 PENDING** orders per trader → `409 Conflict`
- `SELL` rejected if holdings < quantity → `400 Bad Request`

### Portfolio

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/traders/{traderId}/portfolio` | Get current holdings |
| `GET`  | `/api/traders/{traderId}/portfolio/analysis` | Sector overlap analysis |
| `POST` | `/api/traders/{traderId}/portfolio` | Directly add holdings |

**Portfolio response:**
```json
{
  "traderId": "T001",
  "positions": { "AAPL": 150, "TSLA": 80 },
  "sectorBreakdown": { "TECH": 230 }
}
```

**Sector overlap analysis response:**
```json
{
  "overlaps": [
    { "basket": "BALANCED",      "overlap": "50.00%" },
    { "basket": "FINANCE_HEAVY", "overlap": "0.00%"  },
    { "basket": "TECH_HEAVY",    "overlap": "75.00%" }
  ],
  "dominantBasket": "TECH_HEAVY",
  "riskFlag": "HIGH"
}
```

Risk thresholds: `≥ 60% → HIGH`, `≥ 40% → MEDIUM`, `< 40% → LOW`

### Error envelope

All errors return a consistent JSON shape:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Trader T001 already has 3 pending orders.",
  "timestamp": "2026-04-02T14:30:00"
}
```

---

## Architecture

```
controller/           HTTP layer — input validation, delegates to services
service/
  OrderService        Order lifecycle: place / fill / cancel
  PortfolioService    Holdings management: increment / decrement / query
  SectorAnalysisService   Pure Java overlap calculation (no DB calls)
repository/           Spring Data JPA with custom JPQL + locking queries
model/
  entity/             JPA entities: Trader, Order, Portfolio
  dto/request/        Inbound payloads with Bean Validation annotations
  dto/response/       Outbound shapes: OrderResponse, PortfolioResponse, etc.
exception/            TradingException + GlobalExceptionHandler
```

**Request flow:**

```
HTTP Request
  → Controller  (@Valid binding)
    → Service   (@Transactional, business rules, row-level locking)
      → Repository  (JPQL queries, PESSIMISTIC_WRITE hints)
        → Oracle DB
      ← entity
    ← domain response
  ← JSON
```

---

## Concurrency Design

Two correctness invariants must hold under parallel load:

1. A trader may not exceed **3 pending orders** at any time.
2. An order can only be **filled or cancelled once**.

### Pessimistic row-level locking

| Operation | Lock target | Why |
|---|---|---|
| `placeOrder` | Trader row | Serialises the count-check + INSERT for the same trader. Without this, two threads can both read `count = 2`, both pass the guard, and both insert — breaching the cap. |
| `fillOrder` / `cancelOrder` | Order row | Serialises concurrent state transitions. The first thread transitions `PENDING → FILLED/CANCELLED`; the second sees the already-changed status and receives `409 Conflict`. |
| `incrementHoldings` / `decrementHoldings` | Portfolio row | Prevents lost updates when two fills for the same stock complete simultaneously. |

All locks are acquired via Spring Data JPA's `@Lock(LockModeType.PESSIMISTIC_WRITE)`, which translates to `SELECT ... FOR UPDATE` on Oracle.

### Optimistic versioning as a second line of defence

The `Order` entity has a `@Version` column. Hibernate increments it on every `UPDATE`. If two transactions somehow both pass pessimistic locking (e.g. due to a lock timeout being configured to zero), the second writer's `UPDATE ... WHERE version = N` will match zero rows and Hibernate will throw `OptimisticLockException` instead of silently overwriting.

### Transaction isolation

`READ_COMMITTED` (isolation level 2) is set explicitly in `application.properties`. This prevents dirty reads while avoiding the serialisation overhead of `REPEATABLE_READ` or `SERIALIZABLE`, which would significantly reduce throughput for independent traders.

---

## Design Decisions

### No Lombok

The codebase uses plain Java constructors, getters, and setters throughout. Key reasons:

- **Naming conflict with Spring 6.** Spring Framework 6 introduced `org.springframework.web.ErrorResponse` as an interface. Having a custom class of the same name caused `builder()` resolution failures at compile time. Renaming to `ApiErrorResponse` and removing Lombok resolved this cleanly.
- **No annotation processor dependency.** The code compiles and is navigable without IDE plugin configuration.
- **Explicit is clear.** Constructor signatures document which fields are required at construction; optional fields are set via setters. There is no ambiguity about which fields a builder call covers.

### `TradingException` carries `HttpStatus`

Rather than mapping exception types to HTTP status codes in the handler, the exception holds the intended status directly. This keeps `GlobalExceptionHandler` to a fixed three handlers regardless of how many domain error conditions are added.

### `SectorAnalysisService` is stateless and DB-free

All basket definitions are compile-time constants. The overlap calculation is a pure function from `Set<String>` to `SectorOverlapResponse` with no I/O. This makes it trivially testable without mocks and has zero performance impact on the request path.

### Sørensen–Dice coefficient for overlap

```
overlap = (2 × |common| / (|portfolio| + |basket|)) × 100
```

This is symmetric and penalises partial coverage from both sides. A simple intersection-over-basket metric would unfairly reward small portfolios (a single matching stock out of one could score 20%), whereas Dice accounts for portfolio size as well.

### H2 for tests, Oracle for production

`src/test/resources/application.properties` overrides the datasource with H2 in-memory and `ddl-auto=create-drop`. This keeps the CI feedback loop fast with no external dependency. The integration tests still exercise real Spring transactions and real Hibernate locking behaviour — just against H2 instead of Oracle.

### HikariCP pool sized at 20

The concurrency tests spawn up to 6 simultaneous threads. Production is sized at 20 connections to comfortably handle burst load while staying within Oracle XE's connection limit (typically 20–30 sessions for the free edition).

---

## Trade-offs

### Pessimistic locking serialises placement per trader

Acquiring a row-level lock on the trader means concurrent placement requests from the same trader queue at the database. For a trading desk this is acceptable — placement is not a hot path and correctness outweighs throughput. For high-frequency placement, an alternative would be a per-trader in-memory queue with a single writer, but that adds state management complexity and breaks horizontal scaling.

### SELL check at placement is advisory, not a reservation

The holdings check at `placeOrder` reads current committed holdings. If a trader places two SELL orders for the same stock before either fills, both can pass the check because neither has yet reduced the committed holdings. The second fill will fail at `decrementHoldings` with a `400 Bad Request` — no actual over-sell can complete, but an order can be placed and then fail at fill time. A stricter system would reserve shares at placement by decrementing an "available" balance, but that significantly complicates the data model.

### Schema managed manually, not via Flyway/Liquibase

`spring.sql.init.mode=always` with `schema.sql` and `data.sql` is sufficient for a single-service deployment. The trade-off is that schema evolution (column additions, renames, index changes) must be handled with hand-written `ALTER` statements rather than versioned migrations. For a production system with multiple environments, Flyway or Liquibase should be added.

### No authentication or authorisation

The API is unauthenticated. A real trading system would require identity verification on every request and validation that the caller has permission to act on behalf of the given `traderId`. Adding Spring Security with JWT or OAuth2 was out of scope.

### Sector baskets are hardcoded

The three benchmark baskets are compile-time constants. Adding or updating a basket requires a code change and redeployment. A database-backed configuration would allow runtime updates but adds schema complexity and caching concerns for a read path that currently requires zero DB calls.
