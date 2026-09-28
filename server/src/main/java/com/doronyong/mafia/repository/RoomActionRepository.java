package com.doronyong.mafia.repository;

import com.doronyong.mafia.domain.RoomActionEntity;
import com.doronyong.mafia.domain.RoomPhase;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomActionRepository extends JpaRepository<RoomActionEntity, Long> {
    // 동일 requestId의 재시도인지 확인한다.
    Optional<RoomActionEntity> findByRoomIdAndActorPlayerIdAndRequestId(
        Long roomId, Long actorPlayerId, UUID requestId
    );

    // 앵무새는 WATCH_ACTION과 TEAM_ATTACK_VOTE를 같은 밤에 각각 한 번씩 제출할 수 있다.
    Optional<RoomActionEntity> findByRoomIdAndActorPlayerIdAndPhaseAndRoundNumberAndActionCode(
        Long roomId, Long actorPlayerId, RoomPhase phase, int roundNumber, String actionCode
    );

    long countByRoomIdAndActorPlayerIdAndActionCode(
        Long roomId, Long actorPlayerId, String actionCode
    );

    List<RoomActionEntity> findByRoomIdAndPhaseAndRoundNumberOrderByIdAsc(
        Long roomId, RoomPhase phase, int roundNumber
    );
}
