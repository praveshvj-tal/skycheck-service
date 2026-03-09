//package com.skyhigh.skycheck.integration;
//
//import com.skyhigh.skycheck.entity.*;
//import com.skyhigh.skycheck.exception.InvalidSeatStateException;
//import com.skyhigh.skycheck.exception.SeatAlreadyReservedException;
//import com.skyhigh.skycheck.repository.*;
//import com.skyhigh.skycheck.service.CheckInService;
//import com.skyhigh.skycheck.service.SeatService;
//import com.skyhigh.skycheck.service.SeatHoldManager;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.test.context.ActiveProfiles;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicBoolean;
//import java.util.concurrent.atomic.AtomicInteger;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.anyLong;
//import static org.mockito.Mockito.*;
//
///**
// * Integration test for concurrent seat reservation
// * Tests that only one passenger can successfully reserve a seat when multiple try simultaneously
// */
//@SpringBootTest
//@ActiveProfiles("test")
//class ConcurrentSeatReservationTest {
//
//    @Autowired
//    private SeatService seatService;
//
//    @MockBean
//    private SeatHoldManager seatHoldManager;
//
//    @Autowired
//    private CheckInService checkInService;
//
//    @Autowired
//    private FlightRepository flightRepository;
//
//    @Autowired
//    private SeatRepository seatRepository;
//
//    @Autowired
//    private PassengerRepository passengerRepository;
//
//    @Autowired
//    private CheckInRepository checkInRepository;
//
//    @Autowired
//    private SeatReservationRepository reservationRepository;
//
//    @Autowired
//    private SeatStateHistoryRepository seatStateHistoryRepository;
//
//    private Flight testFlight;
//    private Seat testSeat;
//    private List<Passenger> testPassengers;
//    private List<CheckIn> testCheckIns;
//    private AtomicBoolean firstHoldGranted;
//
//    private java.util.concurrent.ConcurrentMap<Long, Long> heldSeats;
//
//    @BeforeEach
//    void setUp() {
//        heldSeats = new java.util.concurrent.ConcurrentHashMap<>();
//        when(seatHoldManager.createHold(anyLong(), anyLong()))
//                .thenAnswer(invocation -> {
//                    Long seatId = invocation.getArgument(0);
//                    Long passengerId = invocation.getArgument(1);
//                    Long existing = heldSeats.putIfAbsent(seatId, passengerId);
//                    return existing == null || existing.equals(passengerId);
//                });
//        when(seatHoldManager.verifyHoldOwnership(anyLong(), anyLong()))
//                .thenAnswer(invocation -> {
//                    Long seatId = invocation.getArgument(0);
//                    Long passengerId = invocation.getArgument(1);
//                    return passengerId.equals(heldSeats.get(seatId));
//                });
//        when(seatHoldManager.isHeld(anyLong())).thenAnswer(invocation -> {
//            Long seatId = invocation.getArgument(0);
//            return heldSeats.containsKey(seatId);
//        });
//
//        // Clean up
//        reservationRepository.deleteAll();
//        checkInRepository.deleteAll();
//        seatStateHistoryRepository.deleteAll();
//        seatRepository.deleteAll();
//        passengerRepository.deleteAll();
//        flightRepository.deleteAll();
//
//        // Create test flight
//        testFlight = Flight.builder()
//                .flightNumber("TCN001")
//                .departureTime(LocalDateTime.now().plusHours(6))
//                .arrivalTime(LocalDateTime.now().plusHours(9))
//                .origin("JFK")
//                .destination("LAX")
//                .aircraftType("Boeing 737")
//                .status(Flight.FlightStatus.SCHEDULED)
//                .build();
//        testFlight = flightRepository.save(testFlight);
//
//        // Create single seat
//        testSeat = Seat.builder()
//                .flight(testFlight)
//                .seatNumber("12A")
//                .seatType(Seat.SeatType.WINDOW)
//                .seatClass(Seat.SeatClass.ECONOMY)
//                .state(Seat.SeatState.AVAILABLE)
//                .build();
//        testSeat = seatRepository.save(testSeat);
//
//        // Create multiple passengers
//        testPassengers = new ArrayList<>();
//        testCheckIns = new ArrayList<>();
//
//        for (int i = 0; i < 10; i++) {
//            Passenger passenger = Passenger.builder()
//                    .firstName("Passenger")
//                    .lastName("" + i)
//                    .email("passenger" + i + "@example.com")
//                    .build();
//            passenger = passengerRepository.save(passenger);
//            testPassengers.add(passenger);
//
//            CheckIn checkIn = CheckIn.builder()
//                    .passenger(passenger)
//                    .flight(testFlight)
//                    .status(CheckIn.CheckInStatus.IN_PROGRESS)
//                    .checkInTime(LocalDateTime.now())
//                    .build();
//            checkIn = checkInRepository.save(checkIn);
//            testCheckIns.add(checkIn);
//        }
//    }
//
//    @Test
//    void testConcurrentSeatReservation_OnlyOneSucceeds() throws InterruptedException {
//        // Arrange
//        int threadCount = 10;
//        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
//        CountDownLatch startLatch = new CountDownLatch(1);
//        CountDownLatch doneLatch = new CountDownLatch(threadCount);
//
//        AtomicInteger successCount = new AtomicInteger(0);
//        AtomicInteger failureCount = new AtomicInteger(0);
//        List<Exception> exceptions = new CopyOnWriteArrayList<>();
//
//        // Act - All threads try to hold the same seat simultaneously
//        for (int i = 0; i < threadCount; i++) {
//            final int index = i;
//            executorService.submit(() -> {
//                try {
//                    // Wait for all threads to be ready
//                    startLatch.await();
//
//                    // Try to hold the seat
//                    CheckIn checkIn = testCheckIns.get(index);
//                    seatService.holdSeat(testSeat, checkIn);
//                    successCount.incrementAndGet();
//
//                } catch (SeatAlreadyReservedException |
//                         org.springframework.orm.ObjectOptimisticLockingFailureException e) {
//                    failureCount.incrementAndGet();
//                    exceptions.add(e);
//                } catch (Exception e) {
//                    exceptions.add(e);
//                } finally {
//                    doneLatch.countDown();
//                }
//            });
//        }
//
//        // Start all threads at once
//        startLatch.countDown();
//
//        // Wait for all threads to complete (max 10 seconds)
//        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
//        executorService.shutdown();
//
//        // Assert
//        assertTrue(completed, "All threads should complete");
//
//        // Exactly one reservation should succeed
//        assertEquals(1, successCount.get(),
//                "Exactly one thread should successfully hold the seat");
//        assertEquals(threadCount - 1, failureCount.get(),
//                "Remaining threads should fail");
//
//        // Verify seat state
//        Seat finalSeat = seatRepository.findById(testSeat.getId()).orElseThrow();
//        assertEquals(Seat.SeatState.HELD, finalSeat.getState());
//
//        // Verify only one reservation exists
//        List<SeatReservation> reservations = reservationRepository.findAll();
//        long activeReservations = reservations.stream()
//                .filter(r -> r.getStatus() == SeatReservation.ReservationStatus.ACTIVE)
//                .count();
//        assertEquals(1, activeReservations, "Only one active reservation should exist");
//    }
//
//    @Test
//    void testSequentialSeatOperations_FullWorkflow() {
//        // Arrange
//        CheckIn checkIn = testCheckIns.get(0);
//
//        // Act & Assert - Hold seat
//        SeatReservation holdReservation = seatService.holdSeat(testSeat, checkIn);
//        assertNotNull(holdReservation);
//
//        Seat heldSeat = seatRepository.findById(testSeat.getId()).orElseThrow();
//        assertEquals(Seat.SeatState.HELD, heldSeat.getState());
//
//        // Confirm seat
//        SeatReservation confirmReservation = seatService.confirmSeat(heldSeat, checkIn);
//        assertNotNull(confirmReservation);
//
//        Seat confirmedSeat = seatRepository.findById(testSeat.getId()).orElseThrow();
//        assertEquals(Seat.SeatState.CONFIRMED, confirmedSeat.getState());
//
//        // Try to release confirmed seat - should fail
//        assertThrows(InvalidSeatStateException.class,
//                () -> seatService.releaseSeat(confirmedSeat.getId(), checkIn.getPassenger().getId(), "Test"));
//    }
//}
