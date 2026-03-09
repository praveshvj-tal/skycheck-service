# Workflow Design - SkyCheck Digital Check-In System

## Table of Contents
1. [Seat Lifecycle State Machine](#seat-lifecycle-state-machine)
2. [Check-In Workflow](#check-in-workflow)
3. [Seat Hold and Confirmation Flow](#seat-hold-and-confirmation-flow)
4. [Baggage Validation and Payment Flow](#baggage-validation-and-payment-flow)
5. [Seat Hold Expiration Flow](#seat-hold-expiration-flow)
6. [Database Schema](#database-schema)

---

## Seat Lifecycle State Machine

### State Diagram

```
┌─────────────┐
│  AVAILABLE  │◄──────────────────┐
└──────┬──────┘                   │
       │                           │
       │ holdSeat()                │
       │ (within transaction)      │ expireSeatHold()
       │                           │ (after 120s)
       ↓                           │
┌─────────────┐                   │
│    HELD     │───────────────────┘
└──────┬──────┘
       │
       │ confirmSeat()
       │ (before expiration)
       │
       ↓
┌─────────────┐
│  CONFIRMED  │ (final state)
└─────────────┘
```

### State Transition Rules

| From State | To State | Trigger | Conditions |
|-----------|----------|---------|------------|
| AVAILABLE | HELD | `holdSeat()` | Seat not held by anyone, Redis SETNX succeeds |
| HELD | CONFIRMED | `confirmSeat()` | Hold belongs to passenger, not expired |
| HELD | AVAILABLE | `expireSeatHold()` | 120 seconds elapsed, auto-cleanup |
| HELD | AVAILABLE | `releaseSeat()` | Manual release by passenger |
| CONFIRMED | - | - | **No transitions allowed** |

### Validation Logic

**Before AVAILABLE → HELD:**
- ✅ Seat state is AVAILABLE
- ✅ No active Redis hold exists
- ✅ Flight is still scheduled
- ✅ Passenger has active check-in

**Before HELD → CONFIRMED:**
- ✅ Seat state is HELD
- ✅ Redis hold belongs to passenger
- ✅ Hold has not expired
- ✅ Check-in is not completed yet

**Preventing Invalid Transitions:**
- ❌ Cannot hold HELD or CONFIRMED seats
- ❌ Cannot confirm AVAILABLE seats
- ❌ Cannot release CONFIRMED seats
- ❌ Cannot modify seat after check-in completed

---

## Check-In Workflow

### High-Level Flow

```
START
  ↓
┌─────────────────────────┐
│ 1. Initiate Check-In    │
│    POST /api/check-in   │
│    - Passenger ID       │
│    - Flight Number      │
└───────────┬─────────────┘
            ↓
     Status: IN_PROGRESS
            ↓
┌─────────────────────────┐
│ 2. Browse Seat Map      │
│    GET /flights/{id}/   │
│         seats           │
└───────────┬─────────────┘
            ↓
┌─────────────────────────┐
│ 3. Hold Seat            │
│    POST /check-in/{id}/ │
│         hold-seat       │
│    - Start 120s timer   │
└───────────┬─────────────┘
            ↓
     Seat: HELD (120s)
            ↓
┌─────────────────────────┐
│ 4. Confirm Seat         │
│    POST /check-in/{id}/ │
│         confirm-seat    │
└───────────┬─────────────┘
            ↓
     Seat: CONFIRMED
            ↓
┌─────────────────────────┐
│ 5. Add Baggage          │
│    POST /check-in/{id}/ │
│         baggage         │
└───────────┬─────────────┘
            ↓
      Weight > 25kg?
       ╱         ╲
     YES          NO
      ↓            ↓
┌─────────┐   ┌─────────────┐
│Payment  │   │ 7. Complete │
│Required │   │    Check-In │
└────┬────┘   └──────┬──────┘
     ↓               ↓
┌─────────────┐   END
│6. Process   │
│   Payment   │
└──────┬──────┘
       ↓
┌──────────────┐
│7. Complete   │
│   Check-In   │
└──────┬───────┘
       ↓
     END
```

### Detailed Steps

#### Step 1: Initiate Check-In
**Endpoint:** `POST /api/check-in`

**Request:**
```json
{
  "passengerId": 1,
  "flightNumber": "SH101"
}
```

**Processing:**
1. Validate passenger exists
2. Validate flight exists and is scheduled
3. Check for existing check-in
4. Create CheckIn entity with status IN_PROGRESS
5. Return check-in ID

**Response:**
```json
{
  "checkInId": 101,
  "status": "IN_PROGRESS",
  "message": "Check-in initiated successfully"
}
```

---

#### Step 2: Browse Seat Map
**Endpoint:** `GET /api/flights/{flightId}/seats`

**Processing:**
1. Check Redis cache for seat map
2. If cache miss: Query database
3. Filter by seat state (show only AVAILABLE to this user)
4. Cache result for 10 seconds
5. Return seat list with availability

**Response:**
```json
{
  "flightId": 1,
  "flightNumber": "SH101",
  "seats": [
    {"id": 1, "seatNumber": "12A", "state": "AVAILABLE", ...},
    {"id": 2, "seatNumber": "12B", "state": "HELD", ...}
  ],
  "availableSeats": 25,
  "heldSeats": 3,
  "confirmedSeats": 2
}
```

---

#### Step 3: Hold Seat
**Endpoint:** `POST /api/check-in/{checkInId}/hold-seat`

**Request:**
```json
{
  "seatId": 1,
  "passengerId": 1,
  "checkInId": 101
}
```

**Processing:**
1. Validate check-in status is IN_PROGRESS
2. **Redis Operation:** SETNX seat:hold:1 → passengerId (TTL: 120s)
3. If Redis succeeds:
   - **Database Transaction:**
     - Read seat with optimistic lock
     - Validate state is AVAILABLE
     - Update seat.state = HELD, version++
     - Create SeatReservation (holdExpiresAt = now + 120s)
     - Record SeatStateHistory
4. If Redis fails: Throw SeatAlreadyReservedException
5. Invalidate seat map cache
6. Return hold details with expiration time

**Response:**
```json
{
  "reservationId": 201,
  "seatId": 1,
  "seatNumber": "12A",
  "state": "HELD",
  "holdExpiresAt": "2026-03-08T14:32:00",
  "remainingSeconds": 120
}
```

**Error Scenarios:**
- Seat already held → 409 Conflict
- Optimistic lock failure → Retry 3x → 409 Conflict
- Seat not found → 404 Not Found

---

#### Step 4: Confirm Seat
**Endpoint:** `POST /api/check-in/{checkInId}/confirm-seat`

**Request:**
```json
{
  "seatId": 1,
  "passengerId": 1,
  "checkInId": 101
}
```

**Processing:**
1. Validate hold ownership in Redis
2. Verify hold not expired
3. **Database Transaction:**
   - Read seat with optimistic lock
   - Validate state is HELD
   - Update seat.state = CONFIRMED, version++
   - Update SeatReservation.status = CONFIRMED
   - Record SeatStateHistory
4. Delete Redis hold key (no longer needed)
5. Invalidate seat map cache

**Response:**
```json
{
  "seatId": 1,
  "seatNumber": "12A",
  "state": "CONFIRMED",
  "message": "Seat confirmed successfully"
}
```

**Error Scenarios:**
- Hold expired → 410 Gone
- Wrong passenger → 409 Conflict
- Seat state changed → 409 Conflict

---

## Baggage Validation and Payment Flow

### Flow Diagram

```
Add Baggage
    ↓
Weight ≤ 25kg?
    ╱      ╲
  YES       NO
   ↓         ↓
No Fee   Calculate Excess Fee
   ↓         ↓
Continue  Pause Check-In
          Status: WAITING_FOR_PAYMENT
              ↓
         Process Payment
              ↓
         Payment Success?
            ╱    ╲
          YES     NO
           ↓       ↓
        Resume   Retry
        Status:
     IN_PROGRESS
```

### Detailed Steps

#### Add Baggage
**Endpoint:** `POST /api/check-in/{checkInId}/baggage`

**Request:**
```json
{
  "checkInId": 101,
  "weightKg": 30.5
}
```

**Processing:**
1. Validate check-in exists and not completed
2. Calculate excess weight: `weight - 25kg`
3. If excess weight > 0:
   - Calculate fee: `excessWeight × $10/kg`
   - Set paymentStatus = PENDING
   - **Update CheckIn.status = WAITING_FOR_PAYMENT**
   - Return 402 Payment Required
4. If weight ≤ 25kg:
   - Set paymentStatus = NOT_REQUIRED
   - Return 200 OK

**Response (Payment Required):**
```json
{
  "baggageId": 301,
  "weightKg": 30.5,
  "excessWeightKg": 5.5,
  "excessFeeAmount": 55.00,
  "paymentStatus": "PENDING",
  "paymentRequired": true,
  "message": "Baggage exceeds limit by 5.50 kg. Fee: $55.00"
}
```

---

#### Process Payment
**Endpoint:** `POST /api/check-in/{checkInId}/payment`

**Request:**
```json
{
  "checkInId": 101,
  "amount": 55.00,
  "paymentMethod": "CREDIT_CARD"
}
```

**Processing:**
1. Validate check-in status is WAITING_FOR_PAYMENT
2. Retrieve baggage record
3. Verify payment amount ≥ excessFeeAmount
4. **Simulate Payment Gateway** (generate payment ID)
5. Update baggage.paymentStatus = COMPLETED
6. **Resume Check-In:** status = IN_PROGRESS
7. Return payment confirmation

**Response:**
```json
{
  "paymentId": "PAY-1234567890",
  "amount": 55.00,
  "status": "COMPLETED",
  "paidAt": "2026-03-08T14:35:00",
  "message": "Payment processed successfully"
}
```

---

## Seat Hold Expiration Flow

### Automatic Expiration

```
┌───────────────────────────────────────┐
│  Redis TTL Mechanism                  │
│                                       │
│  T=0s:  SETNX seat:hold:1 → passenger │
│         EXPIRE 120                    │
│                                       │
│  T=120s: Key automatically deleted    │
│                                       │
└───────────────────────────────────────┘
            ↓
┌───────────────────────────────────────┐
│  Scheduled Cleanup Job (every 30s)   │
│                                       │
│  1. Query: SELECT * FROM              │
│     seat_reservations WHERE           │
│     status = 'ACTIVE' AND             │
│     reservationType = 'HOLD' AND      │
│     holdExpiresAt < NOW()             │
│                                       │
│  2. For each expired hold:            │
│     - Update seat.state = AVAILABLE   │
│     - Update reservation.status =     │
│       EXPIRED                         │
│     - Record state change history     │
│     - Evict cache                     │
│                                       │
└───────────────────────────────────────┘
```

### Why Dual-Layer?

**Scenario 1: Normal Operation**
- Redis TTL expires at exactly 120s
- Key is deleted automatically
- Cleanup job finds expired holds and updates database

**Scenario 2: Redis Restart**
- All TTL keys lost
- Cleanup job detects expired holds in database
- Updates seat states correctly

**Scenario 3: Clock Skew**
- Small time differences between servers
- ±2 second tolerance
- Cleanup job provides consistency guarantee

---

## Concurrency Scenarios

### Scenario 1: Two Passengers Select Same Seat

```
Time    Passenger A              Redis                Passenger B
─────────────────────────────────────────────────────────────────
T0      holdSeat(12A)            SETNX seat:hold:1    holdSeat(12A)
T0+10ms   ↓                      SUCCESS              ↓
T0+15ms DB: seat.state=HELD      passengerId=A        SETNX seat:hold:1
T0+20ms   ↓                         ↓                 FAIL (key exists)
T0+25ms SUCCESS                     ↓                 409 CONFLICT
        ✅                          ↓                 ❌
```

**Result:** Only Passenger A succeeds. Passenger B receives clear error.

---

### Scenario 2: Optimistic Locking Conflict

```
Time    Transaction A            Database             Transaction B
─────────────────────────────────────────────────────────────────
T0      BEGIN                    seat(v=0)            BEGIN
T1      READ seat (v=0)          seat(v=0)            READ seat (v=0)
T2      seat.state = HELD        seat(v=0)            seat.state = HELD
T3      COMMIT                   seat(v=1) ✅         COMMIT
T4      SUCCESS                  seat(v=1)            ObjectOptimistic
                                                      LockingFailure ❌
T5                                                    RETRY
T6                                                    READ seat (v=1)
T7                                                    state=HELD
                                                      409 CONFLICT
```

**Result:** Transaction A commits. Transaction B retries, sees HELD state, returns conflict error.

---

## Database Schema

### Entity Relationship Diagram

```
┌─────────────────┐
│     Flight      │
│─────────────────│
│ PK id           │
│    flight_number│
│    departure    │
│    destination  │
└────────┬────────┘
         │
         │ 1:N
         │
┌────────┴────────┐
│      Seat       │
│─────────────────│
│ PK id           │
│ FK flight_id    │
│    seat_number  │
│    state        │◄────────────┐
│    version      │ (optimistic │
└────────┬────────┘  locking)   │
         │                      │
         │ 1:N                  │ 1:1
         │                      │
┌────────┴─────────┐     ┌──────┴──────────┐
│ SeatReservation  │     │  CheckIn        │
│──────────────────│     │─────────────────│
│ PK id            │     │ PK id           │
│ FK seat_id       │─────│ FK seat_id      │
│ FK check_in_id   │────►│ FK passenger_id │
│ FK passenger_id  │     │ FK flight_id    │
│    hold_expires  │     │    status       │
│    status        │     └────────┬────────┘
└──────────────────┘              │
                                  │ 1:1
                         ┌────────┴────────┐
                         │    Baggage      │
                         │─────────────────│
                         │ PK id           │
                         │ FK check_in_id  │
                         │    weight_kg    │
                         │    excess_fee   │
                         │    payment_stat │
                         └─────────────────┘

┌──────────────────┐
│   Passenger      │
│──────────────────│
│ PK id            │
│    first_name    │
│    last_name     │
│    email         │
└────────┬─────────┘
         │
         │ 1:N
         │
┌────────┴─────────────┐
│ SeatStateHistory     │
│──────────────────────│
│ PK id                │
│ FK seat_id           │
│ FK passenger_id      │
│    previous_state    │
│    new_state         │
│    reason            │
│    changed_at        │
└──────────────────────┘
```

### Key Relationships

1. **Flight → Seats (1:N)**
   - Each flight has multiple seats
   - Cascade delete: removing flight removes all seats

2. **CheckIn → Seat (N:1)**
   - Each check-in can have one seat
   - Multiple check-ins over time can reference same seat

3. **CheckIn → SeatReservation (1:N)**
   - Check-in may have multiple reservations (if passenger changes seat)
   - Only one reservation is ACTIVE at a time

4. **CheckIn → Baggage (1:1)**
   - Each check-in has at most one baggage record

5. **Seat → SeatStateHistory (1:N)**
   - Complete audit trail of all state changes

### Critical Indexes

```sql
-- High-frequency queries
CREATE INDEX idx_seats_flight_state ON seats(flight_id, state);
CREATE INDEX idx_reservations_expires ON seat_reservations(hold_expires_at);

-- Concurrency support
CREATE INDEX idx_seats_state ON seats(state);

-- Lookup queries
CREATE UNIQUE INDEX uq_flight_seat ON seats(flight_id, seat_number);
```

---

## Seat Hold and Confirmation Flow

### Detailed Sequence Diagram

```
Passenger    API          SeatService    Redis       Database
   │           │               │           │             │
   │──hold─────►│               │           │             │
   │           │──holdSeat()───►│           │             │
   │           │               │──SETNX────►│             │
   │           │               │           (TTL=120s)    │
   │           │               │◄──OK──────│             │
   │           │               │                         │
   │           │               │──BEGIN TRANSACTION─────►│
   │           │               │──SELECT seat (v=0)─────►│
   │           │               │◄──seat─────────────────│
   │           │               │                         │
   │           │               │──UPDATE seat.state─────►│
   │           │               │  SET state='HELD'       │
   │           │               │  WHERE version=0        │
   │           │               │                         │
   │           │               │──INSERT reservation────►│
   │           │               │──INSERT history────────►│
   │           │               │──COMMIT────────────────►│
   │           │               │◄──SUCCESS──────────────│
   │           │◄──reservation─│           │             │
   │◄──200 OK──│               │           │             │
   │           │               │           │             │
   │           │               │           │             │
   │ (wait)    │               │           │             │
   │ (user decides)            │           │             │
   │           │               │           │             │
   │──confirm──►│               │           │             │
   │           │──confirmSeat()─►           │             │
   │           │               │──VERIFY───►│             │
   │           │               │◄──VALID───│             │
   │           │               │                         │
   │           │               │──BEGIN TRANSACTION─────►│
   │           │               │──SELECT seat (v=1)─────►│
   │           │               │◄──seat─────────────────│
   │           │               │──UPDATE seat.state─────►│
   │           │               │  SET state='CONFIRMED'  │
   │           │               │  WHERE version=1        │
   │           │               │──UPDATE reservation────►│
   │           │               │──INSERT history────────►│
   │           │               │──COMMIT────────────────►│
   │           │               │                         │
   │           │               │──DEL hold key──────────►│
   │           │◄──confirmed───│           │             │
   │◄──200 OK──│               │           │             │
```

---

## Check-In State Transitions

### State Diagram

```
┌─────────────────┐
│  IN_PROGRESS    │◄────────┐
└────────┬────────┘          │
         │                   │
         │ addBaggage()      │ processPayment()
         │ weight > 25kg     │
         │                   │
         ↓                   │
┌────────────────────┐       │
│ WAITING_FOR_PAYMENT│───────┘
└────────┬───────────┘
         │
         │ completeCheckIn()
         │ (after payment + seat confirmed)
         ↓
┌─────────────────┐
│   COMPLETED     │ (final state)
└─────────────────┘
```

### Validation Matrix

| Action | IN_PROGRESS | WAITING_FOR_PAYMENT | COMPLETED |
|--------|-------------|---------------------|-----------|
| Hold Seat | ✅ | ❌ | ❌ |
| Confirm Seat | ✅ | ❌ | ❌ |
| Add Baggage | ✅ | ❌ | ❌ |
| Process Payment | ❌ | ✅ | ❌ |
| Complete Check-In | ✅* | ❌ | ❌ |

*Only if seat confirmed and no pending payment

---

## Error Handling Patterns

### Structured Error Response

```json
{
  "timestamp": "2026-03-08T14:30:00",
  "status": 409,
  "error": "Seat Already Reserved",
  "message": "Seat 12A is not available. Current state: HELD",
  "path": "/api/check-in/101/hold-seat"
}
```

### HTTP Status Codes

| Status | Use Case |
|--------|----------|
| 200 OK | Successful operation |
| 201 Created | Check-in initiated |
| 204 No Content | Check-in cancelled |
| 400 Bad Request | Invalid input |
| 402 Payment Required | Excess baggage fee pending |
| 404 Not Found | Resource doesn't exist |
| 409 Conflict | Seat already reserved, concurrent modification |
| 410 Gone | Seat hold expired |
| 500 Internal Error | Unexpected system error |

---

## Performance Optimizations

### 1. Seat Map Caching
- **Cache Key:** `seatMap::{flightId}`
- **TTL:** 10 seconds
- **Invalidation:** On any seat state change
- **Impact:** 90%+ cache hit rate → P95 < 100ms

### 2. Database Indexes
- Composite index on (flight_id, state)
- Covers most common query: "available seats for flight"
- Query time: ~10ms for 200 seats

### 3. Connection Pooling
- HikariCP with 20 max connections
- Connection timeout: 30s
- Prevents connection exhaustion

### 4. Redis Connection Pool
- Jedis pool: 20 active, 10 idle
- Timeout: 2s
- Fast fail on Redis unavailability

---

## Monitoring & Observability

### Key Metrics

1. **Seat Hold Metrics**
   - Hold creation rate
   - Hold expiration rate
   - Redis TTL accuracy

2. **Concurrency Metrics**
   - Optimistic lock retry rate
   - Conflict resolution time
   - Failed hold attempts

3. **Performance Metrics**
   - Seat map P95/P99 latency
   - API endpoint response times
   - Cache hit ratio

4. **Business Metrics**
   - Check-ins per minute
   - Seat utilization rate
   - Payment completion rate

### Logging Strategy

**Correlation ID:** Track requests across services
**Structured Logs:** JSON format for parsing
**Log Levels:**
- ERROR: System failures
- WARN: Business rule violations
- INFO: Key business events
- DEBUG: Detailed flow for troubleshooting

---

**Document Version:** 1.0  
**Last Updated:** March 8, 2026  
**Author:** Backend Engineering Team

