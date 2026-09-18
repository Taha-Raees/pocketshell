#!/bin/sh
# wayland-scanner codegen for mc. Run from gui-runtime/src.
# Args: SCANNER WL_XML XDG_XML
set -e
SCANNER="$1"; WL_XML="$2"; XDG_XML="$3"
"$SCANNER" server-header "$WL_XML" protocol-wayland-server.h
"$SCANNER" private-code  "$WL_XML" protocol-wayland-server.c
"$SCANNER" server-header "$XDG_XML" protocol-xdg-shell-server.h
"$SCANNER" private-code  "$XDG_XML" protocol-xdg-shell.c
echo "protocol code generated"
