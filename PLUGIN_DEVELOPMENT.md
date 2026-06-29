# HawkDeskPOS Plugin Development Guide

## Overview

Plugins are standard Java JARs dropped into the `plugins/` directory next to the app.
On startup the app discovers them automatically using Java's ServiceLoader API.
Plugins can contribute full-screen panels to the CardLayout and buttons to the top nav bar,
and optionally access the database via Hibernate.

---

## Prerequisites

- Java 17+
- Maven (or any build tool)
- A copy of the plugin API classes (see below)

---

## Step 1 — Set Up Your Maven Project

Create a new Maven project for your plugin.

```xml
<!-- pom.xml -->
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.yourcompany</groupId>
    <artifactId>my-hawkpos-plugin</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
    </properties>

    <dependencies>
        <!-- Plugin API — provided at runtime by the host app -->
        <dependency>
            <groupId>com.olympus.system</groupId>
            <artifactId>hawkdeskpos-plugin-api</artifactId>
            <version>1.0.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

> **Note:** The plugin API classes (`Plugin`, `PluginMeta`, `PluginContext`) are already
> on the classpath at runtime because the host app provides them. Mark the dependency as
> `provided` so they are not bundled into your plugin JAR.

---

## Step 2 — Implement the `Plugin` Interface

Create your main plugin class:

```java
package com.yourcompany.myplugin;

import com.olympus.system.hawkdeskpos.plugin.Plugin;
import com.olympus.system.hawkdeskpos.plugin.PluginContext;
import com.olympus.system.hawkdeskpos.plugin.PluginMeta;

public class MyPlugin implements Plugin {

    @Override
    public PluginMeta getMeta() {
        return new PluginMeta(
            "com.yourcompany.myplugin",   // unique id
            "My Plugin",                  // display name
            "1.0.0",                      // version
            "Does something useful",      // description
            "Your Name",                  // author
            "1.0.0"                       // minimum app version
        );
    }

    @Override
    public void onLoad(PluginContext ctx) {
        // Register your panel under a unique card name
        ctx.registerPanel("MY_PLUGIN_PANEL", MyPanel::new);

        // Add a nav bar button that navigates to your panel
        ctx.addNavItem("My Plugin", "MY_PLUGIN_PANEL");
    }

    @Override
    public void onUnload() {
        // Clean up resources (timers, connections, etc.) if needed
    }
}
```

---

## Step 3 — Create Your Panel

Your panel is a standard `JPanel`. It will be shown in the app's CardLayout when the user
clicks your nav button.

```java
package com.yourcompany.myplugin;

import javax.swing.*;
import java.awt.*;

public class MyPanel extends JPanel {

    public MyPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(0xF0, 0xF2, 0xF5));

        JLabel label = new JLabel("Hello from My Plugin!", SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 20f));
        add(label, BorderLayout.CENTER);
    }
}
```

### Navigating back to the dashboard

To provide a "Back" button, use `Home.navigate()` via reflection or simply use the card name
constant `"DASHBOARD"`:

```java
// Navigate to any registered card by name
JButton back = new JButton("← Back");
back.addActionListener(e -> {
    // Use the public static navigate method from Home
    try {
        Class<?> home = Class.forName("com.olympus.system.hawkdeskpos.frontend.Home");
        home.getMethod("navigate", String.class).invoke(null, "DASHBOARD");
    } catch (Exception ex) {
        ex.printStackTrace();
    }
});
```

---

## Step 4 — Register the Plugin with ServiceLoader

Create the ServiceLoader descriptor file at:

```
src/main/resources/META-INF/services/com.olympus.system.hawkdeskpos.plugin.Plugin
```

The file content is the fully-qualified class name of your Plugin implementation:

```
com.yourcompany.myplugin.MyPlugin
```

If your plugin has multiple `Plugin` implementations, list each one on a separate line.

---

## Step 5 — Access the Database (Optional)

If your plugin needs to read or write data, request the `SessionFactory` from the context:

```java
@Override
public void onLoad(PluginContext ctx) {
    SessionFactory sf = ctx.getSessionFactory();  // null if not available

    ctx.registerPanel("MY_PLUGIN_PANEL", () -> new MyPanel(sf));
    ctx.addNavItem("My Plugin", "MY_PLUGIN_PANEL");
}
```

Your panel can then open Hibernate sessions normally:

```java
public class MyPanel extends JPanel {
    private final SessionFactory sf;

    public MyPanel(SessionFactory sf) {
        this.sf = sf;
        // use sf.openSession() to query or persist data
    }
}
```

> **Important:** Always close sessions in a `finally` block or use try-with-resources.
> Leaking sessions will degrade app performance.

---

## Step 6 — Build the JAR

```bash
mvn package
```

This produces `target/my-hawkpos-plugin-1.0.0.jar`.

---

## Step 7 — Install the Plugin

**Option A — Copy manually:**

Place the JAR into the `plugins/` directory next to the app executable:

```
HawkDeskPOS.jar
plugins/
  my-hawkpos-plugin-1.0.0.jar
```

If a `plugins/` directory does not exist next to the app, the app will create one on first
launch, or fall back to `~/.hawkdeskpos/plugins/`.

**Option B — Install from the app:**

1. Log in and go to **Settings → Plugins**
2. Click **+ Install Plugin**
3. Select your JAR file
4. Restart the app

---

## Step 8 — Verify

Restart HawkDeskPOS. Your plugin's nav button will appear in the top bar (subject to user
permissions), and your panel will be reachable by clicking it.

Check the application log for confirmation:

```
INFO: Loaded plugin: My Plugin v1.0.0
```

If loading fails, the log will show a warning with the reason.

---

## Plugin Directory Structure

A well-structured plugin project looks like this:

```
my-hawkpos-plugin/
├── pom.xml
└── src/main/
    ├── java/com/yourcompany/myplugin/
    │   ├── MyPlugin.java          ← Plugin implementation
    │   └── MyPanel.java           ← UI panel(s)
    └── resources/
        └── META-INF/services/
            └── com.olympus.system.hawkdeskpos.plugin.Plugin
```

---

## Plugin API Reference

| Interface / Class | Description |
|---|---|
| `Plugin` | Implement this. `onLoad(ctx)` is called once at startup. |
| `PluginMeta` | Record: `id`, `name`, `version`, `description`, `author`, `minAppVersion` |
| `PluginContext` | Passed to `onLoad()`. Use it to register panels, nav items, and get the SessionFactory. |
| `PluginRegistry` | Internal singleton — do not use directly from plugins. |
| `PluginLoader` | Internal loader — do not use directly from plugins. |

### PluginContext methods

| Method | Description |
|---|---|
| `registerPanel(cardName, panelFactory)` | Register a `JPanel` under a unique card name string. |
| `addNavItem(label, cardName)` | Add a button to the top nav bar pointing to your card. |
| `getSessionFactory()` | Get the Hibernate SessionFactory for DB access. May return `null`. |

---

## Tips

- **Card names must be unique.** Use a reverse-domain prefix to avoid clashes, e.g. `"COM_YOURCOMPANY_MYPLUGIN"`.
- **Plugin classes are isolated.** Each plugin JAR gets its own `URLClassLoader`. Plugins cannot access each other's classes directly.
- **Swing thread safety.** Any UI updates from background threads must be wrapped in `SwingUtilities.invokeLater()`.
- **Errors are contained.** If your `onLoad()` throws, the host app logs a warning and continues. Other plugins and built-in features are unaffected.
- **Restart required.** Installing or removing a plugin via the Settings UI takes effect after restarting the app.
