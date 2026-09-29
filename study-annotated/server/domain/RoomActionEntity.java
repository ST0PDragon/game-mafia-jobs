// [파일 역할] room_actions 테이블 = 플레이어가 제출한 능력 사용 기록 한 건.
//   제출 시점에는 "기록"만 하고, 밤 능력의 실제 효과는 NightResolutionService가 나중에 적용한다.
//   (해적의 공격 대상 선택만 예외로 PirateAttackSelectionEntity에 따로 저장한다.)
// 원본 위치: server/src/main/java/com/doronyong/mafia/domain/RoomActionEntity.java

package com.doronyong.mafia.domain; // 도메인 패키지

import jakarta.persistence.Column;           // 컬럼 매핑
import jakarta.persistence.Entity;           // 엔티티 표시
import jakarta.persistence.GeneratedValue;   // PK 자동 생성
import jakarta.persistence.GenerationType;   // PK 생성 전략
import jakarta.persistence.Id;               // PK 표시
import jakarta.persistence.Table;            // 테이블 설정
import jakarta.persistence.UniqueConstraint; // 유니크 제약
import java.util.UUID;                       // 128비트 고유 ID 타입 (requestId용)

/** 낮/밤에 접수한 행동. 밤 행동의 효과는 밤 종료 판정기가 적용한다. */
@Entity
@Table(name = "room_actions", uniqueConstraints = {
    // request_id는 통신 재시도용, 두 번째 제약은 단계·라운드·능력별 중복 방지용이다.
    // 해적 공격 대상은 밤 동안 바꿀 수 있으므로 이 테이블 대신 pirate_attack_selections에 저장한다.
    // ① 같은 사람이 같은 requestId를 두 번 저장할 수 없다 → 재시도가 두 번 반영되는 것을 DB가 최종 차단.
    @UniqueConstraint(columnNames = {"room_id", "actor_player_id", "request_id"}),
    // ② 같은 사람이 같은 단계·라운드에 같은 능력을 두 번 낼 수 없다 → "밤마다 1명" 규칙을 DB가 보장.
    @UniqueConstraint(columnNames = {"room_id", "actor_player_id", "phase", "round_number", "action_code"})
})
public class RoomActionEntity {
    // 행동 PK. 응답의 actionId로 나간다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어느 방의 행동인지
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    // 행동한 사람의 playerId
    @Column(name = "actor_player_id", nullable = false)
    private Long actorPlayerId;

    // 제출된 단계(DAY/NIGHT). import 없이 전체 이름(jakarta.persistence.Enumerated)으로 적었다. 의미는 다른 파일과 같다.
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private RoomPhase phase;

    // 제출 당시 라운드 번호(= RoomEntity.nightNumber)
    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    // 클라이언트가 만든 요청 고유번호. 재시도 판별용.
    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    // 능력 코드 문자열(ActionCode 이름). enum이 아니라 String인 이유: 옛 코드(TEAM_ATTACK_VOTE)도 읽기 위해.
    @Column(name = "action_code", nullable = false)
    private String actionCode;

    // 대상의 playerId
    @Column(name = "target_player_id", nullable = false)
    private Long targetPlayerId;

    // 처리 상태: "SUBMITTED"(접수) → "RESOLVED"(판정 완료)
    @Column(nullable = false)
    private String status;

    // JPA용 기본 생성자
    protected RoomActionEntity() {}

    // 새 행동 접수. 상태는 항상 SUBMITTED로 시작한다.
    public RoomActionEntity(Long roomId, Long actorPlayerId, RoomPhase phase, int roundNumber,
                            UUID requestId, String actionCode, Long targetPlayerId) {
        this.roomId = roomId;
        this.actorPlayerId = actorPlayerId;
        this.phase = phase;
        this.roundNumber = roundNumber;
        this.requestId = requestId;
        this.actionCode = actionCode;
        this.targetPlayerId = targetPlayerId;
        this.status = "SUBMITTED"; // 아직 효과 없음
    }

    // getter들 (requestId getter는 없다: 조회는 Repository 쿼리로 한다)
    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public Long getActorPlayerId() { return actorPlayerId; }
    public RoomPhase getPhase() { return phase; }
    public int getRoundNumber() { return roundNumber; }
    public String getActionCode() { return actionCode; }
    public Long getTargetPlayerId() { return targetPlayerId; }
    public String getStatus() { return status; }

    // 판정 완료 표시. 밤 판정 끝 또는 포수 사격 직후 호출된다.
    public void resolve() { this.status = "RESOLVED"; }
}
