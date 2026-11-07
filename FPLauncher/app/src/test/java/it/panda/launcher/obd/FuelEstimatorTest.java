package it.panda.launcher.obd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class FuelEstimatorTest {

    @Test
    public void flussoAriaA2500GiriMezzoCarico() {
        // 1.2 Fire, 2500 rpm, 50 kPa, 30 °C: circa 12 g/s
        double maf = FuelEstimator.airFlowFromMap(2500, 50, 30, 1.242, 0.80);
        assertEquals(11.9, maf, 0.2);
    }

    @Test
    public void consumoPlausibileA80Kmh() {
        double maf = FuelEstimator.airFlowFromMap(2500, 50, 30, 1.242, 0.80);
        double lph = FuelEstimator.litersPerHour(maf);
        assertEquals(3.9, lph, 0.1);
    }

    @Test
    public void mediaDelViaggio() {
        FuelEstimator f = new FuelEstimator();
        assertNull(f.average());
        // un'ora a 50 km/h consumando 3 l/h = 6 l/100 km
        f.add(3.0, 50, 3600);
        assertEquals(6.0, f.average(), 0.001);
    }
}
