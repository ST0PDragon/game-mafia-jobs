// [파일 역할] 서버의 핵심 규칙 2: 한 밤 동안 제출된 행동을 **한꺼번에** 판정한다.
//   제출 순서와 상관없이 결과가 같도록 "차단 → 방문 기록 → 보호 → 해적 공격 → 조사 보고 → 승리 검사" 순서로 처리한다.
//   아직 타이머가 없어서 지금은 테스트 코드가 resolveNight / beginNextNight를 직접 호출한다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/service/NightResolutionService.java

package com.doronyong.mafia.service; // 서비스 패키지

import com.doronyong.mafia.domain.ActionCode;                  // 능력 코드
import com.doronyong.mafia.domain.Faction;                     // 진영
import com.doronyong.mafia.domain.PirateAttackSelectionEntity; // 해적 대상 선택
import com.doronyong.mafia.domain.RoomActionEntity;            // 행동 기록
import com.doronyong.mafia.domain.RoomEntity;                  // 방
import com.doronyong.mafia.domain.RoomPhase;                   // 단계
import com.doronyong.mafia.domain.RoomPlayerEntity;            // 참가자
import com.doronyong.mafia.domain.RoomReportEntity;            // 보고
import com.doronyong.mafia.repository.RoomActionRepository;    // 이하 Repository
import com.doronyong.mafia.repository.PirateAttackSelectionRepository;
import com.doronyong.mafia.repository.RoomPlayerRepository;
import com.doronyong.mafia.repository.RoomReportRepository;
import com.doronyong.mafia.repository.RoomRepository;
import java.util.ArrayList;               // 방문 기록 리스트
import java.util.HashMap;                 // playerId → 참가자 빠른 조회용
import java.util.List;
import java.util.Map;
import java.util.Set;                     // 중복 없는 집합(차단된 사람, 보호된 사람)
import java.util.stream.Collectors;       // stream 결과를 Set/문자열로 모으는 도구
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 한 밤의 행동을 동시에 판정한다. 게임 진행 타이머가 밤을 끝낼 때 내부에서 한 번 호출한다.
 * 처리 순서: 차단 → 방문 기록/보호 → 해적 공격 → 개인 조사 보고 → 승리/낮 전환.
 * 행동 제출 순서는 결과에 영향을 주지 않는다. 새 밤 능력의 우선순위를 바꿀 때 이 순서를 검토한다.
 */
@Service
public class NightResolutionService {
    // 필요한 Repository/서비스
    private final RoomRepository rooms;
    private final RoomPlayerRepository players;
    private final RoomActionRepository actions;
    private final PirateAttackSelectionRepository pirateSelections;
    private final RoomReportRepository reports;
    private final RoomOutcomeService outcome;

    // 생성자 주입
    public NightResolutionService(RoomRepository rooms, RoomPlayerRepository players,
                                  RoomActionRepository actions,
                                  PirateAttackSelectionRepository pirateSelections,
                                  RoomReportRepository reports,
                                  RoomOutcomeService outcome) {
        this.rooms = rooms;
        this.players = players;
        this.actions = actions;
        this.pirateSelections = pirateSelections;
        this.reports = reports;
        this.outcome = outcome;
    }

    // 밤 판정. 하나의 트랜잭션이라 중간에 오류가 나면 사망·보고·단계 변경이 모두 취소된다.
    @Transactional
    public void resolveNight(Long roomsId) {
        // ── 0. 준비 ──
        // 행동 제출도 같은 방 잠금을 사용한다. 밤 종료와 마지막 제출이 겹쳐도 서로 섞이지 않는다.
        RoomEntity room = rooms.lockById(roomsId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        // 밤이 아니면 판정 불가. 같은 밤을 두 번 판정하는 것도 여기서 막힌다(첫 판정 후 DAY가 되므로).
        if (room.getPhase() != RoomPhase.NIGHT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is not in the night phase");
        }

        // 참가자 전원(playerId 오름차순)
        List<RoomPlayerEntity> roster = players.findByRoomIdOrderByPlayerIdAsc(roomsId);
        // playerId → 참가자 지도. 리스트를 매번 뒤지지 않고 byId.get(3)처럼 바로 찾기 위해.
        Map<Long, RoomPlayerEntity> byId = new HashMap<>();
        roster.forEach(player -> byId.put(player.getPlayerId(), player));
        // 이번 밤에 제출된 행동 전부(제출 순)
        List<RoomActionEntity> submitted = actions.findByRoomIdAndPhaseAndRoundNumberOrderByIdAsc(
            roomsId, RoomPhase.NIGHT, room.getNightNumber());
        // 이번 밤의 제출만 읽는다. 다른 밤 기록은 능력 횟수 계산에는 쓰이지만 이번 판정에는 쓰지 않는다.

        // ── 1. 차단 ──
        // 실제 갑판장의 차단은 동시에 적용한다. 갑판장끼리 막아도 이미 낸 차단 행동은 유지한다.
        Set<Long> blocked = submitted.stream()
            // BLOCK 행동 중에서
            .filter(action -> action.getActionCode().equals(ActionCode.BLOCK.name()))
            // 실제 직업이 갑판장인 사람의 것만 (갑판장으로 위장한 원숭이의 BLOCK은 효과 없음)
            .filter(action -> "CREW_BOATSWAIN".equals(byId.get(action.getActorPlayerId()).getRoleCode()))
            // 차단 대상의 playerId를 모아 집합으로
            .map(RoomActionEntity::getTargetPlayerId).collect(Collectors.toSet());

        // 차단되지 않은 행동만 남긴다. 단, BLOCK 자체는 차단돼도 유지(위 "동시 적용" 규칙).
        List<RoomActionEntity> unblocked = submitted.stream()
            .filter(action -> !blocked.contains(action.getActorPlayerId())
                || action.getActionCode().equals(ActionCode.BLOCK.name()))
            .toList();

        // ── 2. 방문 기록 ──
        // 감시자가 보는 '방문'과 실제 효과는 다르다. 옛 버전의 해적 투표는 방문이 아니며,
        // 이번 밤 최종 공격에 한해 아래에서 실행 해적 한 명의 방문을 추가한다.
        // 원숭이는 효과가 없어도 차단되지 않았다면 방문 흔적은 남는다.
        // Visit = (누가, 누구에게, 무슨 행동으로). 망루지기·앵무새 보고의 재료.
        List<Visit> visits = new ArrayList<>();
        for (RoomActionEntity action : unblocked) {
            // 옛 TEAM_ATTACK_VOTE만 빼고 모든 행동을 방문으로 기록
            if (!action.getActionCode().equals(ActionCode.TEAM_ATTACK_VOTE.name())) {
                visits.add(new Visit(action.getActorPlayerId(), action.getTargetPlayerId(),
                    action.getActionCode()));
            }
        }
        // 실제 효과가 있는 행동 = 차단되지 않았고 + 원숭이가 아닌 사람의 행동
        List<RoomActionEntity> effective = unblocked.stream()
            // 위장 행동은 받되 실제 보호·차단·조사 효과에는 포함하지 않는다.
            .filter(action -> !"CREW_MONKEY".equals(byId.get(action.getActorPlayerId()).getRoleCode()))
            .toList();

        // ── 3. 보호 ──
        // 효과 있는 PROTECT의 대상들을 집합으로
        Set<Long> protectedPlayers = effective.stream()
            .filter(action -> action.getActionCode().equals(ActionCode.PROTECT.name()))
            .map(RoomActionEntity::getTargetPlayerId).collect(Collectors.toSet());

        // ── 4. 해적 공격 ──
        // 공유 대상은 이번 밤 마지막 선택이다. 표 수는 세지 않는다. 아무도 선택하지 않았다면 공격도 없다.
        PirateAttackSelectionEntity selected = pirateSelections
            .findTopByRoomIdAndNightNumberOrderByIdDesc(roomsId, room.getNightNumber())
            .orElse(null);
        // 살아 있고 차단되지 않은 해적 한 명이 공격을 실행한다. 전원이 차단되면 공격할 수 없다.
        RoomPlayerEntity executor = roster.stream()
            .filter(RoomPlayerEntity::isAlive)                               // 산 사람 중
            .filter(player -> "PIRATE_RAIDER".equals(player.getRoleCode()))  // 해적 직업(앵무새 제외)이고
            .filter(player -> !blocked.contains(player.getPlayerId()))       // 차단되지 않은
            .findFirst().orElse(null); // roster는 playerId 오름차순이다.  → 가장 작은 번호가 실행자
        // 대상이 정해져 있고 실행할 해적이 있을 때만
        if (selected != null && executor != null) {
            Long victimId = selected.getTargetPlayerId(); // 공격 대상
            // 실행 해적의 방문 기록 추가(망루지기·앵무새가 볼 수 있게). 선택한 해적이 아니라 "실행자"가 기록된다.
            visits.add(new Visit(executor.getPlayerId(), victimId, "PIRATE_ATTACK"));
            RoomPlayerEntity victim = byId.get(victimId);
            // 대상이 존재하고, 아직 살아 있고, 보호받지 않았으면 사망
            if (victim != null && victim.isAlive() && !protectedPlayers.contains(victimId)) {
                victim.kill();
                // 사망 소식은 공개 정보 → 참가자 전원에게 한 줄씩
                for (RoomPlayerEntity recipient : roster) {
                    report(roomsId, recipient.getPlayerId(), room.getNightNumber(),
                        "NIGHT_DEATH", victimId + "번 플레이어가 밤에 사망했습니다.");
                }
            }
        }

        // ── 5. 개인 조사 보고 ──
        // 방문 기록이 모두 모인 뒤(해적 공격 포함)에 보고를 만들어야 망루지기가 해적 방문을 볼 수 있다.
        for (RoomActionEntity action : effective) {
            // 결과는 수신자별 room_reports에 적는다. GET /me/reports가 본인 것만 반환한다.
            RoomPlayerEntity actor = byId.get(action.getActorPlayerId());
            if (!actor.isAlive()) continue; // 죽은 플레이어에게 조사 결과를 전달하지 않는다.
            Long targetId = action.getTargetPlayerId();
            // 문자열 → enum 으로 바꿔 switch. "->" 화살표 문법(자바 14+)은 break가 필요 없다.
            switch (ActionCode.valueOf(action.getActionCode())) {
                // 선장: 대상의 실제 진영
                case INVESTIGATE_FACTION -> report(roomsId, actor.getPlayerId(), room.getNightNumber(),
                    "FACTION_RESULT", targetId + "번의 진영: " + outcome.factionOf(byId.get(targetId)));
                // 망루지기: 대상을 방문한 사람들(자기 자신 제외)의 번호
                case WATCH_VISITORS -> {
                    String visitorIds = visits.stream()
                        .filter(visit -> visit.targetId().equals(targetId)             // 대상에게 온 방문 중
                            && !visit.actorId().equals(actor.getPlayerId()))           // 내 방문은 빼고
                        .map(visit -> visit.actorId().toString()).distinct().sorted() // 번호만, 중복 제거, 정렬
                        .collect(Collectors.joining(", "));                           // "2, 3" 형태 문자열로
                    report(roomsId, actor.getPlayerId(), room.getNightNumber(), "VISITORS",
                        targetId + "번 방문자: " + (visitorIds.isEmpty() ? "없음" : visitorIds));
                }
                // 앵무새: 대상이 한 행동(누구에게 무엇을)
                case WATCH_ACTION -> {
                    // 앵무새는 방문한 행동과 실제 해적 공격을 본다. 대상 변경 이력은 방문이 아니다.
                    String watched = visits.stream()
                        .filter(visit -> visit.actorId().equals(targetId))               // 대상이 "행동한 사람"인 방문
                        .map(visit -> visit.actionCode() + " → " + visit.targetId())     // "PROTECT → 5"
                        .collect(Collectors.joining(", "));
                    report(roomsId, actor.getPlayerId(), room.getNightNumber(), "WATCHED_ACTION",
                        targetId + "번의 행동: " + (watched.isEmpty() ? "없음" : watched));
                }
                // 주정뱅이: 시체의 실제 직업 코드(원숭이면 CREW_MONKEY가 그대로 보인다)
                case READ_CORPSE_ROLE -> report(roomsId, actor.getPlayerId(), room.getNightNumber(),
                    "CORPSE_ROLE", targetId + "번의 직업: " + byId.get(targetId).getRoleCode());
                // PROTECT, BLOCK은 위에서 이미 반영했고, 옛 TEAM_ATTACK_VOTE는 무시
                default -> { /* 차단·보호와 이전 버전의 투표 기록은 위에서 처리했거나 무시한다. */ }
            }
        }

        // ── 5-1. 원숭이의 가짜 조사 결과 ──
        // 원숭이의 위장 조사는 그럴듯한 반대 결과를 준다. 다른 위장 행동은 효과가 없다.
        // effective가 아니라 unblocked를 도는 이유: 원숭이 행동은 effective에서 빠져 있기 때문.
        for (RoomActionEntity action : unblocked) {
            RoomPlayerEntity actor = byId.get(action.getActorPlayerId());
            // 살아 있는 원숭이가 선장 흉내(INVESTIGATE_FACTION)를 냈을 때만
            if (actor.isAlive() && "CREW_MONKEY".equals(actor.getRoleCode())
                && action.getActionCode().equals(ActionCode.INVESTIGATE_FACTION.name())) {
                Faction actual = outcome.factionOf(byId.get(action.getTargetPlayerId())); // 진짜 진영
                Faction falseResult = actual == Faction.PIRATE ? Faction.CREW : Faction.PIRATE; // 반대로 뒤집기
                // 진짜 선장과 똑같은 형식의 보고를 보내 원숭이가 눈치채지 못하게 한다
                report(roomsId, actor.getPlayerId(), room.getNightNumber(), "FACTION_RESULT",
                    action.getTargetPlayerId() + "번의 진영: " + falseResult);
            }
        }

        // ── 6. 마무리 ──
        // 이번 밤 행동을 모두 "판정 완료"로 표시(차단된 행동 포함)
        submitted.forEach(RoomActionEntity::resolve);
        // 사망 적용 후 최종 인원으로 승리를 확인한다. 승자가 있으면 DAY로 되돌리지 않는다.
        outcome.check(room, roster);
        // 승자가 없으면 낮으로 전환
        if (room.getPhase() != RoomPhase.GAME_OVER) room.completeNight();
        // 메서드가 끝나면 트랜잭션 커밋 → 변경 감지로 사망/상태/단계 UPDATE가 한꺼번에 나가고 방 잠금이 풀린다.
    }

    /** 낮 진행 타이머가 끝날 때 내부에서 호출한다. 밤 번호를 올려 새 행동을 받는다. */
    @Transactional
    public void beginNextNight(Long roomsId) {
        // 단계 전환도 같은 방 잠금을 잡는다(낮 행동 제출과 섞이지 않게)
        RoomEntity room = rooms.lockById(roomsId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        // 낮이 아니면 409 (GAME_OVER 상태에서 호출돼도 여기서 막힌다)
        if (room.getPhase() != RoomPhase.DAY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is not in the day phase");
        }
        room.beginNextNight(); // 라운드 +1, 단계 NIGHT
        // ⚠ 낮 투표(VOTE)가 생기면 DAY → VOTE → NIGHT 흐름에 맞게 이 부분을 바꿔야 한다.
    }

    // 보고 한 줄 저장 도우미
    private void report(Long roomsId, Long recipientPlayerId, int round,
                        String type, String message) {
        reports.save(new RoomReportEntity(roomsId, recipientPlayerId, round, type, message));
    }

    // record는 클래스 안에도 선언할 수 있다. 이 서비스 안에서만 쓰는 "방문 한 건" 값 객체.
    // visit.actorId(), visit.targetId(), visit.actionCode()로 값을 꺼낸다.
    private record Visit(Long actorId, Long targetId, String actionCode) {}
}
