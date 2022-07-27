{
  description = "Lobjur";

  inputs.flake-utils.url = "github:numtide/flake-utils";

  outputs = { self, nixpkgs, flake-utils }:
  flake-utils.lib.eachDefaultSystem (system:
  let pkgs = nixpkgs.legacyPackages.${system}; in
    rec {
      devShells.default = pkgs.mkShell {
        buildInputs = with pkgs; [
          gjs
          clojure
          clj-kondo
          clojure-lsp
          nodejs
          gtk4
          gobject-introspection
          libadwaita
          openjdk
          libsoup_3
          glib-networking
          glib
          gobject-introspection
        ];
      };
      #packages = flake-utils.lib.flattenTree {
      #  
      #};
      #defaultPackage = packages.hello;
      #apps.hello = flake-utils.lib.mkApp { drv = packages.hello; };
      #defaultApp = apps.hello;
    }
  );
}
