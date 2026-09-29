// [파일 역할] API가 주고받는 JSON의 모양(DTO = Data Transfer Object)을 모아 둔 파일.
//   엔티티를 그대로 JSON으로 내보내지 않고 DTO로 옮겨 담는 이유:
//   ① 원숭이의 실제 직업, userId 같은 숨겨야 할 필드가 새어 나가지 않게, ② DB 구조가 바뀌어도 API 모양은 유지하려고.
//   필드 이름을 바꾸면 Unity RoleApiClient.cs의 필드 이름도 똑같이 바꿔야 한다(JsonUtility는 이름으로 매칭).
// 원본 위치: server/src/main/java/com/doronyong/mafia/dto/RoleApiDtos.java

package com.doronyong.mafia.dto; // DTO 패키지

import com.doronyong.mafia.domain.Faction;                     // 진영 enum
import com.doronyong.mafia.domain.PirateAttackSelectionEntity; // 해적 선택 엔티티 → ActionResponse 변환용
import com.doronyong.mafia.domain.RoleEntity;                  // 직업 엔티티 → RoleView 변환용
import com.doronyong.mafia.domain.RoomActionEntity;            // 행동 엔티티 → ActionResponse 변환용
import com.doronyong.mafia.domain.RoomPhase;                   // 단계 enum
import com.doronyong.mafia.domain.RoomReportEntity;            // 보고 엔티티 → ReportView 변환용
import jakarta.validation.constraints.NotBlank;      // 문자열이 null/빈칸이면 안 됨
import jakarta.validation.constraints.NotNull;       // null이면 안 됨
import jakarta.validation.constraints.PositiveOrZero; // 0 이상이어야 함
import java.util.List;                               // 목록 타입
import java.util.UUID;                               // requestId 타입

/** HTTP JSON 계약. 필드 이름을 바꾸면 Unity RoleApiClient의 공개 필드도 함께 수정한다. */
// final + private 생성자: DTO들을 담는 "보관함" 클래스라 객체를 만들 일이 없다는 뜻.
public final class RoleApiDtos {
    private RoleApiDtos() {} // 인스턴스 생성 금지

    // record: 필드 선언만 하면 생성자, getter(code() 형태), equals/hashCode/toString을 자동으로 만들어 주는 불변 클래스.
    // Jackson(스프링의 JSON 변환기)이 record를 {"code":..,"name":..,"faction":..} 로 바꿔 준다.
    public record RoleView(String code, String name, Faction faction) {
        // 엔티티 → DTO 변환용 정적 팩토리 메서드. RoleView::from 형태로 stream map에 넘기기 좋다.
        public static RoleView from(RoleEntity role) {
            return new RoleView(role.getCode(), role.getName(), role.getFaction());
        }
    }

    // GET /roles 응답: {"roles":[{...},{...}]}
    public record RoleListResponse(List<RoleView> roles) {}
    // remainingUses=-1은 게임 전체 횟수 제한 없음. 라운드당 중복 제출은 별도로 제한한다.
    // 능력 하나의 정보: 코드, 남은 횟수, 사용 단계(DAY/NIGHT)
    public record AbilityView(String actionCode, int remainingUses, RoomPhase phase) {}
    // GET /me/role 응답
    public record MyRoleResponse(
        // 공개 이름은 gamesId지만 현재 값은 내부 rooms.id다.
        Long gamesId,               // 게임 번호
        RoleView role,              // 보여 줄 직업(원숭이면 위장 직업)
        boolean alive,              // 내가 살아 있는지
        List<AbilityView> abilities, // 쓸 수 있는 능력 목록(0개 또는 1개)
        List<Long> allies           // 해적이면 동료 해적의 playerId, 아니면 빈 목록
    ) {}

    // 보고 하나. 엔티티의 roomId/recipient는 빼고 화면에 필요한 값만 담는다.
    public record ReportView(Long id, int roundNumber, String type, String message) {
        public static ReportView from(RoomReportEntity report) {
            return new ReportView(
                report.getId(), report.getRoundNumber(), report.getType(), report.getMessage()
            );
        }
    }

    // GET /me/reports 응답: {"reports":[...]}
    public record ReportsResponse(List<ReportView> reports) {}

    /** 이번 밤의 공유 대상. 미선택 시 hasTarget=false이고 두 playerId는 0이므로 무시한다. */
    // Long이 아니라 소문자 long(원시 타입)이라 null을 넣을 수 없어서, "없음"을 0 + hasTarget=false로 표현한다.
    public record PirateAttackTargetResponse(
        Long gamesId,            // 게임 번호
        int nightNumber,         // 몇 번째 밤
        boolean hasTarget,       // 대상이 정해졌는지 (← 이 값으로 판단해야 한다. 0번 플레이어도 있으니까)
        long targetPlayerId,     // 공격 대상
        long selectedByPlayerId  // 마지막으로 고른 해적
    ) {}

    /**
     * requestId는 재시도 시에도 같은 값을 사용한다.
     * playerId 0은 현재 Unity 로컬 프로토타입의 첫 플레이어 번호라 허용한다.
     */
    // POST /actions 요청 본문. 컨트롤러의 @Valid가 아래 검증 애너테이션을 검사하고, 어기면 400을 돌려준다.
    public record SubmitActionRequest(
        @NotNull UUID requestId,                  // 필수. 문자열이 UUID 형식이 아니면 JSON 변환 단계에서 400
        @NotBlank String actionCode,              // 필수. 빈 문자열 불가
        @NotNull @PositiveOrZero Long targetPlayerId // 필수. 음수 불가(0은 허용)
    ) {}

    // POST /actions 응답
    public record ActionResponse(
        Long actionId,       // 저장된 행 id
        Long gamesId,        // 게임 번호
        String status,       // SUBMITTED / RESOLVED / SELECTED
        String actionCode,   // 제출한 능력
        Long targetPlayerId  // 대상
    ) {
        // 일반 행동(room_actions) → 응답
        public static ActionResponse from(RoomActionEntity action) {
            return new ActionResponse(
                action.getId(), action.getRoomId(), action.getStatus(),
                action.getActionCode(), action.getTargetPlayerId()
            );
        }

        // 해적 대상 선택(pirate_attack_selections) → 응답. 테이블이 달라도 Unity는 같은 모양으로 받는다.
        // ※ actionId는 테이블마다 따로 증가하므로 일반 행동의 id와 숫자가 겹칠 수 있다.
        public static ActionResponse from(PirateAttackSelectionEntity selection) {
            return new ActionResponse(
                selection.getId(), selection.getRoomId(), "SELECTED",
                "SELECT_ATTACK_TARGET", selection.getTargetPlayerId()
            );
        }
    }
}
