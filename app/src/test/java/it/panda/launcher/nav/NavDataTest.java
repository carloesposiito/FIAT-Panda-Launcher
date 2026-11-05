package it.panda.launcher.nav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavDataTest {

    @Test
    public void pacchettoCompleto() {
        NavData d = NavData.parse("Via Roma verso Piazza Duomo#ROTATORY_RIGHT#1.2#km#17:39#18 km#22 min");
        assertEquals("Via Roma verso Piazza Duomo", d.direction);
        assertEquals("ROTATORY_RIGHT", d.image);
        assertEquals("1,2 km", d.distanceText());
        assertEquals("17:39", d.arrival);
        assertEquals("18 km", d.remainingDistance);
        assertEquals("22 min", d.remainingTime);
        assertFalse(d.isEmpty());
    }

    @Test
    public void pacchettoVuotoFineNavigazione() {
        assertTrue(NavData.parse("######").isEmpty());
        assertTrue(NavData.parse("").isEmpty());
        assertTrue(NavData.parse(null).isEmpty());
    }

    @Test
    public void campiMancantiOInPiu() {
        NavData corto = NavData.parse("Via Roma#CONTINUE_STRAIGHT#350#m");
        assertEquals("350 m", corto.distanceText());
        assertEquals("", corto.remainingTime);

        NavData lungo = NavData.parse("Corso #5#SLIGHT_LEFT#200#m#18:00#3 km#5 min");
        assertEquals("Corso #5", lungo.direction);
        assertEquals("SLIGHT_LEFT", lungo.image);
        assertEquals("5 min", lungo.remainingTime);
    }
}
