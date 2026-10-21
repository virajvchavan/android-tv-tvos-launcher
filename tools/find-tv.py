#!/usr/bin/env python3
"""Finds the TV on the local network and saves its address in .tv-ip for the other scripts.

Usage: python3 tools/find-tv.py
Looks for devices on this Mac's /24 network with the ADB port (5555) open, then asks each
one for its model over adb. A device that hasn't approved this Mac shows as "unauthorized":
accept the prompt on the TV and run this again.
"""
import concurrent.futures
import os
import socket
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def local_ip():
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]


def adb_open(ip):
    with socket.socket() as s:
        # Some TVs answer slowly from standby; shorter timeouts miss them.
        s.settimeout(1.5)
        return ip if s.connect_ex((ip, 5555)) == 0 else None


def adb(serial, *args):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True).stdout.strip()


prefix = local_ip().rsplit(".", 1)[0]
with concurrent.futures.ThreadPoolExecutor(64) as pool:
    candidates = [ip for ip in pool.map(adb_open, (f"{prefix}.{i}" for i in range(1, 255))) if ip]

if not candidates:
    raise SystemExit(f"No device on {prefix}.x has port 5555 open. Is the TV on, with USB debugging on?")

found = None
for ip in candidates:
    serial = f"{ip}:5555"
    subprocess.run(["adb", "connect", serial], capture_output=True)
    state = subprocess.run(["adb", "-s", serial, "get-state"], capture_output=True, text=True)
    if state.returncode != 0:
        print(f"{ip}: {state.stderr.strip() or 'no answer'}")
        continue
    model = adb(serial, "shell", "getprop", "ro.product.model")
    print(f"{ip}: {model}")
    found = found or ip

if found:
    with open(os.path.join(ROOT, ".tv-ip"), "w") as f:
        f.write(found + "\n")
    print(f"Saved {found} to .tv-ip")
else:
    raise SystemExit("Found ADB ports but none approved this Mac yet. Accept the prompt on the TV and run again.")
