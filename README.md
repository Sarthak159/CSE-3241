# Community Robotics Management Console

A Java console application for managing customers, robots, rentals, returns, and delivery assignments in SQLite using the `community_robotic.db` schema.

## Project Files

- `WarehouseApp.java`: main application source
- `community_robotic.db`: default SQLite database used by the app
- `Warehouse.db`: older warehouse-style database kept in the repo, but no longer the default target
- `lib/sqlite-jdbc-3.36.0.jar`: SQLite JDBC driver

## Features

- Add, edit, delete, search, and list customers from `Customer`
- Add, edit, delete, search, and list robots across `Robot` and `Asset`
- Record rentals in `Rental`
- Return equipment by updating `Rental.returnDate`
- Record delivery and pickup assignments in `Delivers`
- Validate that the selected database matches the expected community schema before opening the menu

## Database Tables Used

The app expects these community-schema tables:

- `Customer`
- `Community_Facility`
- `Asset`
- `Robot`
- `Rental`
- `Driverless_Car`
- `Delivers`
- `Warranty`
- `Order_Request_Facility`

Notes about the model:

- Robot status, model, year, and order request live on `Asset`, not `Robot`.
- Returning a rental sets `Rental.returnDate` and moves the robot asset back to `Available`.
- Delivery and pickup assignments are both recorded in `Delivers`.
- New robots must use a valid `model` + `year` + `orderRequestNum` combination that already exists in `Warranty`.

## Requirements

- Java JDK installed
- SQLite JDBC jar in `lib/`

## Compile

From the project folder:

```bash
javac WarehouseApp.java
```

## Run

Use the default community database:

```bash
java -cp ".:lib/sqlite-jdbc-3.36.0.jar" WarehouseApp
```

Use a specific SQLite file that matches the same schema:

```bash
java -cp ".:lib/sqlite-jdbc-3.36.0.jar" WarehouseApp /path/to/community_robotic.db
```

You can also set an environment variable:

```bash
export COMMUNITY_DB_PATH=/path/to/community_robotic.db
```

The app still accepts the older `WAREHOUSE_DB_PATH` variable as a fallback.

If you want to suppress newer Java native-access warnings from the SQLite driver, use:

```bash
java --enable-native-access=ALL-UNNAMED -cp ".:lib/sqlite-jdbc-3.36.0.jar" WarehouseApp
```

## Successful Database Connection

When the application starts correctly, it prints a message like:

```text
Connected to SQLite database successfully: /full/path/to/community_robotic.db
```

## Main Menu

The program provides these options:

1. Manage Customers
2. Manage Robots
3. Rent Robots
4. Return Equipment
5. Record Delivery Assignment
6. Record Pickup Assignment
7. Exit

## Notes

- The default database file is `community_robotic.db`.
- `Warehouse.db` is no longer the expected schema for current app behavior.
- The app validates the schema at startup instead of creating missing tables automatically.
- If warehouse-era `EQUIPMENT_RETURN`, `DELIVERY`, or `PICKUP` tables are present in the selected community database, the app removes those legacy leftovers on startup because their foreign keys conflict with the community schema.
