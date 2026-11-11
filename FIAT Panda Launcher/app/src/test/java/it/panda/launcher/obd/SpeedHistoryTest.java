package it.panda.launcher.obd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpeedHistoryTest {

    @Test
    public void unPuntoOgniDieciSecondiConLaVelocitaMedia() {
        SpeedHistory h = new SpeedHistory();
        for (int i = 0; i < 10; i++) {
            h.add(60.0, 1.0);
        }
        assertEquals(1, h.points().size());
        assertEquals(60.0, h.points().get(0), 0.001);
    }

    @Test
    public void sottoDieciSecondiNessunPunto() {
        SpeedHistory h = new SpeedHistory();
        h.add(60.0, 9.0);
        assertTrue(h.points().isEmpty());
    }

    @Test
    public void velocitaMancanteContaComeFerma() {
        SpeedHistory h = new SpeedHistory();
        h.add(null, 10.0);
        assertEquals(0.0, h.points().get(0), 0.001);
    }

    @Test
    public void tieneSoloLUltimaMezzOra() {
        SpeedHistory h = new SpeedHistory();
        for (int i = 0; i < SpeedHistory.CAPACITY + 20; i++) {
            h.add((double) i, 10.0);
        }
        assertEquals(SpeedHistory.CAPACITY, h.points().size());
        assertEquals(SpeedHistory.CAPACITY + 19.0, h.points().get(SpeedHistory.CAPACITY - 1), 0.001);
    }

    @Test
    public void resetSvuotaTutto() {
        SpeedHistory h = new SpeedHistory();
        h.add(50.0, 10.0);
        h.reset();
        assertTrue(h.points().isEmpty());
    }
}