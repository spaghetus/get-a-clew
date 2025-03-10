{pkgs ? import <nixpkgs> {}, ...}:
with pkgs; let
  deps = [jdk libglvnd];
in
  mkShell {
    buildInputs = deps;
    LD_LIBRARY_PATH = lib.makeLibraryPath deps;
  }
