// [파일 역할] 통합 테스트. 스프링 전체(DB, 보안, 컨트롤러, 서비스)를 실제로 띄워 놓고 게임 규칙을 시나리오별로 확인한다.
//   방 생성·직업 배정 API가 아직 없으므로, 테스트가 Repository로 방과 플레이어를 직접 DB에 넣는다.
//   실행: server/ 폴더에서 `mvn test`
// 원본 위치: server/src/test/java/com/doronyong/mafia/RoleApiIntegrationTest.java

package com.doronyong.mafia; // 앱과 같은 패키지 → @SpringBootTest가 MafiaServerApplication을 찾아 설정을 불러온다

// static import: 클래스 이름 없이 assertTrue(...), get(...), status() 처럼 바로 쓰기 위해
import static org.junit.jupiter.api.Assertions.*; // assertTrue, assertEquals, assertThrows ...
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt; // 가짜 JWT 붙이기
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*; // get(...), post(...)
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;   // status(), jsonPath(...)

import com.doronyong.mafia.domain.*;                       // 엔티티, enum 전부
import com.doronyong.mafia.dto.RoleApiDtos.SubmitActionRequest; // 요청 DTO
import com.doronyong.mafia.repository.*;                   // Repository 전부
import com.doronyong.mafia.service.*;                      // 서비스 전부
import java.util.UUID;                                     // requestId 생성
import org.junit.jupiter.api.BeforeEach;                   // 각 테스트 전에 실행
import org.junit.jupiter.api.Test;                         // 테스트 메서드 표시
import org.springframework.beans.factory.annotation.Autowired; // 필드 주입
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc; // MockMvc 준비
import org.springframework.boot.test.context.SpringBootTest; // 스프링 전체를 띄우는 테스트
import org.springframework.http.MediaType;                  // Content-Type 상수
import org.springframework.test.web.servlet.MockMvc;        // 실제 서버 없이 HTTP 요청을 흉내 내는 도구
import org.springframework.web.server.ResponseStatusException; // 서비스가 던지는 예외 확인용

@SpringBootTest        // 실제 앱과 같은 스프링 컨테이너를 띄운다(기본 H2 메모리 DB + Flyway 마이그레이션 실행)
@AutoConfigureMockMvc  // MockMvc 빈을 만들어 준다 → HTTP 계층(보안 포함)까지 테스트 가능
class RoleApiIntegrationTest {
    // 테스트에서는 생성자 대신 @Autowired 필드 주입을 흔히 쓴다.
    @Autowired MockMvc mvc;                                   // HTTP 요청 흉내
    @Autowired RoomRepository rooms;                          // 데이터 준비/검증용 Repository들
    @Autowired RoomPlayerRepository players;
    @Autowired RoomActionRepository actions;
    @Autowired PirateAttackSelectionRepository pirateSelections;
    @Autowired RoomReportRepository reports;
    @Autowired RoleService roles;                             // 서비스를 직접 호출하는 테스트도 있다
    @Autowired NightResolutionService nights;                 // 밤 판정을 직접 실행(타이머 대신)
    Long roomsId;                                             // 이번 테스트에서 쓸 방 번호

    // 매 테스트 시작 전: 이전 테스트 데이터를 지우고 "첫 밤" 상태의 새 방을 만든다.
    @BeforeEach void setUp() {
        // 외래키(FK) 때문에 자식 테이블부터 지운다(rooms를 먼저 지우면 FK 오류).
        reports.deleteAll();
        pirateSelections.deleteAll();
        actions.deleteAll();
        players.deleteAll();
        rooms.deleteAll();
        // NIGHT, 1라운드 방 생성 후 id 저장
        roomsId = rooms.save(new RoomEntity(RoomPhase.NIGHT, 1)).getId();
    }

    // 도우미: playerId=id인 플레이어 추가. userId는 100+id로 정한다(예: 3번 플레이어 = 계정 103).
    void add(long id, String role, boolean alive) {
        players.save(new RoomPlayerEntity(roomsId, 100L + id, id, role, alive));
    }

    // 도우미: actor번 플레이어가 target번에게 code 능력을 제출(매번 새 requestId). HTTP를 거치지 않고 서비스 직접 호출.
    void submit(long actor, String code, long target) {
        roles.submitAction(roomsId, 100L + actor,
            new SubmitActionRequest(UUID.randomUUID(), code, target));
    }

    // 도우미: id번 플레이어의 보고서 목록을 문자열로(record의 toString 덕분에 내용 검색이 쉽다)
    String reportText(long id) {
        return roles.getMyReports(roomsId, 100L + id).reports().toString();
    }

    // [시나리오] 공개 직업 목록에는 활성 직업만 나온다
    @Test void catalogueContainsOnlyActiveRoles() throws Exception {
        // 로그인 없이 GET /api/v1/roles → 200 확인 후 응답 본문 문자열을 꺼낸다
        String body = mvc.perform(get("/api/v1/roles"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("CREW_MONKEY"));   // 새 원숭이(선원)는 있고
        assertTrue(body.contains("PIRATE_PARROT")); // 앵무새도 있고
        // 비활성 직업 4개는 없어야 한다
        for (String excluded : new String[] {
            "PIRATE_KRAKEN", "PIRATE_SPY", "CREW_GHOST", "NEUTRAL_MONKEY"
        }) assertFalse(body.contains(excluded));
        // 중립 진영 필터 → 활성 중립 직업이 없으니 roles 배열 길이 0
        // jsonPath("$.roles.length()"): JSON에서 roles 배열의 길이를 꺼내는 표현식
        mvc.perform(get("/api/v1/roles").param("faction", "NEUTRAL"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(0));
    }

    // [시나리오] 내 직업 API: 인증, 해적 동료, 앵무새 능력, 해적 대상 조회 권한, 옛 URL, 비참가자
    @Test void privateRoleShowsPirateAlliesAndParrotAbilities() throws Exception {
        add(0, "PIRATE_RAIDER", true); // 0번 해적 (계정 100)
        add(1, "PIRATE_PARROT", true); // 1번 앵무새 (계정 101)
        add(2, "CREW_DOCTOR", true);   // 2번 선의 (계정 102)
        // 토큰 없이 호출 → 401
        mvc.perform(get("/api/v1/games/{gamesId}/me/role", roomsId))
            .andExpect(status().isUnauthorized());
        // .with(jwt()...): 서명 검증을 건너뛰는 테스트용 가짜 JWT를 붙인다. sub=100 → 0번 해적
        mvc.perform(get("/api/v1/games/{gamesId}/me/role", roomsId)
                .with(jwt().jwt(token -> token.subject("100"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gamesId").value(roomsId))    // 응답 필드 이름이 gamesId인지
            .andExpect(jsonPath("$.allies[0]").value(1))        // 동료로 1번(앵무새)이 보이는지
            .andExpect(jsonPath("$.abilities[0].actionCode").value("SELECT_ATTACK_TARGET")); // 해적 능력
        // 앵무새: 능력이 1개(WATCH_ACTION)
        mvc.perform(get("/api/v1/games/{gamesId}/me/role", roomsId)
                .with(jwt().jwt(token -> token.subject("101"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.abilities.length()").value(1));
        // 앵무새도 해적 진영이라 공유 대상 조회 가능. 아직 선택이 없으니 hasTarget=false
        mvc.perform(get("/api/v1/games/{gamesId}/pirate-attack", roomsId)
                .with(jwt().jwt(token -> token.subject("101"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasTarget").value(false));
        // 선의(선원)는 해적 대상 조회 불가 → 403
        mvc.perform(get("/api/v1/games/{gamesId}/pirate-attack", roomsId)
                .with(jwt().jwt(token -> token.subject("102"))))
            .andExpect(status().isForbidden());
        // 예전 URL(/rooms/...)은 더 이상 없다 → 404
        mvc.perform(get("/api/v1/rooms/{roomsId}/me/role", roomsId)
                .with(jwt().jwt(token -> token.subject("100"))))
            .andExpect(status().isNotFound());
        // 이 방에 없는 계정(999) → 403
        mvc.perform(get("/api/v1/games/{gamesId}/me/reports", roomsId)
                .with(jwt().jwt(token -> token.subject("999"))))
            .andExpect(status().isForbidden());
    }

    // [시나리오] 같은 requestId 재시도는 같은 결과, 새 requestId로 또 내면 409
    @Test void actionRetryReturnsSameResultAndNewRequestConflicts() throws Exception {
        add(0, "CREW_DOCTOR", true);
        add(1, "PIRATE_RAIDER", true);
        // 텍스트 블록("""..."""): 여러 줄 문자열. formatted로 %s 자리에 UUID를 넣는다.
        String body = """
            {"requestId":"%s","actionCode":"PROTECT","targetPlayerId":1}
            """.formatted(UUID.randomUUID());
        // 1차 제출 → 202
        String first = mvc.perform(post("/api/v1/games/{gamesId}/actions", roomsId)
                .with(jwt().jwt(token -> token.subject("100")))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.gamesId").value(roomsId))
            .andReturn().getResponse().getContentAsString();
        // 똑같은 본문(같은 requestId)으로 재시도 → 역시 202
        String retry = mvc.perform(post("/api/v1/games/{gamesId}/actions", roomsId)
                .with(jwt().jwt(token -> token.subject("100")))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals(first, retry);       // 응답이 완전히 같고
        assertEquals(1, actions.count()); // DB에는 한 줄만 저장됨(두 번 반영되지 않음)
        // 새 requestId로 같은 밤에 또 PROTECT → 409
        mvc.perform(post("/api/v1/games/{gamesId}/actions", roomsId)
                .with(jwt().jwt(token -> token.subject("100")))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"requestId":"%s","actionCode":"PROTECT","targetPlayerId":0}
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isConflict());
    }

    // [시나리오] 선장·망루지기·앵무새·선의가 한 밤에 함께 행동 → 결과는 각자에게만
    @Test void captainLookoutParrotAndDoctorResolvePrivately() {
        add(0, "CREW_CAPTAIN", true);
        add(1, "CREW_LOOKOUT", true);
        add(2, "CREW_DOCTOR", true);
        add(3, "PIRATE_RAIDER", true);
        add(4, "PIRATE_PARROT", true);
        add(5, "CREW_SAILOR", true);
        submit(0, "INVESTIGATE_FACTION", 3); // 선장이 3번(해적) 조사
        submit(1, "WATCH_VISITORS", 5);      // 망루지기가 5번 감시
        submit(2, "PROTECT", 5);             // 선의가 5번 보호
        submit(3, "SELECT_ATTACK_TARGET", 5); // 해적이 5번 공격
        submit(4, "WATCH_ACTION", 2);        // 앵무새가 2번(선의)의 행동 감시
        nights.resolveNight(roomsId);        // 밤 판정
        // 보호받아서 5번 생존
        assertTrue(players.findByRoomIdAndPlayerId(roomsId, 5L).orElseThrow().isAlive());
        assertTrue(reportText(0).contains("PIRATE"));     // 선장은 "3번의 진영: PIRATE"
        assertFalse(reportText(0).contains("VISITORS"));  // 선장에게 망루지기 결과가 섞이지 않음
        assertTrue(reportText(1).contains("2, 3"));       // 5번 방문자 = 2번(선의), 3번(해적)
        assertTrue(reportText(4).contains("PROTECT"));    // 앵무새는 "2번의 행동: PROTECT → 5"
        // 승자가 없으니 낮으로 넘어감
        assertEquals(RoomPhase.DAY, rooms.findById(roomsId).orElseThrow().getPhase());
    }

    // [시나리오] 갑판장이 선의를 막음 → 보호 실패 → 사망 → 해적 2 : 선원 2 동수라 해적 승리
    @Test void boatswainBlocksProtectionThenPiratesWinAtParity() {
        add(0, "CREW_BOATSWAIN", true);
        add(1, "CREW_DOCTOR", true);
        add(2, "CREW_SAILOR", true);
        add(3, "PIRATE_RAIDER", true);
        add(4, "PIRATE_PARROT", true);
        submit(0, "BLOCK", 1);               // 갑판장이 선의 차단
        submit(1, "PROTECT", 2);             // 선의가 2번 보호(하지만 차단됨)
        submit(3, "SELECT_ATTACK_TARGET", 2); // 해적이 2번 공격
        nights.resolveNight(roomsId);
        assertFalse(players.findByRoomIdAndPlayerId(roomsId, 2L).orElseThrow().isAlive()); // 2번 사망
        assertTrue(reportText(0).contains("NIGHT_DEATH")); // 사망 소식은 모두에게
        assertEquals(Faction.PIRATE, rooms.findById(roomsId).orElseThrow().getWinnerFaction()); // 해적 승리
    }

    // [시나리오] 해적 한 명의 선택만으로도 공격이 된다(과반수 투표 없음)
    @Test void onePirateSelectionKillsWithoutMajority() {
        add(0, "CREW_SAILOR", true);
        add(1, "CREW_DOCTOR", true);
        add(2, "CREW_CAPTAIN", true);
        add(3, "PIRATE_RAIDER", true);
        add(4, "PIRATE_PARROT", true);
        submit(3, "SELECT_ATTACK_TARGET", 0);
        nights.resolveNight(roomsId);
        assertFalse(players.findByRoomIdAndPlayerId(roomsId, 0L).orElseThrow().isAlive()); // 0번 사망
        assertTrue(reportText(0).contains("NIGHT_DEATH"));  // 죽은 사람도 공개 보고는 받는다
        // 선원 2 : 해적 2 → 해적 승리
        assertEquals(Faction.PIRATE, rooms.findById(roomsId).orElseThrow().getWinnerFaction());
    }

    // [시나리오] 마지막 선택이 공유 대상이 되고, 늦게 온 재시도가 대상을 되돌리지 않는다
    @Test void latestPirateSelectionIsSharedAndRetryDoesNotRevertIt() throws Exception {
        add(0, "CREW_SAILOR", true);
        add(1, "CREW_DOCTOR", true);
        add(2, "CREW_CAPTAIN", true);
        add(3, "PIRATE_RAIDER", true);
        add(4, "PIRATE_RAIDER", true);
        add(5, "PIRATE_PARROT", true);
        add(6, "CREW_LOOKOUT", true);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        // ① 3번 해적이 HTTP로 0번 선택 → status "SELECTED"
        mvc.perform(post("/api/v1/games/{gamesId}/actions", roomsId)
                .with(jwt().jwt(token -> token.subject("103")))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"requestId":"%s","actionCode":"SELECT_ATTACK_TARGET","targetPlayerId":0}
                    """.formatted(firstId)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("SELECTED"))
            .andExpect(jsonPath("$.targetPlayerId").value(0));
        // ② 4번 해적이 1번으로 변경
        roles.submitAction(roomsId, 104L,
            new SubmitActionRequest(secondId, "SELECT_ATTACK_TARGET", 1L));
        // ③ 3번 해적이 다시 2번으로 변경 → 이게 최종
        submit(3, "SELECT_ATTACK_TARGET", 2);
        // 늦게 도착한 동일 requestId 재시도는 현재 공유 대상을 되돌리지 않는다.
        // ④ ②와 같은 requestId 재시도 → 새 줄을 만들지 않으므로 최종 대상은 여전히 2번
        roles.submitAction(roomsId, 104L,
            new SubmitActionRequest(secondId, "SELECT_ATTACK_TARGET", 1L));
        assertEquals(3, pirateSelections.count()); // ①②③만 저장(④는 저장 안 됨)
        // 앵무새(계정 105)로 조회해도 대상은 2번
        assertEquals(2L, roles.getPirateAttackTarget(roomsId, 105L).targetPlayerId());
        mvc.perform(get("/api/v1/games/{gamesId}/pirate-attack", roomsId)
                .with(jwt().jwt(token -> token.subject("103"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasTarget").value(true))
            .andExpect(jsonPath("$.targetPlayerId").value(2));
        // 앵무새가 대상을 바꾸려 하면 403 (조회는 되지만 변경은 불가)
        // assertThrows: 람다 실행 중 해당 예외가 나야 통과, 그 예외 객체를 돌려준다
        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
            () -> submit(5, "SELECT_ATTACK_TARGET", 0));
        assertEquals(403, denied.getStatusCode().value());
        nights.resolveNight(roomsId);
        // 0번, 1번은 생존, 최종 대상 2번만 사망
        assertTrue(players.findByRoomIdAndPlayerId(roomsId, 0L).orElseThrow().isAlive());
        assertTrue(players.findByRoomIdAndPlayerId(roomsId, 1L).orElseThrow().isAlive());
        assertFalse(players.findByRoomIdAndPlayerId(roomsId, 2L).orElseThrow().isAlive());
    }

    // [시나리오] 해적이 한 명뿐인데 차단당하면 공격 실패
    @Test void blockedOnlyRaiderCannotAttack() {
        add(0, "CREW_BOATSWAIN", true);
        add(1, "CREW_SAILOR", true);
        add(2, "CREW_DOCTOR", true);
        add(3, "PIRATE_RAIDER", true);
        submit(0, "BLOCK", 3);               // 유일한 해적 차단
        submit(3, "SELECT_ATTACK_TARGET", 1); // 선택은 저장되지만
        nights.resolveNight(roomsId);
        assertTrue(players.findByRoomIdAndPlayerId(roomsId, 1L).orElseThrow().isAlive()); // 실행자가 없어 생존
        assertFalse(reportText(1).contains("NIGHT_DEATH"));
    }

    // [시나리오] 선택한 해적이 차단돼도 다른 해적이 대신 실행한다
    @Test void anotherRaiderCanExecuteSharedAttackWhenSelectorIsBlocked() {
        add(0, "CREW_BOATSWAIN", true);
        add(1, "CREW_SAILOR", true);
        add(2, "CREW_DOCTOR", true);
        add(3, "CREW_CAPTAIN", true);
        add(4, "PIRATE_RAIDER", true);
        add(5, "PIRATE_RAIDER", true);
        submit(0, "BLOCK", 4);               // 4번 해적 차단
        submit(4, "SELECT_ATTACK_TARGET", 1); // 4번이 1번 선택
        nights.resolveNight(roomsId);        // → 5번 해적이 실행자가 되어 공격
        assertFalse(players.findByRoomIdAndPlayerId(roomsId, 1L).orElseThrow().isAlive());
        assertTrue(reportText(1).contains("NIGHT_DEATH"));
    }

    // [시나리오] 포수는 첫 낮에 쏠 수 있고, 유일한 해적을 쏘면 즉시 선원 승리
    @Test void gunnerShootsOnFirstDayAndWins() {
        // setUp의 밤 방 대신 낮 방을 새로 만든다
        roomsId = rooms.save(new RoomEntity(RoomPhase.DAY, 1)).getId();
        add(0, "CREW_GUNNER", true);
        add(1, "PIRATE_RAIDER", true);
        add(2, "CREW_SAILOR", true);
        submit(0, "DAY_SHOOT", 1); // 밤 판정 없이 즉시 효과
        assertFalse(players.findByRoomIdAndPlayerId(roomsId, 1L).orElseThrow().isAlive());
        assertEquals(Faction.CREW, rooms.findById(roomsId).orElseThrow().getWinnerFaction());
        assertTrue(reportText(2).contains("DAY_SHOOT")); // 다른 사람도 처형 소식을 받음
        // 게임당 1회라 남은 횟수 0
        assertEquals(0, roles.getMyRole(roomsId, 100L).abilities().get(0).remainingUses());
    }

    // [시나리오] 선의는 이틀 연속 자기 자신을 보호할 수 없다
    @Test void doctorCannotProtectSelfOnConsecutiveNights() {
        add(0, "CREW_DOCTOR", true);
        add(1, "PIRATE_RAIDER", true);
        add(2, "CREW_SAILOR", true);
        submit(0, "PROTECT", 0);        // 1일째 밤: 자기 보호
        nights.resolveNight(roomsId);   // 밤 1 → 낮 1
        nights.beginNextNight(roomsId); // 낮 1 → 밤 2
        // 2일째 밤에 또 자기 보호 → 409
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> submit(0, "PROTECT", 0));
        assertEquals(409, error.getStatusCode().value());
        submit(0, "PROTECT", 2); // 다른 사람 보호는 가능(예외 없이 통과해야 함)
    }

    // [시나리오] 주정뱅이는 두 밤에 걸쳐 시체 2구의 직업을 보고, 그 뒤엔 횟수가 0
    @Test void drunkReadsTwoCorpsesAcrossNights() {
        add(0, "CREW_DRUNK", true);
        add(1, "CREW_SAILOR", false); // 이미 죽은 1번
        add(2, "CREW_DOCTOR", false); // 이미 죽은 2번
        add(3, "CREW_CAPTAIN", true);
        add(4, "PIRATE_RAIDER", true);
        submit(0, "READ_CORPSE_ROLE", 1); // 밤 1: 1번 시체
        nights.resolveNight(roomsId);
        nights.beginNextNight(roomsId);
        submit(0, "READ_CORPSE_ROLE", 2); // 밤 2: 2번 시체
        nights.resolveNight(roomsId);
        assertTrue(reportText(0).contains("CREW_SAILOR"));  // 결과 1
        assertTrue(reportText(0).contains("CREW_DOCTOR"));  // 결과 2
        assertEquals(0, roles.getMyRole(roomsId, 100L).abilities().get(0).remainingUses()); // 2회 모두 사용
        assertFalse(reportText(3).contains("CORPSE_ROLE")); // 다른 사람에게는 안 보임
    }

    // [시나리오] 원숭이는 자기 직업을 선장으로 보고, 조사 결과는 반대로 받는다
    @Test void monkeySeesCaptainDisguiseAndFalseInvestigation() {
        add(0, "CREW_MONKEY", true); // 0번은 선장으로 위장된다.  (0 % 4 = 0 → CREW_CAPTAIN)
        add(1, "PIRATE_RAIDER", true);
        add(2, "CREW_SAILOR", true);
        add(3, "CREW_CAPTAIN", true);
        // 원숭이 본인에게는 선장으로 보인다
        assertEquals("CREW_CAPTAIN", roles.getMyRole(roomsId, 100L).role().code());
        submit(0, "INVESTIGATE_FACTION", 1); // 가짜 선장이 해적 조사
        submit(3, "INVESTIGATE_FACTION", 1); // 진짜 선장이 해적 조사
        nights.resolveNight(roomsId);
        assertTrue(reportText(0).contains("CREW"));   // 원숭이: 반대로 "CREW"
        assertTrue(reportText(3).contains("PIRATE")); // 진짜 선장: "PIRATE"
    }
}
