package com.taskmanager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TaskManagerGUI extends JFrame {

    private static final String DB_URL = "jdbc:sqlite:tasks.db";

    private JTable tasksTable;
    private DefaultTableModel tableModel;
    private JButton loadButton;
    private JButton addButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    public TaskManagerGUI() {
        super("Prosty Menedżer Zadań");

        initDatabase();
        initComponents();
        
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS tasks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "title TEXT NOT NULL," +
                    "description TEXT," +
                    "is_done BOOLEAN NOT NULL" +
                    ")";
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Błąd inicjalizacji bazy danych: " + e.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // Górny panel z przyciskami
        JPanel topPanel = new JPanel();
        
        loadButton = new JButton("Wczytaj Zadania");
        loadButton.addActionListener(e -> {
            statusLabel.setText("Ładowanie danych... Proszę czekać.");
            loadButton.setEnabled(false);
            progressBar.setVisible(true);
            progressBar.setIndeterminate(true);
            new LoadTasksWorker().execute();
        });
        
        addButton = new JButton("Dodaj Zadanie");
        addButton.addActionListener(e -> showAddTaskDialog());
        
        topPanel.add(loadButton);
        topPanel.add(addButton);

        add(topPanel, BorderLayout.NORTH);

        // Środkowy panel z tabelą
        tableModel = new DefaultTableModel(new String[]{"ID", "Tytuł", "Opis", "Zrobione"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Zablokowanie edycji komórek bezpośrednio w tabeli
            }
        };
        tasksTable = new JTable(tableModel);
        add(new JScrollPane(tasksTable), BorderLayout.CENTER);

        // Dolny panel ze statusem
        JPanel bottomPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Gotowe");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        bottomPanel.add(statusLabel, BorderLayout.WEST);
        bottomPanel.add(progressBar, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void showAddTaskDialog() {
        JTextField titleField = new JTextField(20);
        JTextField descField = new JTextField(20);
        JCheckBox doneCheckBox = new JCheckBox("Zrobione");

        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
        panel.add(new JLabel("Tytuł:"));
        panel.add(titleField);
        panel.add(new JLabel("Opis:"));
        panel.add(descField);
        panel.add(new JLabel("Status:"));
        panel.add(doneCheckBox);

        int result = JOptionPane.showConfirmDialog(this, panel, "Nowe Zadanie",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (result == JOptionPane.OK_OPTION) {
            String title = titleField.getText();
            String description = descField.getText();
            boolean isDone = doneCheckBox.isSelected();
            
            if (title.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Tytuł nie może być puste!");
                return;
            }

            statusLabel.setText("Zapisywanie zadania...");
            progressBar.setVisible(true);
            progressBar.setIndeterminate(true);
            Task task = new Task(title, description, isDone);
            new AddTaskWorker(task).execute();
        }
    }

    // --- Klasy Workerów ---

    private class LoadTasksWorker extends SwingWorker<List<Task>, Void> {
        @Override
        protected List<Task> doInBackground() throws Exception {
            List<Task> tasks = new ArrayList<>();
            // Sztuczne opóźnienie wg wymagań
            Thread.sleep(4000);

            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM tasks")) {
                
                while (rs.next()) {
                    tasks.add(new Task(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getBoolean("is_done")
                    ));
                }
            }
            return tasks;
        }

        @Override
        protected void done() {
            try {
                List<Task> tasks = get();
                tableModel.setRowCount(0); // Wyczyszczenie istniejących danych
                for (Task t : tasks) {
                    tableModel.addRow(new Object[]{
                            t.getId(),
                            t.getTitle(),
                            t.getDescription(),
                            t.isDone() ? "Tak" : "Nie"
                    });
                }
                statusLabel.setText("Gotowe. Wczytano " + tasks.size() + " zadań.");
            } catch (Exception e) {
                e.printStackTrace();
                statusLabel.setText("Błąd połączenia: " + e.getMessage());
            } finally {
                loadButton.setEnabled(true);
                progressBar.setVisible(false);
                progressBar.setIndeterminate(false);
            }
        }
    }

    private class AddTaskWorker extends SwingWorker<Boolean, Void> {
        private final Task task;

        public AddTaskWorker(Task task) {
            this.task = task;
        }

        @Override
        protected Boolean doInBackground() throws Exception {
            String sql = "INSERT INTO tasks(title, description, is_done) VALUES(?,?,?)";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, task.getTitle());
                pstmt.setString(2, task.getDescription());
                pstmt.setBoolean(3, task.isDone());
                int affectedRows = pstmt.executeUpdate();
                return affectedRows > 0;
            }
        }

        @Override
        protected void done() {
            try {
                boolean success = get();
                if (success) {
                    statusLabel.setText("Gotowe. Zadanie dodane."); // Zostanie zaraz nadpisane przez ładowanie
                    
                    // Odświeżenie danych i wywołanie LoadTasksWorker, co uruchomi JProgressBar i zmieni status
                    statusLabel.setText("Ładowanie danych... Proszę czekać.");
                    loadButton.setEnabled(false);
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(true);
                    new LoadTasksWorker().execute();
                } else {
                    statusLabel.setText("Błąd zapisu zadania (żadne wiersze nie zostały poprawione).");
                    progressBar.setVisible(false);
                    progressBar.setIndeterminate(false);
                }
            } catch (Exception e) {
                e.printStackTrace();
                statusLabel.setText("Błąd połączenia: " + e.getMessage());
                progressBar.setVisible(false);
                progressBar.setIndeterminate(false);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new TaskManagerGUI().setVisible(true);
        });
    }
}
