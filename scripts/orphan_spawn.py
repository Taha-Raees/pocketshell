#!/usr/bin/env python3
"""Double-fork spawn: the spawned process is orphaned (re-parented to PID 1)
before this call's shell exits, mimicking how the gradle daemon survives
the sandbox's call-boundary reaper."""
import os, sys, subprocess

def orphan_spawn(argv, logpath):
    pid = os.fork()
    if pid > 0:
        os.waitpid(pid, 0)   # reap the intermediate child
        return               # grandchild is now orphaned -> PID 1
    # first child: new session, fork again
    os.setsid()
    pid2 = os.fork()
    if pid2 > 0:
        os._exit(0)          # intermediate exits -> grandchild reparented
    # grandchild: fully detached, redirect stdio, exec
    fd = os.open(logpath, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o644)
    os.dup2(fd, 1)
    os.dup2(fd, 2)
    devnull = os.open(os.devnull, os.O_RDONLY)
    os.dup2(devnull, 0)
    os.execvp(argv[0], argv)

if __name__ == "__main__":
    which = sys.argv[1]
    if which == "dev3000":
        orphan_spawn(["bun", "run", "dev"], "/home/z/my-project/dev.log")
    elif which == "ctrl3999":
        orphan_spawn(["python3", "-m", "http.server", "3999", "--bind", "127.0.0.1"],
                     "/home/z/my-project/ctrl3999.log")
