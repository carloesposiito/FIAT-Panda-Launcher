package it.panda.launcher.obd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class DiagnosticsTest {

    @Test
    public void decodificaCodici() {
        assertEquals("P0133", DtcParser.decode(0x01, 0x33));
        assertEquals("C0300", DtcParser.decode(0x43, 0x00));
        assertEquals("B1234", DtcParser.decode(0x92, 0x34));
        assertEquals("U0100", DtcParser.decode(0xC1, 0x00));
    }

    @Test
    public void rispostaKLine() {
        // tre codici per riga, coppie a zero = vuoto
        assertEquals(Arrays.asList("P0133", "P0420"), DtcParser.parse("43013304200000\r\n>", 0x43));
        assertEquals(Arrays.asList("P0133", "P0420", "P0300", "P0171"),
                DtcParser.parse("43 01 33 04 20 03 00\r43 01 71 00 00 00 00\r\r>", 0x43));
    }

    @Test
    public void rispostaCan() {
        assertEquals(Arrays.asList("P0133", "P0420"), DtcParser.parse("4302013304 20", 0x43));
        assertEquals(Arrays.asList("P0133", "P0420", "P0300"),
                DtcParser.parse("00A\r0: 43 03 01 33 04 20\r1: 03 00 00 00 00 00 00\r>", 0x43));
    }

    @Test
    public void nessunCodice() {
        assertTrue(DtcParser.parse("43 00 00 00 00 00 00", 0x43).isEmpty());
        assertTrue(DtcParser.parse("NO DATA", 0x43).isEmpty());
        assertTrue(DtcParser.parse(null, 0x47).isEmpty());
        assertEquals(Collections.singletonList("P0171"), DtcParser.parse("47 01 71 00 00 00 00", 0x47));
    }

    @Test
    public void spiaMotore() {
        assertTrue(DtcParser.milOn("41 01 82 07 E5 00"));
        assertFalse(DtcParser.milOn("41 01 00 07 E5 00"));
        assertNull(DtcParser.milOn("NO DATA"));
    }

    @Test
    public void avvisi() {
        ObdSnapshot s = new ObdSnapshot();
        assertTrue(Diagnostics.evaluate(s).isEmpty());
        assertNull(Diagnostics.worst(Diagnostics.evaluate(s)));

        s.fuelLevelPct = 6.0;
        List<Diagnostics.Alert> a = Diagnostics.evaluate(s);
        assertEquals(1, a.size());
        assertEquals(Diagnostics.Severity.ORANGE, Diagnostics.worst(a));

        s.milOn = true;
        s.dtcStored = Collections.singletonList("P0420");
        s.dtcPending = Arrays.asList("P0420", "P0133");
        a = Diagnostics.evaluate(s);
        // spia + P0420 memorizzato + P0133 in attesa (P0420 non ripetuto) + riserva
        assertEquals(4, a.size());
        assertEquals(Diagnostics.Severity.RED, Diagnostics.worst(a));
        assertEquals("Catalizzatore: efficienza sotto la soglia", a.get(1).title);
    }

    @Test
    public void batteriaNonCarica() {
        ObdSnapshot s = new ObdSnapshot();
        s.rpm = 900.0;
        s.batteryV = 12.3;
        assertEquals(Diagnostics.Severity.RED, Diagnostics.worst(Diagnostics.evaluate(s)));
        s.batteryV = 14.1;
        assertTrue(Diagnostics.evaluate(s).isEmpty());
    }

    @Test
    public void descrizioneGenerica() {
        assertTrue(DtcCatalog.describe("P0399").startsWith("Motore e cambio: accensione"));
        assertTrue(DtcCatalog.describe("B1001").startsWith("Carrozzeria"));
    }
}
