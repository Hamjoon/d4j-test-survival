package org.apache.commons.lang.time;

import static org.junit.Assert.*;

import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;

/**
 * Comprehensive JUnit4 test suite for {@link StopWatch}.
 */
public class StopWatchTest {

    private StopWatch watch;

    @Before
    public void setUp() {
        watch = new StopWatch();
    }

    /* -------------------------------------------------
       Basic state validation
       ------------------------------------------------- */

    @Test
    public void testInitialState() {
        assertEquals(0L, watch.getTime());               // unstarted returns 0
        assertEquals("00:00:00.000", watch.toString()); // formatted zero
    }

    @Test
    public void testStartStopAndTime() throws InterruptedException {
        watch.start();
        Thread.sleep(15);                 // give it a small measurable interval
        watch.stop();

        long elapsed = watch.getTime();
        assertTrue("Elapsed time should be >=15ms but was " + elapsed, elapsed >= 15);
        // after stop, getTime must be stable
        long afterStop = watch.getTime();
        assertEquals(elapsed, afterStop);
    }

    @Test(expected = IllegalStateException.class)
    public void testStartWhenAlreadyRunning() {
        watch.start();
        watch.start(); // should throw
    }

    @Test(expected = IllegalStateException.class)
    public void testStartAfterStopWithoutReset() {
        watch.start();
        watch.stop();
        watch.start(); // illegal – must reset first
    }

    @Test(expected = IllegalStateException.class)
    public void testStopBeforeStart() {
        watch.stop(); // illegal
    }

    @Test
    public void testResetClearsState() throws InterruptedException {
        watch.start();
        Thread.sleep(10);
        watch.stop();
        assertTrue(watch.getTime() > 0);

        watch.reset();
        assertEquals(0L, watch.getTime());
        // after reset we can start again
        watch.start();
        Thread.sleep(5);
        watch.stop();
        assertTrue(watch.getTime() > 0);
    }

    /* -------------------------------------------------
       Split functionality
       ------------------------------------------------- */

    @Test
    public void testSplitAndSplitTime() throws InterruptedException {
        watch.start();
        Thread.sleep(12);
        watch.split();

        long splitTime = watch.getSplitTime();
        assertTrue("Split time should be >=12ms", splitTime >= 12);

        // after a further pause, total time must be larger than split time
        Thread.sleep(8);
        long total = watch.getTime();
        assertTrue("Total time should be >= split time", total >= splitTime);
    }

    @Test(expected = IllegalStateException.class)
    public void testSplitWhenNotRunning() {
        watch.split(); // illegal – never started
    }

    @Test(expected = IllegalStateException.class)
    public void testGetSplitTimeWhenNotSplit() {
        watch.start();
        watch.getSplitTime(); // illegal – not split yet
    }

    @Test
    public void testUnsplitRestoresRunningState() throws InterruptedException {
        watch.start();
        Thread.sleep(10);
        watch.split();
        long split1 = watch.getSplitTime();

        watch.unsplit();
        // after unsplit, splitState cleared – calling getSplitTime now must fail
        try {
            watch.getSplitTime();
            fail("Expected IllegalStateException after unsplit");
        } catch (IllegalStateException e) {
            // expected
        }

        // continue timing, then split again and verify new split time
        Thread.sleep(10);
        watch.split();
        long split2 = watch.getSplitTime();
        assertTrue("Second split should be larger than first split", split2 > split1);
    }

    @Test(expected = IllegalStateException.class)
    public void testUnsplitWithoutSplit() {
        watch.start();
        watch.unsplit(); // illegal – never split
    }

    /* -------------------------------------------------
       Suspend / Resume functionality
       ------------------------------------------------- */

    @Test
    public void testSuspendResume() throws InterruptedException {
        watch.start();
        Thread.sleep(10);
        watch.suspend();

        long timeDuringSuspend = watch.getTime();
        Thread.sleep(15); // this interval must NOT be counted

        // time should stay constant while suspended
        assertEquals(timeDuringSuspend, watch.getTime());

        watch.resume();
        Thread.sleep(7); // counted again

        long finalTime = watch.getTime();
        assertTrue("Final time must be greater than time at suspend",
                   finalTime > timeDuringSuspend);
        // Verify that the extra time after resume is roughly the sleep after resume
        long afterResume = finalTime - timeDuringSuspend;
        assertTrue("Time after resume should be about 7ms (was " + afterResume + ")", afterResume >= 6);
    }

    @Test(expected = IllegalStateException.class)
    public void testSuspendWhenNotRunning() {
        watch.suspend(); // illegal – never started
    }

    @Test(expected = IllegalStateException.class)
    public void testResumeWhenNotSuspended() {
        watch.start();
        watch.resume(); // illegal – never suspended
    }

    /* -------------------------------------------------
       toString and toSplitString format validation
       ------------------------------------------------- */

    @Test
    public void testToStringFormat() throws InterruptedException {
        watch.start();
        Thread.sleep(5);
        watch.stop();

        String formatted = watch.toString();
        assertTrue("toString should match HH:MM:SS.mmm pattern",
                   Pattern.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}", formatted));
    }

    @Test
    public void testToSplitStringFormat() throws InterruptedException {
        watch.start();
        Thread.sleep(5);
        watch.split();

        String splitFormatted = watch.toSplitString();
        assertTrue("toSplitString should match HH:MM:SS.mmm pattern",
                   Pattern.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}", splitFormatted));
    }

    @Test(expected = IllegalStateException.class)
    public void testToSplitStringWithoutSplit() {
        watch.start();
        watch.toSplitString(); // should throw because not split
    }

    /* -------------------------------------------------
       Additional edge cases
       ------------------------------------------------- */

    @Test
    public void testMultipleStartStopCyclesWithReset() throws InterruptedException {
        // cycle 1
        watch.start();
        Thread.sleep(3);
        watch.stop();
        long first = watch.getTime();
        assertTrue(first >= 3);

        // reset and reuse
        watch.reset();
        assertEquals(0L, watch.getTime());

        // cycle 2
        watch.start();
        Thread.sleep(4);
        watch.stop();
        long second = watch.getTime();
        assertTrue(second >= 4);
        assertTrue(second != first); // very unlikely to be exactly same
    }

    @Test
    public void testIllegalStateTransitionsAfterSplit() {
        watch.start();
        watch.split();
        try {
            watch.start();
            fail("Should not be able to start when already running (even after split)");
        } catch (IllegalStateException e) {
            // expected
        }
        try {
            watch.suspend();
            // suspend is allowed while running, even after split – verify it works
            watch.resume(); // should be okay after suspend
        } catch (IllegalStateException e) {
            fail("Suspend/Resume after split should be valid");
        }
    }
}
