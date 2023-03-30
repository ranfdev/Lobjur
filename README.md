# Lobjur

This a native https://lobste.rs client written using GTK4, libadwaita and ClojureScript (gjs runtime, custom target). 

<a href='https://flathub.org/apps/details/com.ranfdev.Lobjur'><img width='240' alt='Download on Flathub' src='https://flathub.org/assets/badges/flathub-badge-en.png'/></a>

## Screenshot
![Home page screenshot](https://user-images.githubusercontent.com/23294184/188264118-76c8df44-10a4-4894-902e-66a006037d7f.png)





## Features
- Browse hottest and active stories
- Browse the comments of each story
- Browse stories by tag
- Lookup user info

## Developement

ClojureScript requires a decent amount of dependencies for compilation; they are listed in the file `flake.nix`.

Provided you have the [nix package manager](https://nixos.org/download.html) installed, with flakes enabled, you can enter inside a shell environment with all the required dependencies, by doing

```sh
nix develop
```

The following will continuosly watch for file changes and will recompile the changed files to JavaScript.

```sh
npx shadow-cljs watch app
```

The following will actually run the app. The app is able to connect to the compiler, to provide hot-reload on some components.

```sh
gjs build/app.js
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

