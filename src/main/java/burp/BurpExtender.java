package burp;

import java.awt.*;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Vector;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/**
 * BurpNote Extension
 * 
 * Database Table Structure Update:
 * CREATE TABLE burp_notes (
 *     id INT AUTO_INCREMENT PRIMARY KEY,
 *     domain VARCHAR(255),
 *     content TEXT,
 *     create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 * );
 */
public class BurpExtender implements IBurpExtender, ITab {
    private IBurpExtenderCallbacks callbacks;
    private PrintWriter stdout;
    private PrintWriter stderr;
    
    // UI Components
    private JPanel mainPanel;
    
    // DB Config Components
    private JTextField hostField;
    private JTextField portField;
    private JTextField dbNameField;
    private JTextField userField;
    private JPasswordField passField;
    private JLabel statusLabel;
    private JButton connectButton;

    // Add Note Components
    private JTextField domainInput;
    private JTextArea contentArea;
    private JButton insertButton;

    // Search Components
    private JTextField searchDomainInput;
    private JRadioButton exactMatchRadio;
    private JRadioButton fuzzyMatchRadio;
    private JButton searchButton;
    private JButton deleteButton; // New Delete Button
    private JTable resultsTable;
    private DefaultTableModel tableModel;
    private JTextArea detailArea; // New component for showing full content

    // All Notes Components
    private JButton refreshButton;
    private JButton deleteAllButton;
    private JTable allNotesTable;
    private DefaultTableModel allNotesModel;
    private JTextArea allNotesDetailArea;

    // Database Connection
    private Connection dbConnection;
    private static final String DEFAULT_TABLE = "burp_notes";

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.stdout = new PrintWriter(callbacks.getStdout(), true);
        this.stderr = new PrintWriter(callbacks.getStderr(), true);

        callbacks.setExtensionName("BurpNote Database Connector");

        SwingUtilities.invokeLater(this::initializeUI);
        
        stdout.println("BurpNote extension loaded.");
    }

    private void initializeUI() {
        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 1. Top Panel: Database Connection (Global)
        JPanel topPanel = createConnectionPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // 2. Center Panel: Tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Add Note", createAddNotePanel());
        tabbedPane.addTab("Search Notes", createSearchPanel());
        tabbedPane.addTab("All Notes", createAllNotesPanel());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Register tab
        callbacks.customizeUiComponent(mainPanel);
        callbacks.addSuiteTab(this);
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Database Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 1
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("MySQL Host:"), gbc);
        hostField = new JTextField("localhost", 15);
        gbc.gridx = 1; panel.add(hostField, gbc);

        gbc.gridx = 2; panel.add(new JLabel("Port:"), gbc);
        portField = new JTextField("3306", 5);
        gbc.gridx = 3; panel.add(portField, gbc);

        gbc.gridx = 4; panel.add(new JLabel("Database:"), gbc);
        dbNameField = new JTextField("burp_db", 10);
        gbc.gridx = 5; panel.add(dbNameField, gbc);

        // Row 2
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Username:"), gbc);
        userField = new JTextField("root", 15);
        gbc.gridx = 1; panel.add(userField, gbc);

        gbc.gridx = 2; panel.add(new JLabel("Password:"), gbc);
        passField = new JPasswordField(15);
        gbc.gridx = 3; panel.add(passField, gbc);

        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> connectToDatabase());
        gbc.gridx = 4; gbc.gridwidth = 2;
        panel.add(connectButton, gbc);

        // Row 3
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 6;
        statusLabel = new JLabel("Status: Not Connected");
        statusLabel.setForeground(Color.GRAY);
        panel.add(statusLabel, gbc);

        return panel;
    }

    private JPanel createAddNotePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Domain Input
        JPanel domainPanel = new JPanel(new BorderLayout(5, 5));
        domainPanel.add(new JLabel("Domain:"), BorderLayout.WEST);
        domainInput = new JTextField();
        domainPanel.add(domainInput, BorderLayout.CENTER);
        panel.add(domainPanel, BorderLayout.NORTH);

        // Content Input
        JPanel contentPanel = new JPanel(new BorderLayout(5, 5));
        contentPanel.add(new JLabel("Content:"), BorderLayout.NORTH);
        contentArea = new JTextArea();
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentPanel.add(new JScrollPane(contentArea), BorderLayout.CENTER);
        panel.add(contentPanel, BorderLayout.CENTER);

        // Insert Button
        insertButton = new JButton("Insert into Database");
        insertButton.setEnabled(false);
        insertButton.addActionListener(e -> insertContent());
        panel.add(insertButton, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Search Controls
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlsPanel.add(new JLabel("Domain:"));
        searchDomainInput = new JTextField(20);
        controlsPanel.add(searchDomainInput);

        ButtonGroup matchGroup = new ButtonGroup();
        exactMatchRadio = new JRadioButton("Exact Match");
        fuzzyMatchRadio = new JRadioButton("Fuzzy Match", true);
        matchGroup.add(exactMatchRadio);
        matchGroup.add(fuzzyMatchRadio);
        controlsPanel.add(exactMatchRadio);
        controlsPanel.add(fuzzyMatchRadio);

        searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchContent());
        controlsPanel.add(searchButton);

        deleteButton = new JButton("Delete Selected");
        deleteButton.setEnabled(false);
        deleteButton.addActionListener(e -> deleteSelectedContent());
        controlsPanel.add(deleteButton);
        
        panel.add(controlsPanel, BorderLayout.NORTH);

        // Master-Detail View (Split Pane)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        
        // 1. Master: Results Table
        String[] columnNames = {"ID", "Domain", "Content (Preview)", "Time"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table read-only
            }
        };
        resultsTable = new JTable(tableModel);
        resultsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // Add listener to update detail view
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int viewRow = resultsTable.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = resultsTable.convertRowIndexToModel(viewRow);
                    String content = (String) tableModel.getValueAt(modelRow, 2); // Col 2 is Content
                    detailArea.setText(content);
                    detailArea.setCaretPosition(0); // Scroll to top
                    deleteButton.setEnabled(true); // Enable delete button
                } else {
                    deleteButton.setEnabled(false); // Disable if no selection
                    detailArea.setText("");
                }
            }
        });
        
        JScrollPane tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Results List"));
        splitPane.setTopComponent(tableScrollPane);

        // 2. Detail: Content Area
        detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setLineWrap(false); // No wrap for API lists usually
        detailArea.setFont(new Font("Monospaced", Font.PLAIN, 12)); // Use monospaced font for code/APIs
        
        JScrollPane detailScrollPane = new JScrollPane(detailArea);
        detailScrollPane.setBorder(BorderFactory.createTitledBorder("Full Content View"));
        splitPane.setBottomComponent(detailScrollPane);

        splitPane.setResizeWeight(0.5); // 50/50 split
        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createAllNotesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Controls
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        refreshButton = new JButton("Refresh / Load All");
        refreshButton.addActionListener(e -> loadAllContent());
        controlsPanel.add(refreshButton);
        
        deleteAllButton = new JButton("Delete Selected");
        deleteAllButton.setEnabled(false);
        deleteAllButton.addActionListener(e -> deleteAllNotesContent());
        controlsPanel.add(deleteAllButton);
        
        panel.add(controlsPanel, BorderLayout.NORTH);

        // Master-Detail View (Split Pane)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        
        // 1. Master: Results Table
        String[] columnNames = {"ID", "Domain", "Content (Preview)", "Time"};
        allNotesModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table read-only
            }
        };
        allNotesTable = new JTable(allNotesModel);
        allNotesTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // Add listener to update detail view
        allNotesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int viewRow = allNotesTable.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = allNotesTable.convertRowIndexToModel(viewRow);
                    String content = (String) allNotesModel.getValueAt(modelRow, 2); // Col 2 is Content
                    allNotesDetailArea.setText(content);
                    allNotesDetailArea.setCaretPosition(0); // Scroll to top
                    deleteAllButton.setEnabled(true); // Enable delete button
                } else {
                    deleteAllButton.setEnabled(false); // Disable if no selection
                    allNotesDetailArea.setText("");
                }
            }
        });
        
        JScrollPane tableScrollPane = new JScrollPane(allNotesTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("All Notes List"));
        splitPane.setTopComponent(tableScrollPane);

        // 2. Detail: Content Area
        allNotesDetailArea = new JTextArea();
        allNotesDetailArea.setEditable(false);
        allNotesDetailArea.setLineWrap(false); // No wrap for API lists usually
        allNotesDetailArea.setFont(new Font("Monospaced", Font.PLAIN, 12)); // Use monospaced font for code/APIs
        
        JScrollPane detailScrollPane = new JScrollPane(allNotesDetailArea);
        detailScrollPane.setBorder(BorderFactory.createTitledBorder("Full Content View"));
        splitPane.setBottomComponent(detailScrollPane);

        splitPane.setResizeWeight(0.5); // 50/50 split
        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private void connectToDatabase() {
        String host = hostField.getText().trim();
        String port = portField.getText().trim();
        String dbName = dbNameField.getText().trim();
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());

        if (host.isEmpty() || port.isEmpty() || dbName.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, "Please fill in all connection details.", "Connection Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 1. Connect to server without DB to ensure DB exists
        String serverUrl = String.format("jdbc:mysql://%s:%s/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", host, port);
        // 2. Connect to specific DB
        String dbUrl = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", host, port, dbName);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (dbConnection != null && !dbConnection.isClosed()) {
                    dbConnection.close();
                }
                Class.forName("com.mysql.cj.jdbc.Driver");
                
                // Step 1: Create Database if not exists
                try (Connection serverConn = DriverManager.getConnection(serverUrl, user, pass);
                     Statement stmt = serverConn.createStatement()) {
                    stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                }
                
                // Step 2: Connect to the database
                dbConnection = DriverManager.getConnection(dbUrl, user, pass);
                
                // Step 3: Create Table if not exists
                String createTableSQL = "CREATE TABLE IF NOT EXISTS " + DEFAULT_TABLE + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "domain VARCHAR(255), " +
                        "content TEXT, " +
                        "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
                
                try (Statement stmt = dbConnection.createStatement()) {
                    stmt.executeUpdate(createTableSQL);
                }
                
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    statusLabel.setText("Database connected successfully");
                    statusLabel.setForeground(new Color(0, 128, 0));
                    insertButton.setEnabled(true);
                    stdout.println("Database connected: " + dbUrl);
                } catch (Exception ex) {
                    statusLabel.setText("Connection failed: " + ex.getMessage());
                    statusLabel.setForeground(Color.RED);
                    insertButton.setEnabled(false);
                    stderr.println("Database connection error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(mainPanel, "Connection Failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void insertContent() {
        String domain = domainInput.getText().trim();
        String content = contentArea.getText().trim();

        if (content.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, "Content cannot be empty.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Assuming domain can be empty if user wants, but typically shouldn't for this use case
        // Let's allow empty domain but maybe warn? Or just proceed. 

        if (dbConnection == null) {
            JOptionPane.showMessageDialog(mainPanel, "Not connected to database.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                // Ensure table exists or columns are correct - we assume user has updated schema
                String sql = "INSERT INTO " + DEFAULT_TABLE + " (domain, content) VALUES (?, ?)";
                try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
                    pstmt.setString(1, domain);
                    pstmt.setString(2, content);
                    int affectedRows = pstmt.executeUpdate();
                    return affectedRows > 0;
                }
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        JOptionPane.showMessageDialog(mainPanel, "Data inserted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                        contentArea.setText("");
                        domainInput.setText("");
                        stdout.println("Inserted content for domain: " + domain);
                    } else {
                        JOptionPane.showMessageDialog(mainPanel, "Insert failed: No rows affected.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    handleDbException(ex);
                }
            }
        }.execute();
    }

    private void searchContent() {
        String domain = searchDomainInput.getText().trim();
        boolean isExact = exactMatchRadio.isSelected();

        if (domain.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, "Please enter a domain to search.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (dbConnection == null) {
            JOptionPane.showMessageDialog(mainPanel, "Not connected to database.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        new SwingWorker<Vector<Vector<Object>>, Void>() {
            @Override
            protected Vector<Vector<Object>> doInBackground() throws Exception {
                String sql;
                if (isExact) {
                    sql = "SELECT id, domain, content, create_time FROM " + DEFAULT_TABLE + " WHERE domain = ?";
                } else {
                    sql = "SELECT id, domain, content, create_time FROM " + DEFAULT_TABLE + " WHERE domain LIKE ?";
                }

                Vector<Vector<Object>> data = new Vector<>();
                try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
                    if (isExact) {
                        pstmt.setString(1, domain);
                    } else {
                        pstmt.setString(1, "%" + domain + "%");
                    }

                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            Vector<Object> row = new Vector<>();
                            row.add(rs.getInt("id"));
                            row.add(rs.getString("domain"));
                            row.add(rs.getString("content"));
                            row.add(rs.getTimestamp("create_time"));
                            data.add(row);
                        }
                    }
                }
                return data;
            }

            @Override
            protected void done() {
                try {
                    Vector<Vector<Object>> result = get();
                    tableModel.setRowCount(0); // Clear existing
                    for (Vector<Object> row : result) {
                        tableModel.addRow(row);
                    }
                    stdout.println("Search completed. Found " + result.size() + " records.");
                } catch (Exception ex) {
                    handleDbException(ex);
                }
            }
        }.execute();
    }

    private void deleteSelectedContent() {
        int[] viewRows = resultsTable.getSelectedRows();
        if (viewRows.length == 0) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(mainPanel, 
            "Are you sure you want to delete " + viewRows.length + " selected note(s)?", 
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        java.util.List<Integer> ids = new java.util.ArrayList<>();
        java.util.List<Integer> modelRows = new java.util.ArrayList<>();
        
        for (int viewRow : viewRows) {
            int modelRow = resultsTable.convertRowIndexToModel(viewRow);
            modelRows.add(modelRow);
            ids.add((Integer) tableModel.getValueAt(modelRow, 0));
        }
        
        // Sort model rows in descending order for safe removal
        java.util.Collections.sort(modelRows, java.util.Collections.reverseOrder());

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                if (ids.isEmpty()) return false;
                
                StringBuilder sqlBuilder = new StringBuilder("DELETE FROM " + DEFAULT_TABLE + " WHERE id IN (");
                for (int i = 0; i < ids.size(); i++) {
                    sqlBuilder.append(i == 0 ? "?" : ", ?");
                }
                sqlBuilder.append(")");
                
                try (PreparedStatement pstmt = dbConnection.prepareStatement(sqlBuilder.toString())) {
                    for (int i = 0; i < ids.size(); i++) {
                        pstmt.setInt(i + 1, ids.get(i));
                    }
                    int affectedRows = pstmt.executeUpdate();
                    return affectedRows > 0;
                }
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        for (int modelRow : modelRows) {
                            tableModel.removeRow(modelRow);
                        }
                        detailArea.setText("");
                        deleteButton.setEnabled(false);
                        JOptionPane.showMessageDialog(mainPanel, "Records deleted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                        stdout.println("Deleted " + ids.size() + " records.");
                    } else {
                        JOptionPane.showMessageDialog(mainPanel, "Delete failed: Records not found.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    handleDbException(ex);
                }
            }
        }.execute();
    }

    private void loadAllContent() {
        if (dbConnection == null) {
            JOptionPane.showMessageDialog(mainPanel, "Not connected to database.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        new SwingWorker<Vector<Vector<Object>>, Void>() {
            @Override
            protected Vector<Vector<Object>> doInBackground() throws Exception {
                String sql = "SELECT id, domain, content, create_time FROM " + DEFAULT_TABLE + " ORDER BY create_time DESC";
                Vector<Vector<Object>> data = new Vector<>();
                try (Statement stmt = dbConnection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        Vector<Object> row = new Vector<>();
                        row.add(rs.getInt("id"));
                        row.add(rs.getString("domain"));
                        row.add(rs.getString("content"));
                        row.add(rs.getTimestamp("create_time"));
                        data.add(row);
                    }
                }
                return data;
            }

            @Override
            protected void done() {
                try {
                    Vector<Vector<Object>> result = get();
                    allNotesModel.setRowCount(0); // Clear existing
                    for (Vector<Object> row : result) {
                        allNotesModel.addRow(row);
                    }
                    stdout.println("Loaded " + result.size() + " records.");
                } catch (Exception ex) {
                    handleDbException(ex);
                }
            }
        }.execute();
    }

    private void deleteAllNotesContent() {
        int[] viewRows = allNotesTable.getSelectedRows();
        if (viewRows.length == 0) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(mainPanel, 
            "Are you sure you want to delete " + viewRows.length + " selected note(s)?", 
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        java.util.List<Integer> ids = new java.util.ArrayList<>();
        java.util.List<Integer> modelRows = new java.util.ArrayList<>();
        
        for (int viewRow : viewRows) {
            int modelRow = allNotesTable.convertRowIndexToModel(viewRow);
            modelRows.add(modelRow);
            ids.add((Integer) allNotesModel.getValueAt(modelRow, 0));
        }
        
        // Sort model rows in descending order for safe removal
        java.util.Collections.sort(modelRows, java.util.Collections.reverseOrder());

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                if (ids.isEmpty()) return false;
                
                StringBuilder sqlBuilder = new StringBuilder("DELETE FROM " + DEFAULT_TABLE + " WHERE id IN (");
                for (int i = 0; i < ids.size(); i++) {
                    sqlBuilder.append(i == 0 ? "?" : ", ?");
                }
                sqlBuilder.append(")");
                
                try (PreparedStatement pstmt = dbConnection.prepareStatement(sqlBuilder.toString())) {
                    for (int i = 0; i < ids.size(); i++) {
                        pstmt.setInt(i + 1, ids.get(i));
                    }
                    int affectedRows = pstmt.executeUpdate();
                    return affectedRows > 0;
                }
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        for (int modelRow : modelRows) {
                            allNotesModel.removeRow(modelRow);
                        }
                        allNotesDetailArea.setText("");
                        deleteAllButton.setEnabled(false);
                        JOptionPane.showMessageDialog(mainPanel, "Records deleted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                        stdout.println("Deleted " + ids.size() + " records.");
                    } else {
                        JOptionPane.showMessageDialog(mainPanel, "Delete failed: Records not found.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    handleDbException(ex);
                }
            }
        }.execute();
    }

    private void handleDbException(Exception ex) {
        try {
            if (dbConnection == null || dbConnection.isClosed()) {
                statusLabel.setText("Connection lost");
                statusLabel.setForeground(Color.RED);
                insertButton.setEnabled(false);
            }
        } catch (SQLException ignored) {}
        
        JOptionPane.showMessageDialog(mainPanel, "Database Operation Failed:\n" + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        stderr.println("Database error: " + ex.getMessage());
        ex.printStackTrace(stderr);
    }

    @Override
    public String getTabCaption() {
        return "BurpNote";
    }

    @Override
    public Component getUiComponent() {
        return mainPanel;
    }
}
