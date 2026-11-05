package it.panda.launcher.obd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TripComputerTest {

    @Test
    public void mediaEDistanza() {
        TripComputer t = new TripComputer();
        assertNull(t.summary().averageL100());
        // un'ora a 50 km/h consumando 3 l/h = 6 l/100 km
        t.add(3.0, 50.0, 3600);
        TripComputer.Summary s = t.summary();
        assertEquals(50.0, s.distanceKm, 1e-9);
        assertEquals(3.0, s.fuelLiters, 1e-9);
        assertEquals(6.0, s.averageL100(), 1e-9);
        assertEquals(50.0, s.averageSpeedKmh(), 1e-9);
    }

    @Test
    public void leSosteNonAbbassanoLaVelocitaMedia() {
        TripComputer t = new TripComputer();
        t.add(3.0, 60.0, 1800); // mezz'ora a 60 = 30 km
        t.add(0.8, 0.0, 1800);  // mezz'ora fermi al minimo
        TripComputer.Summary s = t.summary();
        assertEquals(60.0, s.averageSpeedKmh(), 1e-9);
        assertEquals(3600, s.seconds, 1e-9);
        // il minimo conta nei consumi: (1.5 + 0.4) l su 30 km
        assertEquals(1.9 / 30 * 100, s.averageL100(), 1e-9);
        assertEquals(60.0, s.maxSpeedKmh, 1e-9);
    }

    @Test
    public void datiMancantiNonRompono() {
        TripComputer t = new TripComputer();
        t.add(null, 40.0, 60);
        t.add(2.0, null, 60);
        t.add(2.0, 40.0, -1);
        TripComputer.Summary s = t.summary();
        assertEquals(40.0 / 60, s.distanceKm, 1e-9);
        assertEquals(2.0 / 60, s.fuelLiters, 1e-9);
        assertEquals(120, s.seconds, 1e-9);
    }

    @Test
    public void salvataggioERipristino() {
        TripComputer a = new TripComputer();
        a.add(4.5, 90.0, 1234);
        TripComputer b = new TripComputer();
        b.restore(a.serialize());
        assertEquals(a.summary().distanceKm, b.summary().distanceKm, 1e-5);
        assertEquals(a.summary().fuelLiters, b.summary().fuelLiters, 1e-5);
        assertEquals(a.summary().maxSpeedKmh, b.summary().maxSpeedKmh, 1e-5);

        b.restore("spazzatura");
        assertEquals(0, b.summary().distanceKm, 0);
        b.restore(null);
        assertEquals(0, b.summary().seconds, 0);
    }

    @Test
    public void azzeramento() {
        TripComputer t = new TripComputer();
        t.add(3.0, 50.0, 600);
        t.reset();
        assertEquals(0, t.summary().distanceKm, 0);
        assertNull(t.summary().averageL100());
    }
}
