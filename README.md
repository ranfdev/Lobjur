# Lobjur

A native GNOME client for Lobsters and Hacker News, built with GTK4, libadwaita and ClojureScript (GJS runtime). 

<a href='https://flathub.org/apps/details/com.ranfdev.Lobjur'><img width='240' alt='Download on Flathub' src='https://flathub.org/assets/badges/flathub-badge-en.png'/></a>

## Screenshot
![Home page screenshot](https://github.com/ranfdev/Lobjur/blob/master/data/screenshots/1.png?raw=true)





## Features

- Browse multiple sources (Lobsters and Hacker News) and choose feeds
- Collapsible threaded comments
- Read articles in-app with an integrated web view
- Sidebar navigation and mobile-friendly controls
- On-demand loading and pagination

## Development

This project is built to be compiled and packaged using Flatpak. To build locally with flatpak-builder run:

```sh
flatpak-builder --force-clean build-dir com.ranfdev.Lobjur.json
```

Alternatively install directly from Flathub: https://flathub.org/apps/details/com.ranfdev.Lobjur


The following will continuosly watch for file changes and will recompile the changed files to JavaScript.

```sh
npx shadow-cljs watch app
```

The following will actually run the app. The app is able to connect to the compiler, to provide hot-reload on some components.

```sh
gjs build/app.js
```

Run the Html2Gtk widgets demo script:

```sh
npm run demo:html2gtk
```

Once the app is running, you can also inspect its internal state with a REPL, using your favorite editor integration or running

```sh
npx shadow-cljs cljs-repl app
```

## Release

For who mantains the app.

- Create a dist folder using

```sh
./build-aux/make-dist.sh
```

- Upload the dist archive as a github release. 
- Wait for the flathub build bot to create an update.

