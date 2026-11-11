package it.panda.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class CarProfileTest {

    @Test
    public void folderNameJoinsBrandAndModel() {
        assertEquals("FIAT_Panda", CarProfile.folderName("FIAT", "Panda"));
    }

    @Test
    public void folderNameReplacesSpacesAndDropsSymbols() {
        assertEquals("Alfa_Romeo_Giulietta_1.4", CarProfile.folderName("Alfa  Romeo", "Giulietta 1.4!"));
    }

    @Test
    public void folderNameStripsAccents() {
        assertEquals("Citroen_C3", CarProfile.folderName("Citroën", "C3"));
    }

    @Test
    public void folderNameWithOnlyOneFieldHasNoDanglingUnderscore() {
        assertEquals("Panda", CarProfile.folderName("", "Panda"));
        assertEquals("FIAT", CarProfile.folderName("FIAT", " "));
    }

    @Test
    public void folderNameNeverContainsPathSeparators() {
        String f = CarProfile.folderName("../..", "a/b\\c");
        assertFalse(f.contains("/"));
        assertFalse(f.contains("\\"));
        assertFalse(f.startsWith("."));
    }

    @Test
    public void defaultCarKeepsLegacyKeys() {
        assertEquals("tank_liters", CarProfile.keyFor(CarProfile.DEFAULT_ID, "tank_liters"));
    }

    @Test
    public void otherCarsGetTheirOwnKeys() {
        assertEquals("car.golf.tank_liters", CarProfile.keyFor("golf", "tank_liters"));
        assertFalse(CarProfile.keyFor("golf", "x").equals(CarProfile.keyFor("polo", "x")));
    }

    @Test
    public void prefixOfOneCarNeverMatchesAnothersKeys() {
        String prefix = CarProfile.keyFor("fiat_panda", "");
        assertFalse(CarProfile.keyFor("fiat_panda_2", "tank_liters").startsWith(prefix));
    }
}
