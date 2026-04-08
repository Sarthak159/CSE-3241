import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Scanner;

public class WarehouseApp {

    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";

    public static void main(String[] args) {
        try {
            initializeDatabase();
            System.out.println("Connected to SQLite database successfully: " + getDatabasePath());
        } catch (SQLException e) {
            System.out.println("Unable to initialize database: " + e.getMessage());
            return;
        }

        try (Scanner in = new Scanner(System.in)) {
            boolean running = true;
            while (running) {
                printMainMenu();
                int choice = readInt(in, "Choose an option: ");

                switch (choice) {
                    case 1 -> customerMenu(in);
                    case 2 -> robotMenu(in);
                    case 3 -> rentRobot(in);
                    case 4 -> returnEquipment(in);
                    case 5 -> deliverRobot(in);
                    case 6 -> pickupRobot(in);
                    case 7 -> {
                        running = false;
                        System.out.println("Exiting program. Goodbye.");
                    }
                    default -> System.out.println("Invalid option. Try again.");
                }
            }
        }
    }

    private static void initializeDatabase() throws SQLException {
        try {
            Class.forName(SQLITE_DRIVER);
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                    "SQLite JDBC driver not found. Run the app with the sqlite-jdbc jar on the classpath.",
                    e
            );
        }

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS CUSTOMER (" +
                            "customer_id INTEGER PRIMARY KEY, " +
                            "name TEXT NOT NULL, " +
                            "phone TEXT NOT NULL" +
                            ")"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS ROBOT (" +
                            "robot_id INTEGER PRIMARY KEY, " +
                            "model TEXT NOT NULL, " +
                            "status TEXT NOT NULL" +
                            ")"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS RENTAL (" +
                            "rental_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "customer_id INTEGER NOT NULL, " +
                            "robot_id INTEGER NOT NULL, " +
                            "rental_date TEXT NOT NULL, " +
                            "expected_return_date TEXT, " +
                            "FOREIGN KEY (customer_id) REFERENCES CUSTOMER(customer_id), " +
                            "FOREIGN KEY (robot_id) REFERENCES ROBOT(robot_id)" +
                            ")"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS EQUIPMENT_RETURN (" +
                            "return_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "rental_id INTEGER NOT NULL, " +
                            "customer_id INTEGER NOT NULL, " +
                            "robot_id INTEGER NOT NULL, " +
                            "return_date TEXT NOT NULL, " +
                            "FOREIGN KEY (rental_id) REFERENCES RENTAL(rental_id), " +
                            "FOREIGN KEY (customer_id) REFERENCES CUSTOMER(customer_id), " +
                            "FOREIGN KEY (robot_id) REFERENCES ROBOT(robot_id)" +
                            ")"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS DELIVERY (" +
                            "delivery_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "customer_id INTEGER NOT NULL, " +
                            "robot_id INTEGER, " +
                            "rental_id INTEGER, " +
                            "driverless_car_id TEXT NOT NULL, " +
                            "delivery_date TEXT NOT NULL, " +
                            "destination TEXT NOT NULL, " +
                            "CHECK (robot_id IS NOT NULL OR rental_id IS NOT NULL), " +
                            "FOREIGN KEY (customer_id) REFERENCES CUSTOMER(customer_id), " +
                            "FOREIGN KEY (robot_id) REFERENCES ROBOT(robot_id), " +
                            "FOREIGN KEY (rental_id) REFERENCES RENTAL(rental_id)" +
                            ")"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS PICKUP (" +
                            "pickup_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "customer_id INTEGER NOT NULL, " +
                            "robot_id INTEGER, " +
                            "rental_id INTEGER, " +
                            "driverless_car_id TEXT NOT NULL, " +
                            "pickup_date TEXT NOT NULL, " +
                            "pickup_address TEXT NOT NULL, " +
                            "CHECK (robot_id IS NOT NULL OR rental_id IS NOT NULL), " +
                            "FOREIGN KEY (customer_id) REFERENCES CUSTOMER(customer_id), " +
                            "FOREIGN KEY (robot_id) REFERENCES ROBOT(robot_id), " +
                            "FOREIGN KEY (rental_id) REFERENCES RENTAL(rental_id)" +
                            ")"
            );
        }
    }

    private static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDatabasePath());
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    private static Path getDatabasePath() {
        return Paths.get("Warehouse.db").toAbsolutePath().normalize();
    }

    // ---------------- MAIN MENU ----------------

    private static void printMainMenu() {
        System.out.println("\n=== Warehouse Robot Management System ===");
        System.out.println("1) Manage Customers");
        System.out.println("2) Manage Robots");
        System.out.println("3) Rent Robots");
        System.out.println("4) Return Equipment");
        System.out.println("5) Delivery of Robots");
        System.out.println("6) Pickup of Robots");
        System.out.println("7) Exit");
    }

    // ---------------- CUSTOMER MENU ----------------

    private static void customerMenu(Scanner in) {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Customer Menu ---");
            System.out.println("1) Add Customer");
            System.out.println("2) Edit Customer");
            System.out.println("3) Delete Customer");
            System.out.println("4) Search Customer");
            System.out.println("5) List All Customers");
            System.out.println("6) Back");

            int choice = readInt(in, "Choose an option: ");

            switch (choice) {
                case 1 -> addCustomer(in);
                case 2 -> editCustomer(in);
                case 3 -> deleteCustomer(in);
                case 4 -> searchCustomer(in);
                case 5 -> listCustomers();
                case 6 -> back = true;
                default -> System.out.println("Invalid option. Try again.");
            }
        }
    }

    private static void addCustomer(Scanner in) {
        int id = readInt(in, "Customer ID: ");
        String name = readLine(in, "Name: ");
        String phone = readLine(in, "Phone: ");

        try {
            if (findCustomerById(id) != null) {
                System.out.println("A customer with that ID already exists.");
                return;
            }

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO CUSTOMER (customer_id, name, phone) VALUES (?, ?, ?)"
                 )) {
                stmt.setInt(1, id);
                stmt.setString(2, name);
                stmt.setString(3, phone);
                stmt.executeUpdate();
            }

            System.out.println("Customer added.");
        } catch (SQLException e) {
            printDatabaseError("add customer", e);
        }
    }

    private static void editCustomer(Scanner in) {
        int id = readInt(in, "Enter Customer ID to edit: ");

        try {
            Customer customer = findCustomerById(id);
            if (customer == null) {
                System.out.println("Customer not found.");
                return;
            }

            System.out.println("Editing customer: " + customer);
            String newName = readLine(in, "New name (leave blank to keep current): ");
            String newPhone = readLine(in, "New phone (leave blank to keep current): ");

            String updatedName = newName.trim().isEmpty() ? customer.getName() : newName;
            String updatedPhone = newPhone.trim().isEmpty() ? customer.getPhone() : newPhone;

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE CUSTOMER SET name = ?, phone = ? WHERE customer_id = ?"
                 )) {
                stmt.setString(1, updatedName);
                stmt.setString(2, updatedPhone);
                stmt.setInt(3, id);
                stmt.executeUpdate();
            }

            System.out.println("Customer updated: " + new Customer(id, updatedName, updatedPhone));
        } catch (SQLException e) {
            printDatabaseError("edit customer", e);
        }
    }

    private static void deleteCustomer(Scanner in) {
        int id = readInt(in, "Enter Customer ID to delete: ");

        try {
            Customer customer = findCustomerById(id);
            if (customer == null) {
                System.out.println("Customer not found.");
                return;
            }

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM CUSTOMER WHERE customer_id = ?"
                 )) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            }

            System.out.println("Customer deleted.");
        } catch (SQLException e) {
            printDatabaseError("delete customer", e);
        }
    }

    private static void searchCustomer(Scanner in) {
        System.out.println("Search by:");
        System.out.println("1) Customer ID");
        System.out.println("2) Name contains");
        int choice = readInt(in, "Choose: ");

        switch (choice) {
            case 1 -> {
                int id = readInt(in, "Customer ID: ");
                try {
                    Customer customer = findCustomerById(id);
                    if (customer == null) {
                        System.out.println("No customer found.");
                    } else {
                        System.out.println("Found: " + customer);
                    }
                } catch (SQLException e) {
                    printDatabaseError("search customer", e);
                }
            }
            case 2 -> {
                String key = readLine(in, "Name keyword: ").trim().toLowerCase();
                try (Connection conn = getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                             "SELECT customer_id, name, phone FROM CUSTOMER " +
                                     "WHERE LOWER(name) LIKE ? ORDER BY customer_id"
                     )) {
                    stmt.setString(1, "%" + key + "%");

                    boolean foundAny = false;
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            System.out.println("Found: " + mapCustomer(rs));
                            foundAny = true;
                        }
                    }

                    if (!foundAny) {
                        System.out.println("No customers matched.");
                    }
                } catch (SQLException e) {
                    printDatabaseError("search customer", e);
                }
            }
            default -> System.out.println("Invalid choice.");
        }
    }

    private static void listCustomers() {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT customer_id, name, phone FROM CUSTOMER ORDER BY customer_id"
             );
             ResultSet rs = stmt.executeQuery()) {
            boolean foundAny = false;
            while (rs.next()) {
                if (!foundAny) {
                    System.out.println("\nCustomers:");
                    foundAny = true;
                }
                System.out.println(" - " + mapCustomer(rs));
            }

            if (!foundAny) {
                System.out.println("No customers in system.");
            }
        } catch (SQLException e) {
            printDatabaseError("list customers", e);
        }
    }

    private static Customer findCustomerById(int id) throws SQLException {
        try (Connection conn = getConnection()) {
            return findCustomerById(conn, id);
        }
    }

    private static Customer findCustomerById(Connection conn, int id) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT customer_id, name, phone FROM CUSTOMER WHERE customer_id = ?"
        )) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapCustomer(rs);
                }
            }
        }
        return null;
    }

    // ---------------- ROBOT MENU ----------------

    private static void robotMenu(Scanner in) {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Robot Menu ---");
            System.out.println("1) Add Robot");
            System.out.println("2) Edit Robot");
            System.out.println("3) Delete Robot");
            System.out.println("4) Search Robot");
            System.out.println("5) List All Robots");
            System.out.println("6) Back");

            int choice = readInt(in, "Choose an option: ");

            switch (choice) {
                case 1 -> addRobot(in);
                case 2 -> editRobot(in);
                case 3 -> deleteRobot(in);
                case 4 -> searchRobot(in);
                case 5 -> listRobots();
                case 6 -> back = true;
                default -> System.out.println("Invalid option. Try again.");
            }
        }
    }

    private static void addRobot(Scanner in) {
        int id = readInt(in, "Robot ID: ");
        String model = readLine(in, "Model: ");
        String status = readRobotStatus(in, "Status (AVAILABLE/RENTED/MAINTENANCE): ", false);

        try {
            if (findRobotById(id) != null) {
                System.out.println("A robot with that ID already exists.");
                return;
            }

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO ROBOT (robot_id, model, status) VALUES (?, ?, ?)"
                 )) {
                stmt.setInt(1, id);
                stmt.setString(2, model);
                stmt.setString(3, status);
                stmt.executeUpdate();
            }

            System.out.println("Robot added.");
        } catch (SQLException e) {
            printDatabaseError("add robot", e);
        }
    }

    private static void editRobot(Scanner in) {
        int id = readInt(in, "Enter Robot ID to edit: ");

        try {
            Robot robot = findRobotById(id);
            if (robot == null) {
                System.out.println("Robot not found.");
                return;
            }

            System.out.println("Editing robot: " + robot);
            String newModel = readLine(in, "New model (leave blank to keep current): ");
            String newStatus = readRobotStatus(in, "New status (leave blank to keep current): ", true);

            String updatedModel = newModel.trim().isEmpty() ? robot.getModel() : newModel;
            String updatedStatus = newStatus.trim().isEmpty() ? robot.getStatus() : newStatus;

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE ROBOT SET model = ?, status = ? WHERE robot_id = ?"
                 )) {
                stmt.setString(1, updatedModel);
                stmt.setString(2, updatedStatus);
                stmt.setInt(3, id);
                stmt.executeUpdate();
            }

            System.out.println("Robot updated: " + new Robot(id, updatedModel, updatedStatus));
        } catch (SQLException e) {
            printDatabaseError("edit robot", e);
        }
    }

    private static void deleteRobot(Scanner in) {
        int id = readInt(in, "Enter Robot ID to delete: ");

        try {
            Robot robot = findRobotById(id);
            if (robot == null) {
                System.out.println("Robot not found.");
                return;
            }

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM ROBOT WHERE robot_id = ?"
                 )) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            }

            System.out.println("Robot deleted.");
        } catch (SQLException e) {
            printDatabaseError("delete robot", e);
        }
    }

    private static void searchRobot(Scanner in) {
        System.out.println("Search by:");
        System.out.println("1) Robot ID");
        System.out.println("2) Model contains");
        int choice = readInt(in, "Choose: ");

        switch (choice) {
            case 1 -> {
                int id = readInt(in, "Robot ID: ");
                try {
                    Robot robot = findRobotById(id);
                    if (robot == null) {
                        System.out.println("No robot found.");
                    } else {
                        System.out.println("Found: " + robot);
                    }
                } catch (SQLException e) {
                    printDatabaseError("search robot", e);
                }
            }
            case 2 -> {
                String key = readLine(in, "Model keyword: ").trim().toLowerCase();
                try (Connection conn = getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                             "SELECT robot_id, model, status FROM ROBOT " +
                                     "WHERE LOWER(model) LIKE ? ORDER BY robot_id"
                     )) {
                    stmt.setString(1, "%" + key + "%");

                    boolean foundAny = false;
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            System.out.println("Found: " + mapRobot(rs));
                            foundAny = true;
                        }
                    }

                    if (!foundAny) {
                        System.out.println("No robots matched.");
                    }
                } catch (SQLException e) {
                    printDatabaseError("search robot", e);
                }
            }
            default -> System.out.println("Invalid choice.");
        }
    }

    private static void listRobots() {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT robot_id, model, status FROM ROBOT ORDER BY robot_id"
             );
             ResultSet rs = stmt.executeQuery()) {
            boolean foundAny = false;
            while (rs.next()) {
                if (!foundAny) {
                    System.out.println("\nRobots:");
                    foundAny = true;
                }
                System.out.println(" - " + mapRobot(rs));
            }

            if (!foundAny) {
                System.out.println("No robots in system.");
            }
        } catch (SQLException e) {
            printDatabaseError("list robots", e);
        }
    }

    private static Robot findRobotById(int id) throws SQLException {
        try (Connection conn = getConnection()) {
            return findRobotById(conn, id);
        }
    }

    private static Robot findRobotById(Connection conn, int id) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT robot_id, model, status FROM ROBOT WHERE robot_id = ?"
        )) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRobot(rs);
                }
            }
        }
        return null;
    }

    // ---------------- RENTAL / RETURN / DELIVERY / PICKUP ----------------

    private static void rentRobot(Scanner in) {
        System.out.println("\n--- Rent Robots ---");
        int customerId = readInt(in, "Customer ID: ");
        int robotId = readInt(in, "Robot ID: ");
        String rentalDate = readLine(in, "Rental date (YYYY-MM-DD): ");
        String expectedReturnDate = readLine(in, "Expected return date (YYYY-MM-DD, optional): ").trim();

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                Customer customer = findCustomerById(conn, customerId);
                if (customer == null) {
                    System.out.println("Customer not found.");
                    conn.rollback();
                    return;
                }

                Robot robot = findRobotById(conn, robotId);
                if (robot == null) {
                    System.out.println("Robot not found.");
                    conn.rollback();
                    return;
                }

                if (!"AVAILABLE".equals(robot.getStatus())) {
                    System.out.println("Robot is not available for rental.");
                    conn.rollback();
                    return;
                }

                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO RENTAL (customer_id, robot_id, rental_date, expected_return_date) " +
                                "VALUES (?, ?, ?, ?)"
                )) {
                    stmt.setInt(1, customerId);
                    stmt.setInt(2, robotId);
                    stmt.setString(3, rentalDate);
                    setNullableText(stmt, 4, expectedReturnDate);
                    stmt.executeUpdate();
                }

                updateRobotStatus(conn, robotId, "RENTED");
                conn.commit();
                System.out.println("Rental recorded.");
            } catch (SQLException e) {
                rollbackQuietly(conn);
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            printDatabaseError("record rental", e);
        }
    }

    private static void returnEquipment(Scanner in) {
        System.out.println("\n--- Return Equipment ---");
        int customerId = readInt(in, "Customer ID: ");
        int robotId = readInt(in, "Robot ID: ");
        String returnDate = readLine(in, "Return date (YYYY-MM-DD): ");

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                Customer customer = findCustomerById(conn, customerId);
                if (customer == null) {
                    System.out.println("Customer not found.");
                    conn.rollback();
                    return;
                }

                Robot robot = findRobotById(conn, robotId);
                if (robot == null) {
                    System.out.println("Robot not found.");
                    conn.rollback();
                    return;
                }

                Integer rentalId = findActiveRentalId(conn, customerId, robotId);
                if (rentalId == null) {
                    System.out.println("No active rental found for that customer and robot.");
                    conn.rollback();
                    return;
                }

                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO EQUIPMENT_RETURN (rental_id, customer_id, robot_id, return_date) " +
                                "VALUES (?, ?, ?, ?)"
                )) {
                    stmt.setInt(1, rentalId);
                    stmt.setInt(2, customerId);
                    stmt.setInt(3, robotId);
                    stmt.setString(4, returnDate);
                    stmt.executeUpdate();
                }

                updateRobotStatus(conn, robotId, "AVAILABLE");
                conn.commit();
                System.out.println("Equipment returned.");
            } catch (SQLException e) {
                rollbackQuietly(conn);
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            printDatabaseError("return equipment", e);
        }
    }

    private static void deliverRobot(Scanner in) {
        System.out.println("\n--- Delivery of Robots ---");
        int customerId = readInt(in, "Customer ID: ");
        ReferenceSelection reference = readRobotOrRentalReference(in);
        String driverlessCarId = readLine(in, "Driverless car ID: ");
        String deliveryDate = readLine(in, "Delivery date: ");
        String destination = readLine(in, "Delivery address or destination: ");

        try (Connection conn = getConnection()) {
            Customer customer = findCustomerById(conn, customerId);
            if (customer == null) {
                System.out.println("Customer not found.");
                return;
            }

            ResolvedReference resolvedReference = resolveReference(conn, customerId, reference);
            if (resolvedReference == null) {
                return;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO DELIVERY " +
                            "(customer_id, robot_id, rental_id, driverless_car_id, delivery_date, destination) " +
                            "VALUES (?, ?, ?, ?, ?, ?)"
            )) {
                stmt.setInt(1, customerId);
                setNullableInt(stmt, 2, resolvedReference.getRobotId());
                setNullableInt(stmt, 3, resolvedReference.getRentalId());
                stmt.setString(4, driverlessCarId);
                stmt.setString(5, deliveryDate);
                stmt.setString(6, destination);
                stmt.executeUpdate();
            }

            System.out.println("Robot delivered.");
        } catch (SQLException e) {
            printDatabaseError("record delivery", e);
        }
    }

    private static void pickupRobot(Scanner in) {
        System.out.println("\n--- Pickup of Robots ---");
        int customerId = readInt(in, "Customer ID: ");
        ReferenceSelection reference = readRobotOrRentalReference(in);
        String driverlessCarId = readLine(in, "Driverless car ID: ");
        String pickupDate = readLine(in, "Pickup date: ");
        String pickupAddress = readLine(in, "Pickup address: ");

        try (Connection conn = getConnection()) {
            Customer customer = findCustomerById(conn, customerId);
            if (customer == null) {
                System.out.println("Customer not found.");
                return;
            }

            ResolvedReference resolvedReference = resolveReference(conn, customerId, reference);
            if (resolvedReference == null) {
                return;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO PICKUP " +
                            "(customer_id, robot_id, rental_id, driverless_car_id, pickup_date, pickup_address) " +
                            "VALUES (?, ?, ?, ?, ?, ?)"
            )) {
                stmt.setInt(1, customerId);
                setNullableInt(stmt, 2, resolvedReference.getRobotId());
                setNullableInt(stmt, 3, resolvedReference.getRentalId());
                stmt.setString(4, driverlessCarId);
                stmt.setString(5, pickupDate);
                stmt.setString(6, pickupAddress);
                stmt.executeUpdate();
            }

            System.out.println("Robot picked up.");
        } catch (SQLException e) {
            printDatabaseError("record pickup", e);
        }
    }

    private static ReferenceSelection readRobotOrRentalReference(Scanner in) {
        while (true) {
            System.out.println("Reference by:");
            System.out.println("1) Robot ID");
            System.out.println("2) Rental ID");
            int choice = readInt(in, "Choose: ");

            switch (choice) {
                case 1 -> {
                    return new ReferenceSelection(readInt(in, "Robot ID: "), null);
                }
                case 2 -> {
                    return new ReferenceSelection(null, readInt(in, "Rental ID: "));
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private static ResolvedReference resolveReference(
            Connection conn,
            int customerId,
            ReferenceSelection selection
    ) throws SQLException {
        if (selection.getRobotId() != null) {
            Robot robot = findRobotById(conn, selection.getRobotId());
            if (robot == null) {
                System.out.println("Robot not found.");
                return null;
            }
            return new ResolvedReference(selection.getRobotId(), null);
        }

        Rental rental = findRentalById(conn, selection.getRentalId());
        if (rental == null) {
            System.out.println("Rental not found.");
            return null;
        }
        if (rental.getCustomerId() != customerId) {
            System.out.println("Rental does not belong to that customer.");
            return null;
        }

        return new ResolvedReference(rental.getRobotId(), rental.getRentalId());
    }

    private static Rental findRentalById(Connection conn, int rentalId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT rental_id, customer_id, robot_id, rental_date, expected_return_date " +
                        "FROM RENTAL WHERE rental_id = ?"
        )) {
            stmt.setInt(1, rentalId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRental(rs);
                }
            }
        }
        return null;
    }

    private static Integer findActiveRentalId(Connection conn, int customerId, int robotId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT r.rental_id " +
                        "FROM RENTAL r " +
                        "LEFT JOIN EQUIPMENT_RETURN er ON er.rental_id = r.rental_id " +
                        "WHERE r.customer_id = ? AND r.robot_id = ? AND er.rental_id IS NULL " +
                        "ORDER BY r.rental_id DESC LIMIT 1"
        )) {
            stmt.setInt(1, customerId);
            stmt.setInt(2, robotId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("rental_id");
                }
            }
        }
        return null;
    }

    private static void updateRobotStatus(Connection conn, int robotId, String status) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE ROBOT SET status = ? WHERE robot_id = ?"
        )) {
            stmt.setString(1, status);
            stmt.setInt(2, robotId);
            stmt.executeUpdate();
        }
    }

    private static void setNullableText(PreparedStatement stmt, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            stmt.setNull(index, Types.VARCHAR);
        } else {
            stmt.setString(index, value);
        }
    }

    private static void setNullableInt(PreparedStatement stmt, int index, Integer value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.INTEGER);
        } else {
            stmt.setInt(index, value);
        }
    }

    private static void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // Ignore rollback failures while surfacing the original error.
        }
    }

    // ---------------- INPUT HELPERS ----------------

    private static int readInt(Scanner in, String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = in.nextLine().trim();
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid integer.");
            }
        }
    }

    private static String readLine(Scanner in, String prompt) {
        System.out.print(prompt);
        return in.nextLine();
    }

    private static String readRobotStatus(Scanner in, String prompt, boolean allowBlank) {
        while (true) {
            String status = readLine(in, prompt).trim().toUpperCase();
            if (allowBlank && status.isEmpty()) {
                return "";
            }
            if (status.equals("AVAILABLE") || status.equals("RENTED") || status.equals("MAINTENANCE")) {
                return status;
            }
            System.out.println("Status must be AVAILABLE, RENTED, or MAINTENANCE.");
        }
    }

    private static void printDatabaseError(String action, SQLException e) {
        System.out.println("Database error while trying to " + action + ": " + e.getMessage());
    }

    private static Customer mapCustomer(ResultSet rs) throws SQLException {
        return new Customer(
                rs.getInt("customer_id"),
                rs.getString("name"),
                rs.getString("phone")
        );
    }

    private static Robot mapRobot(ResultSet rs) throws SQLException {
        return new Robot(
                rs.getInt("robot_id"),
                rs.getString("model"),
                rs.getString("status")
        );
    }

    private static Rental mapRental(ResultSet rs) throws SQLException {
        return new Rental(
                rs.getInt("rental_id"),
                rs.getInt("customer_id"),
                rs.getInt("robot_id"),
                rs.getString("rental_date"),
                rs.getString("expected_return_date")
        );
    }

    // ---------------- ENTITY CLASSES ----------------

    static class Customer {
        private final int customerId;
        private final String name;
        private final String phone;

        Customer(int customerId, String name, String phone) {
            this.customerId = customerId;
            this.name = name;
            this.phone = phone;
        }

        String getName() {
            return name;
        }

        String getPhone() {
            return phone;
        }

        @Override
        public String toString() {
            return "Customer{id=" + customerId + ", name='" + name + "', phone='" + phone + "'}";
        }
    }

    static class Robot {
        private final int robotId;
        private final String model;
        private final String status;

        Robot(int robotId, String model, String status) {
            this.robotId = robotId;
            this.model = model;
            this.status = status;
        }

        String getModel() {
            return model;
        }

        String getStatus() {
            return status;
        }

        @Override
        public String toString() {
            return "Robot{id=" + robotId + ", model='" + model + "', status='" + status + "'}";
        }
    }

    static class Rental {
        private final int rentalId;
        private final int customerId;
        private final int robotId;
        private final String rentalDate;
        private final String expectedReturnDate;

        Rental(int rentalId, int customerId, int robotId, String rentalDate, String expectedReturnDate) {
            this.rentalId = rentalId;
            this.customerId = customerId;
            this.robotId = robotId;
            this.rentalDate = rentalDate;
            this.expectedReturnDate = expectedReturnDate;
        }

        int getRentalId() {
            return rentalId;
        }

        int getCustomerId() {
            return customerId;
        }

        int getRobotId() {
            return robotId;
        }

        @Override
        public String toString() {
            return "Rental{id=" + rentalId + ", customerId=" + customerId +
                    ", robotId=" + robotId + ", rentalDate='" + rentalDate +
                    "', expectedReturnDate='" + expectedReturnDate + "'}";
        }
    }

    static class ReferenceSelection {
        private final Integer robotId;
        private final Integer rentalId;

        ReferenceSelection(Integer robotId, Integer rentalId) {
            this.robotId = robotId;
            this.rentalId = rentalId;
        }

        Integer getRobotId() {
            return robotId;
        }

        Integer getRentalId() {
            return rentalId;
        }
    }

    static class ResolvedReference {
        private final Integer robotId;
        private final Integer rentalId;

        ResolvedReference(Integer robotId, Integer rentalId) {
            this.robotId = robotId;
            this.rentalId = rentalId;
        }

        Integer getRobotId() {
            return robotId;
        }

        Integer getRentalId() {
            return rentalId;
        }
    }
}
