package com.ontos.rule.biz.web;

import com.ontos.rule.biz.web.dto.InvocationDto;
import com.ontos.rule.core.invocation.InvocationRecorder;
import com.ontos.rule.core.model.InvocationRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core 调用追溯查询 API。
 *
 * <p>所有调用 core 的记录（含本平台调用 + 其他平台通过 API 调用）
 * 都能在这里查到。
 */
@RestController
@RequestMapping("/api/invocations")
public class InvocationController {

    private final InvocationRecorder recorder;

    public InvocationController(InvocationRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * 列出最近调用。
     *
     * <pre>{@code
     * GET /api/invocations                     全部（最近 50）
     * GET /api/invocations?caller=src-xxx      按来源筛选
     * GET /api/invocations?limit=200           调上限
     * }</pre>
     */
    @GetMapping
    public List<InvocationDto> recent(
        @RequestParam(value = "caller", required = false) String caller,
        @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return recorder.recent(caller, limit).stream()
            .map(InvocationDto::from)
            .toList();
    }

    /**
     * 按 caller 聚合统计。
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<InvocationRecord> all = recorder.recent(null, Integer.MAX_VALUE);

        // 按 caller 计数
        Map<String, Long> byCaller = new LinkedHashMap<>();
        Map<String, Long> byBackend = new LinkedHashMap<>();
        Map<String, Long> byMode = new LinkedHashMap<>();
        long success = 0, failed = 0;

        for (InvocationRecord r : all) {
            byCaller.merge(r.context().caller(), 1L, Long::sum);
            byBackend.merge(r.backend().name(), 1L, Long::sum);
            byMode.merge(r.mode().name(), 1L, Long::sum);
            if (r.isSuccess()) success++;
            else failed++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalInvocations", (long) all.size());
        result.put("successCount", success);
        result.put("failedCount", failed);
        result.put("byCaller", byCaller);
        result.put("byBackend", byBackend);
        result.put("byMode", byMode);
        return result;
    }
}
