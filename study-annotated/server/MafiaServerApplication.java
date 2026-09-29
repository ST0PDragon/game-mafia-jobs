// [파일 역할] 서버 프로그램의 시작점(main). 이 클래스를 실행하면 스프링 부트가 켜지고 8080 포트에서 요청을 받는다.
// 원본 위치: server/src/main/java/com/doronyong/mafia/MafiaServerApplication.java

// 이 클래스가 속한 패키지. 스프링은 이 패키지와 그 하위 패키지(config, controller, service ...)를 자동으로 스캔한다.
package com.doronyong.mafia;

// 스프링 부트 앱을 실제로 실행해 주는 클래스.
import org.springframework.boot.SpringApplication;
// 아래 애너테이션 하나에 "설정 클래스 + 자동 설정 + 컴포넌트 스캔" 세 가지가 들어 있다.
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication:
//  - @Configuration      : 이 클래스도 스프링 설정 클래스로 쓴다.
//  - @EnableAutoConfiguration : pom.xml에 있는 라이브러리(web, jpa, security, flyway...)를 보고 필요한 설정을 자동으로 만든다.
//  - @ComponentScan      : @Service, @RestController, @Configuration 등이 붙은 클래스를 찾아 객체(빈)로 등록한다.
@SpringBootApplication
// 클래스 이름은 자유. 보통 "프로젝트명 + Application"으로 짓는다.
public class MafiaServerApplication {
    // 자바 프로그램의 진입점. `mvn spring-boot:run` 또는 IDE 실행 버튼이 이 메서드를 호출한다.
    public static void main(String[] args) {
        // 스프링 컨테이너를 만들고, 내장 톰캣 서버를 띄우고, Flyway 마이그레이션을 실행한 뒤 요청을 기다린다.
        SpringApplication.run(MafiaServerApplication.class, args);
    } // main 끝
} // 클래스 끝
