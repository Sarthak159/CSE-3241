# Warehouse Robot Management System

A Java console application for managing warehouse customers, robots, rentals, returns, deliveries, and pickups using SQLite.

## Project Files

- `WarehouseApp.java`: main application source
- `Warehouse.db`: SQLite database used by the app
- `lib/sqlite-jdbc-3.36.0.jar`: SQLite JDBC driver

## Features

- Add, edit, delete, search, and list customers
- Add, edit, delete, search, and list robots
- Record robot rentals
- Record equipment returns
- Record robot deliveries
- Record robot pickups
- Store all data in SQLite instead of temporary in-memory lists

## Database Tables

The application uses these tables in `Warehouse.db`:

- `CUSTOMER`
- `ROBOT`
- `RENTAL`
- `EQUIPMENT_RETURN`
- `DELIVERY`
- `PICKUP`

The app creates any missing tables automatically when it starts.

## Requirements

- Java JDK installed
- SQLite JDBC jar in `lib/`

## Compile

From the project folder:

```bash
javac WarehouseApp.java
```

## Run

```bash
java -cp ".:lib/sqlite-jdbc-3.36.0.jar" WarehouseApp
```

If you want to suppress newer Java native-access warnings from the SQLite driver, use:

```bash
java --enable-native-access=ALL-UNNAMED -cp ".:lib/sqlite-jdbc-3.36.0.jar" WarehouseApp
```

## Successful Database Connection

When the application starts correctly, it prints a message like:

```text
Connected to SQLite database successfully: /full/path/to/Warehouse.db
```

## Main Menu

The program provides these options:

1. Manage Customers
2. Manage Robots
3. Rent Robots
4. Return Equipment
5. Delivery of Robots
6. Pickup of Robots
7. Exit

## Notes

- Robot status is updated automatically when a rental is recorded or equipment is returned.
- Deliveries and pickups can be recorded by either `robot ID` or `rental ID`.
- The database file used by the current project is `Warehouse.db`.
