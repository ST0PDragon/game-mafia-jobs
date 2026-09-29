// [파일 역할] room_actions 테이블 Repository. 재시도 판별, 중복 제출 검사, 사용 횟수 계산, 밤 판정용 조회.
// 원본 위치: server/src/main/java/com/doronyong/mafia/repository/RoomActionRepository.java

package com.doronyong.mafia.repository; // Repository 패키지

import com.doronyong.mafia.domain.RoomActionEntity; // 대상 엔티티
import com.doronyong.mafia.domain.RoomPhase;        // 단계 enum (조건에 사용)
import java.util.List;                              // 여러 줄 결과
import java.util.Optional;                          // 0~1줄 결과
import java.util.UUID;                              // requestId 타입
import org.springframework.data.jpa.repository.JpaRepository; // 기본 CRUD

public interface RoomActionRepository extends JpaRepository<RoomActionEntity, Long> {
    // 동일 requestId의 재시도인지 확인한다.
    // → WHERE room_id = ? AND actor_player_id = ? AND request_id = ?
    Optional<RoomActionEntity> findByRoomIdAndActorPlayerIdAndRequestId(
        Long roomId, Long actorPlayerId, UUID requestId
    );

    // 일반 능력은 같은 단계·라운드에 한 번만 제출한다. 해적의 공유 대상 변경은 별도 테이블에 저장한다.
    // → WHERE room_id=? AND actor_player_id=? AND phase=? AND round_number=? AND action_code=?
    // 이름이 길지만 조건 5개를 And로 이은 것뿐이다. 선의의 "지난 밤 자기 보호" 확인에도 재사용한다.
    Optional<RoomActionEntity> findByRoomIdAndActorPlayerIdAndPhaseAndRoundNumberAndActionCode(
        Long roomId, Long actorPlayerId, RoomPhase phase, int roundNumber, String actionCode
    );

    // countBy... → SELECT COUNT(*) ... 게임 전체에서 이 능력을 몇 번 썼는지(남은 횟수 계산용).
    // ⚠ 차단당한 밤의 제출도 한 번으로 센다(주정뱅이 2회 제한에 영향).
    long countByRoomIdAndActorPlayerIdAndActionCode(
        Long roomId, Long actorPlayerId, String actionCode
    );

    // 이번 밤에 제출된 행동 전부를 제출 순서(id 오름차순)대로. 밤 판정의 입력이다.
    // → WHERE room_id=? AND phase=? AND round_number=? ORDER BY id ASC
    List<RoomActionEntity> findByRoomIdAndPhaseAndRoundNumberOrderByIdAsc(
        Long roomId, RoomPhase phase, int roundNumber
    );
}
