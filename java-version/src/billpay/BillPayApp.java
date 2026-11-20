package billpay;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class BillPayApp extends JFrame {
    private static final Color BG_CANVAS = new Color(248, 250, 252);
    private static final Color SURFACE = Color.WHITE;
    private static final Color BORDER_SUBTLE = new Color(226, 232, 240);
    private static final Color BORDER_STRONG = new Color(203, 213, 225);
    private static final Color INK_PRIMARY = new Color(15, 23, 42);
    private static final Color INK_SECONDARY = new Color(51, 65, 85);
    private static final Color INK_MUTED = new Color(100, 116, 139);

    private static final Color EMERALD_BG = new Color(236, 253, 245);
    private static final Color EMERALD_BG_HOVER = new Color(209, 250, 229);
    private static final Color EMERALD_BORDER = new Color(110, 231, 183);
    private static final Color EMERALD_TEXT = new Color(6, 95, 70);
    private static final Color EMERALD_ACCENT = new Color(5, 150, 105);

    private static final Color BLUE_ACCENT = new Color(37, 99, 235);
    private static final Color BLUE_TEXT = new Color(29, 78, 216);
    private static final Color RED_ACCENT = new Color(220, 38, 38);
    private static final Color RED_HOVER = new Color(185, 28, 28);
    private static final Color SLATE_HOVER = new Color(241, 245, 249);
    private static final Color TAB_TRACK_BG = new Color(226, 232, 240);

    private static final Font FONT_BRAND = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    private static final Font FONT_LABEL = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private static final Font FONT_BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private static final Font FONT_SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font FONT_SMALL_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font FONT_MONO = new Font(Font.MONOSPACED, Font.PLAIN, 14);
    private static final Font FONT_MONO_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 14);
    private static final Font FONT_TALLY_BIG = new Font(Font.MONOSPACED, Font.BOLD, 24);
    private static final Font FONT_MONO_SMALL = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private enum FilterMode {
        ALL, PAID, UNPAID
    }

    private enum FeedbackTone {
        SUCCESS, INFO, ERROR, NEUTRAL
    }

    private static final class FeedbackState {
        final FeedbackTone tone;
        final String message;
        final String actionLabel;
        final Runnable onAction;

        FeedbackState(FeedbackTone tone, String message, String actionLabel, Runnable onAction) {
            this.tone = tone;
            this.message = message;
            this.actionLabel = actionLabel;
            this.onAction = onAction;
        }
    }

    private final PaymentStore store = new PaymentStore();
    private TreeSet<Integer> paidNumbers;
    private final Deque<TreeSet<Integer>> history = new ArrayDeque<TreeSet<Integer>>();
    private FilterMode filterMode = FilterMode.ALL;
    private List<Integer> lastCheckedNumbers = new ArrayList<Integer>();
    private FeedbackState feedback = null;
    private boolean confirmReset = false;
    private Integer lastScrolledFocus = null;
    private int currentGridColumns = 12;

    // Header widgets
    private StyledButton resetBtn;
    private JPanel resetConfirmPanel;

    // Lookup widgets
    private JTextField numberInput;
    private StyledButton clearInputBtn;
    private JLabel feedbackLabel;
    private JButton feedbackActionBtn;

    // Tally widgets
    private JLabel paidCountLabel;
    private JLabel remainingCountLabel;
    private SmoothProgressBar progressBar;
    private JLabel collectedLabel;

    // Toolbar widgets
    private StyledButton tabAllBtn;
    private StyledButton tabPaidBtn;
    private StyledButton tabUnpaidBtn;
    private StyledButton undoBtn;
    private StyledButton copyUnpaidBtn;

    // Grid widgets
    private JScrollPane gridScrollPane;
    private ScrollableGridPanel gridContainer;
    private JPanel emptyStatePanel;
    private JLabel emptyTitleLabel;
    private JLabel emptyDescLabel;
    private final Map<Integer, ChecklistTile> visibleTiles = new HashMap<Integer, ChecklistTile>();

    public BillPayApp() {
        super("BillPay 146 — Payment Checklist");
        this.paidNumbers = store.loadPaidNumbers();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(780, 600));
        setSize(1200, 820);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_CANVAS);
        setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildMainContent(), BorderLayout.CENTER);

        // Ctrl/Cmd+Z keyboard shortcut for Undo (works on JDK 8 through JDK 23 without deprecation warnings)
        String osName = System.getProperty("os.name", "").toLowerCase();
        int menuMask = osName.contains("mac") ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask), "undoAction");
        getRootPane().getActionMap().put("undoAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!numberInput.isFocusOwner() || numberInput.getText().isEmpty()) {
                    handleUndo();
                }
            }
        });

        refreshUI();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                numberInput.requestFocusInWindow();
            }
        });
    }

    private JComponent buildHeader() {
        JPanel headerOuter = new JPanel(new BorderLayout());
        headerOuter.setBackground(SURFACE);
        headerOuter.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_SUBTLE));

        JPanel headerInner = new JPanel(new BorderLayout(24, 0));
        headerInner.setOpaque(false);
        headerInner.setBorder(new EmptyBorder(12, 24, 12, 24));

        JLabel brandLabel = new JLabel("BillPay 146");
        brandLabel.setFont(FONT_BRAND);
        brandLabel.setForeground(INK_PRIMARY);
        headerInner.add(brandLabel, BorderLayout.WEST);

        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionsPanel.setOpaque(false);

        StyledButton exportCsvBtn = StyledButton.outline("Export CSV");
        exportCsvBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleExportCsv();
            }
        });
        actionsPanel.add(exportCsvBtn);

        resetBtn = StyledButton.outline("Reset");
        resetBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                confirmReset = true;
                refreshUI();
            }
        });
        actionsPanel.add(resetBtn);

        resetConfirmPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        resetConfirmPanel.setOpaque(false);

        StyledButton confirmResetBtn = StyledButton.danger("Confirm Reset");
        confirmResetBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleConfirmReset();
            }
        });

        StyledButton cancelResetBtn = StyledButton.ghost("Cancel");
        cancelResetBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                confirmReset = false;
                refreshUI();
            }
        });

        resetConfirmPanel.add(confirmResetBtn);
        resetConfirmPanel.add(cancelResetBtn);
        resetConfirmPanel.setVisible(false);
        actionsPanel.add(resetConfirmPanel);

        headerInner.add(actionsPanel, BorderLayout.EAST);
        headerOuter.add(headerInner, BorderLayout.CENTER);
        return headerOuter;
    }

    private JComponent buildMainContent() {
        JPanel main = new JPanel(new BorderLayout(0, 18));
        main.setBackground(BG_CANVAS);
        main.setBorder(new EmptyBorder(20, 24, 20, 24));

        main.add(buildSummaryPanel(), BorderLayout.NORTH);
        main.add(buildGridSection(), BorderLayout.CENTER);

        return main;
    }

    private JComponent buildSummaryPanel() {
        RoundedPanel card = new RoundedPanel(12, SURFACE, BORDER_SUBTLE);
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(18, 20, 18, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        // Left: Number input panel
        JPanel leftPanel = new JPanel();
        leftPanel.setOpaque(false);
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));

        JPanel labelRow = new JPanel(new BorderLayout());
        labelRow.setOpaque(false);
        labelRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel inputLabel = new JLabel("Check or mark number");
        inputLabel.setFont(FONT_LABEL);
        inputLabel.setForeground(INK_PRIMARY);
        labelRow.add(inputLabel, BorderLayout.WEST);

        JLabel rangeLabel = new JLabel("Range: 1-" + NumberParser.TOTAL_PEOPLE);
        rangeLabel.setFont(FONT_MONO_SMALL);
        rangeLabel.setForeground(INK_MUTED);
        labelRow.add(rangeLabel, BorderLayout.EAST);

        leftPanel.add(labelRow);
        leftPanel.add(Box.createVerticalStrut(10));

        JPanel formRow = new JPanel(new BorderLayout(8, 0));
        formRow.setOpaque(false);
        formRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        formRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        JPanel inputWrap = new JPanel(new BorderLayout(4, 0));
        inputWrap.setBackground(SURFACE);
        inputWrap.setBorder(new CompoundBorder(
                new LineBorder(BORDER_STRONG, 1, true),
                new EmptyBorder(4, 10, 4, 6)
        ));

        numberInput = new PlaceholderTextField("Type number (e.g. 42) or list (12, 18, 25-30)");
        numberInput.setFont(FONT_MONO);
        numberInput.setForeground(INK_PRIMARY);
        numberInput.setBorder(BorderFactory.createEmptyBorder());
        numberInput.setOpaque(false);
        numberInput.setToolTipText("Type number (e.g. 42) or list (12, 18, 25-30)");
        numberInput.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleSubmitInput();
            }
        });
        numberInput.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onInputChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onInputChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onInputChanged();
            }
        });

        clearInputBtn = StyledButton.ghost("Clear");
        clearInputBtn.setFont(FONT_SMALL);
        clearInputBtn.setMargin(new Insets(2, 6, 2, 6));
        clearInputBtn.setVisible(false);
        clearInputBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                numberInput.setText("");
                numberInput.requestFocusInWindow();
                refreshUI();
            }
        });

        inputWrap.add(numberInput, BorderLayout.CENTER);
        inputWrap.add(clearInputBtn, BorderLayout.EAST);

        StyledButton markPaidBtn = StyledButton.primary("Mark Paid");
        markPaidBtn.setPreferredSize(new Dimension(112, 40));
        markPaidBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleSubmitInput();
            }
        });

        formRow.add(inputWrap, BorderLayout.CENTER);
        formRow.add(markPaidBtn, BorderLayout.EAST);

        leftPanel.add(formRow);
        leftPanel.add(Box.createVerticalStrut(10));

        JPanel feedbackRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        feedbackRow.setOpaque(false);
        feedbackRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        feedbackLabel = new JLabel(" ");
        feedbackLabel.setFont(FONT_SMALL);
        feedbackLabel.setForeground(INK_MUTED);
        feedbackRow.add(feedbackLabel);

        feedbackActionBtn = new JButton();
        feedbackActionBtn.setFont(FONT_SMALL_BOLD);
        feedbackActionBtn.setForeground(BLUE_TEXT);
        feedbackActionBtn.setBorder(new EmptyBorder(0, 10, 0, 0));
        feedbackActionBtn.setContentAreaFilled(false);
        feedbackActionBtn.setFocusPainted(false);
        feedbackActionBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        feedbackActionBtn.setVisible(false);
        feedbackRow.add(feedbackActionBtn);

        leftPanel.add(feedbackRow);

        gbc.gridx = 0;
        gbc.weightx = 0.60;
        gbc.insets = new Insets(0, 0, 0, 20);
        card.add(leftPanel, gbc);

        // Right: Tally summary panel
        JPanel rightPanel = new JPanel();
        rightPanel.setOpaque(false);
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_SUBTLE),
                new EmptyBorder(0, 20, 0, 0)
        ));

        JPanel countsRow = new JPanel(new BorderLayout());
        countsRow.setOpaque(false);
        countsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel paidBox = new JPanel();
        paidBox.setOpaque(false);
        paidBox.setLayout(new BoxLayout(paidBox, BoxLayout.Y_AXIS));
        JLabel paidCaption = new JLabel("Paid");
        paidCaption.setFont(FONT_SMALL);
        paidCaption.setForeground(INK_MUTED);
        JPanel paidValRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        paidValRow.setOpaque(false);
        paidCountLabel = new JLabel("0");
        paidCountLabel.setFont(FONT_TALLY_BIG);
        paidCountLabel.setForeground(INK_PRIMARY);
        JLabel totalSuffix = new JLabel("/ " + NumberParser.TOTAL_PEOPLE);
        totalSuffix.setFont(FONT_MONO);
        totalSuffix.setForeground(INK_MUTED);
        paidValRow.add(paidCountLabel);
        paidValRow.add(totalSuffix);
        paidBox.add(paidCaption);
        paidBox.add(paidValRow);

        JPanel remBox = new JPanel();
        remBox.setOpaque(false);
        remBox.setLayout(new BoxLayout(remBox, BoxLayout.Y_AXIS));
        JLabel remCaption = new JLabel("Remaining", SwingConstants.RIGHT);
        remCaption.setFont(FONT_SMALL);
        remCaption.setForeground(INK_MUTED);
        remCaption.setAlignmentX(Component.RIGHT_ALIGNMENT);
        remainingCountLabel = new JLabel(String.valueOf(NumberParser.TOTAL_PEOPLE), SwingConstants.RIGHT);
        remainingCountLabel.setFont(FONT_TALLY_BIG);
        remainingCountLabel.setForeground(INK_PRIMARY);
        remainingCountLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        remBox.add(remCaption);
        remBox.add(remainingCountLabel);

        countsRow.add(paidBox, BorderLayout.WEST);
        countsRow.add(remBox, BorderLayout.EAST);

        rightPanel.add(countsRow);
        rightPanel.add(Box.createVerticalStrut(10));

        progressBar = new SmoothProgressBar();
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar.setPreferredSize(new Dimension(100, 8));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        rightPanel.add(progressBar);
        rightPanel.add(Box.createVerticalStrut(8));

        JPanel metaRow = new JPanel(new BorderLayout());
        metaRow.setOpaque(false);
        metaRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        collectedLabel = new JLabel("0.0% collected");
        collectedLabel.setFont(FONT_MONO_SMALL);
        collectedLabel.setForeground(INK_MUTED);
        metaRow.add(collectedLabel, BorderLayout.WEST);

        JLabel hintLabel = new JLabel("Click any cell to toggle status");
        hintLabel.setFont(FONT_SMALL);
        hintLabel.setForeground(INK_MUTED);
        metaRow.add(hintLabel, BorderLayout.EAST);

        rightPanel.add(metaRow);

        gbc.gridx = 1;
        gbc.weightx = 0.40;
        gbc.insets = new Insets(0, 0, 0, 0);
        card.add(rightPanel, gbc);

        return card;
    }

    private JComponent buildGridSection() {
        JPanel section = new JPanel(new BorderLayout(0, 12));
        section.setOpaque(false);

        // Toolbar
        JPanel toolbar = new JPanel(new BorderLayout(12, 8));
        toolbar.setOpaque(false);

        RoundedPanel tabsPanel = new RoundedPanel(8, TAB_TRACK_BG, TAB_TRACK_BG);
        tabsPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4));

        tabAllBtn = StyledButton.tab("All (146)");
        tabPaidBtn = StyledButton.tab("Paid (0)");
        tabUnpaidBtn = StyledButton.tab("Unpaid (146)");

        tabAllBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                filterMode = FilterMode.ALL;
                refreshUI();
            }
        });
        tabPaidBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                filterMode = FilterMode.PAID;
                refreshUI();
            }
        });
        tabUnpaidBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                filterMode = FilterMode.UNPAID;
                refreshUI();
            }
        });

        tabsPanel.add(tabAllBtn);
        tabsPanel.add(tabPaidBtn);
        tabsPanel.add(tabUnpaidBtn);

        toolbar.add(tabsPanel, BorderLayout.WEST);

        JPanel rightToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightToolbar.setOpaque(false);

        rightToolbar.add(buildLegendItem(SURFACE, BORDER_STRONG, false, "Unpaid"));
        rightToolbar.add(buildLegendItem(EMERALD_BG, EMERALD_BORDER, true, "Paid"));
        rightToolbar.add(buildLegendItem(SURFACE, BLUE_ACCENT, false, "Inputted"));
        rightToolbar.add(Box.createHorizontalStrut(6));

        undoBtn = StyledButton.outline("Undo");
        undoBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleUndo();
            }
        });
        rightToolbar.add(undoBtn);

        copyUnpaidBtn = StyledButton.outline("Copy Unpaid");
        copyUnpaidBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleCopyUnpaid();
            }
        });
        rightToolbar.add(copyUnpaidBtn);

        toolbar.add(rightToolbar, BorderLayout.EAST);
        section.add(toolbar, BorderLayout.NORTH);

        // Scrollable Grid
        gridContainer = new ScrollableGridPanel();
        gridContainer.setBackground(BG_CANVAS);
        gridContainer.setBorder(new EmptyBorder(4, 2, 12, 6));

        gridScrollPane = new JScrollPane(gridContainer);
        gridScrollPane.setBorder(BorderFactory.createEmptyBorder());
        gridScrollPane.getViewport().setBackground(BG_CANVAS);
        gridScrollPane.getVerticalScrollBar().setUnitIncrement(18);
        gridScrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int viewportWidth = gridScrollPane.getViewport().getWidth();
                int cols = Math.max(5, Math.min(15, viewportWidth / 82));
                if (cols != currentGridColumns) {
                    currentGridColumns = cols;
                    rebuildGrid();
                }
            }
        });

        // Empty State Panel
        emptyStatePanel = new RoundedPanel(12, SURFACE, BORDER_SUBTLE);
        emptyStatePanel.setLayout(new GridBagLayout());
        JPanel emptyInner = new JPanel();
        emptyInner.setOpaque(false);
        emptyInner.setLayout(new BoxLayout(emptyInner, BoxLayout.Y_AXIS));

        emptyTitleLabel = new JLabel("", SwingConstants.CENTER);
        emptyTitleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        emptyTitleLabel.setForeground(INK_PRIMARY);
        emptyTitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        emptyDescLabel = new JLabel("", SwingConstants.CENTER);
        emptyDescLabel.setFont(FONT_BODY);
        emptyDescLabel.setForeground(INK_MUTED);
        emptyDescLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        StyledButton showAllBtn = StyledButton.outline("Show All " + NumberParser.TOTAL_PEOPLE);
        showAllBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        showAllBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                filterMode = FilterMode.ALL;
                refreshUI();
            }
        });

        emptyInner.add(emptyTitleLabel);
        emptyInner.add(Box.createVerticalStrut(6));
        emptyInner.add(emptyDescLabel);
        emptyInner.add(Box.createVerticalStrut(14));
        emptyInner.add(showAllBtn);
        emptyStatePanel.add(emptyInner);

        JPanel centerHolder = new JPanel(new BorderLayout());
        centerHolder.setOpaque(false);
        centerHolder.add(gridScrollPane, BorderLayout.CENTER);
        section.add(centerHolder, BorderLayout.CENTER);

        return section;
    }

    private JComponent buildLegendItem(final Color fill, final Color border, final boolean checked, String text) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        item.setOpaque(false);

        JComponent swatch = new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(13, 13);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fill);
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 3, 3);
                g2.setColor(border);
                g2.setStroke(new BasicStroke(border.equals(BLUE_ACCENT) ? 1.8f : 1.0f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 3, 3);
                if (checked) {
                    g2.setColor(EMERALD_TEXT);
                    g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(3, 7, 5, 9);
                    g2.drawLine(5, 9, 10, 4);
                }
                g2.dispose();
            }
        };

        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_SMALL);
        lbl.setForeground(INK_SECONDARY);

        item.add(swatch);
        item.add(lbl);
        return item;
    }

    private void onInputChanged() {
        if (feedback != null) {
            feedback = null;
        }
        refreshUI();
    }

    private void pushHistorySnapshot() {
        while (history.size() >= 20) {
            history.removeFirst();
        }
        history.addLast(new TreeSet<Integer>(paidNumbers));
    }

    private void persistState() {
        try {
            store.savePaidNumbers(paidNumbers);
        } catch (IOException ex) {
            feedback = new FeedbackState(
                    FeedbackTone.ERROR,
                    "Could not save to " + store.getDataPath().getFileName() + ": " + ex.getMessage(),
                    null,
                    null
            );
        }
    }

    private List<Integer> markNumbersPaid(List<Integer> nums) {
        List<Integer> newlyMarked = new ArrayList<Integer>();
        for (Integer n : nums) {
            if (!paidNumbers.contains(n)) {
                newlyMarked.add(n);
            }
        }
        if (!newlyMarked.isEmpty()) {
            pushHistorySnapshot();
            paidNumbers.addAll(newlyMarked);
            persistState();
        }
        return newlyMarked;
    }

    private void unmarkNumbersPaid(List<Integer> nums) {
        boolean changed = false;
        for (Integer n : nums) {
            if (paidNumbers.contains(n)) {
                changed = true;
                break;
            }
        }
        if (changed) {
            pushHistorySnapshot();
            paidNumbers.removeAll(nums);
            persistState();
        }
    }

    private void handleToggleSingle(final int num) {
        confirmReset = false;
        lastCheckedNumbers = Collections.singletonList(num);

        if (paidNumbers.contains(num)) {
            unmarkNumbersPaid(Collections.singletonList(num));
            feedback = new FeedbackState(
                    FeedbackTone.NEUTRAL,
                    "Marked #" + num + " as unpaid.",
                    "Undo",
                    new Runnable() {
                        @Override
                        public void run() {
                            handleUndo();
                        }
                    }
            );
        } else {
            markNumbersPaid(Collections.singletonList(num));
            feedback = new FeedbackState(
                    FeedbackTone.SUCCESS,
                    "Marked #" + num + " as paid.",
                    "Undo",
                    new Runnable() {
                        @Override
                        public void run() {
                            handleUndo();
                        }
                    }
            );
        }
        refreshUI();
    }

    private void handleSubmitInput() {
        confirmReset = false;
        String raw = numberInput.getText();
        if (raw == null || raw.trim().isEmpty()) {
            feedback = new FeedbackState(
                    FeedbackTone.ERROR,
                    "Enter a number between 1 and " + NumberParser.TOTAL_PEOPLE + ".",
                    null,
                    null
            );
            numberInput.requestFocusInWindow();
            refreshUI();
            return;
        }

        final NumberParser.ParseResult parsed = NumberParser.parse(raw);
        if (parsed.validNumbers.isEmpty()) {
            if (!parsed.outOfRange.isEmpty()) {
                feedback = new FeedbackState(
                        FeedbackTone.ERROR,
                        "Out of range (" + joinIntegers(parsed.outOfRange) + "). Enter numbers from 1 to "
                                + NumberParser.TOTAL_PEOPLE + ".",
                        null,
                        null
                );
            } else {
                feedback = new FeedbackState(
                        FeedbackTone.ERROR,
                        "Invalid input (" + joinStrings(parsed.invalidTokens) + "). Use numbers from 1 to "
                                + NumberParser.TOTAL_PEOPLE + ".",
                        null,
                        null
                );
            }
            refreshUI();
            return;
        }

        final List<Integer> newlyMarked = markNumbersPaid(parsed.validNumbers);
        lastCheckedNumbers = new ArrayList<Integer>(parsed.validNumbers);
        numberInput.setText("");
        numberInput.requestFocusInWindow();

        if (newlyMarked.isEmpty()) {
            if (parsed.validNumbers.size() == 1) {
                final int target = parsed.validNumbers.get(0);
                feedback = new FeedbackState(
                        FeedbackTone.NEUTRAL,
                        "#" + target + " is already marked paid.",
                        "Unmark #" + target,
                        new Runnable() {
                            @Override
                            public void run() {
                                unmarkNumbersPaid(Collections.singletonList(target));
                                feedback = new FeedbackState(
                                        FeedbackTone.NEUTRAL,
                                        "Marked #" + target + " as unpaid.",
                                        null,
                                        null
                                );
                                refreshUI();
                            }
                        }
                );
            } else {
                feedback = new FeedbackState(
                        FeedbackTone.NEUTRAL,
                        "All " + parsed.validNumbers.size() + " entered numbers were already paid.",
                        "Unmark all",
                        new Runnable() {
                            @Override
                            public void run() {
                                unmarkNumbersPaid(parsed.validNumbers);
                                feedback = new FeedbackState(
                                        FeedbackTone.NEUTRAL,
                                        "Marked " + parsed.validNumbers.size() + " numbers as unpaid.",
                                        null,
                                        null
                                );
                                refreshUI();
                            }
                        }
                );
            }
            refreshUI();
            return;
        }

        final String previewList;
        if (newlyMarked.size() <= 5) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < newlyMarked.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("#").append(newlyMarked.get(i));
            }
            previewList = sb.toString();
        } else {
            previewList = newlyMarked.size() + " numbers";
        }

        String warningSuffix = parsed.outOfRange.isEmpty()
                ? ""
                : " (skipped out-of-range: " + joinIntegers(parsed.outOfRange) + ")";

        feedback = new FeedbackState(
                FeedbackTone.SUCCESS,
                "Marked " + previewList + " as paid." + warningSuffix,
                "Undo",
                new Runnable() {
                    @Override
                    public void run() {
                        unmarkNumbersPaid(newlyMarked);
                        feedback = new FeedbackState(
                                FeedbackTone.NEUTRAL,
                                "Reverted " + previewList + ".",
                                null,
                                null
                        );
                        refreshUI();
                    }
                }
        );
        refreshUI();
    }

    private void handleUndo() {
        if (history.isEmpty()) {
            return;
        }
        paidNumbers = history.removeLast();
        persistState();
        feedback = new FeedbackState(FeedbackTone.NEUTRAL, "Reverted the last change.", null, null);
        refreshUI();
    }

    private void handleCopyUnpaid() {
        List<Integer> unpaid = new ArrayList<Integer>();
        for (int n = 1; n <= NumberParser.TOTAL_PEOPLE; n++) {
            if (!paidNumbers.contains(n)) {
                unpaid.add(n);
            }
        }
        String text = unpaid.isEmpty() ? "Everyone has paid!" : joinIntegers(unpaid);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);

        copyUnpaidBtn.setText("Copied Unpaid");
        Timer timer = new Timer(1800, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyUnpaidBtn.setText("Copy Unpaid");
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void handleExportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Bill Checklist CSV");
        chooser.setSelectedFile(new File("bill-checklist-146.csv"));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));

        int choice = chooser.showSaveDialog(this);
        if (choice == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            if (!target.getName().toLowerCase().endsWith(".csv")) {
                target = new File(target.getParentFile(), target.getName() + ".csv");
            }
            try {
                store.exportCsv(target, paidNumbers);
                feedback = new FeedbackState(
                        FeedbackTone.SUCCESS,
                        "Exported CSV to " + target.getName() + ".",
                        null,
                        null
                );
                refreshUI();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Failed to export CSV:\n" + ex.getMessage(),
                        "Export Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void handleConfirmReset() {
        if (paidNumbers.isEmpty()) {
            confirmReset = false;
            refreshUI();
            return;
        }
        pushHistorySnapshot();
        paidNumbers.clear();
        lastCheckedNumbers = new ArrayList<Integer>();
        confirmReset = false;
        persistState();
        feedback = new FeedbackState(
                FeedbackTone.NEUTRAL,
                "Cleared all " + NumberParser.TOTAL_PEOPLE + " payment checkmarks.",
                "Undo",
                new Runnable() {
                    @Override
                    public void run() {
                        handleUndo();
                    }
                }
        );
        refreshUI();
    }

    private void refreshUI() {
        int paidCount = paidNumbers.size();
        int unpaidCount = NumberParser.TOTAL_PEOPLE - paidCount;
        double paidPercent = Math.round((paidCount * 1000.0) / NumberParser.TOTAL_PEOPLE) / 10.0;

        paidCountLabel.setText(String.valueOf(paidCount));
        remainingCountLabel.setText(String.valueOf(unpaidCount));
        progressBar.setFraction(paidCount / (double) NumberParser.TOTAL_PEOPLE);
        collectedLabel.setText(paidPercent + "% collected");

        clearInputBtn.setVisible(!numberInput.getText().isEmpty());
        undoBtn.setEnabled(!history.isEmpty());
        resetBtn.setEnabled(paidCount > 0);
        resetBtn.setVisible(!confirmReset);
        resetConfirmPanel.setVisible(confirmReset);

        tabAllBtn.setText("All (" + NumberParser.TOTAL_PEOPLE + ")");
        tabPaidBtn.setText("Paid (" + paidCount + ")");
        tabUnpaidBtn.setText("Unpaid (" + unpaidCount + ")");
        tabAllBtn.setActiveTab(filterMode == FilterMode.ALL);
        tabPaidBtn.setActiveTab(filterMode == FilterMode.PAID);
        tabUnpaidBtn.setActiveTab(filterMode == FilterMode.UNPAID);

        NumberParser.ParseResult parsedLive = NumberParser.parse(numberInput.getText());
        updateFeedbackLine(parsedLive);
        rebuildGrid();
    }

    private void updateFeedbackLine(NumberParser.ParseResult parsedLive) {
        // Clear old action listeners
        for (ActionListener al : feedbackActionBtn.getActionListeners()) {
            feedbackActionBtn.removeActionListener(al);
        }
        feedbackActionBtn.setVisible(false);

        String raw = numberInput.getText().trim();
        if (!raw.isEmpty()) {
            if (parsedLive.validNumbers.isEmpty()) {
                feedbackLabel.setForeground(RED_ACCENT);
                feedbackLabel.setFont(FONT_SMALL_BOLD);
                if (!parsedLive.outOfRange.isEmpty()) {
                    feedbackLabel.setText("Out of range (" + joinIntegers(parsedLive.outOfRange)
                            + ") — enter 1 to " + NumberParser.TOTAL_PEOPLE + ".");
                } else {
                    feedbackLabel.setText("Enter numbers from 1 to " + NumberParser.TOTAL_PEOPLE
                            + " (e.g. 42 or 12, 15, 20-25).");
                }
                return;
            }

            if (parsedLive.validNumbers.size() == 1) {
                final int num = parsedLive.validNumbers.get(0);
                boolean isPaid = paidNumbers.contains(num);
                feedbackLabel.setFont(FONT_SMALL_BOLD);
                feedbackLabel.setForeground(isPaid ? EMERALD_TEXT : BLUE_TEXT);
                feedbackLabel.setText(isPaid
                        ? "#" + num + " has already paid"
                        : "#" + num + " has not paid yet — press Enter to mark paid");

                feedbackActionBtn.setText(isPaid ? "Unmark #" + num : "Mark #" + num + " Paid");
                feedbackActionBtn.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        handleToggleSingle(num);
                        numberInput.setText("");
                        numberInput.requestFocusInWindow();
                    }
                });
                feedbackActionBtn.setVisible(true);
                return;
            }

            int alreadyPaid = 0;
            for (Integer n : parsedLive.validNumbers) {
                if (paidNumbers.contains(n)) {
                    alreadyPaid++;
                }
            }
            int unpaid = parsedLive.validNumbers.size() - alreadyPaid;
            feedbackLabel.setFont(FONT_SMALL_BOLD);
            feedbackLabel.setForeground(unpaid == 0 ? EMERALD_TEXT : BLUE_TEXT);
            feedbackLabel.setText(parsedLive.validNumbers.size() + " numbers selected ("
                    + unpaid + " unpaid, " + alreadyPaid + " already paid) — press Enter to mark paid");
            return;
        }

        if (feedback != null) {
            feedbackLabel.setFont(FONT_SMALL_BOLD);
            switch (feedback.tone) {
                case ERROR:
                    feedbackLabel.setForeground(RED_ACCENT);
                    break;
                case SUCCESS:
                    feedbackLabel.setForeground(EMERALD_TEXT);
                    break;
                case INFO:
                    feedbackLabel.setForeground(BLUE_TEXT);
                    break;
                default:
                    feedbackLabel.setForeground(INK_SECONDARY);
                    break;
            }
            feedbackLabel.setText(feedback.message);

            if (feedback.actionLabel != null && feedback.onAction != null) {
                final Runnable action = feedback.onAction;
                feedbackActionBtn.setText(feedback.actionLabel);
                feedbackActionBtn.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        action.run();
                    }
                });
                feedbackActionBtn.setVisible(true);
            }
            return;
        }

        feedbackLabel.setFont(FONT_SMALL);
        feedbackLabel.setForeground(INK_MUTED);
        feedbackLabel.setText("Typing highlights the number in the grid below. Press Enter to check it off.");
    }

    private void rebuildGrid() {
        NumberParser.ParseResult parsedLive = NumberParser.parse(numberInput.getText());
        List<Integer> highlightedList = !parsedLive.validNumbers.isEmpty()
                ? parsedLive.validNumbers
                : lastCheckedNumbers;
        Set<Integer> highlightedSet = new LinkedHashSet<Integer>(highlightedList);
        Integer primaryFocus = highlightedList.isEmpty()
                ? null
                : highlightedList.get(highlightedList.size() - 1);

        List<Integer> visibleNumbers = new ArrayList<Integer>();
        for (int n = 1; n <= NumberParser.TOTAL_PEOPLE; n++) {
            boolean isPaid = paidNumbers.contains(n);
            if (filterMode == FilterMode.PAID && !isPaid) {
                continue;
            }
            if (filterMode == FilterMode.UNPAID && isPaid) {
                continue;
            }
            visibleNumbers.add(n);
        }

        visibleTiles.clear();
        gridContainer.removeAll();

        if (visibleNumbers.isEmpty()) {
            gridContainer.setLayout(new BorderLayout());
            emptyTitleLabel.setText(filterMode == FilterMode.PAID
                    ? "No one has been marked paid yet"
                    : "Everyone has paid");
            emptyDescLabel.setText(filterMode == FilterMode.PAID
                    ? "Enter a number above or click any person in the full checklist to mark them as paid."
                    : "All " + NumberParser.TOTAL_PEOPLE + " people are currently marked as paid.");
            gridContainer.add(emptyStatePanel, BorderLayout.CENTER);
        } else {
            gridContainer.setLayout(new BorderLayout());
            JPanel tilesPanel = new JPanel(new GridLayout(0, currentGridColumns, 8, 8));
            tilesPanel.setOpaque(false);

            for (final Integer num : visibleNumbers) {
                ChecklistTile tile = new ChecklistTile(
                        num,
                        paidNumbers.contains(num),
                        highlightedSet.contains(num)
                );
                tile.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        handleToggleSingle(num);
                    }
                });
                tilesPanel.add(tile);
                visibleTiles.put(num, tile);
            }

            gridContainer.add(tilesPanel, BorderLayout.NORTH);
        }

        gridContainer.revalidate();
        gridContainer.repaint();

        if (primaryFocus != null && !primaryFocus.equals(lastScrolledFocus)) {
            final ChecklistTile focusTile = visibleTiles.get(primaryFocus);
            if (focusTile != null) {
                lastScrolledFocus = primaryFocus;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        Rectangle bounds = SwingUtilities.convertRectangle(
                                focusTile.getParent(),
                                focusTile.getBounds(),
                                gridContainer
                        );
                        bounds.grow(8, 12);
                        gridContainer.scrollRectToVisible(bounds);
                    }
                });
            }
        } else if (primaryFocus == null) {
            lastScrolledFocus = null;
        }
    }

    private static String joinIntegers(List<Integer> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static String joinStrings(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    // --- Custom Components for Consistent Cross-Platform Rendering ---

    private static final class RoundedPanel extends JPanel {
        private final int radius;
        private final Color fillColor;
        private final Color borderColor;

        RoundedPanel(int radius, Color fillColor, Color borderColor) {
            this.radius = radius;
            this.fillColor = fillColor;
            this.borderColor = borderColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fillColor);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.setColor(borderColor);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class SmoothProgressBar extends JComponent {
        private double fraction = 0.0;

        void setFraction(double f) {
            this.fraction = Math.max(0.0, Math.min(1.0, f));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(SLATE_HOVER);
            g2.fillRoundRect(0, 0, w, h, h, h);

            int fillW = (int) Math.round(w * fraction);
            if (fillW > 0) {
                g2.setColor(EMERALD_ACCENT);
                g2.fillRoundRect(0, 0, Math.max(h, fillW), h, h, h);
            }
            g2.dispose();
        }
    }

    private static final class ScrollableGridPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(48, visibleRect.height - 48);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class ChecklistTile extends JButton {
        private final int number;
        private final boolean paid;
        private final boolean highlighted;

        ChecklistTile(int number, boolean paid, boolean highlighted) {
            this.number = number;
            this.paid = paid;
            this.highlighted = highlighted;

            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(74, 46));
            setMinimumSize(new Dimension(64, 44));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int pad = highlighted ? 3 : 1;
            int rw = w - pad * 2;
            int rh = h - pad * 2;

            boolean hover = getModel().isRollover();

            if (highlighted) {
                g2.setColor(BLUE_ACCENT);
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 10, 10);
            }

            Color bg;
            Color border;
            Color fg;

            if (paid) {
                bg = hover ? EMERALD_BG_HOVER : EMERALD_BG;
                border = highlighted ? BLUE_ACCENT : EMERALD_BORDER;
                fg = EMERALD_TEXT;
            } else {
                bg = hover ? BG_CANVAS : SURFACE;
                border = highlighted ? BLUE_ACCENT : (hover ? BORDER_STRONG : BORDER_SUBTLE);
                fg = INK_PRIMARY;
            }

            g2.setColor(bg);
            g2.fillRoundRect(pad, pad, rw, rh, 8, 8);

            g2.setColor(border);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(pad, pad, rw, rh, 8, 8);

            // Number text on left
            g2.setFont(FONT_MONO_BOLD);
            g2.setColor(fg);
            String label = String.valueOf(number);
            FontMetrics fm = g2.getFontMetrics();
            int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(label, pad + 9, textY);

            // Status icon on right
            int iconX = w - pad - 21;
            int iconY = (h - 13) / 2;

            if (paid) {
                g2.setColor(EMERALD_ACCENT);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(iconX + 1, iconY + 7, iconX + 5, iconY + 11);
                g2.drawLine(iconX + 5, iconY + 11, iconX + 12, iconY + 3);
            } else {
                g2.setColor(hover ? INK_MUTED : BORDER_STRONG);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(iconX, iconY, 13, 13, 3, 3);
            }

            g2.dispose();
        }
    }

    private static final class StyledButton extends JButton {
        private enum Variant {
            PRIMARY, OUTLINE, DANGER, GHOST, TAB
        }

        private final Variant variant;
        private boolean activeTab = false;

        private StyledButton(String text, Variant variant) {
            super(text);
            this.variant = variant;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(variant == Variant.PRIMARY ? FONT_LABEL : FONT_SMALL_BOLD);
            setBorder(variant == Variant.PRIMARY
                    ? new EmptyBorder(8, 16, 8, 16)
                    : new EmptyBorder(6, 12, 6, 12));
        }

        static StyledButton primary(String text) {
            return new StyledButton(text, Variant.PRIMARY);
        }

        static StyledButton outline(String text) {
            return new StyledButton(text, Variant.OUTLINE);
        }

        static StyledButton danger(String text) {
            return new StyledButton(text, Variant.DANGER);
        }

        static StyledButton ghost(String text) {
            return new StyledButton(text, Variant.GHOST);
        }

        static StyledButton tab(String text) {
            return new StyledButton(text, Variant.TAB);
        }

        void setActiveTab(boolean active) {
            this.activeTab = active;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int textW = fm.stringWidth(getText() == null ? "" : getText());
            int textH = fm.getHeight();
            int padX = variant == Variant.PRIMARY ? 32 : (variant == Variant.GHOST ? 14 : 24);
            int padY = variant == Variant.PRIMARY ? 18 : (variant == Variant.TAB ? 12 : 14);
            return new Dimension(textW + padX, Math.max(variant == Variant.PRIMARY ? 40 : 30, textH + padY));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            boolean hover = isEnabled() && getModel().isRollover();
            boolean pressed = isEnabled() && getModel().isPressed();

            Color bg = null;
            Color border = null;
            Color fg = INK_SECONDARY;

            switch (variant) {
                case PRIMARY:
                    bg = pressed ? Color.BLACK : (hover ? new Color(30, 41, 59) : INK_PRIMARY);
                    fg = Color.WHITE;
                    break;
                case DANGER:
                    bg = hover ? RED_HOVER : RED_ACCENT;
                    fg = Color.WHITE;
                    break;
                case OUTLINE:
                    bg = hover ? BG_CANVAS : SURFACE;
                    border = hover ? BORDER_STRONG : BORDER_SUBTLE;
                    fg = INK_SECONDARY;
                    break;
                case GHOST:
                    bg = hover ? SLATE_HOVER : null;
                    fg = hover ? INK_PRIMARY : INK_MUTED;
                    break;
                case TAB:
                    if (activeTab) {
                        bg = SURFACE;
                        fg = INK_PRIMARY;
                    } else {
                        bg = hover ? new Color(241, 245, 249, 140) : null;
                        fg = hover ? INK_PRIMARY : INK_SECONDARY;
                    }
                    break;
            }

            if (!isEnabled()) {
                fg = new Color(148, 163, 184);
                if (variant == Variant.OUTLINE) {
                    bg = BG_CANVAS;
                    border = BORDER_SUBTLE;
                }
            }

            int w = getWidth();
            int h = getHeight();
            int arc = variant == Variant.TAB ? 6 : 8;

            if (bg != null) {
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }
            if (border != null) {
                g2.setColor(border);
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }

            g2.setFont(getFont());
            g2.setColor(fg);
            FontMetrics fm = g2.getFontMetrics();
            String text = getText();
            int textX = (w - fm.stringWidth(text)) / 2;
            int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, textX, textY);

            g2.dispose();
        }
    }

    private static final class PlaceholderTextField extends JTextField {
        private final String placeholder;

        PlaceholderTextField(String placeholder) {
            this.placeholder = placeholder;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && placeholder != null && !placeholder.isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(FONT_BODY);
                g2.setColor(new Color(148, 163, 184));
                FontMetrics fm = g2.getFontMetrics();
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(placeholder, 2, y);
                g2.dispose();
            }
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fall back to default look and feel
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                BillPayApp app = new BillPayApp();
                app.setVisible(true);
            }
        });
    }
}
