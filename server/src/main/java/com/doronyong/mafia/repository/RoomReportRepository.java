package com.doronyong.mafia.repository;

import com.doronyong.mafia.domain.RoomReportEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomReportRepository extends JpaRepository<RoomReportEntity, Long> {
    List<RoomReportEntity> findByRoomIdAndRecipientPlayerIdOrderByIdAsc(
        Long roomId, Long recipientPlayerId
    );
}
