package org.ravenclient.updater;

public final class ClientVersion {

    /**
     * The launcher's own version. Keep in sync with &lt;version&gt; in pom.xml.
     * On every release: bump this + pom.xml, run {@code mvn package}, and upload
     * {@code target/RavenClient-update-<version>.zip} + a matching update.json
     * to your server so clients can self-update.
     */
    public static final String VERSION = "1.0.31";

    private ClientVersion() {
    }

    /** Numeric, dot-separated compare: "1.0.10" &gt; "1.0.9". */
    public static int compare(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? num(pa[i]) : 0;
            int vb = i < pb.length ? num(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int num(String s) {
        try {
            int i = 0;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            return i == 0 ? 0 : Integer.parseInt(s.substring(0, i));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
