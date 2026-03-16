package ti.lab.lfsr;

public final class BinaryView {
  private BinaryView() {}

  private static final String[] BYTE_TO_BITS = new String[256];

  static {
    for (int v = 0; v < 256; v++) {
      char[] bits = new char[8];
      for (int b = 7; b >= 0; b--) {
        bits[7 - b] = (((v >>> b) & 1) == 1) ? '1' : '0';
      }
      BYTE_TO_BITS[v] = new String(bits);
    }
  }

  public static void appendBytesAsBinary(StringBuilder sb, byte[] data, int off, int len, boolean appendTrailingSpace) {
    int end = off + len;
    for (int i = off; i < end; i++) {
      sb.append(BYTE_TO_BITS[data[i] & 0xFF]);
      if (appendTrailingSpace || i != end - 1) sb.append(' ');
    }
  }

  public static String bitsToGroupedBinary(String bits, int groupSize) {
    if (bits == null) return "";
    StringBuilder sb = new StringBuilder(bits.length() + Math.max(0, bits.length() / Math.max(1, groupSize)));
    for (int i = 0; i < bits.length(); i++) {
      if (i > 0 && groupSize > 0 && i % groupSize == 0) sb.append(' ');
      sb.append(bits.charAt(i));
    }
    return sb.toString();
  }
}

