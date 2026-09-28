# 해적 직업 API와 능력 판정

Spring Boot 서버는 직업 조회, 비공개 직업 확인, 행동 제출, 비공개 결과 조회를 담당합니다. 현재 Unity의 `Citizen/Mafia/Police` 로컬 게임과 온라인 서버는 아직 연결되지 않았습니다. 통신 DTO는 `Assets/Scripts/Network/RoleApiClient.cs`에 있습니다.

## 이번에 활성화한 직업

| 진영 | 직업 (`role.code`) | 행동 (`actionCode`) | 규칙 |
| --- | --- | --- | --- |
| 선원 | 선장 (`CREW_CAPTAIN`) | `INVESTIGATE_FACTION` | 밤마다 1명의 현재 진영을 본인에게 알림 |
| 선원 | 선의 (`CREW_DOCTOR`) | `PROTECT` | 밤마다 1명 보호. 연속 자기 보호 금지 |
| 선원 | 망루지기 (`CREW_LOOKOUT`) | `WATCH_VISITORS` | 밤마다 감시 대상의 방문자 번호를 본인에게 알림 |
| 선원 | 갑판장 (`CREW_BOATSWAIN`) | `BLOCK` | 밤마다 1명의 다른 밤 능력을 차단 |
| 선원 | 포수 (`CREW_GUNNER`) | `DAY_SHOOT` | 낮에 게임당 한 번 즉시 처형. 첫날 가능 |
| 선원 | 원숭이 (`CREW_MONKEY`) | 위장 직업의 행동 | 선장·선의·망루지기·갑판장 중 하나로 보이며 행동 효과가 없음. 선장 위장 조사는 반대 진영 결과를 받음 |
| 선원 | 주정뱅이 (`CREW_DRUNK`) | `READ_CORPSE_ROLE` | 밤에 사망자 직업 확인, 게임당 2회 |
| 선원 | 일반 선원 (`CREW_SAILOR`) | 없음 | 능력 없이 토론과 투표에 참여 |
| 해적 | 해적 (`PIRATE_RAIDER`) | `SELECT_ATTACK_TARGET` | 해적끼리 서로 알고, 밤 동안 공유 공격 대상을 선택·변경 |
| 해적 | 앵무새 (`PIRATE_PARROT`) | `WATCH_ACTION` | 밤마다 1명의 행동 감시. 공유 공격 대상 조회는 가능하지만 변경은 불가 |

`크라켄`, `세이렌`, `유령 선장`은 구현하지 않았습니다. 이전 직업 사전의 `PIRATE_KRAKEN`, `PIRATE_SPY`, `CREW_GHOST`, `NEUTRAL_MONKEY`는 기존 DB 참조를 위해 남겨 두되 공개 목록에서는 비활성화했습니다. 새 원숭이는 **선원 진영**입니다.

### 밤 판정과 승리

방 타이머가 `NightResolutionService.resolveNight(roomsId)`를 호출하면 행동을 한꺼번에 판정합니다. 갑판장 차단을 먼저 계산하며, 갑판장끼리의 차단은 동시에 성립합니다. 차단된 능력은 효과와 방문 기록이 없습니다. 원숭이 행동은 효과가 없지만 방문 기록은 남습니다.

해적 직업(`PIRATE_RAIDER`)은 밤 동안 같은 공격 대상을 공유합니다. 어느 해적 직업이든 `SELECT_ATTACK_TARGET`을 다시 제출하면 **마지막 선택이 최종 대상**이 됩니다. 과반수 동의는 필요하지 않습니다. 아무도 선택하지 않거나 살아 있고 차단되지 않은 해적 직업이 없으면 공격하지 않습니다. 공격할 수 있는 해적 중 가장 작은 `playerId`가 공격 방문자로 기록됩니다. 선의가 보호한 대상은 죽지 않습니다. 밤 조사 결과는 해당 직업 플레이어의 보고서에만 기록됩니다. 밤 사망과 포수 처형은 모든 방 참가자의 보고서에 기록됩니다.

해적이 모두 사망하면 선원 승리, 살아 있는 해적 수가 살아 있는 선원 수 이상이면 해적 승리입니다. 원래 기획의 **목적지 도착 승리**는 항해/라운드 제한이 정해지지 않아 아직 없습니다. `NightResolutionService.beginNextNight(roomsId)`는 낮이 끝났을 때 내부에서 호출할 단계 전환입니다. 방 타이머, 낮 투표, 자동 단계 전환은 아직 연결되지 않았습니다.

## API

| 메서드 | 경로 | 인증 | 내용 |
| --- | --- | --- | --- |
| GET | `/api/v1/roles?faction=CREW` | 불필요 | 활성 직업 목록 |
| GET | `/api/v1/games/{gamesId}/me/role` | Bearer JWT | 내 직업, 능력, 해적 동료 번호 |
| POST | `/api/v1/games/{gamesId}/actions` | Bearer JWT | 낮/밤 행동 제출 |
| GET | `/api/v1/games/{gamesId}/me/reports` | Bearer JWT | 내게 공개된 결과 |
| GET | `/api/v1/games/{gamesId}/pirate-attack` | Bearer JWT | 살아 있는 해적 팀의 이번 밤 공유 공격 대상 |

JWT의 `sub`는 숫자 `userId`입니다. 서버는 이를 게임 참가자와 연결합니다. 공개 API의 `gamesId`는 현재 내부 `rooms.id` 및 `room_players.room_id`와 같은 숫자입니다. 내부 테이블 이름은 이번 경로 변경에서 유지했습니다. 응답 JSON의 식별자 필드도 `gamesId`입니다. 요청의 `targetPlayerId`는 `userId`가 아닌 **게임 안의 `playerId`**입니다. 이 번호는 향후 참가자 목록 API에서 제공해야 합니다.

행동 요청:

```json
{"requestId":"7e28e60d-4879-4d18-845d-ae62c78ce916","actionCode":"PROTECT","targetPlayerId":3}
```

서버는 HTTP `202`로 `status: "SUBMITTED"`를 돌려줍니다. 포수의 `DAY_SHOOT`는 즉시 `RESOLVED`, 해적의 `SELECT_ATTACK_TARGET`은 `SELECTED`가 됩니다. 통신 실패 후에는 **같은 UUID와 같은 내용**으로 재시도합니다. 일반 행동은 같은 단계·라운드·`actionCode`에 새 요청을 보내면 `409`입니다. 해적 대상 변경만 예외로, 같은 밤에 **새 UUID**로 다시 제출하면 공유 대상이 바뀝니다. 능력 응답의 `phase`는 사용 단계이고, `remainingUses: -1`은 게임 전체 횟수 제한이 없다는 뜻입니다.

공유 대상 조회 예시 (`GET /api/v1/games/12/pirate-attack`):

```json
{"gamesId":12,"nightNumber":1,"hasTarget":true,"targetPlayerId":3,"selectedByPlayerId":5}
```

아직 대상이 없다면 `hasTarget`은 `false`이고 두 `playerId`는 `0`입니다. `playerId=0`도 유효하므로 Unity에서는 반드시 `hasTarget`으로 선택 여부를 판단합니다. 이 조회는 살아 있는 해적 진영에게만 허용됩니다.

### 코드 실행 흐름

1. `RoleController`가 URL의 `gamesId`와 JWT의 `sub`를 읽어 `RoleService`로 넘깁니다. 이때 `gamesId`는 내부 `rooms.id`에 대응합니다.
2. `RoleService.getMyRole`은 본인 참가 기록을 찾고, 원숭이에게는 실제 직업 대신 위장 직업을 반환합니다. 해적 진영이면 동료 `playerId`도 돌려줍니다.
3. `RoleService.submitAction`은 단계, 생존 여부, 직업 권한, 중복·횟수, 대상을 검사합니다. 일반 행동은 `room_actions`, 공유 공격 대상 변경은 `pirate_attack_selections`에 저장합니다. 포수 사격만 즉시 사망과 승리를 판정합니다.
4. 게임 진행 타이머가 `NightResolutionService.resolveNight(gamesId)`를 호출하면 그 밤의 행동을 함께 판정합니다. 결과는 수신자별 `room_reports`에 기록되고, 게임 단계가 `DAY` 또는 `GAME_OVER`로 바뀝니다.
5. Unity `RoleApiClient.GetMyReports`는 JWT 사용자에게 공개된 보고만 가져옵니다. 낮 종료 후 진행 서비스는 `NightResolutionService.beginNextNight(gamesId)`를 호출합니다.

## 실행 및 수정 위치

JDK 17 이상과 Maven으로 `server/`에서 `mvn test`, `mvn spring-boot:run`을 실행합니다. 기본 DB는 개발용 임시 H2입니다. 운영 환경에는 `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_ISSUER_URI`를 설정합니다. API는 HTTPS로 공개해야 합니다.

| 변경할 내용 | 파일 |
| --- | --- |
| 직업 이름·공개 여부 | 새 Flyway 마이그레이션, `RoleEntity` |
| 행동 단계·횟수·대상 규칙 | `ActionCode`, `RoleService` |
| 밤 행동 우선순위·결과 | `NightResolutionService` |
| 해적 공유 공격 대상 | `PirateAttackSelectionEntity`, `PirateAttackSelectionRepository`, `RoleService` |
| 승리 규칙 | `RoomOutcomeService` |
| HTTP 응답 필드 | `RoleApiDtos`, `RoleController`, Unity `RoleApiClient` |
| 방 생성·직업 배정·타이머 | 새 방 진행 서비스에서 `RoomPlayerEntity.assignRole`, `resolveNight`, `beginNextNight` 호출 |

기존 `V1__create_role_api_tables.sql`과 `V2__active_roles_and_actions.sql`은 수정하지 않고 `V3__pirate_shared_attack_target.sql`을 추가했습니다. 온라인 출시를 위해서는 로그인/JWT 발급, 방 생성과 참가, 역할 배정, 낮 투표, 방 타이머, 실시간 동기화, 목적지 승리 규칙을 이어서 구현해야 합니다.

### 규칙을 수정할 때

- **직업 추가·이름 변경:** 새 `V4__...sql`처럼 새 Flyway 파일로 `roles`를 변경합니다. 적용한 `V1`·`V2`·`V3`를 고치면 마이그레이션 체크섬이 달라집니다.
- **능력 추가:** `ActionCode`에 단계·전체 사용 횟수·대상 정책을 정의하고, `RoleService`에 제출 제한을, 밤 능력이라면 `NightResolutionService`에 실제 효과와 비공개 보고를 추가합니다. 포수처럼 낮 즉시 행동이면 `RoleService`의 `DAY_SHOOT` 분기를 참고합니다.
- **승리/진행 변경:** 사망 판정 후 `RoomOutcomeService.check`를 호출합니다. 라운드 단계 전환은 `RoomEntity`와 `NightResolutionService`에서 확인합니다. 낮 투표 처형을 만들 때도 승리를 다시 검사해야 합니다.
- **응답 필드/URL 변경:** `RoleController`, `RoleApiDtos`, Unity `RoleApiClient`, 통합 테스트를 같이 수정합니다. `room_id` 같은 내부 DB 명칭은 공개 `gamesId`와 달라도 됩니다.
- **확인:** `server/`에서 `mvn test`를 실행합니다. `RoleApiIntegrationTest`는 인증 범위, 행동 중복, 밤 판정, 원숭이 위장, 포수 사격 등을 검증합니다.
