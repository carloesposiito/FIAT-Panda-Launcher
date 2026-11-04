package it.panda.launcher.obd;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

public class ObdParserTest {

    @Test
    public void giriConSpazi() {
        int[] d = ObdParser.parseMode01("41 0C 1A F8 \r\r>", 0x0C, 2);
        assertArrayEquals(new int[]{0x1A, 0xF8}, d);
        assertEquals(1726.0, ObdPid.RPM.decode(d), 0.001);
    }

    @Test
    public void giriSenzaSpazi() {
        int[] d = ObdParser.parseMode01("410C1AF8", 0x0C, 2);
        assertEquals(1726.0, ObdPid.RPM.decode(d), 0.001);
    }

    @Test
    public void primaRigaSearching() {
        int[] d = ObdParser.parseMode01("SEARCHING...\r410D32\r\r>", 0x0D, 1);
        assertEquals(50.0, ObdPid.SPEED.decode(d), 0.001);
    }

    @Test
    public void rispostaDiUnAltroPidIgnorata() {
        assertNull(ObdParser.parseMode01("410D32", 0x0C, 2));
    }

    @Test
    public void rispostaTroppoCorta() {
        assertNull(ObdParser.parseMode01("410C1A", 0x0C, 2));
    }

    @Test
    public void noDataNonSonoDati() {
        assertNull(ObdParser.parseMode01("NO DATA\r\r>", 0x0C, 2));
        assertTrue(ObdParser.isError("NO DATA"));
        assertTrue(ObdParser.isError("UNABLE TO CONNECT"));
        assertFalse(ObdParser.isError("410C1AF8"));
    }

    @Test
    public void temperaturaAcqua() {
        int[] d = ObdParser.parseMode01("41 05 82", 0x05, 1);
        assertEquals(90.0, ObdPid.COOLANT_TEMP.decode(d), 0.001);
    }

    @Test
    public void pidSupportati() {
        // BE 1F A8 13: tipico di una centralina benzina
        Set<Integer> s = ObdParser.parseSupportedPids("41 00 BE 1F A8 13", 0x00);
        assertTrue(s.contains(0x01));
        assertFalse(s.contains(0x02));
        assertTrue(s.contains(0x04));
        assertTrue(s.contains(0x05));
        assertTrue(s.contains(0x0C));
        assertTrue(s.contains(0x0D));
        assertTrue(s.contains(0x20));
    }

    @Test
    public void tensioneBatteria() {
        assertEquals(14.1, ObdParser.parseVoltage("14.1V\r\r>"), 0.001);
        assertEquals(12.6, ObdParser.parseVoltage("12,6V"), 0.001);
        assertNull(ObdParser.parseVoltage("?"));
    }

    @Test
    public void comandi() {
        assertEquals("010C", ObdPid.RPM.command());
        assertEquals("0142", ObdPid.MODULE_VOLTAGE.command());
    }
}
