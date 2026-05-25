package com.ontos.rule.biz.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 mock 数据源。
 *
 * <p>启动时生成几组演示数据，用于 execute API 跑批用。
 * 真实场景换成 JDBC DataSource、文件、Kafka 等。
 */
@Service
public class MockDataSource {

    private final Map<String, List<Map<String, Object>>> data = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        data.put("mock-lot", generateLots(100));
        data.put("mock-equipment", generateEquipment(50));
        data.put("mock-wafer", generateWafers(500));
    }

    private List<Map<String, Object>> generateLots(int n) {
        Random r = new Random(42);
        String[] statuses = {"RUNNING", "IDLE", "OnHold", "Completed", "DOWN"};
        List<Map<String, Object>> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("lotId", String.format("LOT-2026%05d", i + 1));
            // -50 ~ 850, 故意制造一些违规
            row.put("temperature", (long) (-50 + r.nextInt(900)));
            row.put("tolerance", (long) r.nextInt(10));
            row.put("maxLimit", 100L);
            row.put("status", statuses[r.nextInt(statuses.length)]);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> generateEquipment(int n) {
        Random r = new Random(7);
        String[] statuses = {"RUNNING", "IDLE", "DOWN", "MAINT", "OFF", "SETUP", "UNKNOWN"};
        List<Map<String, Object>> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("equipmentId", String.format("EQ-%03d", i + 1));
            row.put("status", statuses[r.nextInt(statuses.length)]);
            row.put("temperature", (long) (20 + r.nextInt(80)));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> generateWafers(int n) {
        Random r = new Random(13);
        List<Map<String, Object>> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("waferId", String.format("WF-%06d", i + 1));
            row.put("lotId", String.format("LOT-2026%05d", (i / 5) + 1));
            // defectRate 0 ~ 0.02，超过 0.005 视为违规
            row.put("defectRate", r.nextDouble() * 0.02);
            rows.add(row);
        }
        return rows;
    }

    public List<Map<String, Object>> load(String name) {
        List<Map<String, Object>> rows = data.get(name);
        if (rows == null) {
            throw new IllegalArgumentException("未知数据源: " + name + " · 可用: " + data.keySet());
        }
        return rows;
    }

    public Map<String, Integer> listAvailable() {
        Map<String, Integer> result = new LinkedHashMap<>();
        data.forEach((k, v) -> result.put(k, v.size()));
        return result;
    }
}
