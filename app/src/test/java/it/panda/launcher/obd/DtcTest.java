package it.panda.launcher.obd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class DtcTest {

    @Test
    public void unCodice() {
        assertEquals(Collections.singletonList("P0133"), Dtc.parse("43 01 33 00 00 00 00 \r\r>"));
    }

    @Test
    public void dueCodiciSenzaSpazi() {
        assertEquals(Arrays.asList("P0133", "P0302"), Dtc.parse("430133030200 00"));
    }

    @Test
    public void formatoCanConContatore() {
        // sulle linee CAN dopo 43 c'è il numero di codici
        assertEquals(Arrays.asList("P0133", "P0302"), Dtc.parse("43 02 01 33 03 02"));
    }

    @Test
    public void piuRighe() {
        assertEquals(Arrays.asList("P0133", "P0302"),
                Dtc.parse("43 01 33 00 00 00 00\r43 03 02 00 00 00 00\r\r>"));
    }

    @Test
    public void nessunErrore() {
        assertTrue(Dtc.parse("43 00 00 00 00 00 00").isEmpty());
    }

    @Test
    public void noData() {
        assertTrue(Dtc.parse("NO DATA\r\r>").isEmpty());
    }

    @Test
    public void nullEVuoto() {
        assertTrue(Dtc.parse(null).isEmpty());
        assertTrue(Dtc.parse("").isEmpty());
    }

    @Test
    public void lettereDelSistema() {
        // 0x4123 -> C0123, 0xC100 -> U0100, 0x8001 -> B0001
        assertEquals(Arrays.asList("C0123", "U0100", "B0001"), Dtc.parse("43 41 23 C1 00 80 01"));
    }

    @Test
    public void senzaDuplicati() {
        assertEquals(Collections.singletonList("P0133"), Dtc.parse("43 01 33 01 33 00 00"));
    }

    @Test
    public void mancataAccensioneEGravi() {
        assertTrue(Dtc.isCritical("P0302"));
        assertTrue(Dtc.isCritical("P0300"));
        assertTrue(Dtc.isCritical("P0217"));
    }

    @Test
    public void sondaLambdaEUnAvviso() {
        assertFalse(Dtc.isCritical("P0133"));
        assertFalse(Dtc.isCritical("P0420"));
    }

    @Test
    public void descrizioneNota() {
        assertTrue(Dtc.describe("P0133").toLowerCase().contains("lambda"));
    }

    @Test
    public void descrizioneGenericaPerCodiceSconosciuto() {
        assertEquals("Codice non in elenco", Dtc.describe("P1234"));
    }
}
