package it.panda.launcher.obd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConsumptionHistoryTest {

    @Test
    public void unPuntoOgniDieciSecondi() {
        ConsumptionHistory h = new ConsumptionHistory();
        // 3 l/h a 50 km/h = 6 l/100 km
        for (int i = 0; i < 20; i++) {
            h.add(3.0, 50.0, 1.0);
        }
        assertEquals(2, h.points().size());
        assertEquals(6.0, h.points().get(0), 1e-9);
    }

    @Test
    public void autoFermaLasciaUnBuco() {
        ConsumptionHistory h = new ConsumptionHistory();
        h.add(0.8, 0.0, 10.0);
        assertNull(h.points().get(0));
    }

    @Test
    public void valoriEstremiTagliati() {
        ConsumptionHistory h = new ConsumptionHistory();
        h.add(20.0, 10.0, 10.0); // partenza: 200 l/100 km
        assertEquals(ConsumptionHistory.MAX_L100, h.points().get(0), 1e-9);
    }

    @Test
    public void tieneSoloLUltimaMezzOra() {
        ConsumptionHistory h = new ConsumptionHistory();
        for (int i = 0; i < ConsumptionHistory.CAPACITY + 25; i++) {
            h.add(3.0, 50.0, 10.0);
        }
        assertEquals(ConsumptionHistory.CAPACITY, h.points().size());
        h.reset();
        assertTrue(h.points().isEmpty());
    }
}
