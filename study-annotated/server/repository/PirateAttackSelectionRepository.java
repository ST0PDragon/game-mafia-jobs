// [파일 역할] pirate_attack_selections 테이블 Repository. "이번 밤 마지막 선택"과 재시도 판별 조회.
// 원본 위치: server/src/main/java/com/doronyong/mafia/repository/PirateAttackSelectionRepository.java

package com.doronyong.mafia.repository; // Repository 패키지

import com.doronyong.mafia.domain.PirateAttackSelectionEntity; // 대상 엔티티
import java.util.Optional;                                     // 0~1줄 결과
import java.util.UUID;                                         // requestId 타입
import org.springframework.data.jpa.repository.JpaRepository;  // 기본 CRUD

public interface PirateAttackSelectionRepository
    extends JpaRepository<PirateAttackSelectionEntity, Long> {
    // 방 잠금 안에서 저장하므로 가장 큰 id가 그 밤의 마지막 선택이다.
    // findTop = 결과 중 첫 줄만(LIMIT 1). OrderByIdDesc = id 내림차순 → "가장 최근 선택 한 줄".
    // → WHERE room_id=? AND night_number=? ORDER BY id DESC LIMIT 1
    // (V3 SQL의 idx_pirate_attack_latest 인덱스가 이 쿼리를 빠르게 해 준다.)
    Optional<PirateAttackSelectionEntity> findTopByRoomIdAndNightNumberOrderByIdDesc(
        Long roomId, int nightNumber
    );

    // 같은 requestId로 이미 저장한 선택이 있는지(네트워크 재시도 판별).
    // → WHERE room_id=? AND actor_player_id=? AND request_id=?
    Optional<PirateAttackSelectionEntity> findByRoomIdAndActorPlayerIdAndRequestId(
        Long roomId, Long actorPlayerId, UUID requestId
    );
}
