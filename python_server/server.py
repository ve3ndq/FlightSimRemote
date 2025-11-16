import json
import socket
import threading
import signal
import sys

HOST = "0.0.0.0"
PORT = 5555  # match default in Android app
shutdown_event = threading.Event()

def handle_client(conn, addr):
    print(f"[NEW CONNECTION] {addr}")
    with conn:
        buffer = b""
        while not shutdown_event.is_set():
            conn.settimeout(1.0)
            try:
                data = conn.recv(1024)
            except socket.timeout:
                continue
            if not data:
                break
            buffer += data
            while b"\n" in buffer:
                line, buffer = buffer.split(b"\n", 1)
                if not line.strip():
                    continue
                try:
                    msg = json.loads(line.decode("utf-8"))
                    handle_message(msg, addr)
                except json.JSONDecodeError as e:
                    print(f"[JSON ERROR] {e}: {line!r}")

def handle_message(msg, addr):
    msg_type = msg.get("type")
    cmd_id = msg.get("id")
    print(f"[MESSAGE] from {addr}: type={msg_type}, id={cmd_id}")
    
    # Check for quit command
    if msg_type == "command" and cmd_id == "QUIT_SERVER":
        print("[SHUTDOWN] Quit command received, shutting down server...")
        shutdown_event.set()
        return
    
    if msg_type == "command" and cmd_id:
        # TODO: map cmd_id to SimConnect events here
        # e.g., if cmd_id == "GEAR_TOGGLE": send_simconnect_event("GEAR_TOGGLE")
        pass

def signal_handler(sig, frame):
    print("\n[SHUTDOWN] Ctrl+C detected, shutting down server...")
    shutdown_event.set()
    sys.exit(0)

def start_server():
    signal.signal(signal.SIGINT, signal_handler)
    
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind((HOST, PORT))
        s.listen()
        s.settimeout(1.0)
        print(f"[LISTENING] on {HOST}:{PORT}")
        print("[INFO] Press Ctrl+C to quit, or send QUIT_SERVER command from Android app")
        
        while not shutdown_event.is_set():
            try:
                conn, addr = s.accept()
                thread = threading.Thread(target=handle_client, args=(conn, addr), daemon=True)
                thread.start()
            except socket.timeout:
                continue
        
        print("[SHUTDOWN] Server stopped")

if __name__ == "__main__":
    start_server()
