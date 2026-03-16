package ti.lab.lfsr;

public final class Lfsr33 {
  public static final int SIZE = 33;

  private static final int TAP_B33_IDX = 0;
  private static final int TAP_B13_IDX = 20;

  private final byte[] reg = new byte[SIZE];

  public Lfsr33(String seedBits) {
    if (seedBits == null || seedBits.length() != SIZE) {
      throw new IllegalArgumentException("Seed must be exactly " + SIZE + " bits");
    }
    for (int i = 0; i < SIZE; i++) {
      char c = seedBits.charAt(i);
      if (c == '0') reg[i] = 0;
      else if (c == '1') reg[i] = 1;
      else throw new IllegalArgumentException("Seed must contain only 0/1");
    }
    boolean allZero = true;
    for (byte b : reg) {
      if (b != 0) {
        allZero = false;
        break;
      }
    }
    if (allZero) {
      throw new IllegalArgumentException("All-zero seed is not allowed for maximal period");
    }
  }

  public int nextBit() {
    int out = reg[0] & 1;
    int feedback = (reg[TAP_B33_IDX] ^ reg[TAP_B13_IDX]) & 1;


    for (int i = 0; i < SIZE - 1; i++) {
      reg[i] = reg[i + 1];
    }
    reg[SIZE - 1] = (byte) feedback;
    return out;
  }
}

