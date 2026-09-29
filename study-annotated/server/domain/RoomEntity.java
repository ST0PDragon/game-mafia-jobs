// [파일 역할] rooms 테이블 = 게임 한 판. 현재 단계(phase), 라운드 번호, 승리 진영을 가진다.
//   단계 전환 메서드(finish, completeNight, beginNextNight)가 이 클래스 안에 있어서
//   "상태를 바꾸는 규칙"이 한곳에 모여 있다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/domain/RoomEntity.java

package com.doronyong.mafia.domain; // 도메인 패키지

import jakarta.persistence.Column;         // 컬럼 매핑
import jakarta.persistence.Entity;         // 엔티티 표시
import jakarta.persistence.EnumType;       // enum 저장 방식
import jakarta.persistence.Enumerated;     // enum 필드 표시
import jakarta.persistence.GeneratedValue; // PK 자동 생성
import jakarta.persistence.GenerationType; // PK 생성 전략
import jakarta.persistence.Id;             // PK 표시
import jakarta.persistence.Table;          // 테이블 이름 지정

/**
 * 게임 진행 상태. 공개 API의 gamesId는 현재 이 엔티티의 id이며 DB 테이블 이름은 rooms다.
 * Unity의 로컬 GameState와 달리 온라인 판정의 최종 상태는 서버가 소유한다.
 */
@Entity                 // JPA 엔티티
@Table(name = "rooms")  // rooms 테이블과 연결
public class RoomEntity {
    // 기본키. IDENTITY = DB가 INSERT할 때 1, 2, 3...을 자동으로 매긴다.
    // save()하기 전에는 null이고, 저장된 뒤에야 값이 생긴다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 현재 단계(SETUP/DAY/NIGHT/...). 문자열로 저장.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomPhase phase;

    // 첫 낮/첫 밤은 1이다. 낮이 끝날 때만 증가하고, 행동과 공격 대상의 라운드 키에 사용한다.
    // 이름은 night_number지만 실제로는 "라운드 번호"다. 증가는 beginNextNight(낮 → 다음 밤)에서만 일어난다.
    //   밤으로 시작하면: 밤 1 → 낮 1 → 밤 2 → 낮 2 ...
    // ⚠ 게임을 낮(DAY, 1)으로 시작하면 첫 밤은 beginNextNight를 거쳐 2가 된다.
    //   "첫 밤은 1"을 지키려면 방 진행 서비스를 만들 때 시작 단계를 정해 둬야 한다.
    @Column(name = "night_number", nullable = false)
    private int nightNumber;

    // 승리한 진영. 게임이 끝나기 전에는 null이라 nullable 기본값(true)을 그대로 쓴다.
    @Enumerated(EnumType.STRING)
    @Column(name = "winner_faction")
    private Faction winnerFaction;

    // JPA용 기본 생성자. 외부에서 빈 방을 만들지 못하게 protected.
    protected RoomEntity() {}

    // 새 방을 만들 때 쓰는 생성자. id는 DB가 정하므로 받지 않는다.
    // 테스트에서 new RoomEntity(RoomPhase.NIGHT, 1)처럼 "첫 밤 상태의 방"을 바로 만든다.
    public RoomEntity(RoomPhase phase, int nightNumber) {
        this.phase = phase;             // 시작 단계
        this.nightNumber = nightNumber; // 시작 라운드
    }

    // getter. setter가 없으므로 단계는 아래 세 메서드로만 바뀐다(잘못된 전환을 막는 장치).
    public Long getId() { return id; }
    public RoomPhase getPhase() { return phase; }
    public int getNightNumber() { return nightNumber; }
    public Faction getWinnerFaction() { return winnerFaction; }

    // 게임 종료. RoomOutcomeService.check가 승자를 찾았을 때 호출한다.
    public void finish(Faction winner) {
        // GAME_OVER 이후에는 새 행동을 받지 않는다(RoleService의 단계 검증).
        this.winnerFaction = winner;     // 승리 진영 기록
        this.phase = RoomPhase.GAME_OVER; // 단계를 종료로
        // 트랜잭션 안에서 필드만 바꾸면 JPA "변경 감지"가 커밋 때 UPDATE를 자동으로 보낸다(save 호출 불필요).
    }

    public void completeNight() {
        // 밤 판정이 끝나고 승자가 없을 때 NightResolutionService가 호출한다.
        this.phase = RoomPhase.DAY; // 밤 → 낮. 라운드 번호는 그대로 둔다.
    }

    public void beginNextNight() {
        // 밤 번호를 먼저 올려 지난 밤과 새 밤의 행동이 서로 다른 라운드에 저장되게 한다.
        if (phase != RoomPhase.DAY) {
            // 낮이 아닌데 호출하면 프로그램 버그이므로 예외를 던진다(HTTP 응답용 예외가 아님).
            throw new IllegalStateException("Only a day can advance to the next night");
        }
        this.nightNumber++;          // 라운드 +1
        this.phase = RoomPhase.NIGHT; // 낮 → 밤
    }
}
