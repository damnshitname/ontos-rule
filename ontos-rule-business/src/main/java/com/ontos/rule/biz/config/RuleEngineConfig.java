package com.ontos.rule.biz.config;

import com.ontos.rule.core.RuleEngine;
import com.ontos.rule.core.impl.DefaultRuleEngine;
import com.ontos.rule.core.invocation.InMemoryInvocationRecorder;
import com.ontos.rule.core.invocation.InvocationRecorder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RuleEngine 装配。
 *
 * <p>把 ontos-rule-core 的 RuleEngine 作为 Spring Bean 暴露给业务层注入。
 * 关键：caller 标识统一为 "src-rest-api"（可配置），所有 REST API 触发的调用都归属此来源。
 */
@Configuration
public class RuleEngineConfig {

    @Value("${ontos.rule.default-caller:src-rest-api}")
    private String defaultCaller;

    @Value("${ontos.rule.invocation-recorder-max-size:5000}")
    private int recorderMaxSize;

    @Bean
    public InvocationRecorder invocationRecorder() {
        return new InMemoryInvocationRecorder(recorderMaxSize);
    }

    @Bean
    public RuleEngine ruleEngine(InvocationRecorder recorder) {
        return DefaultRuleEngine.builder()
            .caller(defaultCaller)
            .recorder(recorder)
            .build();
    }
}
