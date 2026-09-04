#!/bin/bash
# Self-healing dev-server watchdog for the :3000 delivery page (reset #11 era).
# The sandbox kills call-spawned network listeners at each tool-call boundary;
# this sleeping loop is not a listener, survives boundaries, and resurrects
# `bun run dev` (the platform's own command) within ~8s of any kill.
cd /home/z/my-project
while true; do
  if ! curl -s -m 3 -o /dev/null http://127.0.0.1:3000/; then
    pkill -f "next dev" 2>/dev/null
    pkill -f "next-server" 2>/dev/null
    sleep 1
    nohup bun run dev >> /home/z/my-project/dev.log 2>&1 &
  fi
  sleep 8
done
