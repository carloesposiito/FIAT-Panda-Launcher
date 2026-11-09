package it.panda.launcher.obd;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ConnectDelayTest {

    @Test
    public void freshGateHasNoWait() {
        assertEquals(0, new ConnectDelay().remaining(1_000));
    }

    @Test
    public void waitsFullDelayThenCountsDown() {
        ConnectDelay d = new ConnectDelay();
        d.arm(1_000, 20_000);
        assertEquals(20_000, d.remaining(1_000));
        assertEquals(5_000, d.remaining(16_000));
        assertEquals(0, d.remaining(21_000));
        assertEquals(0, d.remaining(99_000));
    }

    @Test
    public void armingAgainRestartsTheWait() {
        ConnectDelay d = new ConnectDelay();
        d.arm(0, 10_000);
        d.arm(8_000, 10_000);
        assertEquals(10_000, d.remaining(8_000));
    }

    @Test
    public void zeroDelayClearsPendingWait() {
        ConnectDelay d = new ConnectDelay();
        d.arm(0, 60_000);
        d.arm(1_000, 0);
        assertEquals(0, d.remaining(1_000));
    }

    @Test
    public void negativeDelayIsTreatedAsZero() {
        ConnectDelay d = new ConnectDelay();
        d.arm(0, -5);
        assertEquals(0, d.remaining(0));
    }
}
