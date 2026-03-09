package com.skyhigh.skycheck.repository;

import com.skyhigh.skycheck.entity.SeatStateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatStateHistoryRepository extends JpaRepository<SeatStateHistory, Long> {

    @Query("SELECT ssh FROM SeatStateHistory ssh WHERE ssh.seat.id = :seatId ORDER BY ssh.changedAt DESC")
    List<SeatStateHistory> findBySeatIdOrderByChangedAtDesc(@Param("seatId") Long seatId);
}

