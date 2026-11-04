package it.panda.launcher.obd;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GearEstimatorTest {

    @Test
    public void ferma() {
        assertEquals(0, GearEstimator.estimate(850, 0));
    }

    @Test
    public void terzaA50() {
        // 50 km/h a ~2580 giri -> 19,4 km/h ogni 1000 giri
        assertEquals(3, GearEstimator.estimate(2580, 50));
    }

    @Test
    public void quintaInAutostrada() {
        assertEquals(5, GearEstimator.estimate(3375, 110));
    }

    @Test
    public void frizionePremutaNonDeterminabile() {
        // in movimento ma giri al minimo: rapporto fuori tabella
        assertEquals(-1, GearEstimator.estimate(900, 60));
    }
}
