# Product Requirements Document (PRD)
## SkyHigh Core – Digital Check-In System

**Version:** 1.0  
**Date:** March 8, 2026  
**Status:** Approved

---

## 1. Problem Statement

SkyHigh Airlines is experiencing operational challenges during peak check-in hours at airports. The current manual and semi-automated check-in process cannot handle:

- **Hundreds of concurrent passengers** attempting to check in simultaneously
- **Seat conflicts** when multiple passengers select the same seat
- **Abandoned seat reservations** that block availability unnecessarily
- **Baggage validation delays** causing check-in bottlenecks
- **Slow seat map loading** leading to poor user experience

These issues result in passenger frustration, operational inefficiencies, and lost revenue opportunities.

---

## 2. Product Vision

Build **SkyHigh Core**, a robust backend digital check-in system that enables:

- **Fast and reliable** seat selection during high-traffic periods
- **Conflict-free** seat assignments with strong consistency guarantees
- **Automated seat lifecycle management** with time-bound reservations
- **Seamless baggage validation** with integrated payment handling
- **Sub-second seat map access** for optimal user experience

---

## 3. Goals & Success Criteria

### Primary Goals

1. **Prevent Seat Conflicts**
   - Zero double-bookings under concurrent access
   - Only one passenger can successfully reserve any given seat

2. **Handle Time-Bound Reservations**
   - Seats held for exactly 120 seconds
   - Automatic release if check-in not completed
   - No manual intervention required

3. **Support High Traffic**
   - Handle hundreds of concurrent check-in requests
   - Seat map API P95 latency < 1 second
   - System remains responsive during peak hours

4. **Validate Baggage Rules**
   - Enforce 25kg weight limit
   - Pause check-in for payment when limit exceeded
   - Resume check-in after successful payment

5. **Maintain Data Consistency**
   - Accurate real-time seat availability
   - Reliable state transitions
   - Complete audit trail of seat assignments

### Success Metrics

- **Seat Conflict Rate:** 0% (zero tolerance)
- **Seat Map P95 Latency:** < 1000ms
- **Auto-Expiration Success Rate:** > 99.9%
- **System Uptime:** > 99.5% during check-in windows
- **Concurrent Users Supported:** 500+ simultaneous check-ins

---

## 4. Target Users

### Primary Users

1. **Airline Passengers**
   - Self-service check-in via web/mobile
   - Selecting seats and adding baggage
   - Completing payment for excess baggage

2. **Airport Kiosk Systems**
   - Automated check-in terminals
   - High-frequency seat map queries
   - Real-time seat availability display

3. **Airline Check-In Agents**
   - Assisting passengers during peak hours
   - Resolving check-in issues
   - Managing seat assignments

### Secondary Users

4. **Operations Team**
   - Monitoring system health
   - Analyzing check-in patterns
   - Managing flight configurations

---

## 5. Functional Requirements

### 5.1 Seat Lifecycle Management

**Requirement ID:** FR-001

**Description:** Each seat must follow a defined state machine with validated transitions.

**Seat States:**
- `AVAILABLE` – Seat is open for selection
- `HELD` – Seat is temporarily reserved for a passenger
- `CONFIRMED` – Seat is permanently assigned

**State Transition Rules:**
- `AVAILABLE` → `HELD`: Only if currently AVAILABLE
- `HELD` → `CONFIRMED`: Only by the passenger holding the seat
- `HELD` → `AVAILABLE`: Automatic expiration after 120 seconds
- `CONFIRMED`: Final state, no further transitions allowed

**Validation:**
- Prevent invalid state transitions
- Reject attempts to hold already HELD/CONFIRMED seats
- Track all state changes with timestamps

---

### 5.2 Time-Bound Seat Hold (120 Seconds)

**Requirement ID:** FR-002

**Description:** When a passenger selects a seat, it must be exclusively reserved for exactly 120 seconds.

**Business Rules:**
- Hold timer starts immediately upon seat selection
- During hold period:
  - Seat is invisible to other passengers
  - Only the holding passenger can confirm the seat
- If check-in not completed within 120 seconds:
  - Seat automatically returns to AVAILABLE state
  - Hold is released without manual intervention

**Technical Requirements:**
- Expiration must work reliably under high load
- No orphaned holds blocking seats indefinitely
- Expiration accuracy: ±2 seconds tolerance

---

### 5.3 Conflict-Free Seat Assignment

**Requirement ID:** FR-003

**Description:** The system must provide absolute guarantee that no seat can be double-booked.

**Hard Constraints:**
- If 100 passengers attempt to reserve the same seat simultaneously:
  - Exactly 1 reservation succeeds
  - Remaining 99 receive clear rejection
- No race conditions under any load scenario
- Strong consistency over eventual consistency

**Technical Requirements:**
- Use database-level concurrency control (optimistic/pessimistic locking)
- Handle concurrent requests gracefully
- Return deterministic error responses for conflicts
- Maintain ACID properties for seat transactions

---

### 5.4 Baggage Validation & Payment Flow

**Requirement ID:** FR-004

**Description:** Validate baggage weight during check-in and enforce payment for excess baggage.

**Business Rules:**
- Maximum allowed baggage weight: **25kg**
- If baggage weight ≤ 25kg:
  - Check-in proceeds normally
- If baggage weight > 25kg:
  - Check-in status changes to `WAITING_FOR_PAYMENT`
  - Calculate excess baggage fee
  - Pause check-in until payment confirmed
  - Resume check-in after successful payment

**Integration Points:**
- Weight Service: External service for baggage weight retrieval
- Payment Service: Simulated payment processing

**Check-In States:**
- `IN_PROGRESS` – Check-in initiated, collecting information
- `WAITING_FOR_PAYMENT` – Excess baggage fee pending
- `COMPLETED` – Check-in successfully finished

---

### 5.5 High-Performance Seat Map Access

**Requirement ID:** FR-005

**Description:** Seat map browsing must be fast and responsive during peak traffic.

**Performance Requirements:**
- **P95 Response Time:** < 1000ms
- **Concurrent Users:** Support 500+ simultaneous requests
- **Data Accuracy:** Near real-time seat availability (< 5 seconds staleness)

**Optimization Strategies:**
- Implement caching for frequently accessed seat maps
- Use efficient database queries with proper indexing
- Cache invalidation on seat state changes
- Load balancing for read-heavy operations

---

### 5.6 Audit Trail & State History

**Requirement ID:** FR-006

**Description:** Maintain complete history of all seat state changes for troubleshooting and compliance.

**Requirements:**
- Log every seat state transition with:
  - Timestamp
  - Previous state
  - New state
  - Passenger ID
  - Reason for change
- Immutable audit records
- Queryable history for customer support

---

## 6. Non-Functional Requirements (NFRs)

### 6.1 Performance

- **Seat Map API:** P95 < 1000ms, P99 < 2000ms
- **Seat Hold API:** P95 < 500ms
- **Check-In Completion:** P95 < 2000ms
- **Throughput:** 1000+ requests/second during peak

### 6.2 Scalability

- Support 500+ concurrent check-ins per flight
- Handle 50+ flights checking in simultaneously
- Horizontal scaling capability for services
- Database connection pooling and optimization

### 6.3 Reliability

- **System Uptime:** 99.5% during check-in windows
- **Seat Hold Expiration:** 99.9% success rate
- **Zero Data Loss:** All transactions must be durable
- **Graceful Degradation:** Fallback mechanisms for service failures

### 6.4 Consistency

- **Strong Consistency:** Seat assignments must be immediately consistent
- **No Double-Bookings:** Zero tolerance for seat conflicts
- **ACID Transactions:** All check-in operations must be atomic

### 6.5 Security

- **Input Validation:** Validate all API inputs
- **SQL Injection Prevention:** Use parameterized queries
- **Authentication:** Secure passenger identity (optional for MVP)
- **Authorization:** Passengers can only modify their own check-ins

### 6.6 Observability

- **Logging:** Structured logs with correlation IDs
- **Metrics:** Track seat hold expiration, conflict rates, API latency
- **Error Tracking:** Clear error messages and exception handling
- **Health Checks:** Endpoint for service health monitoring

### 6.7 Maintainability

- **Code Quality:** Follow SOLID principles and clean architecture
- **Test Coverage:** Minimum 60% code coverage
- **Documentation:** Clear API documentation with examples
- **Database Migrations:** Version-controlled schema changes

### 6.8 Data Integrity

- **Referential Integrity:** Foreign key constraints
- **State Validation:** Prevent invalid state transitions
- **Idempotency:** Safe retry of failed operations
- **Transactional Boundaries:** Clear transaction scopes

---

## 7. API Requirements

### Core Endpoints

1. **GET /api/flights/{flightId}/seats**
   - Retrieve seat map with current availability
   - Filter by seat type (window, aisle, middle)
   - Response time: P95 < 1s

2. **POST /api/seats/{seatId}/hold**
   - Reserve a seat for 120 seconds
   - Return hold expiration timestamp
   - Handle concurrent conflicts

3. **POST /api/seats/{seatId}/confirm**
   - Confirm held seat and complete assignment
   - Validate hold ownership and expiration
   - Make seat CONFIRMED

4. **POST /api/check-in**
   - Initiate check-in for passenger
   - Create check-in session
   - Return check-in ID

5. **POST /api/check-in/{checkInId}/baggage**
   - Add baggage to check-in
   - Validate weight (25kg limit)
   - Trigger payment if excess weight

6. **POST /api/check-in/{checkInId}/payment**
   - Process baggage fee payment
   - Resume check-in after payment
   - Update check-in status

7. **POST /api/check-in/{checkInId}/complete**
   - Finalize check-in process
   - Validate all requirements met
   - Issue boarding pass details

8. **GET /api/check-in/{checkInId}**
   - Retrieve check-in status
   - Show current state and requirements

---

## 8. Out of Scope (for MVP)

- Multi-flight check-in
- Seat selection preferences (automatic assignment)
- Integration with real payment gateways
- Mobile push notifications
- Email confirmations
- Boarding pass PDF generation
- Seat upgrade offers
- Group booking management
- Special assistance requests

---

## 9. Technical Constraints

### Technology Stack
- **Language:** Java 21
- **Framework:** Spring Boot 3.x
- **Database:** PostgreSQL (H2 for testing)
- **Cache:** Redis
- **Build Tool:** Maven
- **API Documentation:** OpenAPI 3.0 / Swagger
- **Containerization:** Docker + Docker Compose

### Infrastructure
- Must run via single `docker-compose up` command
- All dependencies containerized
- Database migrations automated
- Sample data seed for demo

---

## 10. Assumptions

1. Each flight has a predefined seat configuration
2. Passenger authentication is handled upstream (or simulated)
3. Payment service is simulated (not real integration)
4. Weight service is simulated (not real integration)
5. One passenger can only check in for one flight at a time
6. Seat hold timer precision: ±2 seconds acceptable
7. System clock synchronization assumed (for hold expiration)

---

## 11. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Redis failure causes all holds to fail | High | Implement fallback to database-based hold tracking |
| Database connection pool exhaustion | High | Configure HikariCP with proper limits, use connection timeout |
| Clock skew causes incorrect hold expiration | Medium | Use server-side timestamps consistently, document requirement |
| Race condition in seat assignment | Critical | Use optimistic locking with version column, comprehensive testing |
| Cache staleness shows wrong availability | Medium | Aggressive cache invalidation, short TTL (5-10 seconds) |

---

## 12. Success Validation

### Functional Tests
- ✅ 100 concurrent requests for same seat → only 1 succeeds
- ✅ Seat auto-expires after exactly 120 seconds
- ✅ Check-in pauses when baggage > 25kg
- ✅ Check-in completes after payment processed
- ✅ Confirmed seats cannot change state

### Performance Tests
- ✅ Seat map loads in < 1s with 500 concurrent users
- ✅ System handles 1000 req/s sustained load
- ✅ No memory leaks after 1 hour stress test

### Reliability Tests
- ✅ Zero seat conflicts after 10,000 concurrent operations
- ✅ All holds expire within 120±2 seconds
- ✅ System recovers from Redis restart

---

## 13. Appendix

### Glossary

- **Check-In:** Process of passenger confirming their presence for a flight
- **Seat Hold:** Temporary reservation of a seat with automatic expiration
- **Seat Map:** Visual representation of all seats on a flight
- **Boarding Pass:** Document allowing passenger to board the flight
- **Excess Baggage:** Baggage exceeding the 25kg weight limit

### References

- Spring Boot Documentation: https://spring.io/projects/spring-boot
- Redis TTL: https://redis.io/commands/expire
- Optimistic Locking: https://en.wikipedia.org/wiki/Optimistic_concurrency_control

---

**Document Owner:** Backend Architecture Team  
**Reviewers:** Product Management, Engineering Leadership  
**Approval Date:** March 8, 2026

