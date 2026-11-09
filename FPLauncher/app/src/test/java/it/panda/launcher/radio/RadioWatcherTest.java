package it.panda.launcher.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RadioWatcherTest {

    @Test
    public void stripsListeningPrefix() {
        assertEquals("88.5Mhz", RadioWatcher.frequencyOf("In ascolto: 88.5Mhz", "Radio"));
    }

    @Test
    public void commaDecimalAndSpace() {
        assertEquals("98,5 MHz", RadioWatcher.frequencyOf("Radio Deejay", "FM 98,5 MHz"));
    }

    @Test
    public void amFrequency() {
        assertEquals("999 kHz", RadioWatcher.frequencyOf("In ascolto: 999 kHz"));
    }

    @Test
    public void bareDecimalWithoutUnit() {
        assertEquals("101.3", RadioWatcher.frequencyOf("In ascolto: 101.3"));
    }

    @Test
    public void offRadioHasNoFrequency() {
        assertNull(RadioWatcher.frequencyOf("Clicca per aprire", "Radio"));
        assertNull(RadioWatcher.frequencyOf("", ""));
    }
}
