// [파일 역할] room_players 테이블 Repository. "이 방에서 나는 누구인가", "대상은 누구인가", "참가자 전원" 조회.
// 원본 위치: server/src/main/java/com/doronyong/mafia/repository/RoomPlayerRepository.java

package com.doronyong.mafia.repository; // Repository 패키지

import com.doronyong.mafia.domain.RoomPlayerEntity; // 대상 엔티티
import java.util.List;                              // 여러 줄 결과
import java.util.Optional;                          // 0~1줄 결과
import org.springframework.data.jpa.repository.JpaRepository; // 기본 CRUD

public interface RoomPlayerRepository extends JpaRepository<RoomPlayerEntity, Long> {
    // 계정(userId)으로 내 참가 정보 찾기. JWT 사용자가 이 방 참가자인지 확인할 때 쓴다.
    // → WHERE room_id = ? AND user_id = ?
    Optional<RoomPlayerEntity> findByRoomIdAndUserId(Long roomId, Long userId);
    // 방 안 번호(playerId)로 찾기. 행동 대상 확인용.
    // → WHERE room_id = ? AND player_id = ?
    Optional<RoomPlayerEntity> findByRoomIdAndPlayerId(Long roomId, Long playerId);
    // 방 참가자 전원을 번호 오름차순으로. 밤 판정, 공개 보고 전체 발송, 승리 계산에 쓴다.
    // → WHERE room_id = ? ORDER BY player_id ASC
    List<RoomPlayerEntity> findByRoomIdOrderByPlayerIdAsc(Long roomId);
}
