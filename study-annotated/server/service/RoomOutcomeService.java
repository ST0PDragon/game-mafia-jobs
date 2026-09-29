// [파일 역할] 승리 조건 검사 + "이 플레이어는 어느 진영인가" 계산.
//   사망이 생기는 곳(밤 판정, 포수 사격)에서 호출된다. 짧아서 서비스 코드 읽기 연습으로 좋다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/service/RoomOutcomeService.java

package com.doronyong.mafia.service; // 서비스 패키지

import com.doronyong.mafia.domain.Faction;          // 진영 enum
import com.doronyong.mafia.domain.RoleEntity;       // 직업 정의(진영 정보가 여기 있다)
import com.doronyong.mafia.domain.RoomEntity;       // 방(승리 시 finish 호출)
import com.doronyong.mafia.domain.RoomPhase;        // 단계 enum
import com.doronyong.mafia.domain.RoomPlayerEntity; // 참가자
import com.doronyong.mafia.repository.RoleRepository; // 직업 조회
import java.util.List;                              // 참가자 목록
import org.springframework.stereotype.Service;      // 서비스 빈 표시

/**
 * 사망 처리 후 승리 여부를 계산한다. 밤 판정과 포수 낮 사격에서 호출한다.
 * 낮 투표 처형을 추가하면 그 처리 직후에도 check를 호출해야 한다.
 * 목적지 도착 승리는 아직 정의되지 않아 게임 진행 기능에서 추가해야 한다.
 */
@Service // 스프링이 객체를 하나 만들어 두고 RoleService, NightResolutionService에 주입한다
public class RoomOutcomeService {
    // 직업 → 진영을 알기 위해 필요
    private final RoleRepository roles;

    // 생성자 주입
    public RoomOutcomeService(RoleRepository roles) {
        this.roles = roles;
    }

    // 현재 생존자 수로 승패를 판단하고, 승자가 있으면 방을 GAME_OVER로 바꾼다.
    // @Transactional이 없지만, 호출하는 쪽(RoleService/NightResolutionService)의 트랜잭션 안에서 실행된다.
    public void check(RoomEntity room, List<RoomPlayerEntity> players) {
        // 이미 끝난 게임이면 다시 판정하지 않는다(승자가 바뀌는 것을 방지).
        if (room.getPhase() == RoomPhase.GAME_OVER) return;

        // 살아 있는 선원 수: 생존자만 거른 뒤(filter), 진영이 CREW인 사람만 다시 거르고, 개수를 센다(count).
        long crew = players.stream().filter(RoomPlayerEntity::isAlive)
            .filter(player -> factionOf(player) == Faction.CREW).count();
        // 살아 있는 해적 수 (앵무새도 PIRATE 진영이라 포함된다)
        long pirates = players.stream().filter(RoomPlayerEntity::isAlive)
            .filter(player -> factionOf(player) == Faction.PIRATE).count();
        if (pirates == 0 && crew > 0) {
            // 살아 있는 해적이 없어졌을 때만 선원 승리다.
            room.finish(Faction.CREW);
        } else if (pirates > 0 && pirates >= crew) {
            // 해적 수가 선원 수 이상이면 승리다. 인원 규칙을 바꾸려면 여기와 테스트를 수정한다.
            room.finish(Faction.PIRATE);
        }
        // 둘 다 아니면 게임 계속. (해적 0, 선원 0처럼 모두 죽은 경우에도 승자 없이 계속된다 — 드문 예외 상황)
    }

    // 플레이어의 **실제** 직업(roleCode)으로 진영을 구한다. 원숭이는 위장과 상관없이 CREW.
    public Faction factionOf(RoomPlayerEntity player) {
        // roles 테이블에서 직업 정의를 찾는다. 같은 트랜잭션에서는 JPA 1차 캐시 덕분에 같은 직업을 반복 조회해도 DB에 다시 가지 않는다.
        RoleEntity role = roles.findById(player.getRoleCode())
            // 배정된 직업이 사전에 없으면 데이터가 깨진 것이므로 프로그램 오류로 처리
            .orElseThrow(() -> new IllegalStateException("Assigned role is missing"));
        return role.getFaction(); // CREW / PIRATE / NEUTRAL
    }
}
