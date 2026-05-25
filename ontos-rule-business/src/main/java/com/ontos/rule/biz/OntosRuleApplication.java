package com.ontos.rule.biz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ONTOS 规则引擎业务层启动类。
 *
 * <p>启动后访问：
 * <ul>
 *   <li>API 根：<a href="http://localhost:8080/api">/api</a></li>
 *   <li>H2 控制台：<a href="http://localhost:8080/h2-console">/h2-console</a>
 *       (JDBC URL: jdbc:h2:mem:ontosrule)</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class OntosRuleApplication {

    public static void main(String[] args) {
        SpringApplication.run(OntosRuleApplication.class, args);
    }
}
