package cmc.recap.app;

public final class VersionComparator {

    private VersionComparator() {
    }

    public static boolean isLowerThan(String version, String minimumVersion) {
        int[] v = parse(version);
        int[] min = parse(minimumVersion);
        for (int i = 0; i < 3; i++) {
            if (v[i] != min[i]) {
                return v[i] < min[i];
            }
        }
        return false;
    }

    private static int[] parse(String version) {
        String[] parts = version.split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i].trim());
        }
        return result;
    }
}
