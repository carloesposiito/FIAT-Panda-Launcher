package it.panda.launcher.nav;

/**
 * Un'indicazione di navigazione ricevuta dall'app goESP sul telefono.
 * Formato del pacchetto (7 campi separati da '#'):
 * {@code via#FRECCIA#distanza#unità#arrivo#km rimanenti#tempo rimanente},
 * es. {@code Via Roma verso Piazza Duomo#ROTATORY_RIGHT#350#m#17:39#18 km#22 min}.
 * Un pacchetto con tutti i campi vuoti vuol dire "navigazione finita".
 * Classe "pura" (niente Android) così si può provare con i test JUnit.
 */
public final class NavData {

    public final String direction;
    /** Nome della freccia (es. "ROTATORY_RIGHT"), uguale al file in assets/directions; "UNKNOWN" se non riconosciuta. */
    public final String image;
    public final String distance;
    public final String unit;
    public final String arrival;
    public final String remainingDistance;
    public final String remainingTime;

    private NavData(String[] f) {
        direction = f[0];
        image = f[1];
        distance = f[2];
        unit = f[3];
        arrival = f[4];
        remainingDistance = f[5];
        remainingTime = f[6];
    }

    public static NavData parse(String packet) {
        String[] f = new String[7];
        String[] parts = packet == null ? new String[0] : packet.split("#", -1);
        for (int i = 0; i < 7; i++) {
            f[i] = i < parts.length ? parts[i].trim() : "";
        }
        // se la via contiene un '#' i campi in più finiscono in coda: si ricompone la via
        if (parts.length > 7) {
            StringBuilder dir = new StringBuilder(parts[0]);
            int extra = parts.length - 7;
            for (int i = 1; i <= extra; i++) {
                dir.append('#').append(parts[i]);
            }
            f[0] = dir.toString().trim();
            for (int i = 1; i < 7; i++) {
                f[i] = parts[i + extra].trim();
            }
        }
        return new NavData(f);
    }

    /** Nessuna indicazione: navigazione finita o non ancora partita. */
    public boolean isEmpty() {
        return direction.isEmpty() && image.isEmpty() && distance.isEmpty() && arrival.isEmpty();
    }

    /** Distanza alla svolta pronta da mostrare, es. "350 m" o "1,2 km". */
    public String distanceText() {
        if (distance.isEmpty()) {
            return "";
        }
        String d = distance.replace('.', ',');
        return unit.isEmpty() ? d : d + " " + unit;
    }
}
