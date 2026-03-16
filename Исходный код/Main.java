package ti.lab.lfsr;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception ignored) {
      }
      new CryptoFrame().setVisible(true);
    });
  }
}

