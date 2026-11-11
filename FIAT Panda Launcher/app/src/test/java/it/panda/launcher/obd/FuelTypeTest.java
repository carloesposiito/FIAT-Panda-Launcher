package it.panda.launcher.obd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FuelTypeTest {

    @Test
    public void petrolKeepsTheOriginalFormula() {
        // stessi numeri di prima: 12 g/s d'aria = 3,9 l/h
        assertEquals(3.94, FuelEstimator.litersPerHour(12, FuelType.PETROL), 0.01);
        assertEquals(FuelEstimator.litersPerHour(12), FuelEstimator.litersPerHour(12, FuelType.PETROL), 1e-9);
    }

    @Test
    public void lpgBurnsMoreLitersForTheSameAir() {
        // GPL: meno denso (540 g/l) e rapporto 15,5
        assertEquals(5.16, FuelEstimator.litersPerHour(12, FuelType.LPG), 0.01);
        assertTrue(FuelEstimator.litersPerHour(12, FuelType.LPG) > FuelEstimator.litersPerHour(12, FuelType.PETROL));
    }

    @Test
    public void dieselCannotBeEstimatedFromAir() {
        // il diesel lavora con molta aria in eccesso: dall'aria non si ricava il carburante
        assertFalse(FuelType.DIESEL.estimatesFromAir());
        assertTrue(FuelType.PETROL.estimatesFromAir());
        assertTrue(FuelType.LPG.estimatesFromAir());
    }

    @Test
    public void parsesStoredNameAndFallsBackToPetrol() {
        assertEquals(FuelType.DIESEL, FuelType.fromName("DIESEL"));
        assertEquals(FuelType.LPG, FuelType.fromName("LPG"));
        assertEquals(FuelType.PETROL, FuelType.fromName(null));
        assertEquals(FuelType.PETROL, FuelType.fromName("boh"));
    }

    @Test
    public void everyTypeHasAPlausibleDefaultPrice() {
        for (FuelType t : FuelType.values()) {
            assertTrue(t.defaultPriceCents >= 50 && t.defaultPriceCents <= 300);
        }
    }
}
