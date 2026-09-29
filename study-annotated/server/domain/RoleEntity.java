// [파일 역할] roles 테이블(직업 사전) 한 줄을 표현하는 JPA 엔티티.
//   "선장은 선원 진영이고 능력은 INVESTIGATE_FACTION" 같은 **공개 정의**만 담는다.
//   누가 어떤 직업을 받았는지는 RoomPlayerEntity에 따로 저장한다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/domain/RoleEntity.java

package com.doronyong.mafia.domain; // 도메인 패키지

import jakarta.persistence.Column;     // 필드 ↔ 컬럼 매핑 설정(이름, null 허용 등)
import jakarta.persistence.Entity;     // 이 클래스가 DB 테이블과 연결된 엔티티임을 표시
import jakarta.persistence.EnumType;   // enum을 DB에 어떤 형태(STRING/ORDINAL)로 저장할지
import jakarta.persistence.Enumerated; // enum 필드임을 표시
import jakarta.persistence.Id;         // 기본키(PK) 필드 표시
import jakarta.persistence.Table;      // 연결할 테이블 이름 지정

/** 모든 플레이어에게 공개되는 직업 정의. 실제 배정 결과는 RoomPlayerEntity에 저장한다. */
@Entity                 // JPA가 관리하는 객체. 조회하면 DB 한 줄이 이 객체 하나가 된다.
@Table(name = "roles")  // 클래스 이름(RoleEntity)과 테이블 이름(roles)이 달라서 직접 지정
public class RoleEntity {
    // 기본키. 자동 증가 숫자가 아니라 "CREW_CAPTAIN" 같은 문자열 코드를 그대로 PK로 쓴다.
    // (@GeneratedValue가 없으므로 값은 SQL 마이그레이션에서 직접 넣는다.)
    @Id
    private String code;

    // 화면에 보여 줄 한글 이름(예: "선장"). NOT NULL 컬럼.
    @Column(nullable = false)
    private String name;

    // 진영. EnumType.STRING이라 DB에는 "CREW"/"PIRATE" 문자열로 저장된다.
    // (ORDINAL이면 0,1,2 숫자로 저장되어 enum 순서를 바꿀 때 데이터가 꼬이므로 STRING이 안전하다.)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Faction faction;

    // 능력 규칙이 아직 없는 직업은 null이다. 새 능력을 추가할 때 서비스 검증도 함께 확장한다.
    // 값은 ActionCode enum 이름과 같은 문자열(예: "PROTECT"). 일반 선원은 null.
    @Column(name = "action_code")  // 자바 필드명(actionCode)과 컬럼명(action_code)이 달라 지정
    private String actionCode;

    // 보류된 직업도 과거 방의 참조를 위해 남겨 두고 공개 목록에서만 제외한다.
    // false면 GET /roles 목록에 안 나온다(크라켄, 스파이 등). V2 마이그레이션에서 추가된 컬럼.
    @Column(nullable = false)
    private boolean enabled;

    // JPA가 DB에서 읽어 온 값을 채우려면 인자 없는 생성자가 필요하다.
    // 직업은 코드에서 새로 만들 일이 없으므로(SQL로만 추가) 다른 생성자도 없다.
    protected RoleEntity() {}

    // 읽기 전용 getter들. setter가 없으므로 서비스 코드에서 직업 정의를 바꿀 수 없다.
    public String getCode() { return code; }             // 직업 코드
    public String getName() { return name; }             // 한글 이름
    public Faction getFaction() { return faction; }      // 진영
    public String getActionCode() { return actionCode; } // 능력 코드(없으면 null)
    public boolean isEnabled() { return enabled; }       // boolean getter는 관례상 isXxx
}
