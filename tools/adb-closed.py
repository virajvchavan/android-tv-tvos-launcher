#!/usr/bin/env python3
"""Usage: tools/adb-closed.py <tv ip> [port]

Only "Connection refused" proves the port is closed. A timeout or
"No route to host" can be macOS Local Network privacy blocking this Mac.
"""
import errno, socket, sys

ip = sys.argv[1]
port = int(sys.argv[2]) if len(sys.argv) > 2 else 5555
s = socket.socket()
s.settimeout(5)
try:
    s.connect((ip, port))
    print(f"OPEN: {ip}:{port} accepts connections. ADB is still reachable.")
    sys.exit(1)
except ConnectionRefusedError:
    print(f"CLOSED: {ip}:{port} refused the connection. ADB is off.")
except socket.timeout:
    print(f"UNKNOWN: {ip}:{port} timed out. Is the TV on? Proves nothing.")
    sys.exit(2)
except OSError as e:
    print(f"UNKNOWN: {errno.errorcode.get(e.errno, e.errno)} ({e.strerror}). Proves nothing.")
    sys.exit(2)
finally:
    s.close()
