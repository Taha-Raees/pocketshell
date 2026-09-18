# mc — laptop build. Android cross-build: android/build-ndk.sh
#
# Override on the command line:
#   SCANNER   wayland-scanner binary          (user-local prefix OK)
#   WL_XML    wayland.xml                     (from the libwayland install)
#   XDG_XML   xdg-shell.xml                   (from wayland-protocols)
#   P=/path   prefix for libwayland-server/libxkbcommon (adds -I/-L)

P ?= $(HOME)/p2-lab/prefix
SCANNER ?= $(P)/bin/wayland-scanner
WL_XML  ?= $(P)/share/wayland/wayland.xml
XDG_XML ?= $(HOME)/p2-lab/src/wayland-protocols-1.36/stable/xdg-shell/xdg-shell.xml

CC      ?= gcc
CFLAGS  ?= -O2 -g -Wall -Wextra -Wno-unused-parameter
CFLAGS  += -Isrc -Iclient $(shell pkg-config --cflags xkbcommon) -I$(P)/include
LDFLAGS += -L$(P)/lib -L$(P)/lib/x86_64-linux-gnu -Wl,-rpath,$(P)/lib -Wl,-rpath,$(P)/lib/x86_64-linux-gnu
LDLIBS  += -lwayland-server -lxkbcommon

GEN = src/protocol-wayland-server.h src/protocol-wayland-server.c \
      src/protocol-xdg-shell-server.h src/protocol-xdg-shell.c \
      client/xdg-shell-client-protocol.h client/protocol-xdg-shell-client.c

MC_OBJS = src/main.o src/mc_core.o src/shm.o src/shell.o src/seat.o \
          src/protocol-wayland-server.o src/protocol-xdg-shell.o \
          src/backends/sink_x11.o src/backends/sink_ppm.o
CLIENT_OBJS = client/simple-client.o client/protocol-xdg-shell-client.o

all: build/mc build/simple-client

$(GEN):
	@mkdir -p build
	$(SCANNER) server-header $(WL_XML) src/protocol-wayland-server.h
	$(SCANNER) private-code  $(WL_XML) src/protocol-wayland-server.c
	$(SCANNER) server-header $(XDG_XML) src/protocol-xdg-shell-server.h
	$(SCANNER) private-code  $(XDG_XML) src/protocol-xdg-shell.c
	$(SCANNER) client-header $(XDG_XML) client/xdg-shell-client-protocol.h
	$(SCANNER) private-code  $(XDG_XML) client/protocol-xdg-shell-client.c

build/mc: $(MC_OBJS)
	$(CC) $(CFLAGS) -o $@ $(MC_OBJS) $(LDFLAGS) $(LDLIBS) -lX11

build/simple-client: $(CLIENT_OBJS)
	$(CC) $(CFLAGS) -o $@ $(CLIENT_OBJS) $(LDFLAGS) -lwayland-client

src/%.o: src/%.c $(GEN)
	$(CC) $(CFLAGS) -c -o $@ $<
src/backends/%.o: src/backends/%.c $(GEN)
	$(CC) $(CFLAGS) -c -o $@ $<
client/%.o: client/%.c $(GEN)
	$(CC) $(CFLAGS) -c -o $@ $<
src/protocol-wayland-server.o: src/protocol-wayland-server.c
	$(CC) $(CFLAGS) -c -o $@ $<
src/protocol-xdg-shell.o: src/protocol-xdg-shell.c
	$(CC) $(CFLAGS) -c -o $@ $<
client/protocol-xdg-shell-client.o: client/protocol-xdg-shell-client.c
	$(CC) $(CFLAGS) -c -o $@ $<

run: build/mc
	XDG_RUNTIME_DIR=$${XDG_RUNTIME_DIR:-/tmp/mc-xdg} ./build/mc --sink x11

clean:
	rm -rf build $(GEN) src/*.o src/backends/*.o client/*.o
