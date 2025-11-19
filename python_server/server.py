import json
import socket
import threading
import signal
import sys
from SimConnect import SimConnect, AircraftEvents

HOST = "0.0.0.0"
PORT = 5555  # match default in Android app
DISCOVERY_PORT = 5556  # UDP port for discovery
shutdown_event = threading.Event()
sim = None
aircraft_events = None
event_cache = {}

# Commands that need special handling (aliases or data payloads)
COMMAND_EVENT_MAP = {
    "AP_MASTER_TOGGLE": ("AP_MASTER", None),
    "AVIONICS_MASTER_ON": ("AVIONICS_MASTER_SET", 1),
    "AVIONICS_MASTER_OFF": ("AVIONICS_MASTER_SET", 0),
    "CARB_HEAT_ON": ("CARB_HEAT_SET", 1),
    "CARB_HEAT_OFF": ("CARB_HEAT_SET", 0),
    "PARK_BRAKE_TOGGLE": ("PARKING_BRAKES", None),
    "VIEW_COCKPIT": ("VIEW_VIRTUAL_COCKPIT_FORWARD", None),
    "VIEW_VIRTUAL_COCKPIT": ("VIEW_VIRTUAL_COCKPIT_FORWARD", None),
    "VIEW_EXTERNAL": ("CHASE_VIEW_TOGGLE", None),
    "VIEW_SPOT": ("CHASE_VIEW_TOGGLE", None),
    "VIEW_TOP_DOWN": ("VIEW_MAP_ORIENTATION_CYCLE", None),
    "VIEW_NEXT": ("NEXT_VIEW", None),
    "VIEW_PREVIOUS": ("PREV_VIEW", None),
}

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
        send_simconnect_event(cmd_id)

def send_simconnect_event(cmd_id):
    """Send event to SimConnect based on command ID"""
    global aircraft_events

    if not sim:
        print(f"[SIMCONNECT] Not connected, skipping event: {cmd_id}")
        return
    
    if aircraft_events is None:
        try:
            aircraft_events = AircraftEvents(sim)
        except Exception as e:
            print(f"[SIMCONNECT ERROR] Unable to init events: {e}")
            return

    try:
        event_name, data = COMMAND_EVENT_MAP.get(cmd_id, (cmd_id, None))

        handler = event_cache.get(event_name)
        if handler is None:
            handler = aircraft_events.find(event_name)
            if handler is None:
                raise KeyError(event_name)
            event_cache[event_name] = handler

        if data is None:
            handler()
        else:
            handler(data)

        print(f"[SIMCONNECT] Sent event: {event_name} (cmd={cmd_id})")
    except KeyError:
        print(f"[SIMCONNECT] Unknown event mapping for: {cmd_id} -> {event_name}")
    except Exception as e:
        print(f"[SIMCONNECT ERROR] {e}")

def signal_handler(sig, frame):
    print("\n[SHUTDOWN] Ctrl+C detected, shutting down server...")
    shutdown_event.set()
    sys.exit(0)

def discovery_listener():
    """Listen for UDP broadcast discovery messages and respond with server info"""
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as udp_sock:
        udp_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        udp_sock.bind(("", DISCOVERY_PORT))
        udp_sock.settimeout(1.0)
        print(f"[DISCOVERY] Listening on UDP port {DISCOVERY_PORT}")
        
        while not shutdown_event.is_set():
            try:
                data, addr = udp_sock.recvfrom(1024)
                message = data.decode("utf-8").strip()
                
                if message == "HOTKEYNDQ_DISCOVER":
                    print(f"[DISCOVERY] Request from {addr}")
                    # Get local IP address
                    local_ip = socket.gethostbyname(socket.gethostname())
                    response = json.dumps({
                        "type": "discovery_response",
                        "ip": local_ip,
                        "port": PORT,
                        "name": "HotKeyNDQ Server"
                    })
                    udp_sock.sendto(response.encode("utf-8"), addr)
                    print(f"[DISCOVERY] Sent response to {addr}: {local_ip}:{PORT}")
            except socket.timeout:
                continue
            except Exception as e:
                print(f"[DISCOVERY ERROR] {e}")

def start_server():
    global sim
    signal.signal(signal.SIGINT, signal_handler)
    
    # Initialize SimConnect
    try:
        sim = SimConnect()
        print("[SIMCONNECT] Connected to Flight Simulator")
    except Exception as e:
        print(f"[SIMCONNECT WARNING] Could not connect: {e}")
        print("[SIMCONNECT] Server will run but events won't be sent to simulator")
    
    # Start discovery listener thread
    discovery_thread = threading.Thread(target=discovery_listener, daemon=True)
    discovery_thread.start()
    
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
