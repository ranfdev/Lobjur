# Copilot Instructions for Lobjur

## Project Overview

Lobjur is a native Lobsters and Hacker News client built with:
- **ClojureScript** compiled to JavaScript for the GNOME JavaScript (GJS) runtime
- **GTK4** and **libadwaita** for the UI
- **shadow-cljs** with a custom GJS target for compilation
- **HAL (Hypertext Application Language)** for API responses with hypermedia links

The app proxies and normalizes data from Lobsters and HackerNews APIs into a unified HAL-based interface.

## Build & Development Commands

### Development Workflow
```bash
# Start shadow-cljs watch (auto-recompile on file changes)
npx shadow-cljs watch app

# Run the app (in a separate terminal)
gjs build/app.js

# Connect to REPL (requires running watch)
npx shadow-cljs cljs-repl app
```

### Build & Release
```bash
# Compile for production (no watch)
npx shadow-cljs compile app

# Create distribution archive
./build-aux/make-dist.sh
```

## Architecture

### Key Architectural Patterns

#### 1. **URL Scheme Architecture**

The app uses a dual-scheme URL system:
- `in-process://api/*` - Internal API routes (proxied Lobsters/HN data)
- `https://*` - External resources (original article links, images)

**Router dispatch** (`api.router/GET`):
- Parses URL scheme using `api.url/parse-url`
- Routes `in-process://` to internal handlers
- Routes `https://` to external HTTP client (`lobjur.utils.http/GET`)
- Relative paths default to `in-process://api` for backward compatibility

#### 2. **HAL Hypermedia Format**

All API responses follow HAL structure:
```clojure
{:_links {:self {:href "in-process://api/stories/123"}
          :author {:href "in-process://api/users/jcs"}
          :external {:href "https://example.com/article"}}
 :_embedded {:comments [{...} {...}]}
 :title "Story Title"
 :score 42}
```

**Link prefixing**: `api.hal/link` automatically adds `in-process://api` to internal paths

**Navigation helpers** (`api.helpers`):
- `follow-link` - Fetch a linked resource by relation name
- `get-embedded-or-fetch` - Check `_embedded` first, then fetch `_links`
- Smart pagination helpers for collections

#### 3. **State Management**

- **Global state**: `lobjur.state/global-widgets` atom holds GTK widget references
- **Navigation stack**: State machine in `main.cljs` manages view stack
- **Transducer pattern**: `app-transducer` processes state transitions like `:push-story`, `:push-user`

#### 4. **GTK/GJS Integration**

- Import GTK/Adw via strings: `["gjs.gi.Gtk" :as Gtk]`
- Use `rollui.core/build-ui` for declarative widget construction
- Widget vectors: `[Gtk/Button :label "Click" :$clicked handler-fn]`
- Properties prefixed with `$` are signal handlers (e.g., `:$clicked`)

## Key Conventions

### Keep low nesting depth
- Avoid nesting code too much, because that results in a long list of parentheses that is difficult to read and understand. 
- If you find yourself nesting more than 3-4 levels, consider refactoring into smaller functions or using threading macros (`->`, `->>`) to flatten the structure. 

### ClojureScript for GJS

1. **Namespace structure**: Mirror directory structure (`api.router` → `src/main/api/router.cljs`)

2. **GJS imports**: Use string-based requires for GNOME libraries
   ```clojure
   (:require ["gjs.gi.Gtk" :as Gtk]
             ["gjs.gi.Adw" :as Adw])
   ```

3. **JavaScript interop**: Use `^js` type hints when calling GJS methods
   ```clojure
   (doto ^js widget
     (.set_visible true)
     (.add_css_class "card"))
   ```

4. **Async patterns**: Use `core.async` channels or promises for async operations

### API Client Patterns

1. **Route definitions**: Use keyword maps for route handlers in `api.router`
   ```clojure
   {:path "/feeds/:source/:feed"
    :params #{:source :feed}
    :handler (fn [{:keys [source feed page]}] ...)}
   ```

2. **HAL response construction**: Always use `api.hal` helpers
   ```clojure
   (hal/collection stories
                   :self (hal/link "/feeds/lobsters/hot")
                   :next (hal/link "/feeds/lobsters/hot?page=2"))
   ```

3. **URL construction**: Use `api.url/make-url` with explicit schemes
   ```clojure
   (url/make-url :in-process "/stories/123")
   ;; => "in-process://api/stories/123"
   ```

4. **Adapter pattern**: Normalize external APIs to internal format in `api.adapters`

### Widget Development

1. **Declarative UI**: Build widgets with nested vectors
   ```clojure
   [Gtk/Box
    :orientation Gtk/Orientation.VERTICAL
    :spacing 12
    [:children
     [Gtk/Label :label "Title"]
     [Gtk/Button :label "Click Me"]]]
   ```

2. **Signal handlers**: Prefix with `$`
   ```clojure
   [Gtk/Button
    :$clicked #(state/send [:action payload])]
   ```

3. **Styled widgets**: Use CSS classes from libadwaita
   ```clojure
   [Adw/ActionRow
    :css-classes ["card"]]
   ```

### State & Navigation

1. **State updates**: Send actions via `state/send`
   ```clojure
   (state/send [:push-story story-data])
   ```

2. **View stack**: Use `push-view` and `push-titled-view` for navigation
   ```clojure
   (push-titled-view state (comments/comments-view story) (:title story))
   ```

3. **Back navigation**: Always provide back button in header-start
   ```clojure
   {:header-start back-btn}
   ```

## Development Notes

### Compilation Output
- **Target file**: `build/app.js` (main entry point)
- **Libraries**: `build/jslibs/` (ClojureScript deps)
- **Data files**: `data/` (UI definitions, icons - copied to build/)

### Documentation and skills
- **Documentation**: Take advantage of the docs through the gnome-sdk-docs skill. You better lookup the docs instead of doing trial and error. 