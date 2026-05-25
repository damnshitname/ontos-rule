package com.ontos.rule.core.invocation;

import com.ontos.rule.core.model.InvocationRecord;

import java.util.List;

/**
 * 调用记录器接口。
 *
 * <p>每次 core API 调用（eval / execute）后由 DefaultRuleEngine 调用 record(...)。
 *
 * <p>默认实现 InMemoryInvocationRecorder 保留最近 N 条记录在内存。
 * 生产场景应注入持久化实现（写库 / 写日志 / 推 Kafka）。
 */
public interface InvocationRecorder {

    /**
     * 记录一次调用。
     */
    void record(InvocationRecord record);

    /**
     * 查询最近调用记录。
     *
     * @param caller  按来源筛选（null 表示所有）
     * @param limit   返回上限
     */
    List<InvocationRecord> recent(String caller, int limit);

    /**
     * 统计某个来源的调用次数（按 in-memory 记录数）。
     */
    long countByCaller(String caller);

    /**
     * 清空所有记录（仅测试用）。
     */
    void clear();
}
