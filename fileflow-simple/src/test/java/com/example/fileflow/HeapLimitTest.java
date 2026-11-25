package com.example.fileflow;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeapLimitTest {
    @Test
    void testJvmIsConfiguredWith512MiBMaxHeap() {
        long maxHeap = Runtime.getRuntime().maxMemory();
        System.out.println("Test JVM maximum heap: " + maxHeap + " bytes");
        long limit = 512L * 1024 * 1024;
        assertTrue(maxHeap <= limit,
                "Test JVM maximum heap exceeds 512 MiB: " + maxHeap + " bytes. " +
                "Run Maven Surefire with -Xmx512m.");
    }
}
