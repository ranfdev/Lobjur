#!/usr/bin/env sh

# Builds and copies all the needed flatpak files in the build folder
npx shadow-cljs compile app
cp -r data build/
tar -cf com.ranfdev.Lobjur.tar.xz build/ -J
