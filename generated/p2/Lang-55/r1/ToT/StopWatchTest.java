/*********************************************************************
 * Comprehensive JUnit‑4 test suite for org.apache.commons.lang.time.StopWatch
 *
 * The tests are written from the perspective of three “experts”.  Each
 * expert contributed a test case for every public method, covering:
 *
 *   • Typical usage (start → … → stop)
 *   • Edge cases (calling a method twice, calling before start, etc.)
 *   • Error scenarios (IllegalStateException)
 *
 * The final file combines all contributions into a single, self‑contained
 * JUnit‑4 test class that can be run with any JUnit‑4 runner.
 *********************************************************************/

package org.apache.commons.lang.time;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * Test suite for {@link StopWatch}.
 *
 * <p>Note: The real {@code StopWatch} works with {@code System.currentTimeMillis()}
 * so the tests use very short {@code Thread.sleep} intervals.  The assertions
 * use a tolerance to avoid flakiness on slow machines.</p>
 */
public class StopWatchTest {

    /** The object under test – created fresh before each test method. */
    private StopWatch sw;

    /** Small tolerance (ms) when comparing elapsed times. */
    private static final long TOLERANCE_MS = 20L;

    @Before
    public void setUp() {
        sw = new StopWatch();
    }

    /* -----------------------------------------------------------------
     *  start() / stop() / getTime()
     * ----------------------------------------------------------------- */

    /** Typical scenario: start → wait → stop → getTime(). */
    @Test
    public void testStartStopAndGetTime() throws InterruptedException {
        sw.start();
        Thread.sleep(50);                 // give the watch a measurable interval
        sw.stop();

        long elapsed = sw.getTime();
        assertTrue("Elapsed time should be >= 50 ms", elapsed >= 50);
        assertTrue("Elapsed time should not be absurdly large",
                elapsed < 200);
    }

    /** start() cannot be called twice without a reset. */
    @Test(expected = IllegalStateException.class)
    public void testStartTwiceThrows() {
        sw.start();
        sw.start();                       // second start → IllegalStateException
    }

    /** stop() before start() must throw IllegalStateException. */
    @Test(expected = IllegalStateException.class)
    public void testStopWithoutStartThrows() {
        sw.stop();
    }

    /** After reset the watch returns to the un‑started state. */
    @Test
    public void testReset() throws InterruptedException {
        sw.start();
        Thread.sleep(30);
        sw.stop();
        assertTrue("Time after stop should be > 0", sw.getTime() > 0);

        sw.reset();
        assertEquals("After reset, getTime() must be 0", 0, sw.getTime());

        // after reset we can start again
        sw.start();
        Thread.sleep(10);
        sw.stop();
        assertTrue("After reset, new timing must work", sw.getTime() >= 10);
    }

    /** getTime() before the watch is started returns 0. */
    @Test
    public void testGetTimeBeforeStart() {
        assertEquals("Unstarted watch should report 0 ms", 0, sw.getTime());
    }

    /* -----------------------------------------------------------------
     *  split() / unsplit() / getSplitTime()
     * ----------------------------------------------------------------- */

    /** Normal split usage: start → split → getSplitTime(). */
    @Test
    public void testSplitAndGetSplitTime() throws InterruptedException {
        sw.start();
        Thread.sleep(40);
        sw.split();                       // records a split time
        long splitTime = sw.getSplitTime();
        assertTrue("Split time should be >= 40 ms", splitTime >= 40);
        assertTrue("Split time should be < 200 ms", splitTime < 200);

        // after split the watch is still running; stop and ensure total >= split
        sw.stop();
        assertTrue("Total time after stop should be >= split time",
                sw.getTime() >= splitTime);
    }

    /** split() before start() must throw IllegalStateException. */
    @Test(expected = IllegalStateException.class)
    public void testSplitWithoutStartThrows() {
        sw.split();
    }

    /** unsplit() without a prior split must throw IllegalStateException. */
    @Test(expected = IllegalStateException.class)
    public void testUnsplitWithoutSplitThrows() {
        sw.start();
        sw.unsplit();
    }

    /** unsplit() clears the split, making subsequent getSplitTime() illegal. */
    @Test(expected = IllegalStateException.class)
    public void testUnsplitClearsSplit() throws InterruptedException {
        sw.start();
        Thread.sleep(20);
        sw.split();
        sw.unsplit();                     // removes split
        sw.getSplitTime();                // should now fail
    }

    /* -----------------------------------------------------------------
     *  suspend() / resume()
     * ----------------------------------------------------------------- */

    /** Suspend and resume should not count the suspended interval. */
    @Test
    public void testSuspendAndResume() throws InterruptedException {
        sw.start();
        Thread.sleep(30);                 // running part 1
        sw.suspend();
        long timeAtSuspend = sw.getTime();
        Thread.sleep(40);                 // this interval must NOT be counted
        sw.resume();
        Thread.sleep(20);                 // running part 2
        sw.stop();

        long total = sw.getTime();
        // total ≈ 30 + 20 = 50 ms (plus a small overhead)
        assertTrue("Total time should be close to 50 ms",
                total >= 50 && total < 120);
        // time at suspend must be ≈ 30 ms
        assertTrue("Time at suspend should be close to 30 ms",
                Math.abs(timeAtSuspend - 30) < TOLERANCE_MS);
    }

    /** suspend() before start() must throw IllegalStateException. */
    @Test(expected = IllegalStateException.class)
    public void testSuspendWithoutStartThrows() {
        sw.suspend();
    }

    /** resume() without a preceding suspend must throw IllegalStateException. */
    @Test(expected = IllegalStateException.class)
    public void testResumeWithoutSuspendThrows() {
        sw.start();
        sw.resume();
    }

    /* -----------------------------------------------------------------
     *  toString() / toSplitString()
     * ----------------------------------------------------------------- */

    /** toString() should return a HH:mm:ss.SSS formatted string. */
    @Test
    public void testToStringFormat() throws InterruptedException {
        sw.start();
        Thread.sleep(55);
        sw.stop();

        String txt = sw.toString();
        // Expected pattern: 00:00:0x.xx (hours:minutes:seconds.millis)
        assertTrue("toString() format should match HH:mm:ss.SSS",
                txt.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}"));
    }

    /** toSplitString() works only after a split; otherwise it throws. */
    @Test
    public void testToSplitStringAfterSplit() throws InterruptedException {
        sw.start();
        Thread.sleep(30);
        sw.split();

        String splitStr = sw.toSplitString();
        assertTrue("toSplitString() format should match HH:mm:ss.SSS",
                splitStr.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}"));
    }

    /** toSplitString() before a split must throw IllegalStateException. */
    @Test(expected = IllegalStateException.class)
    public void testToSplitStringWithoutSplitThrows() {
        sw.start();
        sw.toSplitString();
    }

    /* -----------------------------------------------------------------
     *  Additional sanity checks (state transitions)
     * ----------------------------------------------------------------- */

    /** After stop the watch cannot be stopped again without a reset. */
    @Test(expected = IllegalStateException.class)
    public void testStopTwiceThrows() {
        sw.start();
        sw.stop();
        sw.stop();                         // second stop → IllegalStateException
    }

    /** After stop the watch can be reset and started again. */
    @Test
    public void testStopThenResetAndStart() throws InterruptedException {
        sw.start();
        Thread.sleep(15);
        sw.stop();
        long first = sw.getTime();

        sw.reset();
        assertEquals("After reset, time must be 0", 0, sw.getTime());

        sw.start();
        Thread.sleep(10);
        sw.stop();
        long second = sw.getTime();

        assertTrue("Second timing must be independent of first", second < first);
    }
}
