import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;

public class WarehouseApp {

    private static final String DEFAULT_DATABASE_NAME = "community_robotic.db";
    private static final String LEGACY_ENV_DATABASE_PATH = "WAREHOUSE_DB_PATH";
    private static final String COMMUNITY_ENV_DATABASE_PATH = "COMMUNITY_DB_PATH";
    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";
    private static final String STATUS_AVAILABLE = "Available";
    private static final String STATUS_RENTED = "Rented";
    private static final String[] ASSET_STATUSES = {
            STATUS_AVAILABLE,
            STATUS_RENTED,
            "Maintenance",
            "Retired",
            "Reserved"
    };
    private static final String CUSTOMER_SELECT =
            "SELECT customerID, firstName, lastName, address, startDate, phone, email, isActive, assignedFacilityID " +
                    "FROM Customer";
    private static final String ROBOT_SELECT =
            "SELECT r.serialNum, r.primaryFunction, r.name, r.trainingLevel, r.batteryAutonomy, " +
                    "a.status, a.year, a.model, a.orderRequestNum " +
                    "FROM Robot r " +
                    "JOIN Asset a ON a.serialNum = r.serialNum";
    private static final String RENTAL_SELECT =
            "SELECT rentalID, dueDate, checkoutDate, returnDate, assetNum, customerID " +
                    "FROM Rental";
    private static final String DRIVERLESS_CAR_SELECT =
            "SELECT dc.serialNum, dc.licensePlate, dc.payloadCapacity, dc.distanceAutonomy, " +
                    "a.status, a.year, a.model, a.orderRequestNum " +
                    "FROM Driverless_Car dc " +
                    "JOIN Asset a ON a.serialNum = dc.serialNum";
    private static final Set<String> DYNAMIC_TABLES = Set.of(
            "Customer",
            "Community_Facility",
            "Asset",
            "Robot",
            "Rental",
            "Driverless_Car",
            "Delivers",
            "Warranty",
            "Order_Request_Facility",
            "EQUIPMENT_RETURN",
            "DELIVERY",
            "PICKUP"
    );

    private static Path databasePath;

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement stmt) throws SQLException;
    }

    @FunctionalInterface
    private interface ResultSetMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection conn) throws SQLException;
    }

    @FunctionalInterface
    private interface TransactionWork {
        boolean run(Connection conn) throws SQLException;
    }

    public static void main(String[] args) {
        databasePath = resolveDatabasePath(args);

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

        try (Connection conn = getConnection()) {
            removeLegacyWarehouseTables(conn);
            validateCommunitySchema(conn);
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
        if (databasePath == null) {
            databasePath = Paths.get(DEFAULT_DATABASE_NAME).toAbsolutePath().normalize();
        }
        return databasePath;
    }

    private static Path resolveDatabasePath(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return Paths.get(args[0]).toAbsolutePath().normalize();
        }

        String envPath = System.getenv(COMMUNITY_ENV_DATABASE_PATH);
        if (envPath != null && !envPath.isBlank()) {
            return Paths.get(envPath).toAbsolutePath().normalize();
        }

        String legacyEnvPath = System.getenv(LEGACY_ENV_DATABASE_PATH);
        if (legacyEnvPath != null && !legacyEnvPath.isBlank()) {
            return Paths.get(legacyEnvPath).toAbsolutePath().normalize();
        }

        return Paths.get(DEFAULT_DATABASE_NAME).toAbsolutePath().normalize();
    }

    private static void validateCommunitySchema(Connection conn) throws SQLException {
        validateTableColumns(conn, "Customer",
                "customerID", "firstName", "lastName", "address", "startDate",
                "phone", "email", "isActive", "assignedFacilityID");
        validateTableColumns(conn, "Community_Facility", "facilityID", "city", "address");
        validateTableColumns(conn, "Asset", "serialNum", "status", "year", "model", "orderRequestNum");
        validateTableColumns(conn, "Robot",
                "serialNum", "primaryFunction", "name", "trainingLevel", "batteryAutonomy");
        validateTableColumns(conn, "Rental",
                "rentalID", "dueDate", "checkoutDate", "returnDate", "assetNum", "customerID");
        validateTableColumns(conn, "Driverless_Car",
                "serialNum", "licensePlate", "payloadCapacity", "distanceAutonomy");
        validateTableColumns(conn, "Delivers", "carNum", "rentalID", "deliveryType");
        validateTableColumns(conn, "Warranty", "model", "year", "orderRequestNum", "warrantyExpiration");
        validateTableColumns(conn, "Order_Request_Facility", "orderRequestNum", "facilityID");
    }

    private static void removeLegacyWarehouseTables(Connection conn) throws SQLException {
        dropLegacyWarehouseTableIfPresent(
                conn,
                "EQUIPMENT_RETURN",
                "return_id", "rental_id", "customer_id", "robot_id", "return_date"
        );
        dropLegacyWarehouseTableIfPresent(
                conn,
                "DELIVERY",
                "delivery_id", "customer_id", "robot_id", "rental_id", "driverless_car_id", "delivery_date", "destination"
        );
        dropLegacyWarehouseTableIfPresent(
                conn,
                "PICKUP",
                "pickup_id", "customer_id", "robot_id", "rental_id", "driverless_car_id", "pickup_date", "pickup_address"
        );
    }

    private static void dropLegacyWarehouseTableIfPresent(Connection conn, String tableName, String... legacyColumns)
            throws SQLException {
        List<String> actualColumns = getTableColumns(conn, tableName);
        if (actualColumns.isEmpty()) {
            return;
        }

        for (String legacyColumn : legacyColumns) {
            if (!containsIgnoreCase(actualColumns, legacyColumn)) {
                return;
            }
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS " + quotedTableName(tableName));
        }
    }

    private static void validateTableColumns(Connection conn, String tableName, String... requiredColumns)
            throws SQLException {
        List<String> actualColumns = getTableColumns(conn, tableName);

        if (actualColumns.isEmpty()) {
            throw new SQLException("Required table missing from database: " + tableName);
        }

        List<String> missingColumns = new ArrayList<>();
        for (String requiredColumn : requiredColumns) {
            if (!containsIgnoreCase(actualColumns, requiredColumn)) {
                missingColumns.add(requiredColumn);
            }
        }

        if (!missingColumns.isEmpty()) {
            throw new SQLException(
                    "Database schema mismatch for table " + tableName +
                            ". Missing columns: " + missingColumns +
                            ". Found columns: " + actualColumns +
                            ". This app expects the community_robotic schema."
            );
        }
    }

    private static List<String> getTableColumns(Connection conn, String tableName) throws SQLException {
        List<String> actualColumns = new ArrayList<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + quotedTableName(tableName) + ")")) {
            while (rs.next()) {
                actualColumns.add(rs.getString("name"));
            }
        }

        return actualColumns;
    }

    private static String quotedTableName(String tableName) throws SQLException {
        if (!DYNAMIC_TABLES.contains(tableName)) {
            throw new SQLException("Unexpected table reference: " + tableName);
        }
        return "\"" + tableName + "\"";
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) {
            if (value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private static void runWithConnection(String action, SqlWork work) {
        try (Connection conn = getConnection()) {
            work.run(conn);
        } catch (SQLException e) {
            printDatabaseError(action, e);
        }
    }

    private static void runInTransaction(String action, TransactionWork work) {
        runWithConnection(action, conn -> {
            conn.setAutoCommit(false);
            try {
                if (work.run(conn)) {
                    conn.commit();
                } else {
                    rollbackQuietly(conn);
                }
            } catch (SQLException e) {
                rollbackQuietly(conn);
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        });
    }

    private static void executeUpdate(Connection conn, String sql, StatementBinder binder) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bind(stmt, binder);
            stmt.executeUpdate();
        }
    }

    private static <T> T queryOne(
            Connection conn,
            String sql,
            ResultSetMapper<T> mapper,
            StatementBinder binder
    ) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bind(stmt, binder);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapper.map(rs) : null;
            }
        }
    }

    private static boolean exists(Connection conn, String sql, StatementBinder binder) throws SQLException {
        return queryOne(conn, sql, rs -> Boolean.TRUE, binder) != null;
    }

    private static <T> boolean printQueryResults(
            Connection conn,
            String sql,
            StatementBinder binder,
            ResultSetMapper<T> mapper,
            String heading,
            String prefix
    ) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bind(stmt, binder);
            try (ResultSet rs = stmt.executeQuery()) {
                boolean foundAny = false;
                while (rs.next()) {
                    if (!foundAny && heading != null && !heading.isBlank()) {
                        System.out.println(heading);
                    }
                    System.out.println(prefix + mapper.map(rs));
                    foundAny = true;
                }
                return foundAny;
            }
        }
    }

    private static void bind(PreparedStatement stmt, StatementBinder binder) throws SQLException {
        if (binder != null) {
            binder.bind(stmt);
        }
    }

    private static void printMainMenu() {
        System.out.println("\n=== Community Robotics Management System ===");
        System.out.println("1) Manage Customers");
        System.out.println("2) Manage Robots");
        System.out.println("3) Rent Robots");
        System.out.println("4) Return Equipment");
        System.out.println("5) Record Delivery Assignment");
        System.out.println("6) Record Pickup Assignment");
        System.out.println("7) Exit");
    }

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
        String firstName = readRequiredLine(in, "First name: ");
        String lastName = readRequiredLine(in, "Last name: ");
        String address = readRequiredLine(in, "Address: ");
        String startDate = normalizeBlankToNull(readLine(in, "Start date (YYYY-MM-DD, optional): "));
        String phone = normalizeBlankToNull(readLine(in, "Phone (optional): "));
        String email = readRequiredLine(in, "Email: ");
        boolean isActive = readBoolean(in, "Is active? (Y/N): ");
        int facilityId = readInt(in, "Assigned facility ID: ");

        runWithConnection("add customer", conn -> {
            if (findCustomerById(conn, id) != null) {
                System.out.println("A customer with that ID already exists.");
                return;
            }

            if (!facilityExists(conn, facilityId)) {
                System.out.println("Assigned facility not found.");
                return;
            }

            executeUpdate(conn,
                    "INSERT INTO Customer " +
                            "(customerID, firstName, lastName, address, startDate, phone, email, isActive, assignedFacilityID) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    stmt -> {
                stmt.setInt(1, id);
                stmt.setString(2, firstName);
                stmt.setString(3, lastName);
                stmt.setString(4, address);
                setNullableText(stmt, 5, startDate);
                setNullableText(stmt, 6, phone);
                stmt.setString(7, email);
                stmt.setBoolean(8, isActive);
                stmt.setInt(9, facilityId);
                    }
            );

            System.out.println("Customer added.");
        });
    }

    private static void editCustomer(Scanner in) {
        int id = readInt(in, "Enter Customer ID to edit: ");

        runWithConnection("edit customer", conn -> {
            Customer customer = findCustomerById(conn, id);
            if (customer == null) {
                System.out.println("Customer not found.");
                return;
            }

            System.out.println("Editing customer: " + customer);
            String newFirstName = readLine(in, "New first name (leave blank to keep current): ").trim();
            String newLastName = readLine(in, "New last name (leave blank to keep current): ").trim();
            String newAddress = readLine(in, "New address (leave blank to keep current): ").trim();
            String newStartDate = readLine(in, "New start date (leave blank to keep current): ").trim();
            String newPhone = readLine(in, "New phone (leave blank to keep current): ").trim();
            String newEmail = readLine(in, "New email (leave blank to keep current): ").trim();
            Boolean newIsActive = readOptionalBoolean(in, "Change active status? (Y/N, leave blank to keep current): ");
            Integer newFacilityId = readOptionalInt(in, "New assigned facility ID (leave blank to keep current): ");

            String updatedFirstName = keepCurrent(newFirstName, customer.firstName());
            String updatedLastName = keepCurrent(newLastName, customer.lastName());
            String updatedAddress = keepCurrent(newAddress, customer.address());
            String updatedStartDate = keepCurrent(newStartDate, customer.startDate());
            String updatedPhone = keepCurrent(newPhone, customer.phone());
            String updatedEmail = keepCurrent(newEmail, customer.email());
            boolean updatedIsActive = keepCurrent(newIsActive, customer.active());
            int updatedFacilityId = keepCurrent(newFacilityId, customer.assignedFacilityId());

            if (!facilityExists(conn, updatedFacilityId)) {
                System.out.println("Assigned facility not found.");
                return;
            }

            executeUpdate(conn,
                    "UPDATE Customer " +
                            "SET firstName = ?, lastName = ?, address = ?, startDate = ?, phone = ?, email = ?, " +
                            "isActive = ?, assignedFacilityID = ? " +
                            "WHERE customerID = ?",
                    stmt -> {
                stmt.setString(1, updatedFirstName);
                stmt.setString(2, updatedLastName);
                stmt.setString(3, updatedAddress);
                setNullableText(stmt, 4, updatedStartDate);
                setNullableText(stmt, 5, updatedPhone);
                stmt.setString(6, updatedEmail);
                stmt.setBoolean(7, updatedIsActive);
                stmt.setInt(8, updatedFacilityId);
                stmt.setInt(9, id);
                    }
            );

            System.out.println("Customer updated: " + new Customer(
                    id,
                    updatedFirstName,
                    updatedLastName,
                    updatedAddress,
                    updatedStartDate,
                    updatedPhone,
                    updatedEmail,
                    updatedIsActive,
                    updatedFacilityId
            ));
        });
    }

    private static void deleteCustomer(Scanner in) {
        int id = readInt(in, "Enter Customer ID to delete: ");

        runWithConnection("delete customer", conn -> {
            Customer customer = findCustomerById(conn, id);
            if (customer == null) {
                System.out.println("Customer not found.");
                return;
            }

            executeUpdate(conn, "DELETE FROM Customer WHERE customerID = ?", stmt -> stmt.setInt(1, id));

            System.out.println("Customer deleted.");
        });
    }

    private static void searchCustomer(Scanner in) {
        System.out.println("Search by:");
        System.out.println("1) Customer ID");
        System.out.println("2) Name contains");
        int choice = readInt(in, "Choose: ");

        switch (choice) {
            case 1 -> {
                int id = readInt(in, "Customer ID: ");
                runWithConnection("search customer", conn -> {
                    Customer customer = findCustomerById(conn, id);
                    if (customer == null) {
                        System.out.println("No customer found.");
                    } else {
                        System.out.println("Found: " + customer);
                    }
                });
            }
            case 2 -> {
                String key = readRequiredLine(in, "Name keyword: ").toLowerCase(Locale.ROOT);
                runWithConnection("search customer", conn -> {
                    String pattern = "%" + key + "%";
                    if (!printQueryResults(
                            conn,
                            CUSTOMER_SELECT + " " +
                                    "WHERE LOWER(firstName || ' ' || lastName) LIKE ? " +
                                    "OR LOWER(lastName || ' ' || firstName) LIKE ? " +
                                    "ORDER BY customerID",
                            stmt -> {
                                stmt.setString(1, pattern);
                                stmt.setString(2, pattern);
                            },
                            WarehouseApp::mapCustomer,
                            null,
                            "Found: "
                    )) {
                        System.out.println("No customers matched.");
                    }
                });
            }
            default -> System.out.println("Invalid choice.");
        }
    }

    private static void listCustomers() {
        runWithConnection("list customers", conn -> {
            if (!printQueryResults(
                    conn,
                    CUSTOMER_SELECT + " ORDER BY customerID",
                    null,
                    WarehouseApp::mapCustomer,
                    "\nCustomers:",
                    " - "
            )) {
                System.out.println("No customers in system.");
            }
        });
    }

    private static Customer findCustomerById(Connection conn, int id) throws SQLException {
        return queryOne(conn, CUSTOMER_SELECT + " WHERE customerID = ?", WarehouseApp::mapCustomer,
                stmt -> stmt.setInt(1, id));
    }

    private static boolean facilityExists(Connection conn, int facilityId) throws SQLException {
        return exists(conn, "SELECT 1 FROM Community_Facility WHERE facilityID = ?",
                stmt -> stmt.setInt(1, facilityId));
    }

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
        int serialNum = readInt(in, "Robot serial number: ");
        String name = readRequiredLine(in, "Robot name: ");
        String primaryFunction = readRequiredLine(in, "Primary function: ");
        int trainingLevel = readInt(in, "Training level: ");
        Integer batteryAutonomy = readOptionalInt(in, "Battery autonomy (optional integer): ");
        String status = readAssetStatus(in, "Asset status (Available/Rented/Maintenance/Retired/Reserved): ", false);
        String model = readRequiredLine(in, "Model: ");
        int year = readInt(in, "Year: ");
        int orderRequestNum = readInt(in, "Order request number: ");

        runInTransaction("add robot", conn -> {
            if (findRobotById(conn, serialNum) != null || assetExists(conn, serialNum)) {
                System.out.println("That serial number is already in use.");
                return false;
            }

            if (!orderRequestExists(conn, orderRequestNum)) {
                System.out.println("Order request number not found.");
                return false;
            }

            if (!warrantyExists(conn, model, year, orderRequestNum)) {
                System.out.println("No matching warranty/model/year/order request combination exists.");
                return false;
            }

            executeUpdate(conn,
                    "INSERT INTO Asset (serialNum, status, year, model, orderRequestNum) VALUES (?, ?, ?, ?, ?)",
                    stmt -> {
                        stmt.setInt(1, serialNum);
                        stmt.setString(2, status);
                        stmt.setInt(3, year);
                        stmt.setString(4, model);
                        stmt.setInt(5, orderRequestNum);
                    }
            );
            executeUpdate(conn,
                    "INSERT INTO Robot (serialNum, primaryFunction, name, trainingLevel, batteryAutonomy) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    stmt -> {
                        stmt.setInt(1, serialNum);
                        stmt.setString(2, primaryFunction);
                        stmt.setString(3, name);
                        stmt.setInt(4, trainingLevel);
                        setNullableInt(stmt, 5, batteryAutonomy);
                    }
            );

            System.out.println("Robot added.");
            return true;
        });
    }

    private static void editRobot(Scanner in) {
        int serialNum = readInt(in, "Enter robot serial number to edit: ");

        runInTransaction("edit robot", conn -> {
            Robot robot = findRobotById(conn, serialNum);
            if (robot == null) {
                System.out.println("Robot not found.");
                return false;
            }

            System.out.println("Editing robot: " + robot);
            String newName = readLine(in, "New robot name (leave blank to keep current): ").trim();
            String newPrimaryFunction = readLine(in, "New primary function (leave blank to keep current): ").trim();
            Integer newTrainingLevel = readOptionalInt(in, "New training level (leave blank to keep current): ");
            Integer newBatteryAutonomy = readOptionalInt(in, "New battery autonomy (leave blank to keep current): ");
            String newStatus = readAssetStatus(in, "New asset status (leave blank to keep current): ", true);
            String newModel = readLine(in, "New model (leave blank to keep current): ").trim();
            Integer newYear = readOptionalInt(in, "New year (leave blank to keep current): ");
            Integer newOrderRequestNum = readOptionalInt(in, "New order request number (leave blank to keep current): ");

            String updatedName = keepCurrent(newName, robot.name());
            String updatedPrimaryFunction = keepCurrent(newPrimaryFunction, robot.primaryFunction());
            int updatedTrainingLevel = keepCurrent(newTrainingLevel, robot.trainingLevel());
            Integer updatedBatteryAutonomy = keepCurrent(newBatteryAutonomy, robot.batteryAutonomy());
            String updatedStatus = keepCurrent(newStatus, robot.status());
            String updatedModel = keepCurrent(newModel, robot.model());
            int updatedYear = keepCurrent(newYear, robot.year());
            int updatedOrderRequestNum = keepCurrent(newOrderRequestNum, robot.orderRequestNum());

            if (!orderRequestExists(conn, updatedOrderRequestNum)) {
                System.out.println("Order request number not found.");
                return false;
            }

            if (!warrantyExists(conn, updatedModel, updatedYear, updatedOrderRequestNum)) {
                System.out.println("No matching warranty/model/year/order request combination exists.");
                return false;
            }

            executeUpdate(conn,
                    "UPDATE Asset SET status = ?, year = ?, model = ?, orderRequestNum = ? WHERE serialNum = ?",
                    stmt -> {
                        stmt.setString(1, updatedStatus);
                        stmt.setInt(2, updatedYear);
                        stmt.setString(3, updatedModel);
                        stmt.setInt(4, updatedOrderRequestNum);
                        stmt.setInt(5, serialNum);
                    }
            );
            executeUpdate(conn,
                    "UPDATE Robot SET primaryFunction = ?, name = ?, trainingLevel = ?, batteryAutonomy = ? " +
                            "WHERE serialNum = ?",
                    stmt -> {
                        stmt.setString(1, updatedPrimaryFunction);
                        stmt.setString(2, updatedName);
                        stmt.setInt(3, updatedTrainingLevel);
                        setNullableInt(stmt, 4, updatedBatteryAutonomy);
                        stmt.setInt(5, serialNum);
                    }
            );

            System.out.println("Robot updated: " + new Robot(
                    serialNum,
                    updatedPrimaryFunction,
                    updatedName,
                    updatedTrainingLevel,
                    updatedBatteryAutonomy,
                    updatedStatus,
                    updatedYear,
                    updatedModel,
                    updatedOrderRequestNum
            ));
            return true;
        });
    }

    private static void deleteRobot(Scanner in) {
        int serialNum = readInt(in, "Enter robot serial number to delete: ");

        runInTransaction("delete robot", conn -> {
            Robot robot = findRobotById(conn, serialNum);
            if (robot == null) {
                System.out.println("Robot not found.");
                return false;
            }

            executeUpdate(conn, "DELETE FROM Robot WHERE serialNum = ?", stmt -> stmt.setInt(1, serialNum));
            executeUpdate(conn, "DELETE FROM Asset WHERE serialNum = ?", stmt -> stmt.setInt(1, serialNum));

            System.out.println("Robot deleted.");
            return true;
        });
    }

    private static void searchRobot(Scanner in) {
        System.out.println("Search by:");
        System.out.println("1) Robot serial number");
        System.out.println("2) Model, name, or function contains");
        int choice = readInt(in, "Choose: ");

        switch (choice) {
            case 1 -> {
                int id = readInt(in, "Robot serial number: ");
                runWithConnection("search robot", conn -> {
                    Robot robot = findRobotById(conn, id);
                    if (robot == null) {
                        System.out.println("No robot found.");
                    } else {
                        System.out.println("Found: " + robot);
                    }
                });
            }
            case 2 -> {
                String key = readRequiredLine(in, "Keyword: ").toLowerCase(Locale.ROOT);
                runWithConnection("search robot", conn -> {
                    String pattern = "%" + key + "%";
                    if (!printQueryResults(
                            conn,
                            ROBOT_SELECT + " " +
                                    "WHERE LOWER(a.model) LIKE ? OR LOWER(r.name) LIKE ? OR LOWER(r.primaryFunction) LIKE ? " +
                                    "ORDER BY r.serialNum",
                            stmt -> {
                                stmt.setString(1, pattern);
                                stmt.setString(2, pattern);
                                stmt.setString(3, pattern);
                            },
                            WarehouseApp::mapRobot,
                            null,
                            "Found: "
                    )) {
                        System.out.println("No robots matched.");
                    }
                });
            }
            default -> System.out.println("Invalid choice.");
        }
    }

    private static void listRobots() {
        runWithConnection("list robots", conn -> {
            if (!printQueryResults(
                    conn,
                    ROBOT_SELECT + " ORDER BY r.serialNum",
                    null,
                    WarehouseApp::mapRobot,
                    "\nRobots:",
                    " - "
            )) {
                System.out.println("No robots in system.");
            }
        });
    }

    private static Robot findRobotById(Connection conn, int serialNum) throws SQLException {
        return queryOne(conn, ROBOT_SELECT + " WHERE r.serialNum = ?", WarehouseApp::mapRobot,
                stmt -> stmt.setInt(1, serialNum));
    }

    private static boolean assetExists(Connection conn, int serialNum) throws SQLException {
        return exists(conn, "SELECT 1 FROM Asset WHERE serialNum = ?", stmt -> stmt.setInt(1, serialNum));
    }

    private static boolean warrantyExists(Connection conn, String model, int year, int orderRequestNum)
            throws SQLException {
        return exists(conn,
                "SELECT 1 FROM Warranty WHERE model = ? AND year = ? AND orderRequestNum = ?",
                stmt -> {
                    stmt.setString(1, model);
                    stmt.setInt(2, year);
                    stmt.setInt(3, orderRequestNum);
                });
    }

    private static boolean orderRequestExists(Connection conn, int orderRequestNum) throws SQLException {
        return exists(conn, "SELECT 1 FROM Order_Request_Facility WHERE orderRequestNum = ?",
                stmt -> stmt.setInt(1, orderRequestNum));
    }

    private static void rentRobot(Scanner in) {
        System.out.println("\n--- Rent Robots ---");
        int customerId = readInt(in, "Customer ID: ");
        int robotId = readInt(in, "Robot serial number: ");
        String checkoutDate = readRequiredLine(in, "Checkout date (YYYY-MM-DD): ");
        String dueDate = readRequiredLine(in, "Due date (YYYY-MM-DD): ");

        runInTransaction("record rental", conn -> {
            Customer customer = findCustomerById(conn, customerId);
            if (customer == null) {
                System.out.println("Customer not found.");
                return false;
            }

            if (!customer.active()) {
                System.out.println("Customer is not active.");
                return false;
            }

            Robot robot = findRobotById(conn, robotId);
            if (robot == null) {
                System.out.println("Robot not found.");
                return false;
            }

            if (!STATUS_AVAILABLE.equalsIgnoreCase(robot.status())) {
                System.out.println("Robot is not available for rental. Current status: " + robot.status());
                return false;
            }

            if (findActiveRentalByAsset(conn, robotId) != null) {
                System.out.println("Robot already has an active rental.");
                return false;
            }

            int rentalId = nextRentalId(conn);
            executeUpdate(conn,
                    "INSERT INTO Rental (rentalID, dueDate, checkoutDate, returnDate, assetNum, customerID) " +
                            "VALUES (?, ?, ?, NULL, ?, ?)",
                    stmt -> {
                        stmt.setInt(1, rentalId);
                        stmt.setString(2, dueDate);
                        stmt.setString(3, checkoutDate);
                        stmt.setInt(4, robotId);
                        stmt.setInt(5, customerId);
                    }
            );

            updateAssetStatus(conn, robotId, STATUS_RENTED);
            System.out.println("Rental recorded with ID " + rentalId + ".");
            return true;
        });
    }

    private static void returnEquipment(Scanner in) {
        System.out.println("\n--- Return Equipment ---");
        int customerId = readInt(in, "Customer ID: ");
        ReferenceSelection reference = readRobotOrRentalReference(in);
        String returnDate = readRequiredLine(in, "Return date (YYYY-MM-DD): ");

        runInTransaction("return equipment", conn -> {
            Customer customer = findCustomerById(conn, customerId);
            if (customer == null) {
                System.out.println("Customer not found.");
                return false;
            }

            Rental rental = resolveActiveRental(conn, customerId, reference);
            if (rental == null) {
                return false;
            }

            executeUpdate(conn, "UPDATE Rental SET returnDate = ? WHERE rentalID = ?",
                    stmt -> {
                        stmt.setString(1, returnDate);
                        stmt.setInt(2, rental.rentalId());
                    });

            updateAssetStatus(conn, rental.assetNum(), STATUS_AVAILABLE);
            System.out.println("Equipment returned for rental " + rental.rentalId() + ".");
            return true;
        });
    }

    private static void deliverRobot(Scanner in) {
        System.out.println("\n--- Record Delivery Assignment ---");
        int customerId = readInt(in, "Customer ID: ");
        ReferenceSelection reference = readRobotOrRentalReference(in);
        int driverlessCarId = readInt(in, "Driverless car serial number: ");
        String deliveryType = readRequiredLine(in, "Delivery type (e.g. Home Delivery): ");

        recordDeliveryAssignment(
                customerId,
                reference,
                driverlessCarId,
                deliveryType,
                "record delivery"
        );
    }

    private static void pickupRobot(Scanner in) {
        System.out.println("\n--- Record Pickup Assignment ---");
        int customerId = readInt(in, "Customer ID: ");
        ReferenceSelection reference = readRobotOrRentalReference(in);
        int driverlessCarId = readInt(in, "Driverless car serial number: ");
        String pickupType = readRequiredLine(in, "Pickup type description: ");
        String storedType = pickupType.toLowerCase(Locale.ROOT).startsWith("pickup")
                ? pickupType
                : "Pickup - " + pickupType;

        recordDeliveryAssignment(
                customerId,
                reference,
                driverlessCarId,
                storedType,
                "record pickup"
        );
    }

    private static void recordDeliveryAssignment(
            int customerId,
            ReferenceSelection reference,
            int driverlessCarId,
            String deliveryType,
            String action
    ) {
        runWithConnection(action, conn -> {
            Customer customer = findCustomerById(conn, customerId);
            if (customer == null) {
                System.out.println("Customer not found.");
                return;
            }

            Rental rental = resolveActiveRental(conn, customerId, reference);
            if (rental == null) {
                return;
            }

            DriverlessCar driverlessCar = findDriverlessCarById(conn, driverlessCarId);
            if (driverlessCar == null) {
                System.out.println("Driverless car not found.");
                return;
            }

            if (!STATUS_AVAILABLE.equalsIgnoreCase(driverlessCar.status())) {
                System.out.println("Driverless car is not available. Current status: " + driverlessCar.status());
                return;
            }

            executeUpdate(conn, "INSERT INTO Delivers (carNum, rentalID, deliveryType) VALUES (?, ?, ?)",
                    stmt -> {
                        stmt.setInt(1, driverlessCarId);
                        stmt.setInt(2, rental.rentalId());
                        stmt.setString(3, deliveryType);
                    });

            System.out.println(
                    "Assignment recorded for rental " + rental.rentalId() +
                            " using car " + driverlessCar.licensePlate() +
                            " (" + deliveryType + ")."
            );
        });
    }

    private static ReferenceSelection readRobotOrRentalReference(Scanner in) {
        while (true) {
            System.out.println("Reference by:");
            System.out.println("1) Robot serial number");
            System.out.println("2) Rental ID");
            int choice = readInt(in, "Choose: ");

            switch (choice) {
                case 1 -> {
                    return new ReferenceSelection(readInt(in, "Robot serial number: "), null);
                }
                case 2 -> {
                    return new ReferenceSelection(null, readInt(in, "Rental ID: "));
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private static Rental resolveActiveRental(Connection conn, int customerId, ReferenceSelection selection)
            throws SQLException {
        if (selection.robotId() != null) {
            Robot robot = findRobotById(conn, selection.robotId());
            if (robot == null) {
                System.out.println("Robot not found.");
                return null;
            }

            Rental rental = findActiveRentalByCustomerAndAsset(conn, customerId, selection.robotId());
            if (rental == null) {
                System.out.println("No active rental found for that customer and robot.");
                return null;
            }
            return rental;
        }

        Rental rental = findRentalById(conn, selection.rentalId());
        if (rental == null) {
            System.out.println("Rental not found.");
            return null;
        }
        if (rental.customerId() != customerId) {
            System.out.println("Rental does not belong to that customer.");
            return null;
        }
        if (rental.returnDate() != null && !rental.returnDate().isBlank()) {
            System.out.println("Rental is already returned.");
            return null;
        }

        return rental;
    }

    private static Rental findRentalById(Connection conn, int rentalId) throws SQLException {
        return queryOne(conn, RENTAL_SELECT + " WHERE rentalID = ?", WarehouseApp::mapRental,
                stmt -> stmt.setInt(1, rentalId));
    }

    private static Rental findActiveRentalByAsset(Connection conn, int assetNum) throws SQLException {
        return queryOne(
                conn,
                RENTAL_SELECT + " WHERE assetNum = ? AND returnDate IS NULL ORDER BY rentalID DESC LIMIT 1",
                WarehouseApp::mapRental,
                stmt -> stmt.setInt(1, assetNum)
        );
    }

    private static Rental findActiveRentalByCustomerAndAsset(Connection conn, int customerId, int assetNum)
            throws SQLException {
        return queryOne(
                conn,
                RENTAL_SELECT + " WHERE customerID = ? AND assetNum = ? AND returnDate IS NULL " +
                        "ORDER BY rentalID DESC LIMIT 1",
                WarehouseApp::mapRental,
                stmt -> {
                    stmt.setInt(1, customerId);
                    stmt.setInt(2, assetNum);
                }
        );
    }

    private static int nextRentalId(Connection conn) throws SQLException {
        Integer nextId = queryOne(conn, "SELECT COALESCE(MAX(rentalID), 0) + 1 AS nextId FROM Rental",
                rs -> rs.getInt("nextId"), null);
        return nextId == null ? 1 : nextId;
    }

    private static void updateAssetStatus(Connection conn, int serialNum, String status) throws SQLException {
        executeUpdate(conn, "UPDATE Asset SET status = ? WHERE serialNum = ?",
                stmt -> {
                    stmt.setString(1, status);
                    stmt.setInt(2, serialNum);
                });
    }

    private static DriverlessCar findDriverlessCarById(Connection conn, int serialNum) throws SQLException {
        return queryOne(conn, DRIVERLESS_CAR_SELECT + " WHERE dc.serialNum = ?", WarehouseApp::mapDriverlessCar,
                stmt -> stmt.setInt(1, serialNum));
    }

    private static void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // Ignore rollback failures while surfacing the original error.
        }
    }

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

    private static Integer readOptionalInt(Scanner in, String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = in.nextLine().trim();
            if (s.isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid integer or leave it blank.");
            }
        }
    }

    private static String readLine(Scanner in, String prompt) {
        System.out.print(prompt);
        return in.nextLine();
    }

    private static String readRequiredLine(Scanner in, String prompt) {
        while (true) {
            String value = readLine(in, prompt).trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("This value is required.");
        }
    }

    private static boolean readBoolean(Scanner in, String prompt) {
        while (true) {
            Boolean parsed = parseBooleanToken(readLine(in, prompt).trim());
            if (parsed != null) {
                return parsed;
            }
            System.out.println("Please enter Y or N.");
        }
    }

    private static Boolean readOptionalBoolean(Scanner in, String prompt) {
        while (true) {
            String value = readLine(in, prompt).trim();
            if (value.isEmpty()) {
                return null;
            }
            Boolean parsed = parseBooleanToken(value);
            if (parsed != null) {
                return parsed;
            }
            System.out.println("Please enter Y, N, or leave it blank.");
        }
    }

    private static String readAssetStatus(Scanner in, String prompt, boolean allowBlank) {
        while (true) {
            String value = readLine(in, prompt).trim();
            if (allowBlank && value.isEmpty()) {
                return "";
            }

            String canonical = canonicalStatus(value);
            if (canonical != null) {
                return canonical;
            }

            System.out.println("Status must be one of: Available, Rented, Maintenance, Retired, Reserved.");
        }
    }

    private static String canonicalStatus(String value) {
        for (String allowedStatus : ASSET_STATUSES) {
            if (allowedStatus.equalsIgnoreCase(value)) {
                return allowedStatus;
            }
        }
        return null;
    }

    private static String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Boolean parseBooleanToken(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "y", "yes", "true", "1" -> true;
            case "n", "no", "false", "0" -> false;
            default -> null;
        };
    }

    private static String keepCurrent(String updatedValue, String currentValue) {
        return updatedValue.isEmpty() ? currentValue : updatedValue;
    }

    private static <T> T keepCurrent(T updatedValue, T currentValue) {
        return updatedValue == null ? currentValue : updatedValue;
    }

    private static void printDatabaseError(String action, SQLException e) {
        System.out.println("Database error while trying to " + action + ": " + e.getMessage());
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

    private static Customer mapCustomer(ResultSet rs) throws SQLException {
        return new Customer(
                rs.getInt("customerID"),
                rs.getString("firstName"),
                rs.getString("lastName"),
                rs.getString("address"),
                rs.getString("startDate"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getBoolean("isActive"),
                rs.getInt("assignedFacilityID")
        );
    }

    private static Robot mapRobot(ResultSet rs) throws SQLException {
        int batteryAutonomy = rs.getInt("batteryAutonomy");
        Integer batteryValue = rs.wasNull() ? null : batteryAutonomy;

        return new Robot(
                rs.getInt("serialNum"),
                rs.getString("primaryFunction"),
                rs.getString("name"),
                rs.getInt("trainingLevel"),
                batteryValue,
                rs.getString("status"),
                rs.getInt("year"),
                rs.getString("model"),
                rs.getInt("orderRequestNum")
        );
    }

    private static Rental mapRental(ResultSet rs) throws SQLException {
        return new Rental(
                rs.getInt("rentalID"),
                rs.getString("dueDate"),
                rs.getString("checkoutDate"),
                rs.getString("returnDate"),
                rs.getInt("assetNum"),
                rs.getInt("customerID")
        );
    }

    private static DriverlessCar mapDriverlessCar(ResultSet rs) throws SQLException {
        return new DriverlessCar(
                rs.getInt("serialNum"),
                rs.getString("licensePlate"),
                rs.getInt("payloadCapacity"),
                rs.getInt("distanceAutonomy"),
                rs.getString("status"),
                rs.getInt("year"),
                rs.getString("model"),
                rs.getInt("orderRequestNum")
        );
    }

    private record Customer(
            int customerId,
            String firstName,
            String lastName,
            String address,
            String startDate,
            String phone,
            String email,
            boolean active,
            int assignedFacilityId
    ) {
        @Override
        public String toString() {
            return "Customer{id=" + customerId +
                    ", name='" + firstName + " " + lastName + "'" +
                    ", email='" + email + "'" +
                    ", phone='" + formatNullable(phone) + "'" +
                    ", active=" + active +
                    ", facilityId=" + assignedFacilityId + "}";
        }
    }

    private record Robot(
            int serialNum,
            String primaryFunction,
            String name,
            int trainingLevel,
            Integer batteryAutonomy,
            String status,
            int year,
            String model,
            int orderRequestNum
    ) {
        @Override
        public String toString() {
            return "Robot{serial=" + serialNum +
                    ", name='" + name + "'" +
                    ", function='" + primaryFunction + "'" +
                    ", status='" + status + "'" +
                    ", model='" + model + "'" +
                    ", year=" + year +
                    ", orderRequest=" + orderRequestNum +
                    ", trainingLevel=" + trainingLevel +
                    ", batteryAutonomy=" + formatNullable(batteryAutonomy) + "}";
        }
    }

    private record Rental(
            int rentalId,
            String dueDate,
            String checkoutDate,
            String returnDate,
            int assetNum,
            int customerId
    ) {
        @Override
        public String toString() {
            return "Rental{id=" + rentalId +
                    ", customerId=" + customerId +
                    ", assetNum=" + assetNum +
                    ", checkoutDate='" + checkoutDate + "'" +
                    ", dueDate='" + dueDate + "'" +
                    ", returnDate='" + formatNullable(returnDate) + "'}";
        }
    }

    private record DriverlessCar(
            int serialNum,
            String licensePlate,
            int payloadCapacity,
            int distanceAutonomy,
            String status,
            int year,
            String model,
            int orderRequestNum
    ) {
        @Override
        public String toString() {
            return "DriverlessCar{serial=" + serialNum +
                    ", licensePlate='" + licensePlate + "'" +
                    ", status='" + status + "'" +
                    ", payloadCapacity=" + payloadCapacity +
                    ", distanceAutonomy=" + distanceAutonomy +
                    ", model='" + model + "'" +
                    ", year=" + year +
                    ", orderRequest=" + orderRequestNum + "}";
        }
    }

    private record ReferenceSelection(Integer robotId, Integer rentalId) {
    }

    private static String formatNullable(Object value) {
        return value == null ? "n/a" : String.valueOf(value);
    }
}
