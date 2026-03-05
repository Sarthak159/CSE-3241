import java.util.ArrayList;
import java.util.Scanner;

public class WarehouseApp {

    // In-memory "tables"
    private static final ArrayList<Customer> customers = new ArrayList<>();
    private static final ArrayList<Robot> robots = new ArrayList<>();

    public static void main(String[] args) {
        try (Scanner in = new Scanner(System.in)) {
            boolean running = true;
            while (running) {
                printMainMenu();
                int choice = readInt(in, "Choose an option: ");

                switch (choice) {
                    case 1 -> customerMenu(in);
                    case 2 -> robotMenu(in);
                    case 3 -> rentRobotStub(in);
                    case 4 -> returnEquipmentStub(in);
                    case 5 -> deliverRobotStub(in);
                    case 6 -> pickupRobotStub(in);
                    case 7 -> {
                        running = false;
                        System.out.println("Exiting program. Goodbye.");
                    }
                    default -> System.out.println("Invalid option. Try again.");
                }
            }
        }
    }

    // ---------------- MAIN MENU ----------------

    private static void printMainMenu() {
        System.out.println("\n=== Warehouse Robot Management System ===");
        System.out.println("1) Manage Customers");
        System.out.println("2) Manage Robots");
        System.out.println("3) Rent Robots (prompt only)");
        System.out.println("4) Return Equipment (prompt only)");
        System.out.println("5) Delivery of Robots (prompt only)");
        System.out.println("6) Pickup of Robots (prompt only)");
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
        if (findCustomerById(id) != null) {
            System.out.println("A customer with that ID already exists.");
            return;
        }

        String name = readLine(in, "Name: ");
        String phone = readLine(in, "Phone: ");

        customers.add(new Customer(id, name, phone));
        System.out.println("Customer added.");
    }

    private static void editCustomer(Scanner in) {
        int id = readInt(in, "Enter Customer ID to edit: ");
        Customer c = findCustomerById(id);
        if (c == null) {
            System.out.println("Customer not found.");
            return;
        }

        System.out.println("Editing customer: " + c);
        String newName = readLine(in, "New name (leave blank to keep current): ");
        String newPhone = readLine(in, "New phone (leave blank to keep current): ");

        if (!newName.trim().isEmpty()) {
            c.setName(newName);
        }
        if (!newPhone.trim().isEmpty()) {
            c.setPhone(newPhone);
        }

        System.out.println("Customer updated: " + c);
    }

    private static void deleteCustomer(Scanner in) {
        int id = readInt(in, "Enter Customer ID to delete: ");
        Customer c = findCustomerById(id);
        if (c == null) {
            System.out.println("Customer not found.");
            return;
        }

        customers.remove(c);
        System.out.println("Customer deleted.");
    }

    private static void searchCustomer(Scanner in) {
        System.out.println("Search by:");
        System.out.println("1) Customer ID");
        System.out.println("2) Name contains");
        int choice = readInt(in, "Choose: ");

        switch (choice) {
            case 1 -> {
                int id = readInt(in, "Customer ID: ");
                Customer c = findCustomerById(id);
                if (c == null) {
                    System.out.println("No customer found.");
                } else {
                    System.out.println("Found: " + c);
                }
            }
            case 2 -> {
                String key = readLine(in, "Name keyword: ").toLowerCase();
                boolean foundAny = false;
                for (Customer c : customers) {
                    if (c.getName().toLowerCase().contains(key)) {
                        System.out.println("Found: " + c);
                        foundAny = true;
                    }
                }
                if (!foundAny) {
                    System.out.println("No customers matched.");
                }
            }
            default -> System.out.println("Invalid choice.");
        }
    }

    private static void listCustomers() {
        if (customers.isEmpty()) {
            System.out.println("No customers in system.");
            return;
        }
        System.out.println("\nCustomers:");
        for (Customer c : customers) {
            System.out.println(" - " + c);
        }
    }

    private static Customer findCustomerById(int id) {
        for (Customer c : customers) {
            if (c.getCustomerId() == id) {
                return c;
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
        if (findRobotById(id) != null) {
            System.out.println("A robot with that ID already exists.");
            return;
        }

        String model = readLine(in, "Model: ");
        String status = readLine(in, "Status (AVAILABLE/RENTED/MAINTENANCE): ").toUpperCase();

        robots.add(new Robot(id, model, status));
        System.out.println("Robot added.");
    }

    private static void editRobot(Scanner in) {
        int id = readInt(in, "Enter Robot ID to edit: ");
        Robot r = findRobotById(id);
        if (r == null) {
            System.out.println("Robot not found.");
            return;
        }

        System.out.println("Editing robot: " + r);
        String newModel = readLine(in, "New model (leave blank to keep current): ");
        String newStatus = readLine(in, "New status (leave blank to keep current): ").toUpperCase();

        if (!newModel.trim().isEmpty()) {
            r.setModel(newModel);
        }
        if (!newStatus.trim().isEmpty()) {
            r.setStatus(newStatus);
        }

        System.out.println("Robot updated: " + r);
    }

    private static void deleteRobot(Scanner in) {
        int id = readInt(in, "Enter Robot ID to delete: ");
        Robot r = findRobotById(id);
        if (r == null) {
            System.out.println("Robot not found.");
            return;
        }

        robots.remove(r);
        System.out.println("Robot deleted.");
    }

    private static void searchRobot(Scanner in) {
        System.out.println("Search by:");
        System.out.println("1) Robot ID");
        System.out.println("2) Model contains");
        int choice = readInt(in, "Choose: ");

        switch (choice) {
            case 1 -> {
                int id = readInt(in, "Robot ID: ");
                Robot r = findRobotById(id);
                if (r == null) {
                    System.out.println("No robot found.");
                } else {
                    System.out.println("Found: " + r);
                }
            }
            case 2 -> {
                String key = readLine(in, "Model keyword: ").toLowerCase();
                boolean foundAny = false;
                for (Robot r : robots) {
                    if (r.getModel().toLowerCase().contains(key)) {
                        System.out.println("Found: " + r);
                        foundAny = true;
                    }
                }
                if (!foundAny) {
                    System.out.println("No robots matched.");
                }
            }
            default -> System.out.println("Invalid choice.");
        }
    }

    private static void listRobots() {
        if (robots.isEmpty()) {
            System.out.println("No robots in system.");
            return;
        }
        System.out.println("\nRobots:");
        for (Robot r : robots) {
            System.out.println(" - " + r);
        }
    }

    private static Robot findRobotById(int id) {
        for (Robot r : robots) {
            if (r.getRobotId() == id) {
                return r;
            }
        }
        return null;
    }

    // ---------------- STUB FLOWS (PROMPT ONLY) ----------------

    private static void rentRobotStub(Scanner in) {
        System.out.println("\n--- Rent Robots (Prompt Only) ---");
        int customerId = readInt(in, "Customer ID: ");
        int robotId = readInt(in, "Robot ID: ");
        String startDate = readLine(in, "Rental start date (YYYY-MM-DD): ");
        int days = readInt(in, "Number of days: ");

        System.out.println("Rental registered (stub). Customer " + customerId +
                " rented Robot " + robotId + " starting " + startDate +
                " for " + days + " days.");
    }

    private static void returnEquipmentStub(Scanner in) {
        System.out.println("\n--- Return Equipment (Prompt Only) ---");
        int customerId = readInt(in, "Customer ID: ");
        int robotId = readInt(in, "Robot ID being returned: ");
        String returnDate = readLine(in, "Return date (YYYY-MM-DD): ");

        System.out.println("Return registered (stub). Robot " + robotId +
                " returned by Customer " + customerId + " on " + returnDate + ".");
    }

    private static void deliverRobotStub(Scanner in) {
        System.out.println("\n--- Delivery of Robots (Prompt Only) ---");
        int customerId = readInt(in, "Customer ID: ");
        int robotId = readInt(in, "Robot ID to deliver: ");
        String address = readLine(in, "Delivery address: ");
        String deliveryDate = readLine(in, "Delivery date/time: ");
        String carId = readLine(in, "Driverless car identifier: ");

        System.out.println("Robot delivered (stub). Robot " + robotId +
                " scheduled for delivery to customer " + customerId + " at " + address +
                " at " + deliveryDate + " using car " + carId + ".");
    }

    private static void pickupRobotStub(Scanner in) {
        System.out.println("\n--- Pickup of Robots (Prompt Only) ---");
        readInt(in, "Customer ID: ");
        int robotId = readInt(in, "Robot ID to pick up: ");
        String address = readLine(in, "Pickup address: ");
        String pickupDate = readLine(in, "Pickup date/time: ");
        String carId = readLine(in, "Driverless car identifier: ");

        System.out.println("Robot pickup scheduled (stub). Robot " + robotId +
                " scheduled for pickup from " + address +
                " at " + pickupDate + " using car " + carId + ".");
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

    // ---------------- ENTITY CLASSES ----------------

    static class Customer {
        private final int customerId;
        private String name;
        private String phone;

        Customer(int customerId, String name, String phone) {
            this.customerId = customerId;
            this.name = name;
            this.phone = phone;
        }

        int getCustomerId() {
            return customerId;
        }

        String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }
                
        void setPhone(String phone) {
            this.phone = phone;
        }

        @Override
        public String toString() {
            return "Customer{id=" + customerId + ", name='" + name + "', phone='" + phone + "'}";
        }
    }

    static class Robot {
        private final int robotId;
        private String model;
        private String status;

        Robot(int robotId, String model, String status) {
            this.robotId = robotId;
            this.model = model;
            this.status = status;
        }

        int getRobotId() {
            return robotId;
        }

        String getModel() {
            return model;
        }

        void setModel(String model) {
            this.model = model;
        }

        void setStatus(String status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return "Robot{id=" + robotId + ", model='" + model + "', status='" + status + "'}";
        }
    }
}