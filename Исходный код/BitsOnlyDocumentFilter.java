package ti.lab.lfsr;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

public final class BitsOnlyDocumentFilter extends DocumentFilter {
  private final int maxLen;

  public BitsOnlyDocumentFilter(int maxLen) {
    this.maxLen = Math.max(0, maxLen);
  }

  @Override
  public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
      throws BadLocationException {
    if (string == null || string.isEmpty()) return;
    replace(fb, offset, 0, string, attr);
  }

  @Override
  public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
      throws BadLocationException {
    if (text == null) text = "";
    String filtered = filterBits(text);
    if (filtered.isEmpty() && length == 0) return;

    int currentLen = fb.getDocument().getLength();
    int newLen = currentLen - length + filtered.length();
    if (newLen > maxLen) {
      int allowed = maxLen - (currentLen - length);
      if (allowed <= 0) return;
      filtered = filtered.substring(0, allowed);
    }

    super.replace(fb, offset, length, filtered, attrs);
  }

  private static String filterBits(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '0' || c == '1') sb.append(c);
    }
    return sb.toString();
  }
}

