// [파일 역할] 게임(방)의 현재 단계. 행동을 받을 수 있는지 판단할 때 쓰인다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/domain/RoomPhase.java

package com.doronyong.mafia.domain; // 도메인 패키지

// 각 값은 DB rooms.phase 컬럼에 문자열("NIGHT" 등)로 저장된다(RoomEntity의 @Enumerated(STRING) 참고).
public enum RoomPhase {
    // SETUP    : 입장 직후, 아직 직업 배정 전
    // DAY      : 낮 토론. 포수의 DAY_SHOOT만 이 단계에서 가능
    // VOTE     : 낮 투표. ⚠ 아직 이 단계로 바꾸는 코드가 없다(정의만 존재).
    // NIGHT    : 밤. 대부분의 능력을 이때 제출
    // GAME_OVER: 승패가 난 뒤. 더 이상 행동을 받지 않는다.
    SETUP, DAY, VOTE, NIGHT, GAME_OVER
}
