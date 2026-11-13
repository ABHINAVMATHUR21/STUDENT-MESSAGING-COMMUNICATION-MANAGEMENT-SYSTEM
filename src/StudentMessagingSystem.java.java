import java.awt.*;
import java.awt.event.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
public class StudentMessagingSystem {
    public static void main(String[] args) { new LoginFrame(); }
}

class Student {
    String name, username, password, section, roll = "";  
    Student(String n, String u, String p, String s) { name=n; username=u; password=p; section=s; }
}
class Teacher {
    String name, username, password;
    ArrayList<String> sections = new ArrayList<>();
    Teacher(String n, String u, String p) { name=n; username=u; password=p; }
}
class Staff {
    String name, username, password, staffRole;
    Staff(String n, String u, String p, String r) { name=n; username=u; password=p; staffRole=r; }
}
class Message {
    int id;
    String fromUser, fromDisplay, to, body, time;
    boolean edited = false;
    Message(String fu, String fd, String t, String b, String tm) { fromUser=fu; fromDisplay=fd; to=t; body=b; time=tm; }
}
class LoginResult {
    String role; Object user;
    LoginResult(String r, Object u) { role=r; user=u; }
}

class UI {
    static final Color NAVY = new Color(30, 41, 59), NAV_IDLE = new Color(51, 65, 85), NAV_ACTIVE = new Color(37, 99, 235),
            PAGE_BG = new Color(243, 244, 246), GREEN = new Color(21, 128, 61), RED = new Color(185, 28, 28);
    static Label label(String t, int size, boolean bold, Color c) {
        Label l = new Label(t); l.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size)); l.setForeground(c); return l;
    }
    static Button styled(Button b, Color bg) { b.setBackground(bg); b.setForeground(Color.WHITE); b.setFont(new Font("SansSerif", Font.BOLD, 13)); return b; }
    static Panel labeledList(String title, List l) {
        Panel p = new Panel(new BorderLayout(0, 3));
        p.add(label(title, 13, true, NAVY), BorderLayout.NORTH); p.add(l, BorderLayout.CENTER);
        return p;
    }
    static WindowAdapter exitOnClose() { return new WindowAdapter() { public void windowClosing(WindowEvent e) { System.exit(0); } }; }
 
    static void fill(List l, ArrayList<String> src, String q) {
        l.removeAll();
        for (String s : src) if (q.isEmpty() || s.toLowerCase().contains(q)) l.add(s);
    }
    static String promptEdit(Frame owner, String currentText) {
        final String[] result = {null};
        Dialog d = new Dialog(owner, "Edit Message", true);
        d.setLayout(new BorderLayout(8, 8));
        TextArea ta = new TextArea(currentText, 5, 40);
        d.add(ta, BorderLayout.CENTER);
        Button save = styled(new Button("Save"), GREEN), cancel = new Button("Cancel");
        Panel btns = new Panel(new FlowLayout(FlowLayout.RIGHT)); btns.add(save); btns.add(cancel);
        d.add(btns, BorderLayout.SOUTH);
        save.addActionListener(e -> { result[0] = ta.getText().trim(); d.dispose(); });
        cancel.addActionListener(e -> d.dispose());
        d.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { d.dispose(); } });
        d.setSize(440, 240); d.setLocationRelativeTo(owner); d.setVisible(true);
        return result[0];
    }
    static boolean confirm(Frame owner, String msg) {
        final boolean[] yes = {false};
        Dialog d = new Dialog(owner, "Please confirm", true);
        d.setLayout(new BorderLayout(10, 10));
        d.add(new Label(msg, Label.CENTER), BorderLayout.CENTER);
        Button y = styled(new Button("Yes, delete"), RED), n = new Button("Cancel");
        y.addActionListener(e -> { yes[0] = true; d.dispose(); });
        n.addActionListener(e -> d.dispose());
        d.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { d.dispose(); } });
        Panel bp = new Panel(new FlowLayout(FlowLayout.CENTER, 12, 8)); bp.add(y); bp.add(n);
        d.add(bp, BorderLayout.SOUTH);
        d.setSize(380, 140); d.setLocationRelativeTo(owner); d.setVisible(true);
        return yes[0];
    }
}

class DataStore {
    static final String ADMIN_USER = "admin", ADMIN_PASS = "admin123";
    static final String URL = "jdbc:mysql://localhost:3306/student_messaging", DB_USER = "root", DB_PASS = "root";

    static Connection con;
    static String err = null;                      
    static ArrayList<String> sections = new ArrayList<>();
    static ArrayList<Student> students = new ArrayList<>();
    static ArrayList<Teacher> teachers = new ArrayList<>();
    static ArrayList<Staff> staffList = new ArrayList<>();

    // ---------- low-level JDBC helpers ----------
    static Connection con() throws SQLException {
        if (con == null || !con.isValid(2)) con = DriverManager.getConnection(URL, DB_USER, DB_PASS);
        return con;
    }
    static PreparedStatement prep(String sql, Object... p) throws SQLException {
        PreparedStatement ps = con().prepareStatement(sql);
        for (int i = 0; i < p.length; i++) ps.setObject(i + 1, p[i]);
        return ps;
    }
    // INSERT / UPDATE / DELETE. Returns rows changed, or -1 on error (message in err).
    static int run(String sql, Object... p) {
        err = null;
        try (PreparedStatement ps = prep(sql, p)) { return ps.executeUpdate(); }
        catch (SQLException e) { err = e.getMessage(); return -1; }
    }
    static boolean exists(String sql, Object... p) {
        err = null;
        try (PreparedStatement ps = prep(sql, p); ResultSet rs = ps.executeQuery()) { return rs.next(); }
        catch (SQLException e) { err = e.getMessage(); return false; }
    }
    static String nz(String s) { return s == null ? "" : s; }
    // Name of the roll-number column in the users table ("" if there is none). Checked once.
    static String rollCol = null;
    static String rollColumn() throws SQLException {
        if (rollCol == null) {
            rollCol = "";
            for (String c : new String[]{"roll_no", "roll_number", "rollno", "enrollment_no"})
                try (ResultSet rs = con().getMetaData().getColumns(con().getCatalog(), null, "users", c)) { if (rs.next()) { rollCol = c; break; } }
        }
        return rollCol;
    }
    // Branch code used in roll numbers, decided by the section letter:
    // Section A, B, C = CS | D = CR | E = AD | F = CA | G = CD | H = CI | I = EC
    static String branchOf(String section) {
        String s = section.trim();
        if (s.isEmpty()) return "";
        char c = Character.toUpperCase(s.charAt(s.length() - 1));
        if ("ABC".indexOf(c) >= 0) return "CS";
        if (c == 'D') return "CR";
        if (c == 'E') return "AD";
        if (c == 'F') return "CA";
        if (c == 'G') return "CD";
        if (c == 'H') return "CI";
        if (c == 'I') return "EC";
        return "";
    }
    static String rollOf(String username, String column, String section) {
        if (!column.isEmpty()) return column;
        int at = username.indexOf('@');
        if (at < 0) return username;
        String local = username.substring(0, at);
        int i = local.length();
        while (i > 0 && Character.isDigit(local.charAt(i - 1))) i--;
        String num = local.substring(i), branch = branchOf(section);
        if (num.isEmpty()) return local;
        boolean hasYear = local.length() >= 4 && local.substring(0, 4).matches("\\d{4}");
        return hasYear && !branch.isEmpty() ? "PIET" + local.substring(2, 4) + branch + num : num;
    }
    // ----- text shown in the contact lists (no e-mail / username) -----
    static String label(Student s) { return s.name + " (" + s.roll + ")"; }
    static String label(Teacher t) { return t.name + " - Teacher"; }
    static String label(Staff f) { return f.name + " - " + f.staffRole; }
    // Finds the username behind a list item.
    static String userOfLabel(String label) {
        for (Student s : students) if (label(s).equals(label)) return s.username;
        for (Teacher t : teachers) if (label(t).equals(label)) return t.username;
        for (Staff f : staffList) if (label(f).equals(label)) return f.username;
        return null;
    }
    static String fail() { return err == null ? "no rows were changed" : err; }

    // ---------- users & sections ----------
    static boolean reload() {
        err = null;
        try (Statement st = con().createStatement()) {
            sections.clear(); students.clear(); teachers.clear(); staffList.clear();
            try (ResultSet rs = st.executeQuery("SELECT section_name FROM sections ORDER BY section_id")) {
                while (rs.next()) sections.add(rs.getString(1));
            }
            String rc = rollColumn();
            try (ResultSet rs = st.executeQuery("SELECT u.name, u.username, u.password, u.role, u.staff_role, s.section_name, "
                    + (rc.isEmpty() ? "NULL" : "u." + rc) + " FROM users u LEFT JOIN sections s ON u.section_id = s.section_id ORDER BY u.user_id")) {
                while (rs.next()) {
                    String n = nz(rs.getString(1)), u = nz(rs.getString(2)), p = nz(rs.getString(3)), role = nz(rs.getString(4)),
                            sr = rs.getString(5), sec = nz(rs.getString(6));
                    if (role.equalsIgnoreCase("STUDENT")) { Student stu = new Student(n, u, p, sec); stu.roll = rollOf(u, nz(rs.getString(7)).trim(), sec); students.add(stu); }
                    else if (role.equalsIgnoreCase("TEACHER")) teachers.add(new Teacher(n, u, p));
                    else if (role.equalsIgnoreCase("STAFF")) staffList.add(new Staff(n, u, p, sr == null ? "Staff" : sr));
                }
            }
            // A teacher can teach many sections (teacher_sections table).
            try (ResultSet rs = st.executeQuery("SELECT u.username, s.section_name FROM teacher_sections ts "
                    + "JOIN users u ON ts.user_id = u.user_id JOIN sections s ON ts.section_id = s.section_id ORDER BY s.section_id")) {
                while (rs.next()) for (Teacher t : teachers) if (t.username.equals(rs.getString(1))) t.sections.add(rs.getString(2));
            }
            return true;
        } catch (SQLException e) { err = e.getMessage(); return false; }
    }
    static LoginResult authenticate(String u, String p) {
        if (u.equals(ADMIN_USER) && p.equals(ADMIN_PASS)) { reload(); return new LoginResult("ADMIN", null); }
        if (!reload()) return null;
        for (Student s : students) if (s.username.equals(u) && s.password.equals(p)) return new LoginResult("STUDENT", s);
        for (Teacher t : teachers) if (t.username.equals(u) && t.password.equals(p)) return new LoginResult("TEACHER", t);
        for (Staff st : staffList) if (st.username.equals(u) && st.password.equals(p)) return new LoginResult("STAFF", st);
        return null;
    }
    static boolean usernameTaken(String u) { return u.equalsIgnoreCase(ADMIN_USER) || exists("SELECT 1 FROM users WHERE username = ?", u); }
    // role = STUDENT / STAFF. section is used by students, staffRole by staff (the other may be null).
    static boolean addUser(String name, String user, String pass, String role, String section, String staffRole) {
        return run("INSERT INTO users(name, username, password, role, section_id, staff_role) "
                + "VALUES(?, ?, ?, ?, (SELECT section_id FROM sections WHERE section_name = ?), ?)", name, user, pass, role, section, staffRole) > 0 && reload();
    }
    // A teacher can have any number of sections.
    static boolean addTeacher(String name, String user, String pass, String[] secs) {
        if (run("INSERT INTO users(name, username, password, role) VALUES(?, ?, ?, 'TEACHER')", name, user, pass) < 1) return false;
        for (String s : secs)
            if (run("INSERT INTO teacher_sections(user_id, section_id) VALUES((SELECT user_id FROM users WHERE username = ?), "
                    + "(SELECT section_id FROM sections WHERE section_name = ?))", user, s) < 1) return false;
        return reload();
    }
    // Messages sent or received by the user are removed first so foreign keys do not block the delete.
    static boolean deleteUser(String username) {
        run("DELETE FROM messages WHERE sender_id = (SELECT user_id FROM users WHERE username = ?) "
                + "OR receiver_id = (SELECT user_id FROM users WHERE username = ?)", username, username);
        if (err != null) return false;
        run("DELETE FROM teacher_sections WHERE user_id = (SELECT user_id FROM users WHERE username = ?)", username);
        if (err != null) return false;
        return run("DELETE FROM users WHERE username = ?", username) > 0 && reload();
    }
    static boolean addSection(String name) { return run("INSERT INTO sections(section_name) VALUES(?)", name) > 0 && reload(); }
    static ArrayList<Student> studentsInSection(String sec) {
        ArrayList<Student> out = new ArrayList<>();
        for (Student s : students) if (s.section.equals(sec)) out.add(s);
        return out;
    }
    static ArrayList<Teacher> teachersOfSection(String sec) {
        ArrayList<Teacher> out = new ArrayList<>();
        for (Teacher t : teachers) if (t.sections.contains(sec)) out.add(t);
        return out;
    }

    // ---------- messages ----------
    // "to" is a username, "SECTION:<name>" or "ALL_STUDENTS" (stored as receiver_type USER / SECTION / ALL).
    static boolean send(String fromUser, String to, String body) {
        String head = "INSERT INTO messages(sender_id, receiver_id, receiver_type, section_id, message_body, sent_at, edited, deleted) "
                + "VALUES((SELECT user_id FROM users WHERE username = ?), ";
        if (to.startsWith("SECTION:"))
            return run(head + "NULL, 'SECTION', (SELECT section_id FROM sections WHERE section_name = ?), ?, NOW(), 0, 0)", fromUser, to.substring(8), body) > 0;
        if (to.equals("ALL_STUDENTS"))
            return run(head + "NULL, 'ALL', NULL, ?, NOW(), 0, 0)", fromUser, body) > 0;
        return run(head + "(SELECT user_id FROM users WHERE username = ?), 'USER', NULL, ?, NOW(), 0, 0)", fromUser, to, body) > 0;
    }
    static boolean editMessage(int id, String text) { return run("UPDATE messages SET message_body = ?, edited = 1 WHERE message_id = ?", text, id) > 0; }
    // Soft delete: the row stays in the table with deleted = 1.
    static boolean deleteMessage(int id) { return run("UPDATE messages SET deleted = 1 WHERE message_id = ?", id) > 0; }

    // Runs the shared message SELECT with an extra WHERE condition (aliases: m = messages, s = sender, r = receiver, sec = section).
    static ArrayList<Message> messagesWhere(String where, Object... p) {
        ArrayList<Message> out = new ArrayList<>();
        String sql = "SELECT m.message_id, s.username, IF(s.role = 'STAFF' AND s.staff_role IS NOT NULL, CONCAT(s.name, ' (', s.staff_role, ')'), s.name), "
                + "m.receiver_type, r.username, sec.section_name, m.message_body, DATE_FORMAT(m.sent_at, '%d-%m-%Y %H:%i:%s'), m.edited "
                + "FROM messages m JOIN users s ON m.sender_id = s.user_id LEFT JOIN users r ON m.receiver_id = r.user_id "
                + "LEFT JOIN sections sec ON m.section_id = sec.section_id "
                + "WHERE m.deleted = 0 AND (" + where + ") ORDER BY m.sent_at, m.message_id";
        err = null;
        try (PreparedStatement ps = prep(sql, p); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String type = nz(rs.getString(4));
                String to = type.equalsIgnoreCase("USER") ? nz(rs.getString(5)) : type.equalsIgnoreCase("SECTION") ? "SECTION:" + nz(rs.getString(6)) : "ALL_STUDENTS";
                Message m = new Message(rs.getString(2), nz(rs.getString(3)), to, nz(rs.getString(7)), rs.getString(8));
                m.id = rs.getInt(1); m.edited = rs.getBoolean(9);
                out.add(m);
            }
        } catch (SQLException e) { err = e.getMessage(); }
        return out;
    }
    // Messages broadcast to a whole section, plus those sent to ALL students.
    static ArrayList<Message> sectionAnnouncements(String sectionName) {
        return messagesWhere("m.receiver_type = 'ALL' OR (m.receiver_type = 'SECTION' AND sec.section_name = ?)", sectionName);
    }
    static ArrayList<Message> conversationBetween(String a, String b) {
        return messagesWhere("m.receiver_type = 'USER' AND ((s.username = ? AND r.username = ?) OR (s.username = ? AND r.username = ?))", a, b, b, a);
    }
    // Announcements this user sent to one target ("SECTION:<name>" or "ALL_STUDENTS").
    static ArrayList<Message> sentTo(String user, String target) {
        return target.startsWith("SECTION:")
                ? messagesWhere("s.username = ? AND m.receiver_type = 'SECTION' AND sec.section_name = ?", user, target.substring(8))
                : messagesWhere("s.username = ? AND m.receiver_type = 'ALL'", user);
    }
}

class LoginFrame extends Frame implements ActionListener {
    final TextField userField = new TextField(22), passField = new TextField(22);
    final Label msg = UI.label(String.format("%48s", ""), 12, true, UI.RED);
    final Button loginBtn = UI.styled(new Button("Sign In"), UI.NAV_ACTIVE);
    LoginFrame() {
        super("Student Messaging & Communication Management System");
        setBackground(UI.NAVY); setLayout(new GridBagLayout());
        Panel f = new Panel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST; g.insets = new Insets(7, 6, 7, 6); g.gridwidth = 2;
        g.gridx = 0; g.gridy = 0; f.add(UI.label("Welcome Back", 24, true, UI.NAVY), g);
        g.gridy = 1; f.add(UI.label("Sign in as Admin, Teacher, Student or Staff", 12, false, Color.GRAY), g);
        g.gridwidth = 1; g.fill = GridBagConstraints.HORIZONTAL;
        g.gridy = 2; g.gridx = 0; g.weightx = 0; f.add(UI.label("Username", 13, true, Color.BLACK), g);
        g.gridx = 1; g.weightx = 1; f.add(userField, g);
        g.gridy = 3; g.gridx = 0; g.weightx = 0; f.add(UI.label("Password", 13, true, Color.BLACK), g);
        g.gridx = 1; g.weightx = 1; passField.setEchoChar('*'); f.add(passField, g);
        g.gridy = 4; g.gridx = 0; g.gridwidth = 2; g.insets = new Insets(14, 6, 6, 6); f.add(loginBtn, g);
        g.gridy = 5; g.insets = new Insets(4, 6, 4, 6); f.add(msg, g);
        Panel card = new Pad(new BorderLayout(), 26, 32);
        card.setBackground(Color.WHITE); card.add(f, BorderLayout.CENTER);
        add(card);
        loginBtn.addActionListener(this); passField.addActionListener(this);
        addWindowListener(UI.exitOnClose());
        setSize(520, 440); setLocationRelativeTo(null); setVisible(true);
        userField.requestFocus();
    }
    public void actionPerformed(ActionEvent e) {
        LoginResult r = DataStore.authenticate(userField.getText().trim(), passField.getText().trim());
        if (r == null) {
            if (DataStore.err != null) { System.err.println("DB error: " + DataStore.err); msg.setText("Cannot reach the database. See console for details."); }
            else msg.setText("Invalid username or password. Please try again.");
            validate(); return;
        }
        dispose();
        if (r.role.equals("ADMIN")) new AdminDashboard();
        else if (r.role.equals("STUDENT")) new StudentDashboard((Student) r.user);
        else if (r.role.equals("TEACHER")) new TeacherDashboard((Teacher) r.user);
        else new StaffDashboard((Staff) r.user);
    }
}

// Small panel with inner padding (AWT has no borders/padding of its own).
class Pad extends Panel {
    int v, h;
    Pad(LayoutManager lm, int v, int h) { super(lm); this.v = v; this.h = h; }
    public Insets getInsets() { return new Insets(v, h, v, h); }
}

class AdminDashboard extends Frame implements ActionListener {
    final CardLayout cards = new CardLayout();
    final Panel content = new Panel(cards);
    final String[] pageNames = {"All Users", "Add Student", "Add Teacher", "Add Staff", "Sections"};
    final Button[] navBtns = new Button[5];
    final Button logoutBtn = UI.styled(new Button("Logout"), new Color(185, 28, 28));
    final Label status = new Label();
    final Label[] statValues = {new Label("0", Label.CENTER), new Label("0", Label.CENTER), new Label("0", Label.CENTER), new Label("0", Label.CENTER)};
    // All Users page
    final Choice typeFilter = new Choice();
    final TextField searchField = new TextField(22);
    final List userList = new List(14);
    final Button deleteBtn = UI.styled(new Button("Delete Selected User"), UI.RED), clearSearchBtn = new Button("Clear Search");
    final ArrayList<Object> shown = new ArrayList<>();
    // Forms
    final TextField sName = field(false), sUser = field(false), sPass = field(true);
    final TextField tName = field(false), tUser = field(false), tPass = field(true);
    final TextField fName = field(false), fUser = field(false), fPass = field(true), secField = field(false);
    final Choice sSection = new Choice(), fRole = new Choice(), fDept = new Choice(), fType = new Choice();
    final List tSection = new List(4, true);          // multi-select: a teacher can teach many sections
    final List sectionList = new List(8);
    final Button addStuBtn = UI.styled(new Button("Add Student"), UI.GREEN), addTeaBtn = UI.styled(new Button("Add Teacher"), UI.GREEN),
            addStaffBtn = UI.styled(new Button("Add Staff"), UI.GREEN), addSecBtn = UI.styled(new Button("Add Section"), UI.GREEN);

    AdminDashboard() {
        super("Admin Dashboard - Student Messaging System");
        setLayout(new BorderLayout());
        setBackground(UI.PAGE_BG);
        for (String r : new String[]{"Principal", "Director", "Registrar", "HOD", "Deputy HOD"}) fRole.add(r);
        // Add more departments here if you need them.
        for (String d : new String[]{"CSE", "CSAI", "IoT", "DS", "ECE", "Mechanical", "Civil"}) fDept.add(d);
        for (String t : new String[]{"Academic", "Admin"}) fType.add(t);
        fDept.setEnabled(false); fType.setEnabled(false);   // HOD needs a department, Deputy HOD also needs a type
        fRole.addItemListener(e -> {
            String r = fRole.getSelectedItem();
            fDept.setEnabled(r.contains("HOD"));
            fType.setEnabled(r.equals("Deputy HOD"));
        });
        for (String t : new String[]{"All Users", "Students", "Teachers", "Staff"}) typeFilter.add(t);
        Font mono = new Font("Monospaced", Font.PLAIN, 13);
        userList.setFont(mono); sectionList.setFont(mono);

        Panel header = new Pad(new BorderLayout(), 14, 20);
        header.setBackground(UI.NAVY);
        header.add(UI.label("Admin Dashboard", 20, true, Color.WHITE), BorderLayout.WEST);
        header.add(UI.label("Logged in as: admin", 12, false, new Color(203, 213, 225)), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        Panel side = new Pad(new BorderLayout(0, 10), 16, 12);
        side.setBackground(UI.NAVY);
        Panel menu = new Panel(new GridLayout(0, 1, 0, 8));
        for (int i = 0; i < navBtns.length; i++) {
            navBtns[i] = UI.styled(new Button(pageNames[i]), UI.NAV_IDLE);
            navBtns[i].addActionListener(this);
            menu.add(navBtns[i]);
        }
        side.add(menu, BorderLayout.NORTH);
        side.add(logoutBtn, BorderLayout.SOUTH);
        add(side, BorderLayout.WEST);

        content.add(usersPage(), "0");
        content.add(page(formCard("Add New Student", "Student can log in with this username and password.",
                new String[]{"Full name", "Username", "Password", "Section"}, new Component[]{sName, sUser, sPass, sSection},
                addStuBtn, clearBtn(sName, sUser, sPass))), "1");
        content.add(page(formCard("Add New Teacher", "Select one or more sections (Ctrl+click for many).",
                new String[]{"Full name", "Username", "Password", "Teaches sections"}, new Component[]{tName, tUser, tPass, tSection},
                addTeaBtn, clearBtn(tName, tUser, tPass))), "2");
        content.add(page(formCard("Add Management Staff", "For HOD / Deputy HOD, also pick the department (and type for Deputy HOD).",
                new String[]{"Full name", "Username", "Password", "Role", "Department", "Deputy HOD type"}, new Component[]{fName, fUser, fPass, fRole, fDept, fType},
                addStaffBtn, clearBtn(fName, fUser, fPass))), "3");
        content.add(sectionsPage(), "4");
        add(content, BorderLayout.CENTER);

        status.setFont(new Font("SansSerif", Font.BOLD, 12));
        add(status, BorderLayout.SOUTH);

        for (Button b : new Button[]{logoutBtn, deleteBtn, clearSearchBtn, addStuBtn, addTeaBtn, addStaffBtn, addSecBtn}) b.addActionListener(this);
        searchField.addTextListener(e -> refreshUsers());
        typeFilter.addItemListener(e -> refreshUsers());
        addWindowListener(UI.exitOnClose());
        afterChange();
        showPage(0);
        if (DataStore.err != null) say("Database error: " + DataStore.err, false);
        setSize(1250, 640); setMinimumSize(new Dimension(1100, 560)); setLocationRelativeTo(null); setVisible(true);
    }

    // ---------- page builders ----------
    Panel usersPage() {
        Panel stats = new Panel(new GridLayout(1, 4, 12, 0));
        String[] caps = {"Students", "Teachers", "Management Staff", "Sections"};
        Color[] cols = {new Color(37, 99, 235), new Color(5, 150, 105), new Color(217, 119, 6), new Color(124, 58, 237)};
        for (int i = 0; i < 4; i++) {
            Panel card = new Pad(new BorderLayout(), 10, 14);
            card.setBackground(cols[i]);
            statValues[i].setFont(new Font("SansSerif", Font.BOLD, 28)); statValues[i].setForeground(Color.WHITE);
            card.add(statValues[i], BorderLayout.CENTER);
            card.add(UI.label(caps[i], 12, false, Color.WHITE), BorderLayout.SOUTH);
            stats.add(card);
        }
        Panel filters = new Panel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filters.add(UI.label("Show:", 13, true, Color.BLACK)); filters.add(typeFilter);
        filters.add(UI.label("Search:", 13, true, Color.BLACK)); filters.add(searchField); filters.add(clearSearchBtn);
        Panel top = new Panel(new BorderLayout(0, 8));
        top.add(stats, BorderLayout.NORTH); top.add(filters, BorderLayout.CENTER);

        Panel table = new Panel(new BorderLayout(0, 4));
        Label head = UI.label(rowText("TYPE", "NAME", "USERNAME", "SECTION / ROLE"), 13, true, UI.NAVY);
        head.setFont(new Font("Monospaced", Font.BOLD, 13));
        table.add(head, BorderLayout.NORTH);
        table.add(userList, BorderLayout.CENTER);
        Panel bottom = new Panel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        bottom.add(deleteBtn); bottom.add(UI.label("Tip: select a user in the list, then click Delete.", 12, false, Color.GRAY));
        table.add(bottom, BorderLayout.SOUTH);

        Panel p = new Pad(new BorderLayout(0, 12), 18, 22);
        p.setBackground(UI.PAGE_BG);
        p.add(top, BorderLayout.NORTH); p.add(table, BorderLayout.CENTER);
        return p;
    }

    Panel sectionsPage() {
        Panel p = new Pad(new BorderLayout(0, 16), 20, 22);
        p.setBackground(UI.PAGE_BG);
        Panel left = new Panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(formCard("Add New Section", "Example: Section C", new String[]{"Section name"}, new Component[]{secField},
                addSecBtn, clearBtn(secField)));
        p.add(left, BorderLayout.NORTH);
        Panel list = new Panel(new BorderLayout(0, 4));
        list.add(UI.label("Existing sections", 14, true, UI.NAVY), BorderLayout.NORTH);
        list.add(sectionList, BorderLayout.CENTER);
        p.add(list, BorderLayout.CENTER);
        return p;
    }

    // White card with aligned label/field rows and a button row underneath.
    Panel formCard(String title, String hint, String[] labels, Component[] comps, Button... btns) {
        Panel f = new Panel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST; g.insets = new Insets(7, 6, 7, 6); g.gridwidth = 2;
        g.gridx = 0; g.gridy = 0; f.add(UI.label(title, 20, true, UI.NAVY), g);
        g.gridy = 1; f.add(UI.label(hint, 12, false, Color.GRAY), g);
        g.gridwidth = 1;
        for (int i = 0; i < labels.length; i++) {
            g.gridy = i + 2; g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE; f.add(UI.label(labels[i], 13, true, Color.BLACK), g);
            g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; f.add(comps[i], g);
        }
        Panel bp = new Panel(new GridLayout(1, btns.length, 10, 0));
        for (Button b : btns) bp.add(b);
        g.gridy = labels.length + 2; g.gridx = 1; g.fill = GridBagConstraints.NONE; g.insets = new Insets(16, 6, 4, 6); f.add(bp, g);
        Panel card = new Pad(new BorderLayout(), 22, 28);
        card.setBackground(Color.WHITE); card.add(f, BorderLayout.CENTER);
        return card;
    }

    Panel page(Panel card) {
        Panel p = new Pad(new FlowLayout(FlowLayout.LEFT, 0, 0), 20, 22);
        p.setBackground(UI.PAGE_BG); p.add(card);
        return p;
    }

    // ---------- small helpers ----------
    static TextField field(boolean password) { TextField t = new TextField(26); if (password) t.setEchoChar('*'); return t; }
    // Wide columns (name 32, username 48) so section/role sits well to the right of the long email usernames.
    static String rowText(String type, String name, String user, String detail) { return String.format("%-10s %-32s %-48s %s", type, name, user, detail); }
    Button clearBtn(TextField... fs) {
        Button b = new Button("Clear");
        b.addActionListener(e -> { for (TextField f : fs) f.setText(""); fs[0].requestFocus(); });
        return b;
    }
    void say(String msg, boolean ok) { status.setForeground(ok ? UI.GREEN : UI.RED); status.setText("   " + msg); }

    void showPage(int i) {
        cards.show(content, "" + i);
        for (int j = 0; j < navBtns.length; j++) navBtns[j].setBackground(j == i ? UI.NAV_ACTIVE : UI.NAV_IDLE);
        say("Viewing: " + pageNames[i], true);
    }

    // Validates a new-user form; shows the reason in the status bar when something is wrong.
    boolean valid(TextField n, TextField u, TextField p, int sectionCount) {
        if (n.getText().trim().isEmpty() || u.getText().trim().isEmpty() || p.getText().trim().isEmpty()) { say("Please fill in name, username and password.", false); return false; }
        if (DataStore.usernameTaken(u.getText().trim())) { say("Username '" + u.getText().trim() + "' is already taken. Choose another.", false); return false; }
        if (sectionCount == 0) { say("Create a section first (Sections page).", false); return false; }
        return true;
    }

    void afterChange() {
        refreshUsers();
        statValues[0].setText("" + DataStore.students.size()); statValues[1].setText("" + DataStore.teachers.size());
        statValues[2].setText("" + DataStore.staffList.size()); statValues[3].setText("" + DataStore.sections.size());
        sSection.removeAll(); tSection.removeAll(); sectionList.removeAll();
        for (String s : DataStore.sections) {
            sSection.add(s); tSection.add(s);
            sectionList.add(String.format("%-14s %2d student(s), %d teacher(s)", s, DataStore.studentsInSection(s).size(), DataStore.teachersOfSection(s).size()));
        }
    }

    void refreshUsers() {
        shown.clear(); userList.removeAll();
        int t = typeFilter.getSelectedIndex();
        String q = searchField.getText().trim().toLowerCase();
        if (t == 0 || t == 1) for (Student s : DataStore.students) addRow(s, rowText("Student", s.name, s.username, s.section), q);
        if (t == 0 || t == 2) for (Teacher x : DataStore.teachers) addRow(x, rowText("Teacher", x.name, x.username, "Teaches " + String.join(", ", x.sections)), q);
        if (t == 0 || t == 3) for (Staff f : DataStore.staffList) addRow(f, rowText("Staff", f.name, f.username, f.staffRole), q);
    }

    void addRow(Object user, String row, String q) {
        if (q.isEmpty() || row.toLowerCase().contains(q)) { userList.add(row); shown.add(user); }
    }

    // Saves a new student/staff user to MySQL, then clears the form. okMsg is built by the caller before the form is cleared.
    void add(String role, TextField n, TextField u, TextField p, String section, String staffRole, String okMsg) {
        if (DataStore.addUser(n.getText().trim(), u.getText().trim(), p.getText().trim(), role, section, staffRole)) {
            say(okMsg, true); n.setText(""); u.setText(""); p.setText(""); afterChange();
        } else say("Database error: " + DataStore.fail(), false);
    }

    // ---------- actions ----------
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        for (int i = 0; i < navBtns.length; i++) if (src == navBtns[i]) { showPage(i); return; }
        if (src == logoutBtn) { dispose(); new LoginFrame(); }
        else if (src == clearSearchBtn) { searchField.setText(""); typeFilter.select(0); refreshUsers(); }
        else if (src == addStuBtn && valid(sName, sUser, sPass, sSection.getItemCount()))
            add("STUDENT", sName, sUser, sPass, sSection.getSelectedItem(), null, "Student '" + sName.getText().trim() + "' added to " + sSection.getSelectedItem() + ".");
        else if (src == addTeaBtn && valid(tName, tUser, tPass, tSection.getItemCount())) {
            String[] secs = tSection.getSelectedItems();
            String nm = tName.getText().trim();
            if (secs.length == 0) say("Select at least one section (Ctrl+click for many).", false);
            else if (DataStore.addTeacher(nm, tUser.getText().trim(), tPass.getText().trim(), secs)) {
                say("Teacher '" + nm + "' added for " + String.join(", ", secs) + ".", true);
                tName.setText(""); tUser.setText(""); tPass.setText(""); afterChange();
            } else say("Database error: " + DataStore.fail(), false);
        }
        else if (src == addStaffBtn && valid(fName, fUser, fPass, fRole.getItemCount())) {
            String r = fRole.getSelectedItem();
            if (r.equals("Deputy HOD")) r += " (" + fType.getSelectedItem() + ")";   // e.g. "Deputy HOD (Academic)"
            if (r.contains("HOD")) r += " - " + fDept.getSelectedItem();            // e.g. "... - CSE"
            add("STAFF", fName, fUser, fPass, null, r, r + " '" + fName.getText().trim() + "' added.");
        }
        else if (src == addSecBtn) {
            String s = secField.getText().trim();
            if (s.isEmpty()) say("Please type a section name.", false);
            else if (DataStore.sections.contains(s)) say("Section '" + s + "' already exists.", false);
            else if (DataStore.addSection(s)) { secField.setText(""); say("Section '" + s + "' created.", true); afterChange(); }
            else say("Database error: " + DataStore.fail(), false);
        } else if (src == deleteBtn) {
            int idx = userList.getSelectedIndex();
            if (idx < 0) { say("Select a user in the list first.", false); return; }
            Object u = shown.get(idx);
            String name = u instanceof Student ? ((Student) u).name : u instanceof Teacher ? ((Teacher) u).name : ((Staff) u).name;
            String uname = u instanceof Student ? ((Student) u).username : u instanceof Teacher ? ((Teacher) u).username : ((Staff) u).username;
            if (!UI.confirm(this, "Delete '" + name + "' and their messages? This cannot be undone.")) return;
            if (DataStore.deleteUser(uname)) { say("User '" + name + "' deleted.", true); afterChange(); }
            else say("Database error: " + DataStore.fail(), false);
        }
    }
}

// Common screen for Student / Teacher / Staff: header, conversation view, compose box, status bar.
// Subclasses build the left sidebar and call init(sidebar) as the last step of their constructor.
abstract class ChatFrame extends Frame implements ActionListener, ItemListener {
    final String myUser, myDisplay;
    String currentPeer = null, currentLabel = "";
    boolean broadcast = false;
    final Label convoHeader = UI.label("Select a person on the left to start chatting", 16, true, UI.NAVY);
    final Label composeLabel = UI.label("Message", 12, true, UI.NAV_ACTIVE);
    final Label status = UI.label(" ", 12, true, UI.GREEN);
    final List convoList = new List(14);
    final ArrayList<Message> convoMsgs = new ArrayList<>();
    final ArrayList<List> contactLists = new ArrayList<>();
    final TextArea body = new TextArea(4, 30);
    final TextField searchField = new TextField(18);
    final Button sendBtn = UI.styled(new Button("Send Message"), UI.NAV_ACTIVE), editBtn = UI.styled(new Button("Edit my message"), UI.NAV_IDLE),
            deleteBtn = UI.styled(new Button("Delete my message"), UI.RED), logoutBtn = UI.styled(new Button("Logout"), UI.RED),
            refreshBtn = UI.styled(new Button("Refresh"), UI.GREEN);

    ChatFrame(String title, String myUser, String myDisplay, String welcome) {
        super(title);
        this.myUser = myUser; this.myDisplay = myDisplay;
        setLayout(new BorderLayout()); setBackground(UI.PAGE_BG);
        Panel header = new Pad(new BorderLayout(), 12, 20);
        header.setBackground(UI.NAVY);
        header.add(UI.label(welcome, 18, true, Color.WHITE), BorderLayout.WEST);
        header.add(logoutBtn, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        convoList.setFont(new Font("Monospaced", Font.PLAIN, 13)); convoList.setBackground(Color.WHITE);
        Panel top = new Pad(new BorderLayout(), 10, 14);
        top.setBackground(Color.WHITE); top.add(convoHeader, BorderLayout.CENTER);
        Panel actions = new Panel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actions.add(refreshBtn); actions.add(editBtn); actions.add(deleteBtn);
        actions.add(UI.label("Select your own message to edit or delete it.", 12, false, Color.GRAY));
        body.setBackground(Color.WHITE);
        Panel compose = new Panel(new BorderLayout(0, 4));
        compose.add(composeLabel, BorderLayout.NORTH); compose.add(body, BorderLayout.CENTER); compose.add(sendBtn, BorderLayout.SOUTH);
        Panel south = new Panel(new BorderLayout(0, 4));
        south.add(actions, BorderLayout.NORTH); south.add(compose, BorderLayout.CENTER);
        Panel center = new Pad(new BorderLayout(0, 8), 14, 14);
        center.setBackground(UI.PAGE_BG);
        center.add(top, BorderLayout.NORTH); center.add(convoList, BorderLayout.CENTER); center.add(south, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        for (Button b : new Button[]{sendBtn, editBtn, deleteBtn, logoutBtn, refreshBtn}) b.addActionListener(this);
        searchField.addTextListener(e -> refreshLists());
        sendBtn.setEnabled(false);
        addWindowListener(UI.exitOnClose());
    }

    void init(Panel side) {
        Panel wrap = new Pad(new BorderLayout(), 14, 14) {
            public Dimension getPreferredSize() { return new Dimension(350, super.getPreferredSize().height); }
        };
        wrap.setBackground(UI.PAGE_BG); wrap.add(side, BorderLayout.CENTER);
        add(wrap, BorderLayout.WEST);
        setSize(1150, 720); setMinimumSize(new Dimension(1000, 620)); setLocationRelativeTo(null); setVisible(true);
    }

    abstract void refreshLists();
    void extra(Object src) {}

    Panel searchRow() {
        Panel p = new Panel(new BorderLayout(6, 0));
        p.add(UI.label("Search", 13, true, Color.BLACK), BorderLayout.WEST); p.add(searchField, BorderLayout.CENTER);
        return p;
    }
    void listen(List... ls) { for (List l : ls) { l.addItemListener(this); contactLists.add(l); } }
    void say(String m, boolean ok) { status.setForeground(ok ? UI.GREEN : UI.RED); status.setText("   " + m); }
    // Shows a database error in the status bar when a DataStore call failed; passes the result through.
    boolean ok(boolean result) { if (!result) say("Database error: " + DataStore.fail(), false); return result; }

    // Shows msgs in the list, word-wrapped; every row of a message points to that message (for edit/delete).
    void fill(ArrayList<Message> msgs, String emptyHint) {
        convoList.removeAll(); convoMsgs.clear();
        if (DataStore.err != null) say("Database error: " + DataStore.err, false);
        if (msgs.isEmpty()) { convoList.add(emptyHint); convoMsgs.add(null); }
        for (Message m : msgs) {
            String head = "[" + m.time.substring(0, 16) + "] " + (m.fromUser.equals(myUser) ? "You" : m.fromDisplay) + ": ";
            String indent = String.format("%" + head.length() + "s", "");
            StringBuilder row = new StringBuilder(head);
            for (String w : (m.body + (m.edited ? " (edited)" : "")).replace("\n", " ").split(" ")) {
                if (row.length() + w.length() > 95 && row.length() > indent.length()) { convoList.add(row.toString()); convoMsgs.add(m); row = new StringBuilder(indent); }
                row.append(w).append(' ');
            }
            convoList.add(row.toString()); convoMsgs.add(m);
        }
        convoList.makeVisible(convoList.getItemCount() - 1);
    }

    void openConversation(String user, String label) {
        broadcast = false; currentPeer = user; currentLabel = label;
        convoHeader.setText("Chat with " + label);
        composeLabel.setText("Your message to " + label + ":");
        sendBtn.setEnabled(true);
        fill(DataStore.conversationBetween(myUser, user), "No messages yet. Type below and click Send to say hello.");
        body.requestFocus();
    }

    void openBroadcast(String target, String label) {
        broadcast = true; currentPeer = target; currentLabel = label;
        convoHeader.setText("Announcement to " + label);
        composeLabel.setText("Your announcement will reach everyone in " + label + ":");
        sendBtn.setEnabled(true);
        fill(DataStore.sentTo(myUser, target), "You have not sent any announcement here yet.");
        body.requestFocus();
    }

    void refreshOpen() {
        if (currentPeer == null) return;
        if (broadcast) openBroadcast(currentPeer, currentLabel); else openConversation(currentPeer, currentLabel);
    }

    Message selectedOwn(String verb) {
        int i = convoList.getSelectedIndex();
        Message m = i < 0 ? null : convoMsgs.get(i);
        if (m == null) { say("Select one of your messages in the list first.", false); return null; }
        if (!m.fromUser.equals(myUser)) { say("You can only " + verb + " messages that you sent.", false); return null; }
        return m;
    }

    public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() != ItemEvent.SELECTED) return;
        List l = (List) e.getSource();
        String sel = l.getItem((Integer) e.getItem());
        String user = DataStore.userOfLabel(sel);
        if (user == null) return;
        for (List o : contactLists) if (o != l && o.getSelectedIndex() >= 0) o.deselect(o.getSelectedIndex());
        openConversation(user, sel);
    }

    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        if (src == logoutBtn) { dispose(); new LoginFrame(); }
        else if (src == refreshBtn) {
            if (currentPeer == null || !sendBtn.isEnabled()) say("Open a chat first, then click Refresh.", false);
            else { refreshOpen(); say("Conversation refreshed.", true); }
        } else if (src == sendBtn) {
            String text = body.getText().trim();
            if (currentPeer == null) say("Pick someone from the left first.", false);
            else if (text.isEmpty()) say("Please type a message first.", false);
            else if (ok(DataStore.send(myUser, currentPeer, text))) { body.setText(""); refreshOpen(); say(broadcast ? "Announcement sent." : "Message sent.", true); }
        } else if (src == editBtn) {
            Message m = selectedOwn("edit");
            if (m == null) return;
            String t = UI.promptEdit(this, m.body);
            if (t != null && !t.isEmpty() && ok(DataStore.editMessage(m.id, t))) { refreshOpen(); say("Message updated.", true); }
        } else if (src == deleteBtn) {
            Message m = selectedOwn("delete");
            if (m != null && UI.confirm(this, "Delete this message? This cannot be undone.") && ok(DataStore.deleteMessage(m.id))) { refreshOpen(); say("Message deleted.", true); }
        } else extra(src);
    }
}

class StudentDashboard extends ChatFrame {
    final Student me;
    final List classmates = new List(6), teachers = new List(6), mgmt = new List(6);
    final ArrayList<String> classSrc = new ArrayList<>(), teacherSrc = new ArrayList<>(), mgmtSrc = new ArrayList<>();
    final Button announceBtn = UI.styled(new Button("View Class Announcements"), UI.NAV_ACTIVE);

    StudentDashboard(Student s) {
        super("Student Dashboard - " + s.name, s.username, s.name, "Welcome, " + s.name + "   |   " + s.section);
        me = s;
        for (Student c : DataStore.studentsInSection(s.section)) if (!c.username.equals(s.username)) classSrc.add(DataStore.label(c));
        for (Teacher t : DataStore.teachersOfSection(s.section)) teacherSrc.add(DataStore.label(t));
        for (Staff f : DataStore.staffList) mgmtSrc.add(DataStore.label(f));
        Panel top = new Panel(new GridLayout(0, 1, 0, 8));
        top.add(announceBtn); top.add(searchRow());
        Panel lists = new Panel(new GridLayout(3, 1, 0, 8));
        lists.add(UI.labeledList("Classmates (click to chat)", classmates));
        lists.add(UI.labeledList("My Teachers (click to chat)", teachers));
        lists.add(UI.labeledList("College Management (click to chat)", mgmt));
        Panel side = new Panel(new BorderLayout(0, 10));
        side.add(top, BorderLayout.NORTH); side.add(lists, BorderLayout.CENTER);
        listen(classmates, teachers, mgmt);
        announceBtn.addActionListener(this);
        init(side); refreshLists();
    }
    void refreshLists() {
        String q = searchField.getText().trim().toLowerCase();
        UI.fill(classmates, classSrc, q); UI.fill(teachers, teacherSrc, q); UI.fill(mgmt, mgmtSrc, q);
    }
    void extra(Object src) {
        if (src != announceBtn) return;
        currentPeer = null;
        convoHeader.setText("Class Announcements - " + me.section);
        composeLabel.setText("Announcements are read-only. Pick a person on the left to chat.");
        sendBtn.setEnabled(false);
        for (List l : contactLists) if (l.getSelectedIndex() >= 0) l.deselect(l.getSelectedIndex());
        fill(DataStore.sectionAnnouncements(me.section), "No announcements yet.");
    }
}

// Shared by Teacher and Staff: pick one of your sections, chat 1-to-1, announce to a section, or tick many students for a bulk message.
// Teachers see only the sections they teach; management staff see every section.
class RosterFrame extends ChatFrame {
    final boolean management;
    final Choice sectionChoice = new Choice();
    final List roster = new List(8), contacts = new List(6);
    final ArrayList<Student> sectionStudents = new ArrayList<>();
    final ArrayList<String> rosterSrc = new ArrayList<>(), contactSrc = new ArrayList<>(), ticked = new ArrayList<>();
    final ArrayList<Checkbox> boxes = new ArrayList<>();
    final CardLayout cl = new CardLayout();
    final Panel tabs = new Panel(cl), boxPanel = new Panel(new GridLayout(0, 1));
    final ScrollPane scroll = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
    final Label tickLabel = UI.label("Selected: 0", 13, true, UI.NAVY);
    final Button announceBtn = UI.styled(new Button("Announce"), UI.NAV_ACTIVE),
            allStudentsBtn = UI.styled(new Button("Message ALL Students (every section)"), new Color(124, 58, 237)),
            tabChat = UI.styled(new Button("1-to-1 Chat"), UI.NAV_ACTIVE), tabBulk = UI.styled(new Button("Bulk Message"), UI.NAV_IDLE),
            allBtn = new Button("Tick all shown"), noneBtn = new Button("Clear ticks"),
            bulkSendBtn = UI.styled(new Button("Send to ticked students"), UI.GREEN);

    RosterFrame(String title, String user, String display, String welcome, ArrayList<String> mySections, boolean management, String contactsTitle, ArrayList<String> contactLabels) {
        super(title, user, display, welcome);
        this.management = management; contactSrc.addAll(contactLabels);
        for (String s : mySections) sectionChoice.add(s);
        Panel secRow = new Panel(new BorderLayout(6, 0));
        secRow.add(UI.label("Section", 13, true, Color.BLACK), BorderLayout.WEST); secRow.add(sectionChoice, BorderLayout.CENTER);
        Panel tabRow = new Panel(new GridLayout(1, 2, 6, 0));
        tabRow.add(tabChat); tabRow.add(tabBulk);
        Panel top = new Panel(new GridLayout(0, 1, 0, 6));
        top.add(secRow); top.add(searchRow()); top.add(announceBtn);
        if (management) top.add(allStudentsBtn);
        top.add(tabRow);

        Panel chat = new Panel(new GridLayout(2, 1, 0, 8));
        chat.add(UI.labeledList("Students (click a name to chat)", roster));
        chat.add(UI.labeledList(contactsTitle + " (click to chat)", contacts));
        Panel tickRow = new Panel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        tickRow.add(allBtn); tickRow.add(noneBtn); tickRow.add(tickLabel);
        Panel holder = new Panel(new BorderLayout());
        holder.add(boxPanel, BorderLayout.NORTH);
        scroll.add(holder);
        Panel bulk = new Panel(new BorderLayout(0, 6));
        bulk.add(tickRow, BorderLayout.NORTH); bulk.add(scroll, BorderLayout.CENTER); bulk.add(bulkSendBtn, BorderLayout.SOUTH);
        tabs.add(chat, "chat"); tabs.add(bulk, "bulk");
        Panel side = new Panel(new BorderLayout(0, 10));
        side.add(top, BorderLayout.NORTH); side.add(tabs, BorderLayout.CENTER);

        listen(roster, contacts);
        for (Button b : new Button[]{announceBtn, allStudentsBtn, tabChat, tabBulk, allBtn, noneBtn, bulkSendBtn}) b.addActionListener(this);
        sectionChoice.addItemListener(e -> loadSection());
        init(side); loadSection(); showTab(false);
    }

    static ArrayList<String> staffLabels(String skip) {
        ArrayList<String> l = new ArrayList<>();
        for (Staff f : DataStore.staffList) if (!f.username.equals(skip)) l.add(DataStore.label(f));
        return l;
    }
    static ArrayList<String> colleagueLabels(String skip) {
        ArrayList<String> l = new ArrayList<>();
        for (Teacher t : DataStore.teachers) l.add(DataStore.label(t));
        l.addAll(staffLabels(skip));
        return l;
    }

    void loadSection() {
        String sec = sectionChoice.getItemCount() == 0 ? "" : sectionChoice.getSelectedItem();
        sectionStudents.clear(); rosterSrc.clear();
        if (!sec.isEmpty())
            for (Student s : DataStore.studentsInSection(sec)) { sectionStudents.add(s); rosterSrc.add(DataStore.label(s)); }
        announceBtn.setLabel("Announce to " + sec);
        refreshLists();
    }

    void refreshLists() {
        String q = searchField.getText().trim().toLowerCase();
        UI.fill(roster, rosterSrc, q); UI.fill(contacts, contactSrc, q);
        boxPanel.removeAll(); boxes.clear();
        for (Student s : sectionStudents) {
            if (!q.isEmpty() && !DataStore.label(s).toLowerCase().contains(q)) continue;
            Checkbox cb = new Checkbox(DataStore.label(s), ticked.contains(s.username));
            cb.addItemListener(e -> {
                if (!cb.getState()) ticked.remove(s.username); else if (!ticked.contains(s.username)) ticked.add(s.username);
                tickLabel.setText("Selected: " + ticked.size());
            });
            Panel row = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 1));
            row.add(cb); boxPanel.add(row); boxes.add(cb);
        }
        tickLabel.setText("Selected: " + ticked.size());
        boxPanel.invalidate(); scroll.validate(); validate();
    }

    void showTab(boolean bulk) {
        cl.show(tabs, bulk ? "bulk" : "chat");
        tabChat.setBackground(bulk ? UI.NAV_IDLE : UI.NAV_ACTIVE); tabBulk.setBackground(bulk ? UI.NAV_ACTIVE : UI.NAV_IDLE);
        if (bulk) {
            sendBtn.setEnabled(false);
            convoHeader.setText("Bulk message");
            fill(new ArrayList<Message>(), "Bulk mode: your message is sent separately to every ticked student.");
            composeLabel.setText("Type one message here, tick students on the left, then click 'Send to ticked students':");
        } else if (currentPeer != null) refreshOpen();
        else { convoHeader.setText("Select a person on the left to start chatting"); composeLabel.setText("Message"); }
    }

    void tickShown(boolean on) {
        for (Checkbox cb : boxes) {
            cb.setState(on);
            String u = DataStore.userOfLabel(cb.getLabel());
            if (on && !ticked.contains(u)) ticked.add(u); else if (!on) ticked.remove(u);
        }
        if (!on) ticked.clear();
        tickLabel.setText("Selected: " + ticked.size());
        if (!on) refreshLists();
    }

    void extra(Object src) {
        if (src == tabChat) showTab(false);
        else if (src == tabBulk) showTab(true);
        else if (src == allBtn) tickShown(true);
        else if (src == noneBtn) tickShown(false);
        else if (src == announceBtn) {
            String sec = sectionChoice.getSelectedItem();
            if (sec == null) { say("No section assigned.", false); return; }
            showTab(false); openBroadcast("SECTION:" + sec, sec);
        }
        else if (src == allStudentsBtn) { showTab(false); openBroadcast("ALL_STUDENTS", "All Students (every section)"); }
        else if (src == bulkSendBtn) {
            String text = body.getText().trim();
            if (ticked.isEmpty()) say("Tick at least one student first.", false);
            else if (text.isEmpty()) say("Please type your message in the box on the right first.", false);
            else {
                int n = 0;
                for (String u : ticked) if (DataStore.send(myUser, u, text)) n++;
                if (n == ticked.size()) { say("Message sent to " + n + " student(s).", true); ticked.clear(); body.setText(""); refreshLists(); }
                else say("Only " + n + " of " + ticked.size() + " sent. Database error: " + DataStore.fail(), false);
            }
        }
    }
}

class TeacherDashboard extends RosterFrame {
    TeacherDashboard(Teacher t) {
        super("Teacher Dashboard - " + t.name, t.username, t.name, "Welcome, " + t.name + "   |   Teaches " + String.join(", ", t.sections),
                t.sections, false, "School Management", staffLabels(null));
    }
}

class StaffDashboard extends RosterFrame {
    StaffDashboard(Staff f) {
        super("Staff Dashboard - " + f.name, f.username, f.name + " (" + f.staffRole + ")", "Welcome, " + f.name + "   |   " + f.staffRole,
                DataStore.sections, true, "Colleagues", colleagueLabels(f.username));
    }
}
