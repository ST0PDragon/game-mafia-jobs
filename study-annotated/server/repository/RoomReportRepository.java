// [파일 역할] room_reports 테이블 Repository. "내 앞으로 온 보고서"만 꺼내는 쿼리 하나가 전부다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/repository/RoomReportRepository.java

package com.doronyong.mafia.repository; // Repository 패키지

import com.doronyong.mafia.domain.RoomReportEntity; // 대상 엔티티
import java.util.List;                              // 여러 줄 결과
import org.springframework.data.jpa.repository.JpaRepository; // 기본 CRUD (save는 여기서 온다)

public interface RoomReportRepository extends JpaRepository<RoomReportEntity, Long> {
    // → WHERE room_id=? AND recipient_player_id=? ORDER BY id ASC (발생 순서대로)
    // 수신자 조건이 있어서 다른 사람의 비밀 조사 결과가 섞일 수 없다.
    // ⚠ (room_id, recipient_player_id) 인덱스가 없어 보고가 많아지면 느려질 수 있다.
    List<RoomReportEntity> findByRoomIdAndRecipientPlayerIdOrderByIdAsc(
        Long roomId, Long recipientPlayerId
    );
}
