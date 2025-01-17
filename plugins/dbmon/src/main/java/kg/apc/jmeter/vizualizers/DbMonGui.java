package kg.apc.jmeter.vizualizers;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import javax.swing.*;
import kg.apc.charting.AbstractGraphRow;
import kg.apc.jmeter.JMeterPluginsUtils;
import kg.apc.jmeter.dbmon.DbMonCollector;
import kg.apc.jmeter.dbmon.DbMonSampleResult;
import kg.apc.jmeter.graphs.AbstractOverTimeVisualizer;
import kg.apc.jmeter.gui.ButtonPanelAddCopyRemove;
import org.apache.jmeter.gui.util.PowerTableModel;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.NullProperty;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class DbMonGui
        extends AbstractOverTimeVisualizer {

    private static final Logger log = LoggerFactory.getLogger(DbMonGui.class);
    private PowerTableModel tableModel;
    private JTable grid;
    private JTextArea errorTextArea;
    private JScrollPane errorPane;
    public static final String[] columnIdentifiers = new String[]{
        "JDBC pool variable name", "Chart label", "Delta", "SQL query (must return a single numeric value)"
    };
    public static final Class[] columnClasses = new Class[]{
        String.class, String.class, Boolean.class, String.class
    };
    private static Object[] defaultValues = new Object[]{
        "", "", false, ""
    };

    public DbMonGui() {
        super();
        setGranulation(1000);
        graphPanel.getGraphObject().setYAxisLabel("Query results");
        graphPanel.getGraphObject().getChartSettings().setExpendRows(true);
    }

    @Override
    protected JSettingsPanel createSettingsPanel() {
        return new JSettingsPanel(this,
                JSettingsPanel.GRADIENT_OPTION
                | JSettingsPanel.LIMIT_POINT_OPTION
                | JSettingsPanel.MAXY_OPTION
                | JSettingsPanel.RELATIVE_TIME_OPTION
                | JSettingsPanel.AUTO_EXPAND_OPTION
                | JSettingsPanel.MARKERS_OPTION_DISABLED);
    }

    @Override
    public String getWikiPage() {
        return "DbMon";
    }

    @Override
    public String getLabelResource() {
        return getClass().getSimpleName();
    }

    @Override
    public String getStaticLabel() {
        return JMeterPluginsUtils.prefixLabel("DbMon Samples Collector");
    }

    @Override
    protected JPanel getGraphPanelContainer() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel innerTopPanel = new JPanel(new BorderLayout());

        errorPane = new JScrollPane();
        errorPane.setMinimumSize(new Dimension(100, 50));
        errorPane.setPreferredSize(new Dimension(100, 50));

        errorTextArea = new JTextArea();
        errorTextArea.setForeground(Color.red);
        errorTextArea.setBackground(new Color(255, 255, 153));
        errorTextArea.setEditable(false);
        errorPane.setViewportView(errorTextArea);

        registerPopup();

        innerTopPanel.add(createSamplerPanel(), BorderLayout.NORTH);
        innerTopPanel.add(errorPane, BorderLayout.SOUTH);
        innerTopPanel.add(getFilePanel(), BorderLayout.CENTER);

        panel.add(innerTopPanel, BorderLayout.NORTH);

        errorPane.setVisible(false);

        return panel;
    }

    private void addErrorMessage(String msg, long time) {
        errorPane.setVisible(true);
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
        String newLine = "";
        if (errorTextArea.getText().length() != 0) {
            newLine = "\n";
        }
        errorTextArea.setText(errorTextArea.getText() + newLine + formatter.format(time) + " - ERROR: " + msg);
        errorTextArea.setCaretPosition(errorTextArea.getDocument().getLength());
        updateGui();
    }

    public void clearErrorMessage() {
        errorTextArea.setText("");
        errorPane.setVisible(false);
    }

    private void registerPopup() {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem hideMessagesMenu = new JMenuItem("Hide Error Panel");
        hideMessagesMenu.addActionListener(new HideAction());
        popup.add(hideMessagesMenu);
        errorTextArea.setComponentPopupMenu(popup);
    }

    @Override
    public void clearData() {
        clearErrorMessage();
        super.clearData();
    }

    private Component createSamplerPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Database Samplers"));
        panel.setPreferredSize(new Dimension(150, 150));

        JScrollPane scroll = new JScrollPane(createGrid());
        scroll.setPreferredSize(scroll.getMinimumSize());
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(new ButtonPanelAddCopyRemove(grid, tableModel, defaultValues), BorderLayout.SOUTH);

        grid.getTableHeader().setReorderingAllowed(false);

        return panel;
    }

    private JTable createGrid() {
        grid = new JTable();
        createTableModel();
        grid.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        grid.setMinimumSize(new Dimension(200, 100));

        grid.getColumnModel().getColumn(0).setPreferredWidth(200);
        grid.getColumnModel().getColumn(1).setPreferredWidth(120);
        grid.getColumnModel().getColumn(2).setPreferredWidth(50);
        grid.getColumnModel().getColumn(3).setPreferredWidth(500);

        return grid;
    }

    private void createTableModel() {
        tableModel = new PowerTableModel(columnIdentifiers, columnClasses);
        grid.setModel(tableModel);
    }

    @Override
    public TestElement createTestElement() {
        TestElement te = new DbMonCollector();
        modifyTestElement(te);
        te.setComment(JMeterPluginsUtils.getWikiLinkText(getWikiPage()));
        return te;
    }

    @Override
    public void modifyTestElement(TestElement te) {
        super.modifyTestElement(te);
        if (grid.isEditing()) {
            grid.getCellEditor().stopCellEditing();
        }

        if (te instanceof DbMonCollector) {
            DbMonCollector dmte = (DbMonCollector) te;
            CollectionProperty rows = JMeterPluginsUtils.tableModelRowsToCollectionProperty(tableModel, DbMonCollector.DATA_PROPERTY);
            dmte.setData(rows);
        }
        super.configureTestElement(te);
    }

    @Override
    public void configure(TestElement te) {
        super.configure(te);
        DbMonCollector dmte = (DbMonCollector) te;
        JMeterProperty dbmonValues = dmte.getSamplerSettings();
        if (!(dbmonValues instanceof NullProperty)) {
            JMeterPluginsUtils.collectionPropertyToTableModelRows((CollectionProperty) dbmonValues, tableModel, columnClasses);
        } else {
            log.warn("Received null property instead of collection");
        }
    }

    @Override
    public void add(SampleResult res) {
        if (res.isSuccessful()) {
            if(isSampleIncluded(res)) {
                super.add(res);
                addDbMonRecord(res.getSampleLabel(), normalizeTime(res.getStartTime()), DbMonSampleResult.getValue(res));
                updateGui(null);
            }
        } else {
            addErrorMessage(res.getResponseMessage(), res.getStartTime());
        }
    }

    private void addDbMonRecord(String rowName, long time, double value) {
        AbstractGraphRow row = model.get(rowName);
        if (row == null) {
            row = getNewRow(model, AbstractGraphRow.ROW_AVERAGES, rowName,
                    AbstractGraphRow.MARKER_SIZE_NONE, false, false, false, true, true);
        }
        row.add(time, value);
    }

    private class HideAction
            implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            errorPane.setVisible(false);
            updateGui();
        }
    }
}
