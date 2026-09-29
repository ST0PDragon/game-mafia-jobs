// [파일 역할] Spring Security 설정. "어떤 URL은 로그인 없이, 나머지는 JWT가 있어야" 규칙을 정한다.
//   이 서버는 JWT를 **발급하지 않고 검증만** 한다(OAuth2 Resource Server). 발급은 외부 인증 서버의 몫.
// 원본 위치: server/src/main/java/com/doronyong/mafia/config/SecurityConfig.java

package com.doronyong.mafia.config; // 설정 패키지

import org.springframework.context.annotation.Bean;          // 메서드가 돌려주는 객체를 스프링 빈으로 등록
import org.springframework.context.annotation.Configuration; // 설정 클래스 표시
import org.springframework.http.HttpMethod;                  // GET/POST 같은 HTTP 메서드 상수
import org.springframework.security.config.Customizer;       // "기본 설정 그대로 써라"를 표현하는 도우미
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // 보안 규칙을 조립하는 빌더
import org.springframework.security.config.http.SessionCreationPolicy;           // 세션 사용 정책
import org.springframework.security.web.SecurityFilterChain;                    // 완성된 보안 필터 묶음

/** 공개 직업 목록 외의 API는 서명 검증된 Bearer JWT가 있어야 호출할 수 있다. */
@Configuration // 이 클래스 안의 @Bean 메서드들을 스프링이 시작할 때 실행한다
public class SecurityConfig {
    // 모든 HTTP 요청이 컨트롤러에 닿기 전에 이 필터 체인을 통과한다.
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            // CSRF 보호 끔. CSRF는 "브라우저 쿠키 로그인"을 노리는 공격이라,
            // 쿠키 대신 Authorization 헤더로 토큰을 보내는 API 서버에서는 보통 끈다.
            .csrf(csrf -> csrf.disable())
            // 세션(서버 메모리 로그인 상태)을 만들지 않는다. 매 요청마다 JWT로 신원을 확인한다.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // URL별 접근 규칙. 위에서부터 차례로 검사해 처음 맞는 규칙이 적용된다.
            .authorizeHttpRequests(auth -> auth
                // GET /api/v1/roles 는 누구나 호출 가능(공개 직업 목록)
                .requestMatchers(HttpMethod.GET, "/api/v1/roles").permitAll()
                // 그 밖의 모든 요청은 인증 필요. 토큰이 없거나 틀리면 401.
                // ⚠ 스프링이 오류를 그리는 /error 경로도 여기에 걸린다. 비로그인 API에서 오류가 나면
                //   원래 코드(400 등) 대신 401이 나갈 수 있으니 "/error"도 permitAll에 넣는 것을 권장.
                .anyRequest().authenticated())
            // "Authorization: Bearer <JWT>" 헤더를 읽어 서명·만료·발급자를 검사하는 기능을 켠다.
            // 기본 설정은 application.yml의 issuer-uri에서 공개키를 받아 서명을 확인한다.
            // ⚠ audience(aud) 검사는 기본으로 하지 않는다. 같은 발급자의 다른 서비스 토큰도 통과할 수 있다.
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
            // 위 설정으로 필터 체인 객체를 완성해 반환
            .build();
    }
}
