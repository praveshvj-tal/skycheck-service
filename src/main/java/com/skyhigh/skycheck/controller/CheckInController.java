package com.skyhigh.skycheck.controller;

import com.skyhigh.skycheck.dto.*;
import com.skyhigh.skycheck.entity.*;
import com.skyhigh.skycheck.mapper.DtoMapper;
import com.skyhigh.skycheck.service.CheckInService;
import com.skyhigh.skycheck.service.SeatHoldManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/check-in")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Check-In", description = "APIs for passenger check-in workflow")
public class CheckInController {

    private final CheckInService checkInService;
    private final SeatHoldManager seatHoldManager;
    private final DtoMapper mapper;

    @PostMapping
    @Operation(
        summary = "Initiate check-in",
        description = "Start the check-in process for a passenger on a specific flight"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Check-in initiated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Passenger or flight not found")
    })
    public ResponseEntity<CheckInResponse> initiateCheckIn(
            @Valid @RequestBody CheckInRequest request) {

        log.info("Initiating check-in for passenger {} on flight {}",
                request.getPassengerId(), request.getFlightNumber());

        CheckIn checkIn = checkInService.initiateCheckIn(
                request.getPassengerId(),
                request.getFlightNumber()
        );

        CheckInResponse response = mapper.toCheckInResponse(checkIn);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{checkInId}/hold-seat")
    @Operation(
        summary = "Hold a seat",
        description = "Reserve a seat for 120 seconds during check-in. " +
                     "The seat will automatically expire if not confirmed within the time window."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Seat held successfully"),
        @ApiResponse(responseCode = "404", description = "Check-in or seat not found"),
        @ApiResponse(responseCode = "409", description = "Seat already reserved by another passenger")
    })
    public ResponseEntity<SeatHoldResponse> holdSeat(
            @Parameter(description = "Check-in ID", required = true)
            @PathVariable Long checkInId,
            @Valid @RequestBody SeatHoldRequest request) {

        log.info("Holding seat {} for check-in {}", request.getSeatId(), checkInId);

        SeatReservation reservation = checkInService.holdSeat(checkInId, request.getSeatId());

        Long remainingTtl = seatHoldManager.getRemainingTtl(request.getSeatId());
        SeatHoldResponse response = mapper.toSeatHoldResponse(reservation, remainingTtl);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{checkInId}/confirm-seat")
    @Operation(
        summary = "Confirm seat reservation",
        description = "Permanently assign the held seat to the passenger. " +
                     "Seat hold must still be active and belong to the passenger."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Seat confirmed successfully"),
        @ApiResponse(responseCode = "404", description = "Check-in or seat not found"),
        @ApiResponse(responseCode = "409", description = "Invalid seat state or hold expired"),
        @ApiResponse(responseCode = "410", description = "Seat hold expired")
    })
    public ResponseEntity<SeatHoldResponse> confirmSeat(
            @Parameter(description = "Check-in ID", required = true)
            @PathVariable Long checkInId,
            @Valid @RequestBody SeatConfirmRequest request) {

        log.info("Confirming seat {} for check-in {}", request.getSeatId(), checkInId);

        SeatReservation reservation = checkInService.confirmSeat(checkInId, request.getSeatId());

        SeatHoldResponse response = SeatHoldResponse.builder()
                .reservationId(reservation.getId())
                .seatId(reservation.getSeat().getId())
                .seatNumber(reservation.getSeat().getSeatNumber())
                .state(reservation.getSeat().getState().name())
                .holdExpiresAt(null)
                .remainingSeconds(0)
                .message("Seat confirmed successfully")
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{checkInId}/baggage")
    @Operation(
        summary = "Add baggage to check-in",
        description = "Add baggage information to check-in. " +
                     "If weight exceeds 25kg, check-in will pause for payment."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Baggage added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid baggage weight"),
        @ApiResponse(responseCode = "404", description = "Check-in not found"),
        @ApiResponse(responseCode = "402", description = "Payment required for excess baggage")
    })
    public ResponseEntity<BaggageResponse> addBaggage(
            @Parameter(description = "Check-in ID", required = true)
            @PathVariable Long checkInId,
            @Valid @RequestBody BaggageRequest request) {

        log.info("Adding baggage to check-in {}", checkInId);

        Baggage baggage = checkInService.addBaggage(checkInId, request.getWeightKg());

        BaggageResponse response = mapper.toBaggageResponse(baggage);

        // Return 402 if payment required
        if (baggage.isPaymentRequired()) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{checkInId}/payment")
    @Operation(
        summary = "Process baggage payment",
        description = "Process payment for excess baggage fees and resume check-in"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payment processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid payment amount"),
        @ApiResponse(responseCode = "404", description = "Check-in not found")
    })
    public ResponseEntity<PaymentResponse> processPayment(
            @Parameter(description = "Check-in ID", required = true)
            @PathVariable Long checkInId,
            @Valid @RequestBody PaymentRequest request) {

        log.info("Processing payment for check-in {}", checkInId);

        Baggage baggage = checkInService.processPayment(
                checkInId,
                request.getAmount(),
                request.getPaymentMethod()
        );

        PaymentResponse response = mapper.toPaymentResponse(baggage);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{checkInId}/complete")
    @Operation(
        summary = "Complete check-in",
        description = "Finalize the check-in process. " +
                     "All requirements (seat confirmation, payment) must be completed."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Check-in completed successfully"),
        @ApiResponse(responseCode = "400", description = "Check-in requirements not met"),
        @ApiResponse(responseCode = "402", description = "Payment required"),
        @ApiResponse(responseCode = "404", description = "Check-in not found")
    })
    public ResponseEntity<CheckInResponse> completeCheckIn(
            @Parameter(description = "Check-in ID", required = true)
            @PathVariable Long checkInId) {

        log.info("Completing check-in {}", checkInId);

        CheckIn checkIn = checkInService.completeCheckIn(checkInId);

        CheckInResponse response = mapper.toCheckInResponse(checkIn);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{checkInId}")
    @Operation(
        summary = "Get check-in status",
        description = "Retrieve the current status and details of a check-in"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Check-in retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Check-in not found")
    })
    public ResponseEntity<CheckInResponse> getCheckInStatus(
            @Parameter(description = "Check-in ID", required = true)
            @PathVariable Long checkInId) {

        log.info("Fetching check-in status for {}", checkInId);

        CheckIn checkIn = checkInService.getCheckInById(checkInId);
        CheckInResponse response = mapper.toCheckInResponse(checkIn);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{checkInId}")
    @Operation(
        summary = "Cancel check-in",
        description = "Cancel a check-in and release any held seats"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Check-in cancelled successfully"),
        @ApiResponse(responseCode = "404", description = "Check-in not found")
    })
    public ResponseEntity<Void> cancelCheckIn(
            @Parameter(description = "Check-in ID", required = true)
            @PathVariable Long checkInId) {

        log.info("Cancelling check-in {}", checkInId);

        checkInService.cancelCheckIn(checkInId);

        return ResponseEntity.noContent().build();
    }
}

