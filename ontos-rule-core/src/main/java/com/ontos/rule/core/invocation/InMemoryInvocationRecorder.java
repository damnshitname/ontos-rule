package com.ontos.rule.core.invocation;

import com.ontos.rule.core.model.InvocationRecord;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 内存实现的调用记录器。
 *
 * <p>线程安全，FIFO 环形缓冲，超过 maxSize 时丢弃最旧记录。
 *
 * <p>不适合生产长期存储——生产应注入数据库 / Kafka 实现。
 */
public class InMemoryInvocationRecorder implements InvocationRecorder {

    public static final int DEFAULT_MAX_SIZE = 10_000;

    private final int maxSize;
    private final Deque<InvocationRecord> records = new ConcurrentLinkedDeque<>();

    public InMemoryInvocationRecorder() {
        this(DEFAULT_MAX_SIZE);
    }

    public InMemoryInvocationRecorder(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0");
        }
        this.maxSize = maxSize;
    }

    @Override
    public void record(InvocationRecord record) {
        records.addFirst(record);
        // 维护环形上限
        while (records.size() > maxSize) {
            records.pollLast();
        }
    }

    @Override
    public List<InvocationRecord> recent(String caller, int limit) {
        List<InvocationRecord> out = new ArrayList<>(Math.min(limit, records.size()));
        for (InvocationRecord r : records) {
            if (caller == null || Objects.equals(caller, r.context().caller())) {
                out.add(r);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    @Override
    public long countByCaller(String caller) {
        long count = 0;
        for (InvocationRecord r : records) {
            if (Objects.equals(caller, r.context().caller())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void clear() {
        records.clear();
    }

    public int size() {
        return records.size();
    }
}
