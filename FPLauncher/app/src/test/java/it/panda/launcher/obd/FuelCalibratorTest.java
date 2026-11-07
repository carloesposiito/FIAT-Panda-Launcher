package it.panda.launcher.obd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FuelCalibratorTest {

    /** Intervallo lungo: il livello filtrato raggiunge subito quello letto. */
    private static final double LONG = 6000;
    private static final double TANK = 40;

    @Test
    public void sottoIlDieciPerCentoNonCambiaNulla() {
        FuelCalibrator c = new FuelCalibrator();
        assertFalse(c.add(80.0, 0, LONG, TANK));
        assertFalse(c.add(75.0, 2, LONG, TANK));
        assertEquals(1.0, c.factor(), 1e-9);
    }

    @Test
    public void stimaTroppoBassaAlzaIlFattore() {
        FuelCalibrator c = new FuelCalibrator();
        c.add(80.0, 0, LONG, TANK);
        c.add(75.0, 1.5, LONG, TANK);
        // 10 punti su 40 l = 4 l reali, stimati 3 l: rapporto 1,333; primo aggiornamento a metà strada
        assertTrue(c.add(70.0, 1.5, LONG, TANK));
        assertEquals(0.5 + 0.5 * 4.0 / 3.0, c.factor(), 1e-9);
    }

    @Test
    public void stimaTroppoAltaAbbassaIlFattore() {
        FuelCalibrator c = new FuelCalibrator();
        c.add(80.0, 0, LONG, TANK);
        assertTrue(c.add(70.0, 5.0, LONG, TANK)); // reali 4 l, stimati 5 l
        assertEquals(0.5 + 0.5 * 0.8, c.factor(), 1e-9);
    }

    @Test
    public void ilRifornimentoAzzeraIlRiferimento() {
        FuelCalibrator c = new FuelCalibrator();
        c.add(30.0, 0, LONG, TANK);
        c.add(28.0, 1.0, LONG, TANK);
        assertFalse(c.add(90.0, 0, LONG, TANK)); // pieno: il livello sale di colpo
        assertEquals(1.0, c.factor(), 1e-9);
        // dopo il pieno si riparte da 90: serve un nuovo calo del 10%
        assertFalse(c.add(85.0, 2.0, LONG, TANK));
        assertTrue(c.add(80.0, 2.0, LONG, TANK));
    }

    @Test
    public void ilFattoreRestaNeiLimiti() {
        FuelCalibrator c = new FuelCalibrator();
        c.add(80.0, 0, LONG, TANK);
        c.add(70.0, 0.001, LONG, TANK); // stima quasi nulla: rapporto enorme, limitato a 2
        assertEquals(1.5, c.factor(), 1e-9);
        // altri aggiornamenti: il fattore non supera mai 1,6
        for (int i = 0; i < 20; i++) {
            c.add(60.0 - 10 * i % 20, 0.001, LONG, TANK);
            c.add(50.0 - 10 * i % 20 - 10, 0.001, LONG, TANK);
        }
        assertTrue(c.factor() <= 1.6 + 1e-9);
        assertTrue(c.factor() >= 0.6 - 1e-9);
    }

    @Test
    public void senzaLivelloNienteCambia() {
        FuelCalibrator c = new FuelCalibrator();
        assertFalse(c.add(null, 3.0, LONG, TANK));
        assertFalse(c.add(null, 3.0, LONG, TANK));
        assertEquals(1.0, c.factor(), 1e-9);
    }

    @Test
    public void salvataggioERipristino() {
        FuelCalibrator c = new FuelCalibrator();
        c.add(80.0, 0, LONG, TANK);
        c.add(75.0, 1.5, LONG, TANK);
        c.add(70.0, 1.5, LONG, TANK);
        String saved = c.serialize();
        FuelCalibrator d = new FuelCalibrator();
        d.restore(saved);
        assertEquals(c.factor(), d.factor(), 1e-9);
        assertEquals(c.factor(), FuelCalibrator.factorOf(saved), 1e-9);
    }

    @Test
    public void statoSalvatoRovinatoSiIgnora() {
        FuelCalibrator c = new FuelCalibrator();
        c.restore("non valido");
        c.restore(null);
        assertEquals(1.0, c.factor(), 1e-9);
        assertEquals(1.0, FuelCalibrator.factorOf(null), 1e-9);
    }

    @Test
    public void azzeramentoTornaAUno() {
        FuelCalibrator c = new FuelCalibrator();
        c.add(80.0, 0, LONG, TANK);
        c.add(70.0, 5.0, LONG, TANK);
        c.reset();
        assertEquals(1.0, c.factor(), 1e-9);
    }
}
