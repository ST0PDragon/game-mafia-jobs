// [파일 역할] 능력(행동) 종류와 각 능력의 "기본 규칙표".
//   언제 쓰는지(낮/밤), 게임 전체에 몇 번 쓸 수 있는지, 살아 있는 대상이어야 하는지, 자기 자신을 고를 수 있는지.
//   RoleService.submitAction이 이 표를 보고 제출을 검사한다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/domain/ActionCode.java

package com.doronyong.mafia.domain; // 도메인 패키지

/**
 * 행동 정책의 한곳짜리 정의. enum 이름은 API actionCode 및 roles.action_code와 일치해야 한다.
 * 새 능력을 만들 때는 이곳, 새 Flyway 마이그레이션, RoleService 검증,
 * NightResolutionService 판정(밤 능력인 경우)을 함께 수정한다.
 * maxUses는 게임 전체 제한이며 -1은 무제한이다. 같은 라운드의 중복 제출은 별도 DB 제약으로 막는다.
 */
// enum은 값마다 생성자 인자를 붙여 "데이터를 가진 상수"로 만들 수 있다.
// 인자 순서: (사용 단계, 최대 사용 횟수, 살아 있는 대상 필요?, 자기 자신 선택 가능?)
public enum ActionCode {
    // 선장: 밤, 무제한, 산 사람만, 자기 자신 불가 → 대상의 진영을 본다
    INVESTIGATE_FACTION(RoomPhase.NIGHT, -1, true, false),
    // 선의: 밤, 무제한, 산 사람만, 자기 자신 가능 → 대상을 보호 (연속 자기 보호 금지는 RoleService에서 따로 검사)
    PROTECT(RoomPhase.NIGHT, -1, true, true),
    // 망루지기: 밤, 무제한, 산 사람만, 자기 자신 가능 → 대상에게 누가 방문했는지 본다
    WATCH_VISITORS(RoomPhase.NIGHT, -1, true, true),
    // 갑판장: 밤, 무제한, 산 사람만, 자기 자신 불가 → 대상의 밤 능력을 막는다
    BLOCK(RoomPhase.NIGHT, -1, true, false),
    // 포수: **낮**, 게임당 1번, 산 사람만, 자기 자신 불가 → 즉시 처형
    DAY_SHOOT(RoomPhase.DAY, 1, true, false),
    // 주정뱅이: 밤, 게임당 2번, **죽은 사람만**(false), 자기 자신 불가 → 시체의 직업을 본다
    READ_CORPSE_ROLE(RoomPhase.NIGHT, 2, false, false),
    // 해적: 밤, 무제한, 산 사람만, 자기 자신 불가 → 팀 공유 공격 대상 선택/변경
    SELECT_ATTACK_TARGET(RoomPhase.NIGHT, -1, true, false),
    // V2에서 제출된 행동을 읽을 수 있도록 남긴 이전 코드. 새 직업에는 배정하지 않는다.
    // (DB에 이 문자열이 남아 있을 때 ActionCode.valueOf가 실패하지 않게 하려고 지우지 않았다.)
    TEAM_ATTACK_VOTE(RoomPhase.NIGHT, -1, true, false),
    // 앵무새: 밤, 무제한, 산 사람만, 자기 자신 불가 → 대상이 누구에게 무슨 행동을 했는지 본다
    WATCH_ACTION(RoomPhase.NIGHT, -1, true, false); // 마지막 상수 뒤에는 세미콜론(;)을 찍고 필드를 선언한다

    // 생성자 순서: 사용 단계, 게임 전체 최대 횟수, 살아 있는 대상 필요 여부, 자기 자신 선택 허용 여부.
    private final RoomPhase phase;            // 사용 가능한 단계
    private final int maxUses;                // 게임 전체 최대 횟수 (-1 = 무제한)
    private final boolean livingTarget;       // true면 산 대상만, false면 죽은 대상만
    private final boolean selfTargetAllowed;  // 자기 자신을 대상으로 고를 수 있는지

    // enum 생성자는 항상 private(생략해도 private). 위 상수를 만들 때만 호출된다.
    ActionCode(RoomPhase phase, int maxUses, boolean livingTarget, boolean selfTargetAllowed) {
        this.phase = phase;
        this.maxUses = maxUses;
        this.livingTarget = livingTarget;
        this.selfTargetAllowed = selfTargetAllowed;
    }

    // getter들. getXxx 대신 record처럼 필드 이름 그대로 짧게 지었다.
    public RoomPhase phase() { return phase; }
    public int maxUses() { return maxUses; }
    public boolean requiresLivingTarget() { return livingTarget; }
    public boolean allowsSelfTarget() { return selfTargetAllowed; }
}
