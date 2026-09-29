// [파일 역할] room_players 테이블 = "어느 방의 몇 번 플레이어가 누구(계정)이고, 어떤 직업이며, 살아 있는가".
//   원숭이의 위장 직업도 여기서 정한다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/domain/RoomPlayerEntity.java

package com.doronyong.mafia.domain; // 도메인 패키지

import jakarta.persistence.Column;           // 컬럼 매핑
import jakarta.persistence.Entity;           // 엔티티 표시
import jakarta.persistence.GeneratedValue;   // PK 자동 생성
import jakarta.persistence.GenerationType;   // PK 생성 전략
import jakarta.persistence.Id;               // PK 표시
import jakarta.persistence.Table;            // 테이블 설정
import jakarta.persistence.UniqueConstraint; // 유니크 제약 선언

/** 방 참가자 한 명의 배정 직업, 보여 줄 직업, 생존 상태. 능력 사용 횟수는 행동 기록에서 센다. */
@Entity
@Table(name = "room_players", uniqueConstraints = {
    // 한 방에 같은 계정이 두 번 들어갈 수 없다.
    @UniqueConstraint(columnNames = {"room_id", "user_id"}),
    // 한 방에 같은 플레이어 번호가 두 개 있을 수 없다.
    // (실제 제약은 V1 SQL이 만든다. 여기 선언은 문서 역할 + ddl-auto로 테이블을 만들 때 쓰인다.)
    @UniqueConstraint(columnNames = {"room_id", "player_id"})
})
public class RoomPlayerEntity {
    // 이 행 자체의 PK(내부용). 게임 규칙에서는 거의 안 쓰고 playerId를 쓴다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어느 방인지. 객체 연관관계(@ManyToOne) 대신 숫자 id만 들고 있는 단순한 방식.
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    // 계정 번호. JWT의 sub와 같은 값.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // userId는 계정 식별자, playerId는 방 안의 번호다. targetPlayerId에는 후자를 사용한다.
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    // 입장 직후 SETUP 단계에서는 아직 배정 전이므로 null일 수 있다.
    // 실제 직업 코드(원숭이면 "CREW_MONKEY"). 규칙 판정은 항상 이 값을 쓴다.
    @Column(name = "role_code")
    private String roleCode;

    // 원숭이에게만 실제 직업과 다른 선원 직업을 보여 준다.
    // 원숭이가 아니면 null. 본인 화면과 "쓸 수 있는 능력" 결정에 쓰인다.
    @Column(name = "shown_role_code")
    private String shownRoleCode;

    // 생존 여부. kill()로만 false가 된다.
    @Column(nullable = false)
    private boolean alive;

    // JPA용 기본 생성자.
    protected RoomPlayerEntity() {}

    // 참가자 생성. 직업 배정 로직(assignRole)을 생성자에서도 재사용한다.
    public RoomPlayerEntity(Long roomId, Long userId, Long playerId,
                            String roleCode, boolean alive) {
        this.roomId = roomId;     // 방
        this.userId = userId;     // 계정
        this.playerId = playerId; // 방 안 번호 (assignRole보다 먼저 넣어야 한다: 위장 계산에 쓰임)
        this.alive = alive;       // 생존 여부(테스트에선 이미 죽은 플레이어도 만든다)
        assignRole(roleCode);     // 직업 + 위장 직업 설정
    }

    // getter들
    public Long getRoomId() { return roomId; }
    public Long getUserId() { return userId; }
    public Long getPlayerId() { return playerId; }
    public String getRoleCode() { return roleCode; } // 실제 직업
    // 보여 줄 직업: 위장 직업이 있으면 그것, 없으면 실제 직업. 원숭이가 아니면 roleCode와 같다.
    public String getShownRoleCode() { return shownRoleCode == null ? roleCode : shownRoleCode; }
    public boolean isAlive() { return alive; }

    public void assignRole(String roleCode) {
        // 배정 서비스는 호출 전 활성 직업인지 검증하고, SETUP에서 참가자 전원을 함께 배정해야 한다.
        // 원숭이는 실제 CREW_MONKEY를 유지하되 비공개 행동만 있는 직업으로 위장한다.
        // playerId로 위장 직업을 결정하므로 배정 후 playerId를 바꾸면 안 된다.
        this.roleCode = roleCode; // 실제 직업 저장
        // 원숭이가 위장할 수 있는 후보 4개(모두 밤 능력이 있는 선원 직업).
        String[] disguises = {"CREW_CAPTAIN", "CREW_DOCTOR", "CREW_LOOKOUT", "CREW_BOATSWAIN"};
        // 원숭이면 playerId % 4 번째 후보로 위장, 아니면 null(위장 없음).
        // Math.floorMod는 음수여도 0~3을 돌려주는 나머지 연산이다. (0번→선장, 1번→선의, 2번→망루지기, 3번→갑판장, 4번→선장 ...)
        // ⚠ 번호만 알면 위장 직업을 예측할 수 있다. 배정 시 무작위로 뽑아 저장하는 편이 안전하다.
        this.shownRoleCode = "CREW_MONKEY".equals(roleCode)
            ? disguises[Math.floorMod(playerId.intValue(), disguises.length)] : null;
        // "CREW_MONKEY".equals(roleCode)처럼 상수를 앞에 두면 roleCode가 null이어도 NPE가 나지 않는다.
    }

    // 사망 처리. 트랜잭션 안에서 호출하면 커밋 때 UPDATE room_players SET alive=false 가 자동으로 나간다.
    public void kill() {
        this.alive = false;
    }
}
