# HawkDeskPOS

A desktop Point-of-Sale application built with Java 17 + Swing (FlatLaf), Hibernate 6, MySQL, and Flyway.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Database Setup](#database-setup)
3. [Configuration](#configuration)
4. [Building the Application](#building-the-application)
5. [Running the Application](#running-the-application)
6. [First Login](#first-login)
7. [Installing on Another Machine](#installing-on-another-machine)
8. [Upgrading an Existing Installation](#upgrading-an-existing-installation)
9. [Project Structure](#project-structure)
10. [Troubleshooting](#troubleshooting)

---

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| Java (JDK or JRE) | 17 or newer | Must be on `PATH` |
| MySQL Server | 8.0 or newer | Running on `localhost:3306` |
| Maven | 3.9+ | Or use the included `mvnw` wrapper |

---

## Database Setup

The application uses **Flyway** for schema management. The database and all tables are created automatically on first launch — you do **not** need to run any SQL scripts manually.

### 1. Create the MySQL user / grant access

Log into MySQL as root and run:

```sql
CREATE DATABASE IF NOT EXISTS hawkdeskpos CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- If you want a dedicated user instead of root:
CREATE USER 'hawkpos'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON hawkdeskpos.* TO 'hawkpos'@'localhost';
FLUSH PRIVILEGES;
```

### 2. Update the connection settings

Edit `src/main/resources/hibernate.cfg.xml` (before building) **or** edit the shaded JAR's embedded config if deploying a pre-built JAR (see [Installing on Another Machine](#installing-on-another-machine)):

```xml
<property name="hibernate.connection.url">
    jdbc:mysql://localhost:3306/hawkdeskpos?createDatabaseIfNotExist=true&amp;zeroDateTimeBehavior=convertToNull&amp;serverTimezone=UTC
</property>
<property name="hibernate.connection.username">root</property>
<property name="hibernate.connection.password">1234</property>
```

> **Change the default password** (`1234`) before deploying to any real environment.

---

## Configuration

Application settings (shop name, tax rate, font size, etc.) are stored in a plain properties file:

```
{USER_HOME}/HawkDeskPOS/config.cnf
```

This file is created automatically on first launch. You can edit it manually while the app is closed. It is **not** inside the JAR — each machine has its own copy.

---

## Building the Application

Use the Maven wrapper (no separate Maven installation required):

```bash
# Windows
mvnw.cmd clean package -DskipTests

# macOS / Linux
./mvnw clean package -DskipTests
```

The build produces a single executable uber-JAR:

```
target/HawkDeskPOS-1.0-SNAPSHOT.jar
```

This JAR contains **all dependencies** (Hibernate, MySQL driver, FlatLaf, Flyway, JasperReports, etc.) and is the only file you need to distribute.

---

## Running the Application

```bash
java -jar HawkDeskPOS-1.0-SNAPSHOT.jar
```

On first run, Flyway automatically runs all 15 migration scripts in order and creates the complete schema.

### Recommended JVM flags (optional but helpful)

```bash
java -Xms256m -Xmx512m -jar HawkDeskPOS-1.0-SNAPSHOT.jar
```

### Windows shortcut

Create a `run.bat` file next to the JAR:

```bat
@echo off
javaw -Xms256m -Xmx512m -jar HawkDeskPOS-1.0-SNAPSHOT.jar
```

Double-clicking `run.bat` will launch the app without a console window.

---

## First Login

| Field | Value |
|---|---|
| Username | `Admin` |
| PIN | `1234` |
| Role | Owner (full access) |

> Change the Admin PIN immediately after first login via **Settings → User Management**.

---

## Installing on Another Machine

### What you need to copy

| File | Where to put it |
|---|---|
| `HawkDeskPOS-1.0-SNAPSHOT.jar` | Any folder, e.g. `C:\HawkPOS\` |
| `run.bat` (optional) | Same folder as the JAR |

The `config.cnf` is generated fresh on the new machine — you do not need to copy it unless you want to transfer settings.

### Step-by-step

1. **Install Java 17+** on the target machine.
   - Download from [https://adoptium.net](https://adoptium.net)
   - Verify: `java -version`

2. **Install MySQL 8** on the target machine (or point to an existing MySQL server).
   - Create the `hawkdeskpos` database and a user (see [Database Setup](#database-setup)).

3. **Edit the DB credentials inside the JAR** if they differ from the defaults (`root` / `1234`).

   The easiest way is to rebuild with the correct `hibernate.cfg.xml` before packaging. Alternatively, unzip the JAR, edit the file, and repack:

   ```bash
   # Unpack
   mkdir hawkpos-edit && cd hawkpos-edit
   jar xf ../HawkDeskPOS-1.0-SNAPSHOT.jar

   # Edit the config
   notepad hibernate.cfg.xml

   # Repack
   jar cfm ../HawkDeskPOS-1.0-SNAPSHOT.jar META-INF/MANIFEST.MF .
   ```

4. **Copy the JAR** to `C:\HawkPOS\` (or any folder) on the target machine.

5. **Run the JAR** — Flyway creates the full schema automatically on first launch:
   ```bash
   java -jar HawkDeskPOS-1.0-SNAPSHOT.jar
   ```

6. **Log in** with `Admin` / `1234` and configure shop details in **Settings → Shop Details**.

---

## Upgrading an Existing Installation

1. Build the new JAR: `mvnw.cmd clean package -DskipTests`
2. Stop the running application.
3. Replace the old JAR with the new one.
4. Start the application — Flyway detects which migrations have not yet run and applies only the new ones automatically.
   - Already-applied migrations are **never re-run**.
   - Existing data is preserved.

> If the target machine has a database from before migrations were introduced, Flyway is configured with `baselineOnMigrate=true` at version 6 — it will skip older scripts and apply only newer ones.

---

## Project Structure

```
HawkDeskPOS/
├── pom.xml                          Maven build descriptor
├── mvnw / mvnw.cmd                  Maven wrapper scripts
└── src/main/
    ├── java/com/olympus/system/hawkdeskpos/
    │   ├── HawkDeskPOS.java         Application entry point
    │   ├── db/
    │   │   ├── dao/                 Hibernate entity classes
    │   │   └── util/Controller.java SessionFactory + Flyway bootstrap
    │   ├── dto/                     Immutable record-based DTOs
    │   ├── frontend/
    │   │   ├── Home.java            Main window + CardLayout navigation
    │   │   ├── admin/               Settings, User Management, Backup
    │   │   ├── components/          Shared UI components (NavBar, CardPanel…)
    │   │   ├── finance/             GRN History
    │   │   ├── reports/             Reports panel
    │   │   ├── sale/                New Sale, Sales History, Find Invoice,
    │   │   │                        Goods Return, Returns dashboard
    │   │   └── stock/               View Stock, Add/Edit Item,
    │   │                            Receive Stock, Stock Adjustment, Low Stock
    │   ├── service/                 Business logic (SaleService, StockService…)
    │   ├── session/                 SessionContext, Permission enum
    │   └── util/Configs.java        config.cnf reader/writer
    └── resources/
        ├── hibernate.cfg.xml        DB connection + Hibernate settings
        └── db/migration/            Flyway SQL scripts (V1 – V15)
```

---

## Troubleshooting

### App fails to start — "Communications link failure"
- MySQL is not running, or the host/port in `hibernate.cfg.xml` is wrong.
- Verify: `mysql -u root -p` connects successfully.

### App fails to start — "Access denied for user"
- Wrong username or password in `hibernate.cfg.xml`.

### App fails to start — "Validate schema" error
- The DB schema does not match what Hibernate expects.
- This usually means a new migration file was added but Flyway did not run (e.g., the JAR was rebuilt without running the app).
- Fix: launch the app normally — Flyway will apply the missing migration.

### Flyway checksum mismatch
- A migration SQL file was edited **after** it was already applied to the DB.
- Never modify a committed migration file. Add a new versioned file instead.

### "config.cnf not found" / settings reset after restart
- The file lives at `%USERPROFILE%\HawkDeskPOS\config.cnf` on Windows.
- Make sure the user running the app has write access to their home directory.

### Blank screen or UI rendering issues
- Ensure Java 17+ is being used: `java -version`
- The app uses FlatLaf; very old Java 8/11 builds are not supported.
