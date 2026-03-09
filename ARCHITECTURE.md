# System Architecture - SkyCheck Digital Check-In System

## Overview

SkyCheck is built using a layered architecture pattern with clean separation of concerns, optimized for high concurrency and performance during peak check-in periods.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                             │
│  (Web/Mobile Apps, Kiosk Systems, Airport Check-in Agents)      │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP/REST
┌────────────────────────────┴────────────────────────────────────┐
│                      API GATEWAY (Future)                        │
│            Rate Limiting, Authentication, Load Balancing         │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────┴────────────────────────────────────┐
│                   APPLICATION LAYER (Spring Boot)                │
│                                                                   │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   Controllers   │  │   Controllers   │  │   Controllers   │ │
│  │    (REST API)   │  │   (Exception    │  │   (Validation)  │ │
│  │                 │  │    Handling)    │  │                 │ │
│  └────────┬────────┘  └─────────────────┘  └─────────────────┘ │
│           │                                                       │
│  ┌────────┴────────────────────────────────────────────────┐   │
│  │                  Service Layer                           │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌─────────────────┐ │   │
│  │  │ CheckInSvc   │ │  SeatService │ │  BaggageService │ │   │
│  │  └──────────────┘ └──────────────┘ └─────────────────┘ │   │
│  │  ┌──────────────┐ ┌──────────────┐                     │   │
│  │  │ FlightSvc    │ │ PassengerSvc │                     │   │
│  │  └──────────────┘ └──────────────┘                     │   │
│  └────────┬─────────────────────────────────────────────────┘   │
│           │                                                       │
│  ┌────────┴────────────────────────────────────────────────┐   │
│  │              Repository Layer (Spring Data JPA)         │   │
│  │  Database access with optimistic locking support       │   │
│  └────────┬────────────────────────────────────────────────┘   │
└───────────┼───────────────────────────────────────────────────┘
            │
┌───────────┴──────────────────┬──────────────────────────────────┐
│                              │                                   │
│  ┌───────────────────────┐  │  ┌────────────────────────────┐  │
│  │   PostgreSQL DB       │  │  │      Redis Cache           │  │
│  │  - Persistent storage │  │  │  - Seat hold TTL (120s)   │  │
│  │  - ACID transactions  │  │  │  - Seat map cache (10s)   │  │
│  │  - Optimistic locking │  │  │  - Distributed locks      │  │
│  └───────────────────────┘  │  └────────────────────────────┘  │
│                              │                                   │
└──────────────────────────────┴───────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    BACKGROUND JOBS                               │
│  ┌────────────────────────────────────────────────────────┐    │
│  │  SeatHoldCleanupJob (Scheduled every 30s)              │    │
│  │  - Finds expired holds in database                     │    │
│  │  - Releases seats back to AVAILABLE                    │    │
│  │  - Ensures consistency between Redis and PostgreSQL    │    │
│  └────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

## Component Architecture

### 1. Controller Layer
**Responsibility:** Handle HTTP requests, validate inputs, format responses

**Components:**
- `CheckInController` - Check-in workflow endpoints
- `SeatMapController` - Seat browsing and availability
- `FlightController` - Flight information
- `GlobalExceptionHandler` - Centralized error handling

**Key Features:**
- Input validation using Bean Validation
- Swagger/OpenAPI documentation
- Structured error responses

---

### 2. Service Layer
**Responsibility:** Business logic, orchestration, transaction management

**Components:**

#### CheckInService
- Orchestrates complete check-in workflow
- Coordinates seat selection, baggage, payment
- Manages check-in state transitions

#### SeatService
- Implements seat lifecycle state machine
- Handles optimistic locking with retry
- Manages seat hold/confirm/release operations
- Records audit trail

#### SeatHoldManager
- Redis-based seat hold with TTL
- Atomic hold creation using SETNX
- Automatic expiration after 120 seconds

#### BaggageService
- Validates baggage weight (25kg limit)
- Calculates excess fees
- Manages payment status

#### FlightService & PassengerService
- Support services for data retrieval

---

### 3. Repository Layer
**Responsibility:** Data access abstraction

**Components:**
- Spring Data JPA repositories
- Custom queries with JPQL
- Optimistic locking support

---

### 4. Data Layer

#### PostgreSQL Database
**Purpose:** Primary persistent storage

**Key Features:**
- ACID transactions
- Foreign key constraints
- Optimistic locking via version column
- Indexes for query performance

**Tables:**
- `flights` - Flight information
- `seats` - Seat configuration and state
- `passengers` - Passenger details
- `check_ins` - Check-in sessions
- `seat_reservations` - Hold/confirm tracking
- `baggage` - Baggage and payment info
- `seat_state_history` - Audit trail

#### Redis Cache
**Purpose:** High-performance distributed cache

**Use Cases:**
1. **Seat Hold TTL** (Primary)
   - Key: `seat:hold:{seatId}`
   - Value: `passengerId`
   - TTL: 120 seconds
   - Automatic expiration

2. **Seat Map Caching** (Performance)
   - Key: `seatMap::{flightId}`
   - Value: Serialized seat list
   - TTL: 10 seconds
   - Cache-aside pattern

---

## Concurrency Strategy

### Optimistic Locking
**Implementation:** JPA `@Version` column on Seat entity

**Flow:**
1. Read seat with current version
2. Modify seat state
3. Save with version check
4. If version changed → `ObjectOptimisticLockingFailureException`
5. Retry with exponential backoff (max 3 attempts)

**Advantages:**
- Higher throughput than pessimistic locks
- No lock contention during read operations
- Suitable for high-traffic scenarios

### Redis Atomic Operations
**Implementation:** SETNX (SET if Not eXists)

**Guarantees:**
- Only one passenger can create hold
- Atomic check-and-set operation
- Distributed lock across multiple app instances

---

## Seat Hold Expiration Strategy

### Dual-Layer Approach

#### Layer 1: Redis TTL (Primary)
- Automatic expiration after 120 seconds
- No manual intervention required
- Works even if application crashes

#### Layer 2: Scheduled Cleanup Job (Backup)
- Runs every 30 seconds
- Queries database for expired holds
- Ensures database consistency with Redis
- Handles edge cases (Redis restart, clock skew)

**Benefits:**
- High reliability (99.9%+ expiration success)
- Consistent state between Redis and PostgreSQL
- Graceful degradation if Redis fails

---

## Caching Strategy

### Seat Map Cache
**Problem:** Most frequent operation, needs P95 < 1s

**Solution:**
- Cache entire seat list per flight in Redis
- TTL: 10 seconds (near real-time)
- Cache-aside pattern with Spring Cache

**Cache Invalidation:**
- Evict on seat state change (hold/confirm/release)
- Evict on scheduled cleanup
- Short TTL prevents stale data

**Performance Impact:**
- Cache Hit: < 50ms response time
- Cache Miss: Query + cache write ~200-500ms
- Target achieved: P95 < 1000ms

---

## Data Flow Diagrams

### Seat Hold Flow
```
Passenger → API → SeatService → Redis (SETNX) → SUCCESS?
                      ↓                              │
                  Database ← Optimistic Lock ← ─────┘
                      ↓
                Update seat.state = HELD
                      ↓
                Create SeatReservation
                      ↓
                Record SeatStateHistory
                      ↓
                Return holdExpiresAt
```

### Seat Expiration Flow
```
Redis TTL Expires → Key Deleted
        ↓
Cleanup Job (every 30s) → Query expired holds
        ↓
    For each expired hold:
        ↓
    Update seat.state = AVAILABLE
        ↓
    Update reservation.status = EXPIRED
        ↓
    Record SeatStateHistory
        ↓
    Evict seat map cache
```

---

## Scalability Considerations

### Horizontal Scaling
- **Stateless Application:** Can run multiple instances
- **Shared Redis:** Distributed locking across instances
- **Shared PostgreSQL:** Connection pooling per instance

### Database Optimization
- **Indexes:** On frequently queried columns
- **Connection Pooling:** HikariCP with limits
- **Query Optimization:** Fetch only required columns
- **Read Replicas:** (Future) Separate read/write databases

### Redis Optimization
- **Connection Pooling:** Jedis pool configuration
- **Key Expiration:** Automatic cleanup
- **Serialization:** Efficient JSON serialization

---

## Reliability & Resilience

### Transaction Management
- Service methods use `@Transactional`
- Rollback on exceptions
- Consistent state across entities

### Retry Mechanism
- Optimistic locking failures retry 3x
- Exponential backoff (100ms, 200ms, 400ms)
- Prevents thundering herd

### Error Handling
- Structured error responses
- Appropriate HTTP status codes
- Detailed logging for debugging

### Monitoring
- Actuator health endpoints
- Prometheus metrics
- Request/response logging

---

## Security Considerations

### Input Validation
- Bean Validation on all DTOs
- SQL injection prevention via JPA
- Parameter sanitization

### Authentication (Future)
- JWT token validation
- Passenger identity verification
- Role-based access control

---

## Technology Stack Summary

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Application | Spring Boot 3.2 | Framework |
| Language | Java 21 | Modern Java features |
| Database | PostgreSQL 16 | ACID persistence |
| Cache | Redis 7 | TTL and caching |
| ORM | Spring Data JPA | Database abstraction |
| API Docs | OpenAPI 3.0 | API documentation |
| Build | Maven | Dependency management |
| Testing | JUnit 5 + Mockito | Unit/integration tests |
| Containerization | Docker + Compose | Deployment |
| Metrics | Micrometer + Prometheus | Observability |

---

## Deployment Architecture

### Development
```
docker-compose up
  ├── postgres:5432
  ├── redis:6379
  └── app:8080
```

### Production (Future)
```
Load Balancer
  ├── App Instance 1 ──┐
  ├── App Instance 2 ──┼─→ Redis Cluster
  └── App Instance N ──┘
           │
           ↓
   PostgreSQL Primary/Replica
```

---

## Performance Targets

| Metric | Target | Strategy |
|--------|--------|----------|
| Seat Map P95 | < 1000ms | Redis caching + indexes |
| Seat Hold P95 | < 500ms | Optimistic locking + Redis |
| Check-in P95 | < 2000ms | Transaction optimization |
| Throughput | 1000+ req/s | Horizontal scaling |
| Concurrent Users | 500+ | Stateless design |

---

## Design Decisions & Trade-offs

### 1. Optimistic vs Pessimistic Locking
**Decision:** Optimistic locking with retry

**Rationale:**
- Higher throughput under load
- No lock contention on reads
- Acceptable retry overhead (< 5% of requests)

**Trade-off:** Occasional retries vs lower throughput

---

### 2. Redis TTL vs Database Polling
**Decision:** Redis TTL + scheduled cleanup

**Rationale:**
- Automatic expiration without polling
- Distributed lock for multi-instance
- Fast key operations

**Trade-off:** Additional infrastructure vs reliability

---

### 3. Cache TTL: 10 seconds
**Decision:** Short TTL for near real-time data

**Rationale:**
- Balance between performance and accuracy
- Acceptable staleness for seat browsing
- Aggressive invalidation on state changes

**Trade-off:** More cache misses vs data freshness

---

### 4. Seat Hold Duration: 120 seconds
**Decision:** Fixed 2-minute window

**Rationale:**
- Sufficient time for decision making
- Prevents seat hoarding
- Industry standard for airline systems

**Trade-off:** User convenience vs seat availability

---

## Future Enhancements

1. **Multi-Region Deployment**
   - Geographic distribution
   - Regional Redis clusters
   - Database replication

2. **Advanced Caching**
   - Distributed cache invalidation
   - Cache warming strategies
   - Predictive prefetching

3. **Event-Driven Architecture**
   - Kafka for state changes
   - Event sourcing for audit
   - Real-time notifications

4. **ML-Based Optimization**
   - Predict popular seats
   - Dynamic hold duration
   - Fraud detection

5. **Advanced Monitoring**
   - Distributed tracing (Zipkin)
   - Real-time alerting
   - SLA monitoring

---

## Glossary

- **Optimistic Locking:** Concurrency control assuming conflicts are rare
- **TTL (Time To Live):** Automatic expiration after specified duration
- **SETNX:** Redis command "SET if Not eXists" for atomic operations
- **Cache-Aside:** Cache pattern where app manages cache population
- **ACID:** Atomicity, Consistency, Isolation, Durability

---

**Document Version:** 1.0  
**Last Updated:** March 8, 2026  
**Author:** Backend Architecture Team

