// [파일 역할] roles 테이블 조회용 Repository.
//   Spring Data JPA는 "인터페이스 + 메서드 이름"만 보고 구현 클래스를 실행 시점에 자동으로 만들어 준다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/repository/RoleRepository.java

package com.doronyong.mafia.repository; // Repository 패키지

import com.doronyong.mafia.domain.Faction;    // 진영 enum
import com.doronyong.mafia.domain.RoleEntity; // 대상 엔티티
import java.util.List;                        // 여러 줄 결과
import org.springframework.data.jpa.repository.JpaRepository; // save/findById/findAll/delete 등 기본 메서드 제공

// JpaRepository<엔티티 타입, PK 타입>. RoleEntity의 PK는 String(code)이라 String.
// 상속만으로 findById("CREW_CAPTAIN") 같은 기본 메서드를 쓸 수 있다.
public interface RoleRepository extends JpaRepository<RoleEntity, String> {
    // 메서드 이름 해석: findBy + Enabled(=true) + OrderBy Code Asc
    // → SELECT * FROM roles WHERE enabled = true ORDER BY code ASC
    List<RoleEntity> findByEnabledTrueOrderByCodeAsc();
    // → SELECT * FROM roles WHERE faction = ? AND enabled = true ORDER BY code ASC
    List<RoleEntity> findByFactionAndEnabledTrueOrderByCodeAsc(Faction faction);
}
