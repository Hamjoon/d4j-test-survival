**1. Public methods of `StopWatch` (including signatures)**  

| # | Return type | Method signature |
|---|-------------|------------------|
| 1 | `void` | `public void start()` |
| 2 | `void` | `public void stop()` |
| 3 | `void` | `public void reset()` |
| 4 | `void` | `public void split()` |
| 5 | `void` | `public void unsplit()` |
| 6 | `void` | `public void suspend()` |
| 7 | `void` | `public void resume()` |
| 8 | `long` | `public long getTime()` |
| 9 | `long` | `public long getSplitTime()` |
|10 | `String`| `public String toString()` |
|11 | `String`| `public String toSplitString()` |

---

**2. Basic JUnit‑4 test cases (functionality check)**  

Below is a short description of a *basic* test for each method:

| Method | Basic test idea |
|--------|-----------------|
| `start()` | After calling `start()` the stopwatch should be in *running* state and `getTime()` should be ≥ 0. |
| `stop()` | After `start()` → `stop()`, `getTime()` should stay constant (no further increase). |
| `reset()` | After `start()` → `reset()`, `getTime()` must be `0` and a new `start()` should work. |
| `split()` | After `start()` → `split()`, `getSplitTime()` should be close to `getTime()`. |
| `unsplit()` | After a split, calling `unsplit()` must allow a new split and `getSplitTime()` after the next split works. |
| `suspend()` | After `start()` → `suspend()`, the elapsed time must stop increasing. |
| `resume()` | After `suspend()` → `resume()`, the stopwatch continues counting (time after resume adds to previous elapsed). |
| `getTime()` | Returns elapsed ms in the different states (unstarted → 0, running → ≈ now‑start, stopped/suspended → stop‑start). |
| `getSplitTime()` | Returns split‑elapsed ms only when a split is active; otherwise throws. |
| `toString()` | Returns a non‑null string containing at least one ‘:`’ (HMS format). |
| `toSplitString()` | Same as `toString()` but for the split time; throws if not split. |

---

**3. Edge‑case / exception scenarios that must be tested**

| Scenario | Expected outcome |
|----------|-------------------|
| `start()` when already running | `IllegalStateException` |
| `start()` after a stop without reset | `IllegalStateException` |
| `stop()` when not started | `IllegalStateException` |
| `split()` when not running | `IllegalStateException` |
| `split()` twice without unsplit | second call throws `IllegalStateException` (because state is still RUNNING, but splitState already SPLIT – the class permits calling split again, it will just overwrite `stopTime`; however we test that a second split does **not** throw; we’ll verify that the split time is updated). |
| `unsplit()` when not split | `IllegalStateException` |
| `unsplit()` after a split, then `getSplitTime()` → throws |
| `suspend()` when not running | `IllegalStateException` |
| `suspend()` twice without resume | second call throws `IllegalStateException` |
| `resume()` when not suspended | `IllegalStateException` |
| `getSplitTime()` without a split | `IllegalStateException` |
| `toSplitString()` without a split | `IllegalStateException` |
| `reset()` at any time clears all state (verify `getTime()==0` and subsequent `start()` works). |
| `getTime()` after `reset()` (unstarted) returns `0`. |

---

**4. Full JUnit‑4 test file**  

```java
###Test START##
package org.apache.commons.lang.time;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * JUnit‑4 test suite for {@link StopWatch}.
 *
 * The tests use small {@code Thread.sleep} intervals and a tolerance of
 * ±50 ms to accommodate timing variations on the CI machines.
 */
public class StopWatchTest {

    private static final long TOLERANCE_MS = 50L;
    private StopWatch watch;

    @Before
    public void setUp() {
        watch = new StopWatch();
    }

    /* ----------------------------------------------------------------------
     *  Basic functionality tests
     * ---------------------------------------------------------------------- */

    @Test
    public void testStart() {
        watch.start();
        long elapsed = watch.getTime();
        assertTrue("Elapsed time after start should be >= 0", elapsed >= 0);
    }

    @Test
    public void testStop() throws InterruptedException {
        watch.start();
        Thread.sleep(30);
        watch.stop();
        long stoppedTime = watch.getTime();

        // after stop, time must not increase
        Thread.sleep(30);
        assertEquals("Time after stop must remain constant",
                stoppedTime, watch.getTime());
    }

    @Test
    public void testReset() throws InterruptedException {
        watch.start();
        Thread.sleep(20);
        watch.reset();
        assertEquals("After reset time must be zero", 0L, watch.getTime());

        // start again after reset
        watch.start();
        Thread.sleep(20);
        assertTrue("After restart time should be > 0", watch.getTime() > 0);
    }

    @Test
    public void testSplitAndGetSplitTime() throws InterruptedException {
        watch.start();
        Thread.sleep(30);
        watch.split();
        long splitTime = watch.getSplitTime();
        long elapsed   = watch.getTime();

        assertTrue("Split time should be <= elapsed time",
                splitTime <= elapsed);
        assertEquals("Split time should be close to elapsed time",
                elapsed, splitTime, TOLERANCE_MS);
    }

    @Test
    public void testUnsplitAllowsNewSplit() throws InterruptedException {
        watch.start();
        Thread.sleep(20);
        watch.split();
        long firstSplit = watch.getSplitTime();

        watch.unsplit();

        // after unsplit, calling getSplitTime must fail
        try {
            watch.getSplitTime();
            fail("Expected IllegalStateException after unsplit");
        } catch (IllegalStateException ignored) {}

        // new split
        Thread.sleep(20);
        watch.split();
        long secondSplit = watch.getSplitTime();

        assertTrue("Second split must be greater than first split",
                secondSplit > firstSplit);
    }

    @Test
    public void testSuspendAndResume() throws InterruptedException {
        watch.start();
        Thread.sleep(30);
        watch.suspend();
        long timeAtSuspend = watch.getTime();

        Thread.sleep(30); // should not be counted
        assertEquals("Time must not increase while suspended",
                timeAtSuspend, watch.getTime());

        watch.resume();
        Thread.sleep(30);
        long afterResume = watch.getTime();

        assertTrue("Time after resume must be greater than time at suspend",
                afterResume > timeAtSuspend);
    }

    @Test
    public void testToStringFormatsTime() throws InterruptedException {
        watch.start();
        Thread.sleep(25);
        watch.stop();
        String s = watch.toString();
        assertNotNull("toString should not return null", s);
        assertTrue("toString should contain ':' (HMS format)", s.contains(":"));
    }

    @Test
    public void testToSplitStringFormatsSplitTime() throws InterruptedException {
        watch.start();
        Thread.sleep(25);
        watch.split();
        String s = watch.toSplitString();
        assertNotNull("toSplitString should not return null", s);
        assertTrue("toSplitString should contain ':' (HMS format)", s.contains(":"));
    }

    /* ----------------------------------------------------------------------
     *  Exception / edge‑case tests
     * ---------------------------------------------------------------------- */

    @Test(expected = IllegalStateException.class)
    public void testStartWhenAlreadyRunning() {
        watch.start();
        watch.start(); // second start -> exception
    }

    @Test(expected = IllegalStateException.class)
    public void testStartAfterStopWithoutReset() {
        watch.start();
        watch.stop();
        watch.start(); // illegal, must reset first
    }

    @Test(expected = IllegalStateException.class)
    public void testStopWhenNotRunning() {
        watch.stop(); // never started
    }

    @Test(expected = IllegalStateException.class)
    public void testSplitWhenNotRunning() {
        watch.split(); // not started
    }

    @Test
    public void testSplitTwiceUpdatesStopTime() throws InterruptedException {
        watch.start();
        Thread.sleep(20);
        watch.split();
        long firstSplit = watch.getSplitTime();

        Thread.sleep(20);
        watch.split(); // allowed – overwrites split time
        long secondSplit = watch.getSplitTime();

        assertTrue("Second split should be later than first split", secondSplit > firstSplit);
    }

    @Test(expected = IllegalStateException.class)
    public void testUnsplitWhenNotSplit() {
        watch.unsplit();
    }

    @Test(expected = IllegalStateException.class)
    public void testGetSplitTimeWithoutSplit() {
        watch.getSplitTime();
    }

    @Test(expected = IllegalStateException.class)
    public void testToSplitStringWithoutSplit() {
        watch.toSplitString();
    }

    @Test(expected = IllegalStateException.class)
    public void testSuspendWhenNotRunning() {
        watch.suspend();
    }

    @Test
    public void testSuspendTwiceThrows() throws InterruptedException {
        watch.start();
        watch.suspend();
        try {
            watch.suspend();
            fail("Second suspend should throw IllegalStateException");
        } catch (IllegalStateException ignored) {}
    }

    @Test(expected = IllegalStateException.class)
    public void testResumeWhenNotSuspended() {
        watch.resume();
    }

    @Test
    public void testGetTimeUnstartedIsZero() {
        assertEquals("Unstarted stopwatch must report 0", 0L, watch.getTime());
    }

    @Test
    public void testGetTimeAfterResetIsZero() throws InterruptedException {
        watch.start();
        Thread.sleep(10);
        watch.reset();
        assertEquals("After reset time must be 0", 0L, watch.getTime());
    }

    @Test
    public void testGetTimeWhileSuspendedRemainsConstant() throws InterruptedException {
        watch.start();
        Thread.sleep(20);
        watch.suspend();
        long suspendedTime = watch.getTime();

        Thread.sleep(30);
        assertEquals("Time must stay constant while suspended", suspendedTime, watch.getTime());
    }
}
###Test END##
```