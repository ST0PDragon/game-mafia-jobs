// [파일 역할] room_reports 테이블 = 특정 플레이어에게 전달할 결과 메시지 한 건.
//   조사 결과처럼 비밀인 정보는 그 사람 앞으로만, 사망 소식처럼 공개 정보는 참가자 전원 앞으로 한 줄씩 저장한다.
//   GET /me/reports는 "내 앞으로 온 줄"만 조회하므로 남의 조사 결과를 볼 수 없다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/domain/RoomReportEntity.java

package com.doronyong.mafia.domain; // 도메인 패키지

import jakarta.persistence.Column;         // 컬럼 매핑
import jakarta.persistence.Entity;         // 엔티티 표시
import jakarta.persistence.GeneratedValue; // PK 자동 생성
import jakarta.persistence.GenerationType; // PK 생성 전략
import jakarta.persistence.Id;             // PK 표시
import jakarta.persistence.Table;          // 테이블 이름

/** 조사 결과와 공개 사건을 수신자별로 저장한다. 다른 참가자의 비공개 보고는 조회할 수 없다. */
@Entity
@Table(name = "room_reports")
public class RoomReportEntity {
    // PK. 조회할 때 id 순으로 정렬하면 발생 순서가 된다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 방
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    // 받는 사람의 playerId. 이 값으로 "내 보고서"를 거른다.
    @Column(name = "recipient_player_id", nullable = false)
    private Long recipientPlayerId;

    // 몇 라운드에 생긴 일인지
    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    // 종류: FACTION_RESULT, VISITORS, WATCHED_ACTION, CORPSE_ROLE, NIGHT_DEATH, DAY_SHOOT
    // Unity는 이 값으로 아이콘/문구를 나눠 보여 줄 수 있다.
    @Column(nullable = false)
    private String type;

    // 사람이 읽는 메시지(최대 500자). 예: "3번의 진영: PIRATE"
    @Column(nullable = false, length = 500)
    private String message;

    // JPA용 기본 생성자
    protected RoomReportEntity() {}

    // 새 보고 생성
    public RoomReportEntity(Long roomId, Long recipientPlayerId, int roundNumber,
                            String type, String message) {
        this.roomId = roomId;
        this.recipientPlayerId = recipientPlayerId;
        this.roundNumber = roundNumber;
        this.type = type;
        this.message = message;
    }

    // 응답(ReportView)에 필요한 값만 getter로 공개한다. roomId/recipient는 밖으로 내보낼 필요가 없다.
    public Long getId() { return id; }
    public int getRoundNumber() { return roundNumber; }
    public String getType() { return type; }
    public String getMessage() { return message; }
}
