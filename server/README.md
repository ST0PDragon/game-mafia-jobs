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
| 해적 | 해적 (`PIRATE_RAIDER`) | `TEAM_ATTACK_VOTE` | 해적끼리 서로 알고 밤마다 공격 대상 투표 |
| 해적 | 앵무새 (`PIRATE_PARROT`) | `WATCH_ACTION`, `TEAM_ATTACK_VOTE` | 밤마다 1명의 행동 감시와 해적 공격 투표를 각각 제출 가능 |

`크라켄`, `세이렌`, `유령 선장`은 구현하지 않았습니다. 이전 직업 사전의 `PIRATE_KRAKEN`, `PIRATE_SPY`, `CREW_GHOST`, `NEUTRAL_MONKEY`는 기존 DB 참조를 위해 남겨 두되 공개 목록에서는 비활성화했습니다. 새 원숭이는 **선원 진영**입니다.

### 밤 판정과 승리

방 타이머가 `NightResolutionService.resolveNight(roomsId)`를 호출하면 행동을 한꺼번에 판정합니다. 갑판장 차단을 먼저 계산하며, 갑판장끼리의 차단은 동시에 성립합니다. 차단된 능력은 효과와 방문 기록이 없습니다. 원숭이 행동은 효과가 없지만 방문 기록은 남습니다.

살아 있는 해적 **전체의 과반수**가 같은 대상을 선택해야 밤 공격 1회가 성립합니다. 가장 작은 `playerId`를 가진 찬성자가 공격 방문자로 기록됩니다. 선의가 보호한 대상은 죽지 않습니다. 밤 조사 결과는 해당 직업 플레이어의 보고서에만 기록됩니다. 밤 사망과 포수 처형은 모든 방 참가자의 보고서에 기록됩니다.

해적이 모두 사망하면 선원 승리, 살아 있는 해적 수가 살아 있는 선원 수 이상이면 해적 승리입니다. 원래 기획의 **목적지 도착 승리**는 항해/라운드 제한이 정해지지 않아 아직 없습니다. `NightResolutionService.beginNextNight(roomsId)`는 낮이 끝났을 때 내부에서 호출할 단계 전환입니다. 방 타이머, 낮 투표, 자동 단계 전환은 아직 연결되지 않았습니다.

## API

| 메서드 | 경로 | 인증 | 내용 |
| --- | --- | --- | --- |
| GET | `/api/v1/roles?faction=CREW` | 불필요 | 활성 직업 목록 |
| GET | `/api/v1/games/{gamesId}/me/role` | Bearer JWT | 내 직업, 능력, 해적 동료 번호 |
| POST | `/api/v1/games/{gamesId}/actions` | Bearer JWT | 낮/밤 행동 제출 |
| GET | `/api/v1/games/{gamesId}/me/reports` | Bearer JWT | 내게 공개된 결과 |

JWT의 `sub`는 숫자 `userId`입니다. 서버는 이를 게임 참가자와 연결합니다. 공개 API의 `gamesId`는 현재 내부 `rooms.id` 및 `room_players.room_id`와 같은 숫자입니다. 내부 테이블 이름은 이번 경로 변경에서 유지했습니다. 응답 JSON의 식별자 필드도 `gamesId`입니다. 요청의 `targetPlayerId`는 `userId`가 아닌 **게임 안의 `playerId`**입니다. 이 번호는 향후 참가자 목록 API에서 제공해야 합니다.

행동 요청:

```json
{"requestId":"7e28e60d-4879-4d18-845d-ae62c78ce916","actionCode":"PROTECT","targetPlayerId":3}
```

서버는 HTTP `202`로 `status: "SUBMITTED"`를 돌려줍니다. 포수의 `DAY_SHOOT`는 즉시 `RESOLVED`가 됩니다. 통신 실패 후에는 **같은 UUID와 같은 내용**으로 재시도합니다. 같은 단계·라운드·`actionCode`의 새 요청은 `409`입니다. 능력 응답의 `phase`는 사용 단계이고, `remainingUses: -1`은 게임 전체 횟수 제한이 없다는 뜻입니다. 같은 행동은 한 밤에 한 번이며 앵무새는 감시와 해적 투표를 각각 한 번 제출할 수 있습니다.

### 코드 실행 흐름

1. `RoleController`가 URL의 `gamesId`와 JWT의 `sub`를 읽어 `RoleService`로 넘깁니다. 이때 `gamesId`는 내부 `rooms.id`에 대응합니다.
2. `RoleService.getMyRole`은 본인 참가 기록을 찾고, 원숭이에게는 실제 직업 대신 위장 직업을 반환합니다. 해적이면 동료 `playerId`와 팀 공격 투표 능력을 더합니다.
3. `RoleService.submitAction`은 단계, 생존 여부, 직업 권한, 중복·횟수, 대상을 검사하고 `room_actions`에 저장합니다. 포수 사격만 즉시 사망과 승리를 판정합니다.
4. 게임 진행 타이머가 `NightResolutionService.resolveNight(gamesId)`를 호출하면 그 밤의 행동을 함께 판정합니다. 결과는 수신자별 `room_reports`에 기록되고, 게임 단계가 `DAY` 또는 `GAME_OVER`로 바뀝니다.
5. Unity `RoleApiClient.GetMyReports`는 JWT 사용자에게 공개된 보고만 가져옵니다. 낮 종료 후 진행 서비스는 `NightResolutionService.beginNextNight(gamesId)`를 호출합니다.

## 실행 및 수정 위치

JDK 17 이상과 Maven으로 `server/`에서 `mvn test`, `mvn spring-boot:run`을 실행합니다. 기본 DB는 개발용 임시 H2입니다. 운영 환경에는 `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_ISSUER_URI`를 설정합니다. API는 HTTPS로 공개해야 합니다.

| 변경할 내용 | 파일 |
| --- | --- |
| 직업 이름·공개 여부 | 새 Flyway 마이그레이션, `RoleEntity` |
| 행동 단계·횟수·대상 규칙 | `ActionCode`, `RoleService` |
| 밤 행동 우선순위·결과 | `NightResolutionService` |
| 승리 규칙 | `RoomOutcomeService` |
| HTTP 응답 필드 | `RoleApiDtos`, `RoleController`, Unity `RoleApiClient` |
| 방 생성·직업 배정·타이머 | 새 방 진행 서비스에서 `RoomPlayerEntity.assignRole`, `resolveNight`, `beginNextNight` 호출 |

기존 `V1__create_role_api_tables.sql`은 수정하지 않고 `V2__active_roles_and_actions.sql`로 마이그레이션했습니다. 온라인 출시를 위해서는 로그인/JWT 발급, 방 생성과 참가, 역할 배정, 낮 투표, 방 타이머, 실시간 동기화, 목적지 승리 규칙을 이어서 구현해야 합니다.

### 규칙을 수정할 때

- **직업 추가·이름 변경:** 새 `V3__...sql`처럼 새 Flyway 파일로 `roles`를 변경합니다. 적용한 `V1`·`V2`를 고치면 마이그레이션 체크섬이 달라집니다.
- **능력 추가:** `ActionCode`에 단계·전체 사용 횟수·대상 정책을 정의하고, `RoleService`에 제출 제한을, 밤 능력이라면 `NightResolutionService`에 실제 효과와 비공개 보고를 추가합니다. 포수처럼 낮 즉시 행동이면 `RoleService`의 `DAY_SHOOT` 분기를 참고합니다.
- **승리/진행 변경:** 사망 판정 후 `RoomOutcomeService.check`를 호출합니다. 라운드 단계 전환은 `RoomEntity`와 `NightResolutionService`에서 확인합니다. 낮 투표 처형을 만들 때도 승리를 다시 검사해야 합니다.
- **응답 필드/URL 변경:** `RoleController`, `RoleApiDtos`, Unity `RoleApiClient`, 통합 테스트를 같이 수정합니다. `room_id` 같은 내부 DB 명칭은 공개 `gamesId`와 달라도 됩니다.
- **확인:** `server/`에서 `mvn test`를 실행합니다. `RoleApiIntegrationTest`는 인증 범위, 행동 중복, 밤 판정, 원숭이 위장, 포수 사격 등을 검증합니다.

## 역할 배정 담당자에게 전달할 계약

1. `gamesId`는 내부 `rooms.id`와 동일합니다. 게임 시작 전 `rooms.phase=SETUP`, `rooms.night_number=0`으로 만들고 참가자별 `room_players` 행을 생성합니다.
2. 참가자마다 `user_id`는 로그인/JWT `sub`와 일치해야 하고 `player_id`는 **해당 게임에서 중복되지 않는 번호**여야 합니다. 클라이언트가 보는 대상 번호는 이 `player_id`입니다.
3. 직업 코드는 위 표의 활성 직업만 사용합니다. 선원/해적 비율과 중복 직업 허용 여부는 배정 정책에서 결정해야 합니다. 현재 판정은 선원·해적 두 진영을 전제로 합니다.
4. `RoomPlayerEntity.assignRole(roleCode)`로 저장하세요. 원숭이의 실제 직업과 위장 직업을 함께 설정하는 메서드이므로 `role_code`만 직접 갱신하면 안 됩니다. 시작 시 전원 `alive=true`로 둡니다.
5. 모두 배정한 뒤 `rooms.phase`를 첫날 `DAY`, `rooms.night_number`를 `1`로 전환해야 `/me/role` 조회와 포수의 첫날 사격이 가능합니다. 현재 이 시작 전환 메서드와 배정 API는 없으며 배정 담당 파트에서 한 트랜잭션으로 구현해야 합니다. 게임 시작 이후에는 재배정하지 않습니다.
6. 실제 직업은 `/me/role`에서 JWT 소유자에게만 공개합니다. `PIRATE_RAIDER`와 `PIRATE_PARROT`에게는 `allies`에 해적 동료의 `playerId`가 포함됩니다. 일반 참가자 목록 API에 `role_code`나 `shown_role_code`를 포함하지 마세요.
