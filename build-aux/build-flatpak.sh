#!/bin/bash
set -e

# Install Node.js dependencies
npm install

# Prepare Clojure dependencies
clojure -P

# Compile ClojureScript
npx shadow-cljs compile app

# Create the launcher script
cat > build/lobjur << 'LAUNCHER_EOF'
#!/usr/bin/gjs -m
import { main } from "./jslibs/main.js";
main();
LAUNCHER_EOF

# Install the launcher
install -D -m 755 build/lobjur /app/bin/lobjur

# Install JavaScript libraries
cp -r build/jslibs /app/bin/

# Install icons
mkdir -p /app/share/
cp -r data/icons /app/share/

# Install desktop file
install -D data/com.ranfdev.Lobjur.desktop /app/share/applications/com.ranfdev.Lobjur.desktop

# Install metainfo
install -D data/com.ranfdev.Lobjur.metainfo.xml /app/share/metainfo/com.ranfdev.Lobjur.metainfo.xml
