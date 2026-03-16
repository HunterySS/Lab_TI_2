package ti.lab.lfsr;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;

public final class CryptoFrame extends JFrame {
  private final JTextField seedField = new JTextField(40);
  private final JLabel seedStatus = new JLabel();

  private final JLabel inputFileLabel = new JLabel("Файл не выбран");
  private File inputFile;

  private final JButton chooseButton = new JButton("Выбрать файл...");
  private final JButton encryptButton = new JButton("Зашифровать");
  private final JButton decryptButton = new JButton("Расшифровать");
  private final JButton saveButton = new JButton("Сохранить результат...");
  private final JLabel resultStatus = new JLabel("Результат ещё не сформирован");

  private final JTextArea keyArea = new JTextArea();
  private final JTextArea inputBinArea = new JTextArea();
  private final JTextArea outputBinArea = new JTextArea();

  private File tempResultFile;
  private String suggestedResultName = "result.bin";
  private SwingWorker<?, ?> inputLoadWorker;
  private SwingWorker<?, ?> outputLoadWorker;
  private SwingWorker<?, ?> keyAppendWorker;

  public CryptoFrame() {
    super("Потоковое шифрование LFSR (m=33, x^33 + x^13 + 1)");
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setMinimumSize(new Dimension(1000, 700));

    initUi();
    wireEvents();
    refreshButtons();
    pack();
    setLocationRelativeTo(null);
  }

  private void initUi() {
    JPanel top = new JPanel(new BorderLayout(10, 10));
    top.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    JPanel seedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    seedPanel.add(new JLabel("Начальное состояние (33 бита):"));
    ((AbstractDocument) seedField.getDocument()).setDocumentFilter(new BitsOnlyDocumentFilter(Lfsr33.SIZE));
    seedField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
    seedPanel.add(seedField);
    seedStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    seedPanel.add(seedStatus);
    top.add(seedPanel, BorderLayout.NORTH);

    JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    filePanel.add(chooseButton);
    filePanel.add(inputFileLabel);
    top.add(filePanel, BorderLayout.SOUTH);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    actions.add(encryptButton);
    actions.add(decryptButton);
    actions.add(saveButton);
    actions.add(resultStatus);

    JPanel north = new JPanel(new BorderLayout());
    north.add(top, BorderLayout.CENTER);
    north.add(actions, BorderLayout.SOUTH);
    add(north, BorderLayout.NORTH);

    keyArea.setEditable(false);
    inputBinArea.setEditable(false);
    outputBinArea.setEditable(false);
    keyArea.setLineWrap(true);
    inputBinArea.setLineWrap(true);
    outputBinArea.setLineWrap(true);

    JScrollPane keyScroll = new JScrollPane(keyArea);
    keyScroll.setBorder(BorderFactory.createTitledBorder("Сгенерированный ключ (первые биты)"));

    JScrollPane inScroll = new JScrollPane(inputBinArea);
    inScroll.setBorder(BorderFactory.createTitledBorder("Исходный файл (двоично, первые байты)"));

    JScrollPane outScroll = new JScrollPane(outputBinArea);
    outScroll.setBorder(BorderFactory.createTitledBorder("Результат (двоично, первые байты)"));

    JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inScroll, outScroll);
    bottomSplit.setResizeWeight(0.5);

    JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, keyScroll, bottomSplit);
    mainSplit.setResizeWeight(0.25);

    add(mainSplit, BorderLayout.CENTER);
  }

  private void wireEvents() {
    chooseButton.addActionListener(e -> chooseInputFile());
    encryptButton.addActionListener(e -> runProcess(true));
    decryptButton.addActionListener(e -> runProcess(false));
    saveButton.addActionListener(e -> saveResultAs());

    seedField.getDocument().addDocumentListener((SimpleDocumentListener) e -> refreshButtons());
  }

  private void refreshButtons() {
    String seed = seedField.getText();
    int len = seed.length();
    boolean seedOk = len == Lfsr33.SIZE;
    seedStatus.setText(seedOk ? "OK" : ("Введено " + len + "/" + Lfsr33.SIZE));

    boolean canRun = seedOk && inputFile != null;
    encryptButton.setEnabled(canRun);
    decryptButton.setEnabled(canRun);
    saveButton.setEnabled(tempResultFile != null && tempResultFile.isFile() && !canRunBusy());
  }

  private void chooseInputFile() {
    JFileChooser fc = new JFileChooser();
    fc.setDialogTitle("Выберите файл для шифрования/дешифрования");
    fc.setFileFilter(new FileNameExtensionFilter("Любые файлы", "*"));
    int res = fc.showOpenDialog(this);
    if (res == JFileChooser.APPROVE_OPTION) {
      inputFile = fc.getSelectedFile();
      inputFileLabel.setText(inputFile.getAbsolutePath());
      clearResult();
      loadInputBinaryPreviewAsync();
      refreshButtons();
    }
  }

  private void runProcess(boolean encrypt) {
    String seed = seedField.getText();
    if (seed.length() != Lfsr33.SIZE) {
      JOptionPane.showMessageDialog(this, "Нужно ввести ровно 33 бита (0/1).", "Ошибка", JOptionPane.ERROR_MESSAGE);
      return;
    }
    if (inputFile == null) {
      JOptionPane.showMessageDialog(this, "Сначала выберите входной файл.", "Ошибка", JOptionPane.ERROR_MESSAGE);
      return;
    }

    clearResult();
    resultStatus.setText("Обработка...");

    File outFile;
    try {
      outFile = createTempResultFile(encrypt);
    } catch (IOException io) {
      JOptionPane.showMessageDialog(this, io.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
      resultStatus.setText("Ошибка");
      return;
    }

    setBusy(true);
    keyArea.setText("");
    outputBinArea.setText("");

    cancelWorker(outputLoadWorker);
    cancelWorker(keyAppendWorker);

    StringBuilder keyHeader = new StringBuilder();
    keyHeader.append("Ключ (0/1), длина = ").append(inputFile.length() * 8L).append(" бит\n");
    keyHeader.append("(формируется в фоне, без зависаний)\n\n");
    keyArea.setText(keyHeader.toString());

    new SwingWorker<CryptoEngine.Result, CharSequence>() {
      @Override
      protected CryptoEngine.Result doInBackground() throws Exception {
        return CryptoEngine.process(inputFile, outFile, seed, bitsChunk -> {
          if (isCancelled()) return;
          publish(bitsChunk);
        });
      }

      @Override
      protected void done() {
        setBusy(false);
        try {
          CryptoEngine.Result r = get();
          tempResultFile = outFile;
          resultStatus.setText((encrypt ? "Зашифровано" : "Расшифровано") + " (показано; готово к сохранению)");
          loadBinaryToAreaAsync(outFile, outputBinArea, w -> outputLoadWorker = w);
          refreshButtons();
        } catch (Exception ex) {
          String msg = ex.getCause() instanceof IOException ? ex.getCause().getMessage() : ex.getMessage();
          if (msg == null || msg.isBlank()) msg = ex.toString();
          JOptionPane.showMessageDialog(CryptoFrame.this, msg, "Ошибка", JOptionPane.ERROR_MESSAGE);
          safeDelete(outFile);
          resultStatus.setText("Ошибка");
        }
      }

      @Override
      protected void process(java.util.List<CharSequence> chunks) {
        StringBuilder sb = new StringBuilder();
        for (CharSequence c : chunks) sb.append(c);
        appendGroupedBits(keyArea, sb);
      }
    }.execute();
  }

  private void setBusy(boolean busy) {
    chooseButton.setEnabled(!busy);
    seedField.setEnabled(!busy);
    if (!busy) {
      refreshButtons();
    } else {
      encryptButton.setEnabled(false);
      decryptButton.setEnabled(false);
      saveButton.setEnabled(false);
    }
  }

  private boolean canRunBusy() {
    return !seedField.isEnabled();
  }

  private void saveResultAs() {
    if (tempResultFile == null || !tempResultFile.isFile()) {
      JOptionPane.showMessageDialog(this, "Сначала выполните шифрование/дешифрование.", "Ошибка", JOptionPane.ERROR_MESSAGE);
      return;
    }

    JFileChooser fc = new JFileChooser();
    fc.setDialogTitle("Сохранить результат как...");
    fc.setSelectedFile(new File(Objects.requireNonNullElse(inputFile != null ? inputFile.getParentFile() : null, new File(".")),
        suggestedResultName));
    int res = fc.showSaveDialog(this);
    if (res != JFileChooser.APPROVE_OPTION) return;

    File dest = fc.getSelectedFile();
    try {
      Files.copy(tempResultFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
      JOptionPane.showMessageDialog(this, "Сохранено:\n" + dest.getAbsolutePath(), "Готово", JOptionPane.INFORMATION_MESSAGE);
    } catch (IOException io) {
      JOptionPane.showMessageDialog(this, io.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
    }
  }

  private File createTempResultFile(boolean encrypt) throws IOException {
    String base = inputFile != null ? inputFile.getName() : "result";
    String suffix = encrypt ? ".enc" : ".dec";
    suggestedResultName = base + suffix;
    File tmp = File.createTempFile("lfsr33_", suffix);
    tmp.deleteOnExit();
    return tmp;
  }

  private void clearResult() {
    if (tempResultFile != null) safeDelete(tempResultFile);
    tempResultFile = null;
    resultStatus.setText("Результат ещё не сформирован");
    keyArea.setText("");
    // inputBinArea is filled on file load
    outputBinArea.setText("");
  }

  private void loadInputBinaryPreviewAsync() {
    if (inputFile == null) return;
    cancelWorker(inputLoadWorker);
    loadBinaryToAreaAsync(inputFile, inputBinArea, w -> inputLoadWorker = w);
  }

  private interface WorkerSetter {
    void set(SwingWorker<?, ?> w);
  }

  private void loadBinaryToAreaAsync(File file, JTextArea area, WorkerSetter setter) {
    area.setText("Загрузка (двоично) ...\n");

    SwingWorker<Void, String> worker = new SwingWorker<>() {
      @Override
      protected Void doInBackground() throws Exception {
        long size = file.length();
        long deadline = System.currentTimeMillis() + 30_000; // goal: up to ~30 sec for a few MB

        try (InputStream in = new FileInputStream(file)) {
          byte[] buf = new byte[32 * 1024];
          long readTotal = 0;
          int n;
          while ((n = in.read(buf)) != -1) {
            if (isCancelled()) break;
            StringBuilder sb = new StringBuilder(n * 9);
            BinaryView.appendBytesAsBinary(sb, buf, 0, n, true);
            sb.append('\n');
            publish(sb.toString());

            readTotal += n;
            if (size > 0) setProgress((int) Math.min(100, (readTotal * 100L) / size));
            if (System.currentTimeMillis() > deadline && size >= 2L * 1024 * 1024) {
              // If file is large and we're exceeding target, keep going but yield to UI more often.
              Thread.sleep(1);
              deadline = System.currentTimeMillis() + 30_000;
            }
          }
        }
        return null;
      }

      @Override
      protected void process(java.util.List<String> chunks) {
        for (String s : chunks) {
          area.append(s);
        }
      }

      @Override
      protected void done() {
        // nothing
      }
    };

    setter.set(worker);
    worker.execute();
  }

  private static void appendGroupedBits(JTextArea area, CharSequence bits) {
    // Append bits but insert a space after each 8 bits for readability
    int col = 0;
    String text = area.getText();
    int lastNl = text.lastIndexOf('\n');
    if (lastNl >= 0) {
      int tail = text.length() - lastNl - 1;
      col = tail % 9; // 8 bits + space
    }

    StringBuilder out = new StringBuilder(bits.length() + bits.length() / 8 + 16);
    for (int i = 0; i < bits.length(); i++) {
      char c = bits.charAt(i);
      if (c != '0' && c != '1') continue;
      out.append(c);
      col++;
      if (col == 8) {
        out.append(' ');
        col = 0;
      }
    }
    area.append(out.toString());
  }

  private static void cancelWorker(SwingWorker<?, ?> w) {
    if (w != null) w.cancel(true);
  }

  private static void safeDelete(File f) {
    try {
      if (f != null) Files.deleteIfExists(f.toPath());
    } catch (Exception ignored) {
      // ignore
    }
  }

  private static File suggestOutputFile(File input, boolean encrypt) {
    String name = input.getName();
    String suffix = encrypt ? ".enc" : ".dec";
    return new File(input.getParentFile(), name + suffix);
  }
}

