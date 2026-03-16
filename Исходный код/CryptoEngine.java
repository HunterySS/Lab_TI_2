package ti.lab.lfsr;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class CryptoEngine {
  public record Result(
      long totalBytes
  ) {}

  private CryptoEngine() {}

  public interface KeyBitsSink {
    void acceptKeyBits(CharSequence bitsChunk);
  }

  public static Result process(File input, File output, String seedBits, KeyBitsSink keySink) throws IOException {
    if (input == null || !input.isFile()) throw new IOException("Некорректный входной файл");
    if (output == null) throw new IOException("Некорректный выходной файл");

    Lfsr33 lfsr = new Lfsr33(seedBits);

    StringBuilder keyChunk = new StringBuilder(8192);

    long total = 0;
    try (var in = new BufferedInputStream(new FileInputStream(input));
         var out = new BufferedOutputStream(new FileOutputStream(output))) {

      byte[] buf = new byte[8192];
      int read;
      while ((read = in.read(buf)) != -1) {
        for (int i = 0; i < read; i++) {
          int plain = buf[i] & 0xFF;
          int cipher = 0;
          for (int bit = 7; bit >= 0; bit--) {
            int p = (plain >>> bit) & 1;
            int k = lfsr.nextBit();
            int c = p ^ k;
            cipher |= (c << bit);
            keyChunk.append(k == 1 ? '1' : '0');
            if (keyChunk.length() >= 8192) {
              if (keySink != null) keySink.acceptKeyBits(keyChunk);
              keyChunk.setLength(0);
            }
          }
          buf[i] = (byte) cipher;
        }

        out.write(buf, 0, read);
        total += read;
      }
    }

    if (keyChunk.length() > 0 && keySink != null) keySink.acceptKeyBits(keyChunk);
    return new Result(total);
  }
}

