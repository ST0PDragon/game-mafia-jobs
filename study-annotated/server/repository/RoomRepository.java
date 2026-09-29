// [파일 역할] rooms 테이블 Repository. 동시 요청을 줄 세우는 "방 잠금" 쿼리가 핵심이다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/repository/RoomRepository.java

package com.doronyong.mafia.repository; // Repository 패키지

import com.doronyong.mafia.domain.RoomEntity;               // 대상 엔티티
import jakarta.persistence.LockModeType;                    // 잠금 종류 상수
import java.util.Optional;                                  // 없을 수도 있는 결과
import org.springframework.data.jpa.repository.JpaRepository; // 기본 CRUD
import org.springframework.data.jpa.repository.Lock;        // 쿼리에 잠금을 거는 애너테이션
import org.springframework.data.jpa.repository.Query;       // JPQL을 직접 적는 애너테이션
import org.springframework.data.repository.query.Param;     // JPQL의 :이름 파라미터와 메서드 인자 연결

// PK가 Long인 RoomEntity용 Repository
public interface RoomRepository extends JpaRepository<RoomEntity, Long> {
    // 행동 제출과 향후 페이즈 전환이 동일한 잠금을 잡아야 상태 변경과 제출이 충돌하지 않는다.
    // PESSIMISTIC_WRITE = 비관적 쓰기 잠금. DB에 "SELECT ... FOR UPDATE"로 나간다.
    //   → 이 트랜잭션이 끝날 때까지 다른 트랜잭션은 같은 방 행을 잠그려다 기다린다.
    //   → 예: 해적 두 명이 동시에 대상을 바꿔도, 밤 판정 도중에 행동이 들어와도 한 번에 하나씩 처리된다.
    // 주의: 잠금은 @Transactional 메서드 안에서 호출해야 의미가 있다(트랜잭션이 끝나면 풀린다).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // JPQL: 테이블이 아니라 엔티티 이름(RoomEntity)과 필드(r.id)로 쓰는 쿼리 언어
    @Query("select r from RoomEntity r where r.id = :roomsId")
    // 방이 없으면 Optional.empty() → 서비스에서 404로 바꾼다
    Optional<RoomEntity> lockById(@Param("roomsId") Long roomsId);
}
