---
name: browsing-gir
description: "Browse GObject Introspection (.gir) files to look up GTK, Adw, Gio, GLib, Soup, and other GNOME API definitions. Use when needing to find available methods, properties, signals, or types for GObject-based libraries."
---

# Browsing GObject Introspection Files

GObject Introspection (`.gir`) files are XML files describing the API of GNOME libraries. They are located at `/usr/share/gir-1.0/`.

The libraries used by this project and their corresponding files:

- `/usr/share/gir-1.0/Gtk-4.0.gir`
- `/usr/share/gir-1.0/Adw-1.gir`
- `/usr/share/gir-1.0/Gio-2.0.gir`
- `/usr/share/gir-1.0/GLib-2.0.gir`
- `/usr/share/gir-1.0/GObject-2.0.gir`
- `/usr/share/gir-1.0/Soup-3.0.gir`

These files are large XML documents. Use Grep to search for specific class names, method names, property names, or signal names rather than reading entire files. For example:

- To find a class: `Grep` for `<class name="Button"` in the relevant `.gir` file.
- To find a method: `Grep` for `<method name="set_label"`.
- To find properties of a class: `Grep` for `<property name=` near the class definition.
- To find signals: `Grep` for `<glib:signal name=`.
