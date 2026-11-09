package it.panda.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Test
    public void uniqueIdAvoidsTakenAndReserved() {
        Set<String> taken = new HashSet<>();
        taken.add(CarProfile.DEFAULT_ID);
        assertEquals("fiat_panda", CarStore.uniqueId("FIAT", "Panda", taken));
        taken.add("fiat_panda");
        assertEquals("fiat_panda_2", CarStore.uniqueId("FIAT", "Panda", taken));
        taken.add("fiat_panda_2");
        assertEquals("fiat_panda_3", CarStore.uniqueId("FIAT", "Panda", taken));
        assertEquals("vw_golf", CarStore.uniqueId("VW", "Golf", taken));
        assertEquals("panda_2", CarStore.uniqueId("", "Panda", taken));
        assertEquals("auto", CarStore.uniqueId("!!", "??", taken));
    }

    @Test
    public void listRoundTripsAndKeepsOddCharacters() {
        List<CarProfile> cars = new ArrayList<>();
        cars.add(new CarProfile("golf", "VW", "Golf\tGTI\n2"));
        cars.add(new CarProfile("c3", "Citroën", "C3"));
        List<CarProfile> back = CarStore.decode(CarStore.encode(cars));
        assertEquals(2, back.size());
        assertEquals("golf", back.get(0).id);
        assertEquals("Golf GTI 2", back.get(0).model);
        assertEquals("Citroën", back.get(1).brand);
    }

    @Test
    public void decodeIgnoresBrokenAndEmpty() {
        assertTrue(CarStore.decode(null).isEmpty());
        assertTrue(CarStore.decode("").isEmpty());
        assertTrue(CarStore.decode("solo\tdue").isEmpty());
    }

    @Test
    public void decodeSkipsReservedDefaultId() {
        assertTrue(CarStore.decode(CarProfile.DEFAULT_ID + "\tX\tY").isEmpty());
    }
}
