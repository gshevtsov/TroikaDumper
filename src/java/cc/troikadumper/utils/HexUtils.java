package cc.troikadumper.utils;

public class HexUtils {

    private static final byte[] HEX_CHAR_TABLE = { (byte) '0', (byte) '1',
            (byte) '2', (byte) '3', (byte) '4', (byte) '5', (byte) '6',
            (byte) '7', (byte) '8', (byte) '9', (byte) 'A', (byte) 'B',
            (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F' };

    public static String toString(byte[] raw) {
        int len = raw.length;
        byte[] hex = new byte[2 * len];
        int index = 0;
        int pos = 0;

        for (byte b : raw) {
            if (pos >= len)
                break;

            pos++;
            int v = b & 0xFF;
            hex[index++] = HEX_CHAR_TABLE[v >>> 4];
            hex[index++] = HEX_CHAR_TABLE[v & 0xF];
        }

        return new String(hex);
    }

    public static byte[] fromString(String hex) {
        int len = hex.length();
        if (len % 2 == 1) {
            throw new IllegalArgumentException("hex length is not even");
        }
        len = len / 2; // actual

        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            bytes[i] = (byte) (Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16) & 0xFF);
        }
        return bytes;
    }

}
