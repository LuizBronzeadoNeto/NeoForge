/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.unittest;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.neoforged.neoforge.logging.ThreadInfoUtil;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class ThreadInfoUtilTest {

    @Test
    void formatsHeaderWithNameIdAndState() {
        Thread current = Thread.currentThread();
        ThreadInfo ti = getInfo(current.getId());
        String s = ThreadInfoUtil.getEntireStacktrace(ti);
        assertTrue(s.startsWith("\"" + current.getName() + "\""), "Header should start with quoted thread name");
        assertTrue(s.contains(" Id=" + current.getId()), "Header should contain thread id");
        assertTrue(s.contains(current.getState().toString()), "Header should contain thread state");
    }

    @Test
    void includesStackTraceLines() {
        Thread current = Thread.currentThread();
        ThreadInfo ti = getInfo(current.getId());
        String s = ThreadInfoUtil.getEntireStacktrace(ti);
        assertTrue(s.contains("\tat "), "Should include at least one stack trace entry line");
    }

    @Test
    void printsLockedMonitorsWhenHeld() throws Exception {
        final Object monitor = new Object();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            synchronized (monitor) {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
            }
        }, "monitor-holder");
        holder.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS), "Thread did not enter synchronized block in time");

        ThreadInfo ti = getInfo(holder.getId());
        String s = ThreadInfoUtil.getEntireStacktrace(ti);
        assertTrue(s.contains("-  locked "), "Should include a 'locked' monitor line");
        assertTrue(s.contains("java.lang.Object"), "Should indicate the monitor class");

        release.countDown();
        holder.join(5000);
    }

    @Test
    void printsBlockedOnWhenThreadIsBlocked() throws Exception {
        final Object monitor = new Object();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch stop = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            synchronized (monitor) {
                entered.countDown();
                try { stop.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            }
        }, "holder");
        holder.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS), "Holder did not enter synchronized block");

        Thread blocked = new Thread(() -> {
            // Attempt to enter the same monitor and then exit immediately
            synchronized (monitor) { /* will only reach here after released */ }
        }, "blocked");
        blocked.start();

        // Wait until the thread is reported as BLOCKED
        boolean seenBlocked = waitForState(blocked, Thread.State.BLOCKED, 5, TimeUnit.SECONDS);
        assertTrue(seenBlocked, "Thread did not reach BLOCKED state in time");

        ThreadInfo ti = getInfo(blocked.getId());
        String s = ThreadInfoUtil.getEntireStacktrace(ti);
        assertTrue(s.contains("-  blocked on "), "Should include a 'blocked on' line");
        assertTrue(s.contains(" owned by \""), "Header should indicate the owning thread");

        // Cleanup
        stop.countDown();
        holder.join(5000);
        blocked.join(5000);
    }

    @Test
    void printsWaitingOnWhenThreadIsWaiting() throws Exception {
        final Object waitObj = new Object();
        CountDownLatch waiting = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean entered = new AtomicBoolean(false);

        Thread waiter = new Thread(() -> {
            synchronized (waitObj) {
                entered.set(true);
                waiting.countDown();
                try {
                    waitObj.wait();
                } catch (InterruptedException ignored) {}
            }
            done.countDown();
        }, "waiter");

        waiter.start();
        assertTrue(waiting.await(5, TimeUnit.SECONDS), "Waiter did not start waiting in time");

        boolean seenWaiting = waitForState(waiter, Thread.State.WAITING, 5, TimeUnit.SECONDS);
        assertTrue(seenWaiting, "Thread did not reach WAITING state in time");

        ThreadInfo ti = getInfo(waiter.getId());
        String s = ThreadInfoUtil.getEntireStacktrace(ti);
        assertTrue(s.contains("-  waiting on "), "Should include a 'waiting on' line");

        synchronized (waitObj) { waitObj.notifyAll(); }
        assertTrue(done.await(5, TimeUnit.SECONDS), "Waiter did not finish");
    }

    @Test
    void printsLockedSynchronizersCount() throws Exception {
        final ReentrantLock lock = new ReentrantLock();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread t = new Thread(() -> {
            lock.lock();
            try {
                entered.countDown();
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            } finally {
                lock.unlock();
            }
        }, "sync-holder");

        t.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS), "Thread did not acquire synchronizer in time");

        ThreadInfo ti = getInfo(t.getId());
        String s = ThreadInfoUtil.getEntireStacktrace(ti);
        assertTrue(s.contains("Number of locked synchronizers = 1"), "Should report a single locked synchronizer");

        release.countDown();
        t.join(5000);
    }

    private static ThreadInfo getInfo(long tid) {
        var mx = ManagementFactory.getThreadMXBean();
        ThreadInfo[] infos = mx.getThreadInfo(new long[]{tid}, true, true);
        return infos[0];
    }

    private static boolean waitForState(Thread t, Thread.State expected, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (t.getState() == expected) return true;
            Thread.sleep(5);
        }
        return t.getState() == expected;
    }
}
