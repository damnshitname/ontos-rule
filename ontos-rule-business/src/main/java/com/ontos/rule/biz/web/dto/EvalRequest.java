package com.ontos.rule.biz.web.dto;

import java.util.Map;

/**
 * 单条求值请求。
 *
 * <p>示例：
 * <pre>
 * { "record": { "temperature": 92, "tolerance": 5, "maxLimit": 100 } }
 * </pre>
 */
public record EvalRequest(Map<String, Object> record) {}
