// [파일 역할] pirate_attack_selections 테이블 = 해적 팀이 밤에 고른 공격 대상의 "변경 이력".
//   해적은 밤 동안 대상을 여러 번 바꿀 수 있으므로 한 번만 허용하는 room_actions 대신 여기에 한 줄씩 쌓는다.
//   그 밤의 **가장 마지막 줄(id가 가장 큰 줄)** 이 최종 공격 대상이다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/domain/PirateAttackSelectionEntity.java

package com.doronyong.mafia.domain; // 도메인 패키지

import jakarta.persistence.Column;           // 컬럼 매핑
import jakarta.persistence.Entity;           // 엔티티 표시
import jakarta.persistence.GeneratedValue;   // PK 자동 생성
import jakarta.persistence.GenerationType;   // PK 생성 전략
import jakarta.persistence.Id;               // PK 표시
import jakarta.persistence.Table;            // 테이블 설정
import jakarta.persistence.UniqueConstraint; // 유니크 제약
import java.util.UUID;                       // requestId 타입

/** 해적이 밤에 고른 공유 공격 대상의 변경 이력. 이번 밤의 마지막 행이 최종 선택이다. */
@Entity
@Table(name = "pirate_attack_selections", uniqueConstraints = {
    // 재시도 판별용 제약만 있다. "밤마다 한 번" 제약은 일부러 없다(대상 변경을 허용하기 위해).
    @UniqueConstraint(columnNames = {"room_id", "actor_player_id", "request_id"})
})
public class PirateAttackSelectionEntity {
    // 증가하는 PK. 방 잠금 안에서만 저장되므로 id 순서 = 선택 순서.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 방
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    // 몇 번째 밤의 선택인지
    @Column(name = "night_number", nullable = false)
    private int nightNumber;

    // 선택한 해적의 playerId (응답의 selectedByPlayerId)
    @Column(name = "actor_player_id", nullable = false)
    private Long actorPlayerId;

    // 재시도 판별용 요청 ID
    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    // 공격 대상 playerId
    @Column(name = "target_player_id", nullable = false)
    private Long targetPlayerId;

    // JPA용 기본 생성자
    protected PirateAttackSelectionEntity() {}

    // 새 선택 한 줄 생성
    public PirateAttackSelectionEntity(Long roomId, int nightNumber, Long actorPlayerId,
                                       UUID requestId, Long targetPlayerId) {
        this.roomId = roomId;
        this.nightNumber = nightNumber;
        this.actorPlayerId = actorPlayerId;
        this.requestId = requestId;
        this.targetPlayerId = targetPlayerId;
    }

    // getter들 (이력이므로 수정 메서드는 없다)
    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public int getNightNumber() { return nightNumber; }
    public Long getActorPlayerId() { return actorPlayerId; }
    public Long getTargetPlayerId() { return targetPlayerId; }
}
