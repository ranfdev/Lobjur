# Lobjur

This a native https://lobste.rs client written using GTK4, libadwaita and ClojureScript (gjs runtime, custom target). 

## Screenshots
![Screenshot from 2022-07-27 23-32-17](https://user-images.githubusercontent.com/23294184/181376266-9d930837-750f-4f2c-a3d1-60cc5e88368b.png)
![Screenshot from 2022-07-27 23-32-26](https://user-images.githubusercontent.com/23294184/181376259-a008bf64-f2f4-47c1-92c9-9954d9fd1d7f.png)




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

