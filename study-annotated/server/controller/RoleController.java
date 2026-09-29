// [파일 역할] HTTP 요청의 입구. URL ↔ 자바 메서드를 연결하고, JWT에서 userId를 꺼내 RoleService에 넘긴다.
//   게임 규칙은 여기 없다(얇은 컨트롤러). 규칙은 전부 RoleService에 있다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/controller/RoleController.java

package com.doronyong.mafia.controller; // 컨트롤러 패키지

import com.doronyong.mafia.domain.Faction;                              // ?faction=CREW 쿼리 파라미터 타입
import com.doronyong.mafia.dto.RoleApiDtos.ActionResponse;              // 행동 제출 응답
import com.doronyong.mafia.dto.RoleApiDtos.MyRoleResponse;              // 내 직업 응답
import com.doronyong.mafia.dto.RoleApiDtos.PirateAttackTargetResponse;  // 해적 공유 대상 응답
import com.doronyong.mafia.dto.RoleApiDtos.RoleListResponse;            // 직업 목록 응답
import com.doronyong.mafia.dto.RoleApiDtos.ReportsResponse;             // 보고 목록 응답
import com.doronyong.mafia.dto.RoleApiDtos.SubmitActionRequest;         // 행동 제출 요청 본문
import com.doronyong.mafia.service.RoleService;                         // 실제 규칙 처리
import jakarta.validation.Valid;                                        // 요청 본문 검증 트리거
import org.springframework.http.HttpStatus;                             // 상태 코드 상수
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 로그인한 사용자 정보 주입
import org.springframework.security.oauth2.jwt.Jwt;                     // 검증된 JWT 객체
import org.springframework.web.bind.annotation.GetMapping;              // GET 매핑
import org.springframework.web.bind.annotation.PathVariable;            // URL의 {값}을 인자로
import org.springframework.web.bind.annotation.PostMapping;             // POST 매핑
import org.springframework.web.bind.annotation.RequestBody;             // 요청 JSON 본문을 객체로
import org.springframework.web.bind.annotation.RequestMapping;          // 공통 URL 앞부분
import org.springframework.web.bind.annotation.RequestParam;            // ?key=value 쿼리 파라미터
import org.springframework.web.bind.annotation.ResponseStatus;          // 성공 시 상태 코드 지정
import org.springframework.web.bind.annotation.RestController;          // JSON을 돌려주는 컨트롤러
import org.springframework.web.server.ResponseStatusException;          // 특정 HTTP 상태로 응답하게 하는 예외

/**
 * Unity가 호출하는 직업 API의 HTTP 진입점.
 * 요청 해석과 인증 사용자 확인만 맡고 게임 규칙은 RoleService에서 처리한다.
 * 공개 URL의 gamesId는 현재 내부 RoomEntity.id와 동일하다. DB 이름을 바꿀 필요는 없다.
 */
@RestController            // = @Controller + @ResponseBody. 반환한 객체(record)를 JSON으로 바꿔 응답한다.
@RequestMapping("/api/v1") // 이 클래스의 모든 URL 앞에 /api/v1이 붙는다
public class RoleController {
    // 의존성. final이라 한 번 넣으면 바뀌지 않는다.
    private final RoleService roleService;

    // 생성자 주입: 스프링이 RoleService 빈을 찾아 자동으로 넣어 준다(생성자가 하나면 @Autowired 생략 가능).
    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    // GET /api/v1/roles  또는  GET /api/v1/roles?faction=CREW
    @GetMapping("/roles")
    public RoleListResponse roles(@RequestParam(required = false) Faction faction) {
        // 공개 정보이므로 로그인 없이 조회할 수 있다. faction이 없으면 전체 직업을 반환한다.
        // required=false라 파라미터가 없으면 faction=null. "CREW" 문자열은 스프링이 Faction.CREW로 자동 변환한다.
        // 없는 값(예: ?faction=ABC)이면 변환 실패로 400.
        return roleService.listRoles(faction);
    }

    // GET /api/v1/games/12/me/role → {gamesId} 자리의 12가 gamesId 인자로 들어온다
    @GetMapping("/games/{gamesId}/me/role")
    public MyRoleResponse myRole(@PathVariable Long gamesId, @AuthenticationPrincipal Jwt jwt) {
        // 다른 참가자의 직업을 조회하지 못하도록 userId를 요청값이 아닌 JWT에서 가져온다.
        // @AuthenticationPrincipal Jwt: SecurityConfig가 검증을 마친 토큰 객체를 그대로 받는다.
        return roleService.getMyRole(gamesId, userId(jwt));
    }

    // GET /api/v1/games/12/me/reports → 나에게 공개된 보고만
    @GetMapping("/games/{gamesId}/me/reports")
    public ReportsResponse myReports(@PathVariable Long gamesId, @AuthenticationPrincipal Jwt jwt) {
        return roleService.getMyReports(gamesId, userId(jwt));
    }

    // GET /api/v1/games/12/pirate-attack → 해적 팀의 이번 밤 공유 대상
    @GetMapping("/games/{gamesId}/pirate-attack")
    public PirateAttackTargetResponse pirateAttack(@PathVariable Long gamesId,
                                                    @AuthenticationPrincipal Jwt jwt) {
        // 해적에게만 이번 밤의 공유 공격 대상을 보여 준다.
        return roleService.getPirateAttackTarget(gamesId, userId(jwt));
    }

    // POST /api/v1/games/12/actions  본문: {"requestId":"...","actionCode":"PROTECT","targetPlayerId":3}
    @PostMapping("/games/{gamesId}/actions")
    // 성공하면 200 대신 202 Accepted: "접수는 했고, 효과는 나중에(밤 판정 때) 적용된다"는 의미.
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ActionResponse submitAction(@PathVariable Long gamesId,
                                       @AuthenticationPrincipal Jwt jwt,
                                       // @RequestBody: JSON 본문 → SubmitActionRequest record
                                       // @Valid: record의 @NotNull 등을 검사. 실패하면 메서드에 들어오기 전에 400.
                                       @Valid @RequestBody SubmitActionRequest request) {
        return roleService.submitAction(gamesId, userId(jwt), request);
    }

    // JWT → userId 변환 도우미. static: 객체 상태를 쓰지 않는 순수 함수.
    private static Long userId(Jwt jwt) {
        // 인증 서버는 JWT의 sub에 숫자 userId를 넣어야 한다.
        try {
            // sub(subject) 클레임 문자열 "100" → 100L
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            // 숫자가 아니면(예: 이메일이 들어 있으면) 이 서버가 쓸 수 없는 토큰이므로 401.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user ID in token", e);
        }
    }
}
