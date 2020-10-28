package de.zvxeb.checkcopy.gui;

import de.zvxeb.checkcopy.CheckControl;
import de.zvxeb.checkcopy.CheckCopy;
import de.zvxeb.checkcopy.CheckEventListener;
import de.zvxeb.checkcopy.CheckMeta;
import de.zvxeb.checkcopy.conflict.ChecksumConflict;
import de.zvxeb.checkcopy.conflict.Conflict;
import de.zvxeb.checkcopy.conflict.SizeConflict;
import de.zvxeb.checkcopy.conflict.TypeConflict;
import de.zvxeb.checkcopy.exception.CheckCancelledException;
import de.zvxeb.checkcopy.exception.ChecksumException;
import de.zvxeb.checkcopy.exception.DirectoryReadException;
import de.zvxeb.checkcopy.exception.NotADirectoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;

public class CheckCopyGUI implements Runnable {

    private static Logger log = LoggerFactory.getLogger(CheckCopyGUI.class);

    private JFrame frame;
    private ComboBoxModel<String> cbmChecksum;
    private JCheckBox cbCheckSize;
    private JCheckBox cbFileDest;
    private JCheckBox cbFailFast;
    private JTextField tfSource;
    private JTextField tfDestination;

    private JFileChooser sourceChooser;
    private JFileChooser destinationChooser;

    private ResourceBundle messages;
    private JToggleButton btnLogHandling;
    private JButton btnClearLog;
    private JComboBox<String> cbChecksum;
    private JCheckBox cbReadParallel;
    private JTextPane tpLog;
    private JScrollPane spLog;
    private JTextField tfStatus;
    private JButton btnCheck;
    private JButton sourceButton;
    private JButton destinationButton;

    private CheckControl control = null;

    private CheckMeta metaInformation = null;
    private Instant start;

    private CheckCopy ccConfig = null;
    private JCheckBox cbAutoScroll;

    public CheckCopyGUI() {
        ccConfig = new CheckCopy();
    }

    public CheckCopyGUI(CheckCopy ccConfig) {
        this.ccConfig = ccConfig;
    }

    public static void main(String...args) {
        SwingUtilities.invokeLater(new CheckCopyGUI());
    }

    private void enableConfig() {
        enableComponents(
                sourceButton,
                tfSource,
                destinationButton,
                tfDestination,
                cbFailFast,
                cbFileDest,
                cbCheckSize,
                cbChecksum,
                cbReadParallel,
                btnLogHandling,
                btnClearLog
        );
    }

    private void disableConfig() {
        disableComponents(
            sourceButton,
            tfSource,
            destinationButton,
            tfDestination,
            cbFailFast,
            cbFileDest,
            cbCheckSize,
            cbChecksum,
            cbReadParallel,
            btnLogHandling,
            btnClearLog
        );
    }

    private CheckEventListener eventListener = new CheckEventListener() {
        @Override
        public void onNotInDestination(CheckControl cc, Path source, Path destination, File f) {
            log(String.format(messages.getString("issue_not_in_destination"), f));
        }

        @Override
        public void onNotInSource(CheckControl cc, Path source, Path destination, File f) {
            log(String.format(messages.getString("issue_not_in_source"), f));
        }

        @Override
        public void onConflict(CheckControl cc, Path source, Path destination, File fs, File fd, Conflict c) {
            if(c instanceof TypeConflict) {
                TypeConflict tc = (TypeConflict) c;
                if(tc.isFile()) {
                    log(String.format(messages.getString("issue_type_source_file"), fs, fd));
                } else {
                    log(String.format(messages.getString("issue_type_source_dir"), fs, fd));
                }
                return;
            }
            if(c instanceof SizeConflict) {
                SizeConflict sc = (SizeConflict) c;
                log(String.format(messages.getString("issue_size"), fs, sc.sourceSize(), sc.destinationSize()));
                return;
            }
            if(c instanceof ChecksumConflict) {
                ChecksumConflict sc = (ChecksumConflict) c;
                log(String.format(messages.getString("issue_checksum"), fs, sc.sourceChecksum(), sc.destinationChecksum()));
                return;
            }
            logError(c.toString());
        }

        @Override
        public void onCancelled(CheckControl cc) {
            log(messages.getString("process_cancel_event"));
        }
    };

    private void checkCopy() {
        if(control != null) {
            if(!control.cancelled()) {
                log(messages.getString("process_cancelled"));
                control.cancel();
            }
            return;
        }

        String sSource = tfSource.getText();
        String sDestination = tfDestination.getText();

        Path pSource = Paths.get(sSource);
        Path pDestination = Paths.get(sDestination);

        if(!pSource.toFile().isDirectory()) {
            showErrorMessage(
                messages.getString("error_source_not_directory_title"),
                messages.getString("error_source_not_directory_message")
            );
            return;
        }

        if(!pDestination.toFile().isDirectory()) {
            showErrorMessage(
                messages.getString("error_destination_not_directory_title"),
                messages.getString("error_destination_not_directory_message")
            );
            return;
        }


        if(!btnLogHandling.isSelected()) {
            tpLog.setText(null);
        }

        control = new CheckControl();

        control.failFast(cbFailFast.isSelected());
        control.failOnDestination(cbFileDest.isSelected());

        control.checkSize(cbCheckSize.isSelected());

        if(!control.checkSize()) {
            log(messages.getString("process_no_size"));
        }

        if(cbChecksum.getSelectedIndex() == 0) {
            log(messages.getString("process_no_checksum"));
            control.checksum(false);
        } else {
            String digestAlgorithm = String.valueOf(cbmChecksum.getSelectedItem()).trim();
            log.debug("Trying to get message digest for {}", digestAlgorithm);
            try {
                MessageDigest md = MessageDigest.getInstance(digestAlgorithm);
                control.checksum(md);
            } catch (NoSuchAlgorithmException e) {
                logError(String.format(messages.getString("error_java"), e.getLocalizedMessage()));

                showErrorMessage(
                    messages.getString("error_digest_title"),
                    String.format(messages.getString("error_digest_message"), digestAlgorithm)
                );

                control.release();
                control = null;
                return;
            }
        }

        disableConfig();

        if(control.executor()!=null) {
            control.executor().shutdown();
            control.executor(null);
        }

        if(cbReadParallel.isSelected()) {
            control.executor(Executors.newFixedThreadPool(2));
        } else {
            control.executor(null);
            log(messages.getString("process_no_parallel_read"));
        }

        control.eventListener(eventListener);

        btnCheck.setText(messages.getString("action_cancel_check"));
        btnCheck.setToolTipText(messages.getString("action_cancel_check_tooltip"));

        metaInformation = new CheckMeta();

        new Thread(() -> {
            while(control != null && !control.cancelled()) {
                if(metaInformation!=null) {
                    if(metaInformation.numberOfFiles() > 0 && start != null) {
                        Duration d = Duration.between(start, Instant.now());
                        status(String.format(messages.getString("status_progress"), metaInformation.numberOfFiles(), metaInformation.numberOfDirectories(), CheckCopy.formatTime(d)));
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        new Thread(() -> {

            try {
                start = Instant.now();
                CheckCopy.checkCopy(control, metaInformation, pSource, pDestination);
                Instant end = Instant.now();
                Duration d = Duration.between(start, end);
                String durS = CheckCopy.formatTime(d);
                log(String.format(messages.getString("status_progress_final"), metaInformation.numberOfFiles(), metaInformation.numberOfDirectories(), durS));
                status(messages.getString("status_done"));
            } catch (IOException e) {
                status(messages.getString("status_error"));
                boolean handled = false;
                if(e instanceof NotADirectoryException) {
                    handled = true;
                    NotADirectoryException nade = (NotADirectoryException) e;
                    logError(
                        String.format(
                            nade.isSource() ?
                                messages.getString("exception_source_not_directory") :
                                messages.getString("exception_destination_not_directory"),
                            nade.getPath()
                        )
                    );
                }
                if(e instanceof DirectoryReadException) {
                    handled = true;
                    DirectoryReadException dre = (DirectoryReadException) e;
                    logError(
                            String.format(
                                    dre.isSource() ?
                                            messages.getString("exception_source_read_failure") :
                                            messages.getString("exception_destination_read_failure"),
                                    dre.getPath()
                            )
                    );
                }
                if(e instanceof ChecksumException) {
                    handled = true;
                    ChecksumException ce = (ChecksumException) e;
                    logError(String.format(messages.getString("exception_checksum_failure"), ce.getPath()));
                }
                if(!handled) {
                    logError(String.format(messages.getString("error_java"), e.getLocalizedMessage()));
                }
            }
            catch(CheckCancelledException cce) {
                status(messages.getString("status_cancelled"));
            } finally {
                control.release();
                control = null;
                enableConfig();
            }
            btnCheck.setText(messages.getString("action_check_copy"));
            btnCheck.setToolTipText(messages.getString("action_check_copy_tooltip"));
        }).start();
    }

    @Override
    public void run() {
        messages = ResourceBundle.getBundle("messages", Locale.getDefault());

        frame = new JFrame(messages.getString("title") + " " + messages.getString("version"), MouseInfo.getPointerInfo().getDevice().getDefaultConfiguration());
        frame.setLocationByPlatform(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        ImageIcon iiSource = getIcon("source.png", -1, 32);
        ImageIcon iiDestination = getIcon("destination.png", -1, 32);
        ImageIcon iiClear = getIcon("delete.png", -1, 16);
        ImageIcon iiKeepLog = getIcon("keeplog.png", -1, 16);
        ImageIcon iiDiscardLog = getIcon("discardlog.png", -1, 16);

        JPanel pTop = new JPanel();
        pTop.setLayout(new GridBagLayout());

        sourceButton = new JButton();
        sourceButton.setMargin(new Insets(0,0,0,0));
        AbstractAction chooseSourceAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(sourceChooser == null) {
                    sourceChooser = new JFileChooser();
                    sourceChooser.setDialogTitle(messages.getString("source_dialog_title"));
                    sourceChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    if(destinationChooser != null) {
                        sourceChooser.setCurrentDirectory(destinationChooser.getCurrentDirectory());
                    }
                }
                if(sourceChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    try {
                        tfSource.setText(sourceChooser.getSelectedFile().getCanonicalPath());
                    } catch (IOException ioException) {
                        log.error("Could not get source path!", ioException);
                    }
                }
            }
        };
        sourceButton.setAction(chooseSourceAction);

        sourceButton.setToolTipText(messages.getString("set_source_tooltip"));
        if(iiSource!=null) {
            sourceButton.setIcon(iiSource);
        } else {
            sourceButton.setText("Src");
        }

        tfSource = new JTextField();
        JPanel sourcePanel = new JPanel();
        sourcePanel.setBorder(
                BorderFactory.
                        createTitledBorder(
                                BorderFactory.
                                        createLineBorder(Color.black), messages.getString("source_title"))
        );
        setupFilePanel(sourcePanel, sourceButton, tfSource);

        if(ccConfig.sourceAndDestination != null && ccConfig.sourceAndDestination.size() > 0) {
            tfSource.setText(ccConfig.sourceAndDestination.get(0));
        }

        destinationButton = new JButton();
        destinationButton.setMargin(new Insets(0,0,0,0));
        AbstractAction chooseDestinationAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(destinationChooser == null) {
                    destinationChooser = new JFileChooser();
                    destinationChooser.setDialogTitle(messages.getString("destination_dialog_title"));
                    destinationChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    if(sourceChooser != null) {
                        destinationChooser.setCurrentDirectory(sourceChooser.getCurrentDirectory());
                    }
                }
                if(destinationChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    try {
                        tfDestination.setText(destinationChooser.getSelectedFile().getCanonicalPath());
                    } catch (IOException ioException) {
                        log.error("Could not get destination path!", ioException);
                    }
                }
            }
        };
        destinationButton.setAction(chooseDestinationAction);
        destinationButton.setToolTipText(messages.getString("set_destination_tooltip"));
        if(iiDestination!=null) {
            destinationButton.setIcon(iiDestination);
        } else {
            destinationButton.setText("Dst");
        }

        tfDestination = new JTextField();
        JPanel destPanel = new JPanel();
        destPanel.setBorder(
                BorderFactory.
                        createTitledBorder(
                                BorderFactory.
                                        createLineBorder(Color.black), messages.getString("destination_title"))
        );
        setupFilePanel(destPanel, destinationButton, tfDestination);
        if(ccConfig.sourceAndDestination != null && ccConfig.sourceAndDestination.size() > 1) {
            tfDestination.setText(ccConfig.sourceAndDestination.get(1));
        }

        frame.getRootPane().getInputMap().put(KeyStroke.getKeyStroke("ctrl S"), "source");
        frame.getRootPane().getActionMap().put("source", chooseSourceAction);

        frame.getRootPane().getInputMap().put(KeyStroke.getKeyStroke("ctrl D"), "destination");
        frame.getRootPane().getActionMap().put("destination", chooseDestinationAction);

        cbFailFast = new JCheckBox(messages.getString("option_fail_fast"));
        cbFailFast.setToolTipText(messages.getString("option_fail_fast_tooltip"));

        if(ccConfig.failFast) {
            cbFailFast.setSelected(true);
        }

        cbFileDest = new JCheckBox(messages.getString("option_fail_unexpected"));
        cbFileDest.setToolTipText(messages.getString("option_fail_unexpected_tooltip"));

        if(ccConfig.failUnexpected) {
            cbFileDest.setSelected(true);
        }

        cbCheckSize = new JCheckBox(
            new AbstractAction(messages.getString("option_check_sizes")) {
                {
                    putValue(Action.SHORT_DESCRIPTION, messages.getString("option_check_sizes_tooltip"));
                }
                @Override
                public void actionPerformed(ActionEvent e) {
                    cbChecksum.setEnabled(cbCheckSize.isSelected());
                    cbReadParallel.setEnabled(cbCheckSize.isSelected());
                }
            }
        );
        cbCheckSize.setSelected(!ccConfig.noSizeCheck);

        cbmChecksum = new DefaultComboBoxModel<String>(new String [] { messages.getString("option_checksum_no_check"), "MD5", "SHA-1", "SHA-256" });
        cbChecksum = new JComboBox<>(cbmChecksum);
        cbChecksum.setToolTipText(messages.getString("option_checksum_tooltip"));
        cbChecksum.setEditable(true);

        if(!ccConfig.checkSumAlgorithm.equalsIgnoreCase(CheckCopy.NULL)) {
            boolean found = false;
            for(int i = 0; i < cbmChecksum.getSize(); i++) {
                if(cbmChecksum.getElementAt(i).equalsIgnoreCase(ccConfig.checkSumAlgorithm)) {
                    cbmChecksum.setSelectedItem(cbmChecksum.getElementAt(i));
                    found = true;
                    break;
                }
            }
            if(!found) {
                cbmChecksum.setSelectedItem(ccConfig.checkSumAlgorithm);
            }
        }

        if(!cbCheckSize.isSelected()) {
            cbChecksum.setEnabled(false);
        }

        cbReadParallel = new JCheckBox(messages.getString("option_read_parallel"));
        cbReadParallel.setToolTipText(messages.getString("option_read_parallel_tooltip"));
        cbReadParallel.setSelected(!ccConfig.noParallelRead);
        if(!cbCheckSize.isSelected()) {
            cbReadParallel.setEnabled(false);
        }


        JPanel pOptions = new JPanel();
        pOptions.setLayout(new GridBagLayout());
        pOptions.setBorder(
                BorderFactory.
                        createTitledBorder(
                                BorderFactory.
                                        createLineBorder(Color.black), messages.getString("option_title"))
        );

        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridx = 0;
        pOptions.add(cbFailFast, gbc);
        gbc.gridx = 1;
        pOptions.add(cbFileDest, gbc);
        gbc.gridx = 2;
        pOptions.add(cbCheckSize, gbc);
        gbc.gridx = 3;
        pOptions.add(cbChecksum, gbc);
        gbc.gridx = 4;
        pOptions.add(cbReadParallel, gbc);
        gbc.gridx = 5;
        gbc.weightx = 1;
        pOptions.add(Box.createGlue(), gbc);


        // TOP


        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        pTop.add(sourcePanel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        pTop.add(destPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        pTop.add(pOptions, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        btnCheck = new JButton(new AbstractAction(messages.getString("action_check_copy")) {
            {
                putValue(Action.SHORT_DESCRIPTION, messages.getString("action_check_copy_tooltip"));
            }
            @Override
            public void actionPerformed(ActionEvent e) {
                checkCopy();
            }
        });
        pTop.add(btnCheck, gbc);

        frame.add(pTop, BorderLayout.NORTH);

        JPanel pBottom = new JPanel();
        pBottom.setLayout(new GridBagLayout());

        tfStatus = new JTextField(messages.getString("status_ready"));
        tfStatus.setFont(tfStatus.getFont().deriveFont(Font.BOLD));
        tfStatus.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        tfStatus.setEditable(false);

        gbc = new GridBagConstraints();
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        pBottom.add(tfStatus, gbc);

        cbAutoScroll = new JCheckBox(messages.getString("toggle_log_auto_scroll"));
        cbAutoScroll.setToolTipText(messages.getString("toggle_log_auto_scroll_tooltip"));
        cbAutoScroll.setSelected(true);

        gbc = new GridBagConstraints();
        gbc.gridx = 1;

        pBottom.add(cbAutoScroll, gbc);

        btnLogHandling = new JToggleButton();
        AbstractAction toggleLogHandling = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(e.getSource() instanceof JToggleButton) {
                    JToggleButton btn = (JToggleButton) e.getSource();
                    if(btn.isSelected()) {
                        if(iiKeepLog!=null) {
                            btn.setIcon(iiKeepLog);
                        } else {
                            btn.setText("K");
                        }
                        btn.setToolTipText(messages.getString("toggle_log_keep_tooltip"));
                    } else {
                        if(iiDiscardLog!=null) {
                            btn.setIcon(iiDiscardLog);
                        } else {
                            btn.setText("D");
                        }
                        btn.setToolTipText(messages.getString("toggle_log_discard_tooltip"));
                    }
                }
            }
        };
        btnLogHandling.setMargin(new Insets(0,0,0,0));
        btnLogHandling.setAction(toggleLogHandling);
        btnLogHandling.setToolTipText(messages.getString("toggle_log_discard_tooltip"));

        if(iiDiscardLog != null) {
            btnLogHandling.setIcon(iiDiscardLog);
        } else {
            btnLogHandling.setText("D");
        }

        gbc.gridx = 2;

        pBottom.add(btnLogHandling, gbc);

        btnClearLog = new JButton();
        btnClearLog.setAction(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tpLog.setText(null);
                status(messages.getString("status_log_cleared"));
            }
        });
        btnClearLog.setMargin(new Insets(0,0,0,0));
        if(iiClear!=null) {
            btnClearLog.setIcon(iiClear);
        } else {
            btnClearLog.setText("C");
        }
        btnClearLog.setToolTipText(messages.getString("action_clear_log_tooltip"));

        gbc.gridx = 3;

        pBottom.add(btnClearLog, gbc);

        frame.add(pBottom, BorderLayout.SOUTH);

        tpLog = new JTextPane();
        tpLog.setPreferredSize(new Dimension(200,100));
        tpLog.setEditable(false);
        spLog = new JScrollPane(tpLog);
        spLog.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        spLog.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        frame.add(spLog, BorderLayout.CENTER);

        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setVisible(true);
    }

    private void status(String s) {
        tfStatus.setText(s);
    }

    private void logC(String s, Color c) {
        StyleContext sc = StyleContext.getDefaultStyleContext();
        AttributeSet color = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c);
        color = sc.addAttribute(color, StyleConstants.Alignment, StyleConstants.ALIGN_JUSTIFIED);

        if(cbAutoScroll.isSelected()) {
            tpLog.setCaretPosition(tpLog.getDocument().getLength());
        }
        try {
            tpLog.getDocument().insertString(tpLog.getDocument().getLength(), s+ "\n", color);
        } catch (BadLocationException e) {
            log.error("Unexpected error:", e);
        }
    }

    private void log(String s) {
        log.info(s);
        logC(s, Color.black);
    }
    private void logError(String s) {
        log.error(s);
        logC(s, Color.red);
    }

    private void showErrorMessage(String title, String message) {
        JOptionPane
            .showMessageDialog(
                frame,
                message,
                title,
                JOptionPane.ERROR_MESSAGE
            );
    }

    private static void setupFilePanel(JPanel p, JButton b, JTextField tf) {
        GridBagConstraints gbc;
        p.setLayout(new GridBagLayout());

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;

        p.add(b, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.weightx = 0;
        p.add(Box.createHorizontalStrut(2), gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        p.add(tf, gbc);
    }

    private static ImageIcon getIcon(String name, int width, int height) {
        InputStream is = CheckCopyGUI.class.getClassLoader().getResourceAsStream(name);
        if(is!=null) {
            try {
                BufferedImage bi = ImageIO.read(is);
                if(bi != null) {
                    return new ImageIcon(bi.getScaledInstance(width, height, Image.SCALE_SMOOTH));
                }
            } catch (IOException e) {
            }
        }

        return null;
    }

    private void enableComponents(JComponent...components) {
        for(JComponent c : components) {
            c.setEnabled(true);
        }
    }

    private void disableComponents(JComponent...components) {
        for(JComponent c : components) {
            c.setEnabled(false);
        }
    }
}
