#!/bin/bash
set -e

# Install Node.js dependencies
npm install

# Prepare Clojure dependencies
clojure -P

# Compile ClojureScript
npx shadow-cljs compile app


# Install the compiled app
cp -r build/* /app/bin/
mv /app/bin/app.js /app/bin/lobjur
chmod +x /app/bin/lobjur

# Install icons
mkdir -p /app/share/
cp -r data/icons /app/share/

# Install desktop file
install -D data/com.ranfdev.Lobjur.desktop /app/share/applications/com.ranfdev.Lobjur.desktop

# Install metainfo
install -D data/com.ranfdev.Lobjur.metainfo.xml /app/share/metainfo/com.ranfdev.Lobjur.metainfo.xml
