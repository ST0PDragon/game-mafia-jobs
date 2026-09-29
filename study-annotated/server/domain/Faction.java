// [파일 역할] 진영(팀) 종류를 나타내는 enum. 승리 판정과 "해적끼리 서로 알기"에 쓰인다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/domain/Faction.java

package com.doronyong.mafia.domain; // 도메인(게임 규칙의 핵심 개념) 패키지

// enum = 정해진 값만 가질 수 있는 타입. 문자열 "CREW" 대신 Faction.CREW를 쓰면 오타를 컴파일러가 잡아 준다.
public enum Faction {
    // CREW   : 선원 진영 (선장, 선의, 망루지기, 갑판장, 포수, 원숭이, 주정뱅이, 일반 선원)
    // PIRATE : 해적 진영 (해적, 앵무새)
    // NEUTRAL: 중립 진영. 지금 활성화된 직업 중에는 없다(옛 NEUTRAL_MONKEY만 비활성 상태로 남아 있음).
    CREW, PIRATE, NEUTRAL
}
