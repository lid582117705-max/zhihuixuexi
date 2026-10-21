package com.boxue.common.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdGeneratorTest {

    @Test
    void 连续发号不重复且递增() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);
        Set<Long> ids = new HashSet<>();
        long previous = -1;

        for (int i = 0; i < 5000; i++) {
            long id = generator.nextId();
            assertTrue(id > previous, "同一实例发号必须递增");
            previous = id;
            ids.add(id);
        }
        assertEquals(5000, ids.size(), "5000 次发号不能重复");
    }

    @Test
    void 不同机器号互不冲突() {
        SnowflakeIdGenerator node1 = new SnowflakeIdGenerator(1);
        SnowflakeIdGenerator node2 = new SnowflakeIdGenerator(2);

        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(node1.nextId());
            ids.add(node2.nextId());
        }
        assertEquals(2000, ids.size(), "不同 workerId 的号段必须隔离");
    }
}
