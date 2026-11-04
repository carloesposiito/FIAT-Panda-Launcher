package it.panda.launcher.apps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FavoriteSelectionTest {

    private static final List<String> ALL = Arrays.asList("a", "b", "c", "d");

    @Test
    public void nessunaScelta_mostraTutte() {
        assertEquals(ALL, FavoriteSelection.select(ALL, null, s -> s));
    }

    @Test
    public void rispettaOrdineSalvato() {
        assertEquals(Arrays.asList("c", "a"),
                FavoriteSelection.select(ALL, Arrays.asList("c", "a"), s -> s));
    }

    @Test
    public void ignoraAppDisinstallate() {
        assertEquals(Collections.singletonList("b"),
                FavoriteSelection.select(ALL, Arrays.asList("x", "b"), s -> s));
    }

    @Test
    public void sceltaVuota_nessunaApp() {
        assertEquals(Collections.emptyList(),
                FavoriteSelection.select(ALL, Collections.emptyList(), s -> s));
    }

    @Test
    public void primoToggle_partiDaTutteSelezionate() {
        assertEquals(Arrays.asList("a", "c", "d"),
                FavoriteSelection.toggle(null, ALL, "b", false));
    }

    @Test
    public void toggleOn_aggiungeInFondo() {
        assertEquals(Arrays.asList("c", "a"),
                FavoriteSelection.toggle(Collections.singletonList("c"), ALL, "a", true));
    }

    @Test
    public void toggleOn_nonDuplica() {
        assertEquals(Collections.singletonList("a"),
                FavoriteSelection.toggle(Collections.singletonList("a"), ALL, "a", true));
    }

    @Test
    public void encodeDecode_andataRitorno() {
        List<String> keys = Arrays.asList("it.x/it.x.Main", "it.y/.Home");
        assertEquals(keys, FavoriteSelection.decode(FavoriteSelection.encode(keys)));
    }

    @Test
    public void decode_assenteONullo() {
        assertNull(FavoriteSelection.decode(null));
    }

    @Test
    public void decode_listaVuotaRestaVuota() {
        assertEquals(Collections.emptyList(),
                FavoriteSelection.decode(FavoriteSelection.encode(Collections.emptyList())));
    }
}
