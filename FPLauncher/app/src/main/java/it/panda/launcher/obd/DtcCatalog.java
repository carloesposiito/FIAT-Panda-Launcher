package it.panda.launcher.obd;

import java.util.HashMap;
import java.util.Map;

/**
 * Descrizione in italiano dei codici di errore più comuni sul motore Fire della Panda 169.
 * Per gli altri si indica almeno la famiglia (sistema e sottosistema).
 */
public final class DtcCatalog {

    private static final Map<String, String> KNOWN = new HashMap<>();

    static {
        put("P0100", "Debimetro / flusso aria: circuito");
        put("P0105", "Sensore pressione collettore (MAP): circuito");
        put("P0106", "Sensore pressione collettore (MAP): valori anomali");
        put("P0107", "Sensore pressione collettore (MAP): segnale basso");
        put("P0108", "Sensore pressione collettore (MAP): segnale alto");
        put("P0110", "Sensore temperatura aria aspirata: circuito");
        put("P0112", "Sensore temperatura aria aspirata: segnale basso");
        put("P0113", "Sensore temperatura aria aspirata: segnale alto");
        put("P0115", "Sensore temperatura acqua: circuito");
        put("P0116", "Sensore temperatura acqua: valori anomali");
        put("P0117", "Sensore temperatura acqua: segnale basso");
        put("P0118", "Sensore temperatura acqua: segnale alto");
        put("P0120", "Sensore posizione farfalla: circuito");
        put("P0121", "Sensore posizione farfalla: valori anomali");
        put("P0122", "Sensore posizione farfalla: segnale basso");
        put("P0123", "Sensore posizione farfalla: segnale alto");
        put("P0125", "Motore lento a raggiungere la temperatura (termostato?)");
        put("P0128", "Termostato: temperatura acqua sotto il previsto");
        put("P0130", "Sonda lambda anteriore: circuito");
        put("P0131", "Sonda lambda anteriore: tensione bassa");
        put("P0132", "Sonda lambda anteriore: tensione alta");
        put("P0133", "Sonda lambda anteriore: risposta lenta");
        put("P0134", "Sonda lambda anteriore: nessuna attività");
        put("P0135", "Sonda lambda anteriore: riscaldatore");
        put("P0136", "Sonda lambda posteriore: circuito");
        put("P0137", "Sonda lambda posteriore: tensione bassa");
        put("P0138", "Sonda lambda posteriore: tensione alta");
        put("P0140", "Sonda lambda posteriore: nessuna attività");
        put("P0141", "Sonda lambda posteriore: riscaldatore");
        put("P0170", "Correzione miscela: anomalia");
        put("P0171", "Miscela troppo magra");
        put("P0172", "Miscela troppo grassa");
        put("P0201", "Iniettore cilindro 1: circuito");
        put("P0202", "Iniettore cilindro 2: circuito");
        put("P0203", "Iniettore cilindro 3: circuito");
        put("P0204", "Iniettore cilindro 4: circuito");
        put("P0300", "Mancate accensioni su più cilindri");
        put("P0301", "Mancate accensioni cilindro 1");
        put("P0302", "Mancate accensioni cilindro 2");
        put("P0303", "Mancate accensioni cilindro 3");
        put("P0304", "Mancate accensioni cilindro 4");
        put("P0325", "Sensore di battito: circuito");
        put("P0335", "Sensore giri motore (albero): circuito");
        put("P0340", "Sensore fase (albero a camme): circuito");
        put("P0351", "Bobina accensione A: circuito");
        put("P0352", "Bobina accensione B: circuito");
        put("P0420", "Catalizzatore: efficienza sotto la soglia");
        put("P0443", "Valvola spurgo canister: circuito");
        put("P0480", "Elettroventola: circuito di comando");
        put("P0500", "Sensore velocità veicolo: circuito");
        put("P0505", "Regolazione del minimo: anomalia");
        put("P0506", "Minimo più basso del previsto");
        put("P0507", "Minimo più alto del previsto");
        put("P0560", "Tensione di alimentazione: anomalia");
        put("P0562", "Tensione di alimentazione bassa");
        put("P0563", "Tensione di alimentazione alta");
        put("P0601", "Centralina motore: errore memoria");
        put("P0650", "Spia motore: circuito");
        put("P1105", "Sensore pressione atmosferica (Fiat)");
        put("P1170", "Sonda lambda: regolazione al limite (Fiat)");
    }

    private DtcCatalog() {
    }

    private static void put(String code, String text) {
        KNOWN.put(code, text);
    }

    /** Descrizione del codice, oppure la famiglia se non è in elenco. */
    public static String describe(String code) {
        String known = KNOWN.get(code);
        if (known != null) {
            return known;
        }
        if (code == null || code.length() != 5) {
            return "Codice sconosciuto";
        }
        String system;
        switch (code.charAt(0)) {
            case 'P':
                system = "Motore e cambio";
                break;
            case 'C':
                system = "Telaio (ABS, sterzo)";
                break;
            case 'B':
                system = "Carrozzeria (airbag, clima, luci)";
                break;
            default:
                system = "Rete di comunicazione";
                break;
        }
        String scope = code.charAt(1) == '0' || code.charAt(1) == '2' ? "codice standard" : "codice del costruttore";
        if (code.charAt(0) == 'P') {
            switch (code.charAt(2)) {
                case '1':
                case '2':
                    return system + ": aria / alimentazione (" + scope + ")";
                case '3':
                    return system + ": accensione / mancate accensioni (" + scope + ")";
                case '4':
                    return system + ": controllo emissioni (" + scope + ")";
                case '5':
                    return system + ": velocità e minimo (" + scope + ")";
                case '6':
                    return system + ": centralina e uscite (" + scope + ")";
                case '7':
                case '8':
                case '9':
                    return system + ": cambio (" + scope + ")";
                default:
                    break;
            }
        }
        return system + " (" + scope + ")";
    }
}
