# Project Structure - SkyCheck Digital Check-In System

## Overview

SkyCheck follows a standard Spring Boot layered architecture with clear separation of concerns. The project is organized by technical layers (controller, service, repository) for maintainability and scalability.

---

## Directory Structure

```
skycheck-service/
│
├── src/
│   ├── main/
│   │   ├── java/com/skyhigh/skycheck/
│   │   │   ├── controller/          # REST API endpoints
│   │   │   ├── service/             # Business logic layer
│   │   │   ├── repository/          # Data access layer
│   │   │   ├── entity/              # JPA entities (domain models)
│   │   │   ├── dto/                 # Data Transfer Objects
│   │   │   ├── exception/           # Custom exceptions and handlers
│   │   │   ├── config/              # Configuration classes
│   │   │   ├── mapper/              # Entity ↔ DTO mappers
│   │   │   ├── schedule/            # Scheduled background jobs
│   │   │   └── SkyCheckApplication.java  # Main application class
│   │   │
│   │   └── resources/
│   │       ├── application.yml      # Main configuration
│   │       └── db/migration/        # Flyway database migrations
│   │           ├── V1__Create_initial_schema.sql
│   │           └── V2__Insert_sample_data.sql
│   │
│   └── test/
│       ├── java/com/skyhigh/skycheck/
│       │   ├── service/             # Unit tests for services
│       │   ├── controller/          # Unit tests for controllers
│       │   └── integration/         # Integration tests
│       │
│       └── resources/
│           └── application-test.yml # Test configuration
│
├── pom.xml                          # Maven dependencies
├── Dockerfile                       # Container build instructions
├── docker-compose.yml               # Multi-container orchestration
├── .gitignore                       # Git ignore patterns
│
└── Documentation/
    ├── README.md                    # Project overview & setup
    ├── PRD.md                       # Product requirements
    ├── ARCHITECTURE.md              # System architecture
    ├── WORKFLOW_DESIGN.md           # Implementation workflows
    ├── PROJECT_STRUCTURE.md         # This file
    ├── API-SPECIFICATION.yml        # OpenAPI specification
    └── CHAT_HISTORY.md              # Design decisions log
```

---

## Package Details

### 1. `controller/` - REST API Layer

**Purpose:** Handle HTTP requests and responses

**Files:**
- `CheckInController.java` - Check-in workflow endpoints
  - POST `/api/check-in` - Initiate check-in
  - POST `/api/check-in/{id}/hold-seat` - Hold seat
  - POST `/api/check-in/{id}/confirm-seat` - Confirm seat
  - POST `/api/check-in/{id}/baggage` - Add baggage
  - POST `/api/check-in/{id}/payment` - Process payment
  - POST `/api/check-in/{id}/complete` - Complete check-in
  - GET `/api/check-in/{id}` - Get status
  - DELETE `/api/check-in/{id}` - Cancel check-in

- `SeatMapController.java` - Seat browsing endpoints
  - GET `/api/flights/{id}/seats` - Get seat map (cached)
  - GET `/api/flights/{id}/seats/available` - Get available seats

- `FlightController.java` - Flight information endpoints
  - GET `/api/flights` - List all flights
  - GET `/api/flights/{id}` - Get flight details

**Responsibilities:**
- Input validation using `@Valid`
- DTOs for request/response
- HTTP status code mapping
- OpenAPI documentation annotations

---

### 2. `service/` - Business Logic Layer

**Purpose:** Implement core business rules and orchestrate operations

**Files:**

#### `CheckInService.java`
**Orchestrates complete check-in workflow**
- Initiates check-in session
- Coordinates seat selection
- Manages baggage addition
- Handles payment processing
- Completes check-in with validation

#### `SeatService.java`
**Manages seat lifecycle and concurrency**
- Implements state machine (AVAILABLE → HELD → CONFIRMED)
- Handles optimistic locking with retry
- Coordinates with SeatHoldManager
- Records state change history
- Cache invalidation on state changes

#### `SeatHoldManager.java`
**Redis-based seat hold with TTL**
- Creates atomic holds using SETNX
- Manages 120-second expiration
- Verifies hold ownership
- Provides remaining TTL

#### `BaggageService.java`
**Validates baggage and calculates fees**
- Enforces 25kg weight limit
- Calculates excess fees ($10/kg)
- Manages payment status
- Simulates payment processing

#### `FlightService.java` & `PassengerService.java`
**Support services for data retrieval**

**Key Patterns:**
- `@Transactional` for ACID operations
- `@Cacheable` for performance
- `@Retryable` for optimistic lock failures
- Constructor injection via Lombok `@RequiredArgsConstructor`

---

### 3. `repository/` - Data Access Layer

**Purpose:** Abstract database operations using Spring Data JPA

**Files:**
- `FlightRepository.java` - Flight data access
- `SeatRepository.java` - Seat queries with locking
- `PassengerRepository.java` - Passenger lookup
- `CheckInRepository.java` - Check-in persistence
- `SeatReservationRepository.java` - Reservation tracking
- `BaggageRepository.java` - Baggage records
- `SeatStateHistoryRepository.java` - Audit trail

**Key Features:**
- Custom JPQL queries for complex operations
- `@Lock(LockModeType.OPTIMISTIC)` for concurrency
- Query methods following Spring Data conventions
- Index hints in query annotations

---

### 4. `entity/` - Domain Models

**Purpose:** JPA entities representing database tables

**Files:**
- `Flight.java` - Flight information
- `Seat.java` - Seat with state machine logic + `@Version`
- `Passenger.java` - Passenger details
- `CheckIn.java` - Check-in session with status
- `SeatReservation.java` - Hold/confirm tracking
- `Baggage.java` - Baggage and payment info
- `SeatStateHistory.java` - Immutable audit records

**Design Principles:**
- Rich domain models with behavior methods
- Enum types for states
- Validation in entity methods (e.g., `canBeHeld()`)
- Lombok for boilerplate reduction
- Hibernate annotations for performance

---

### 5. `dto/` - Data Transfer Objects

**Purpose:** API contracts for requests and responses

**Request DTOs:**
- `CheckInRequest.java` - Initiate check-in
- `SeatHoldRequest.java` - Hold seat
- `SeatConfirmRequest.java` - Confirm seat
- `BaggageRequest.java` - Add baggage
- `PaymentRequest.java` - Process payment

**Response DTOs:**
- `CheckInResponse.java` - Check-in status
- `SeatMapResponse.java` - Flight seat map
- `SeatDto.java` - Individual seat info
- `SeatHoldResponse.java` - Hold confirmation
- `BaggageResponse.java` - Baggage details
- `PaymentResponse.java` - Payment confirmation

**Validation:**
- `@NotNull`, `@NotBlank` for required fields
- `@DecimalMin` for numeric constraints
- Custom validation messages

---

### 6. `exception/` - Error Handling

**Purpose:** Custom exceptions and global error handling

**Custom Exceptions:**
- `ResourceNotFoundException.java` - 404 errors
- `SeatAlreadyReservedException.java` - 409 conflicts
- `SeatHoldExpiredException.java` - 410 gone
- `InvalidSeatStateException.java` - State validation
- `PaymentRequiredException.java` - 402 payment needed
- `InvalidBaggageWeightException.java` - Weight validation

**Error Handler:**
- `GlobalExceptionHandler.java` - Centralized error handling
  - Maps exceptions to HTTP status codes
  - Structured error responses
  - Validation error formatting
  - Logging integration

**Error Response Format:**
- `ErrorResponse.java` - Consistent error structure

---

### 7. `config/` - Configuration

**Purpose:** Application configuration and beans

**Files:**
- `ApplicationConfig.java` - Configuration properties
  - Seat hold TTL (120 seconds)
  - Baggage limits (25kg)
  - Cache TTL settings

- `RedisConfig.java` - Redis setup
  - Connection factory
  - Cache manager with TTL
  - Serialization config

- `OpenApiConfig.java` - Swagger/OpenAPI setup
  - API metadata
  - Server configuration
  - Documentation customization

---

### 8. `mapper/` - Object Mapping

**Purpose:** Convert between entities and DTOs

**Files:**
- `DtoMapper.java` - Bidirectional mapping
  - Entity → DTO for responses
  - Aggregation for complex responses
  - Null-safe transformations

---

### 9. `schedule/` - Background Jobs

**Purpose:** Scheduled tasks for system maintenance

**Files:**
- `SeatHoldCleanupJob.java` - Expires old holds
  - Runs every 30 seconds
  - Finds holds past expiration
  - Updates seat states
  - Maintains Redis/DB consistency

**Configuration:**
- `@EnableScheduling` in main application
- Interval configured via properties
- Transactional cleanup operations

---

## Database Migrations

### `resources/db/migration/`

**Purpose:** Version-controlled schema changes using Flyway

**Files:**
- `V1__Create_initial_schema.sql` - Initial tables and indexes
- `V2__Insert_sample_data.sql` - Demo flights and passengers

**Flyway Features:**
- Automatic migration on startup
- Version tracking
- Rollback support (manual)
- Baseline on existing database

---

## Test Structure

### `test/java/.../service/`
**Unit tests for business logic**
- `SeatServiceTest.java` - 10+ test cases
- `BaggageServiceTest.java` - 9+ test cases
- `CheckInServiceTest.java` - 10+ test cases
- Mocked dependencies
- Fast execution
- High coverage

### `test/java/.../integration/`
**Integration tests with real database**
- `ConcurrentSeatReservationTest.java`
  - Tests 10 concurrent hold attempts
  - Verifies only 1 succeeds
  - Uses ExecutorService for parallelism
  - H2 in-memory database

### `test/resources/`
- `application-test.yml` - Test profile config
  - H2 database
  - Disabled Flyway
  - Fast cleanup intervals

---

## Configuration Files

### `application.yml`
**Main application configuration**

**Sections:**
- `spring.datasource` - PostgreSQL connection
- `spring.jpa` - Hibernate settings
- `spring.data.redis` - Redis connection
- `spring.cache` - Cache configuration
- `app.*` - Custom business rules
- `management.*` - Actuator endpoints
- `logging.*` - Log levels and format
- `springdoc.*` - OpenAPI settings

### `application-test.yml`
**Test profile overrides**
- H2 in-memory database
- Faster cleanup intervals
- Verbose logging

---

## Build Configuration

### `pom.xml`
**Maven project descriptor**

**Key Dependencies:**
- Spring Boot 3.2.3 (Web, Data JPA, Redis, Cache, Validation)
- PostgreSQL driver + H2 for tests
- Redis client (Jedis)
- Lombok for boilerplate
- SpringDoc OpenAPI
- Flyway for migrations
- Testcontainers for integration tests
- JaCoCo for coverage

**Plugins:**
- Spring Boot Maven Plugin
- JaCoCo for test coverage (60% minimum)
- Surefire for unit tests
- Failsafe for integration tests

---

## Containerization

### `Dockerfile`
**Multi-stage build**
1. **Build Stage:** Maven build with dependencies
2. **Runtime Stage:** Minimal JRE image
3. **Health Check:** Actuator endpoint

### `docker-compose.yml`
**Three-service stack**
1. **postgres** - Database (port 5432)
2. **redis** - Cache (port 6379)
3. **app** - Spring Boot application (port 8080)

**Features:**
- Health checks for dependencies
- Named volumes for data persistence
- Custom network for service communication
- Environment variable injection

---

## Key Design Patterns

### 1. Layered Architecture
- Clear separation: Controller → Service → Repository → Database
- Each layer has single responsibility
- Dependencies flow downward only

### 2. Repository Pattern
- Spring Data JPA abstracts database access
- Custom queries for complex operations
- Transaction management at service layer

### 3. DTO Pattern
- Separate API contracts from domain models
- Prevents over-exposure of internal structure
- Versioning flexibility

### 4. Cache-Aside Pattern
- Application manages cache
- Spring Cache abstraction
- Automatic eviction

### 5. Strategy Pattern
- SeatHoldManager encapsulates hold strategy
- Can swap Redis for other implementations
- Clean abstraction

---

## Package Dependencies

```
controller → service → repository → entity
    ↓          ↓           ↓
   dto    exception    database
    ↓          ↓
  mapper    config
```

**Rules:**
- Controllers depend on services and DTOs
- Services depend on repositories and entities
- Repositories depend on entities only
- No circular dependencies
- Config is shared across layers

---

## Code Conventions

### Naming
- Entities: Singular noun (Seat, CheckIn)
- Repositories: EntityRepository
- Services: EntityService
- Controllers: FeatureController
- DTOs: EntityRequest/Response/Dto

### Annotations
- `@RestController` - REST endpoints
- `@Service` - Business logic
- `@Repository` - Data access
- `@Transactional` - Transaction boundaries
- `@Cacheable` - Cache reads
- `@CacheEvict` - Cache invalidation

### Logging
- Use SLF4J `@Slf4j`
- INFO for business events
- DEBUG for detailed flow
- WARN for handled errors
- ERROR for system failures

---

## Module Responsibilities

| Module | Purpose | Key Classes |
|--------|---------|-------------|
| **controller** | HTTP API | CheckInController, SeatMapController |
| **service** | Business logic | SeatService, CheckInService, SeatHoldManager |
| **repository** | Data access | JPA repositories |
| **entity** | Domain models | Seat, CheckIn, Baggage |
| **dto** | API contracts | Request/Response objects |
| **exception** | Error handling | Custom exceptions, GlobalExceptionHandler |
| **config** | Configuration | Redis, OpenAPI, Application properties |
| **mapper** | Object mapping | DtoMapper |
| **schedule** | Background jobs | SeatHoldCleanupJob |

---

## Testing Strategy

### Unit Tests (60%+ coverage)
**Location:** `src/test/java/.../service/`

**Approach:**
- Mock all dependencies using Mockito
- Test business logic in isolation
- Fast execution (< 5 seconds total)
- No external dependencies

**Coverage:**
- All service methods
- Edge cases and error conditions
- State transition validation

### Integration Tests
**Location:** `src/test/java/.../integration/`

**Approach:**
- H2 in-memory database
- Real Redis (Testcontainers optional)
- Multi-threaded concurrency tests
- End-to-end workflow validation

**Key Tests:**
- Concurrent seat reservation (10 threads)
- Complete check-in workflow
- Baggage with payment flow

---

## Database Migration Strategy

### Flyway Versioning
- `V1__Create_initial_schema.sql` - Tables, indexes, constraints
- `V2__Insert_sample_data.sql` - Test data (3 flights, 90 seats, 5 passengers)

### Migration Process
1. Application starts
2. Flyway checks `flyway_schema_history` table
3. Applies pending migrations in order
4. Records applied versions
5. Application starts normally

**Benefits:**
- Version-controlled schema
- Repeatable deployments
- Safe rollbacks
- Team collaboration

---

## Build & Deployment Flow

### Local Development
```bash
# 1. Start dependencies
docker-compose up postgres redis

# 2. Run application
mvn spring-boot:run

# 3. Access Swagger UI
http://localhost:8080/swagger-ui.html
```

### Docker Deployment
```bash
# Single command startup
docker-compose up

# Services available:
# - Application: http://localhost:8080
# - PostgreSQL: localhost:5432
# - Redis: localhost:6379
```

### Testing
```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Coverage report
mvn test jacoco:report
# Report: target/site/jacoco/index.html
```

---

## Performance Considerations

### Database
- **Connection Pooling:** HikariCP (20 max)
- **Indexes:** On flight_id, state, hold_expires_at
- **Batch Operations:** Hibernate batch insert/update
- **Lazy Loading:** Avoid N+1 queries

### Caching
- **Seat Map:** Redis cache (10s TTL)
- **Cache Key:** `seatMap::{flightId}`
- **Invalidation:** On seat state changes
- **Hit Ratio Target:** > 90%

### Redis
- **Connection Pool:** Jedis (20 active)
- **Timeout:** 2 seconds
- **Key Expiration:** Automatic TTL
- **Serialization:** JSON (human-readable)

---

## Security Measures

### Input Validation
- Bean Validation annotations
- Null checks
- Range validation
- SQL injection prevention (JPA)

### Error Handling
- No stack traces exposed to clients
- Generic error messages
- Detailed logging for debugging
- Correlation IDs (future)

### Database
- Parameterized queries only
- Foreign key constraints
- Check constraints for enums
- No raw SQL queries

---

## Monitoring & Health Checks

### Actuator Endpoints
- `/actuator/health` - Service health
- `/actuator/info` - Application info
- `/actuator/metrics` - Performance metrics
- `/actuator/prometheus` - Prometheus format

### Custom Metrics
- Seat hold creation rate
- Hold expiration rate
- Optimistic lock retry rate
- Cache hit/miss ratio

---

## Extension Points

### Adding New Features
1. **New API Endpoint:** Add method in controller
2. **New Business Logic:** Add method in service
3. **New Query:** Add method in repository
4. **New Validation:** Add custom validator
5. **New Exception:** Create exception + handler

### Configuration
- All business rules in `application.yml`
- Easy to adjust without code changes
- Environment-specific overrides

---

## Troubleshooting Guide

### Common Issues

**Issue:** Seat stays HELD indefinitely
- **Check:** Redis connection
- **Check:** Cleanup job logs
- **Fix:** Manually release via API or database

**Issue:** Optimistic locking failures
- **Check:** High concurrency on same seat
- **Expected:** Retries should succeed
- **Fix:** Increase retry attempts if needed

**Issue:** Cache showing stale data
- **Check:** Cache TTL configuration
- **Check:** Cache invalidation on state change
- **Fix:** Reduce TTL or add invalidation

**Issue:** Database connection errors
- **Check:** Connection pool exhaustion
- **Check:** Long-running transactions
- **Fix:** Increase pool size or optimize queries

---

## Code Quality Standards

### SOLID Principles
- **Single Responsibility:** Each class has one purpose
- **Open/Closed:** Extensible via configuration
- **Liskov Substitution:** Interface-based design
- **Interface Segregation:** Focused interfaces
- **Dependency Inversion:** Depend on abstractions

### Clean Code
- Meaningful names
- Small methods (< 20 lines)
- Clear comments for business rules
- No magic numbers
- Constants for configuration

---

## Future Refactoring Opportunities

1. **Extract Payment Service Interface**
   - Support multiple payment gateways
   - Strategy pattern for payment methods

2. **Event-Driven State Changes**
   - Publish events on state transitions
   - Kafka integration for real-time updates

3. **CQRS Pattern**
   - Separate read/write models
   - Optimize queries independently

4. **Circuit Breaker**
   - Resilience4j for external calls
   - Fallback strategies

---

**Document Version:** 1.0  
**Last Updated:** March 8, 2026  
**Maintainer:** Backend Engineering Team

