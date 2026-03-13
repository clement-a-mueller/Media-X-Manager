#!/usr/bin/env python3
"""
MediaX Linux Client — streams music controlled from your Android app.

Requirements:
    pip install requests pillow

Also needs mpv:
    sudo apt install mpv
"""

import argparse
import io
import json
import os
import socket
import subprocess
import sys
import tempfile
import threading
import time
import tkinter as tk
import tkinter.simpledialog

try:
    import requests
except ImportError:
    print("Missing dependency: pip install requests pillow")
    sys.exit(1)

try:
    from PIL import Image, ImageTk, ImageOps
    HAS_PIL = True
except ImportError:
    HAS_PIL = False

# ─── Constants ────────────────────────────────────────────────────────────────

DEFAULT_PORT    = 8765
CONNECT_TIMEOUT = 3
POLL_INTERVAL   = 1.0

BG      = "#0e0e0e"
BG2     = "#181818"
BG3     = "#242424"
ACCENT  = "#a78bfa"
GREEN   = "#4ade80"
RED     = "#f87171"
WHITE   = "#f1f1f1"
MUTED   = "#777777"
ART_SIZE = 96


# ─── API Client ───────────────────────────────────────────────────────────────

class MediaXClient:
    def __init__(self, host: str, port: int = DEFAULT_PORT):
        self.base = f"http://{host}:{port}"
        self.host = host

    def ping(self) -> bool:
        try:
            r = requests.get(f"{self.base}/ping", timeout=CONNECT_TIMEOUT)
            return r.text.strip() == "pong"
        except Exception:
            return False

    def now_playing(self) -> dict:
        try:
            r = requests.get(f"{self.base}/now-playing", timeout=CONNECT_TIMEOUT)
            r.raise_for_status()
            return r.json()
        except Exception:
            return {"id": None, "isPlaying": False, "_error": True}

    def remote_pause(self) -> bool:
        """Tell the phone to pause so only the PC plays."""
        try:
            r = requests.post(f"{self.base}/command/pause", timeout=CONNECT_TIMEOUT)
            return r.ok
        except Exception:
            return False

    def remote_play(self) -> bool:
        """Tell the phone to resume when PC disconnects."""
        try:
            r = requests.post(f"{self.base}/command/resume", timeout=CONNECT_TIMEOUT)
            return r.ok
        except Exception:
            return False

    def track_ended(self) -> bool:
        """Notify the phone that mpv finished the current track naturally."""
        try:
            r = requests.get(f"{self.base}/command/ended", timeout=CONNECT_TIMEOUT)
            return r.ok
        except Exception:
            return False

    def album_art(self, track_id: str):
        if not HAS_PIL:
            return None
        try:
            r = requests.get(f"{self.base}/art/{track_id}", timeout=CONNECT_TIMEOUT)
            if r.status_code == 200:
                img = Image.open(io.BytesIO(r.content))
                img = ImageOps.fit(img, (ART_SIZE, ART_SIZE), Image.LANCZOS)
                return ImageTk.PhotoImage(img)
        except Exception:
            pass
        return None

    def stream_url(self, track_id: str) -> str:
        return f"{self.base}/stream/{track_id}"


# ─── Player (mpv IPC) ─────────────────────────────────────────────────────────

class Player:
    """
    Wraps mpv with an IPC socket so we can pause/resume and seek without
    killing and restarting the process (which caused the "resets on pause" bug).
    """

    def __init__(self):
        self._proc       = None
        self._lock       = threading.Lock()
        self._ipc_path   = os.path.join(tempfile.gettempdir(), "mediax_mpv.sock")
        self.current_id  = None
        self._paused     = False

    # ── IPC helpers ───────────────────────────────────────────────────────────

    def _send_ipc(self, command: list) -> bool:
        """Send a JSON command to mpv's IPC socket. Returns True on success."""
        try:
            with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as s:
                s.settimeout(1.0)
                s.connect(self._ipc_path)
                msg = json.dumps({"command": command}) + "\n"
                s.sendall(msg.encode())
            print(f"[IPC OK] {command}")
            return True
        except Exception as e:
            print(f"[IPC FAIL] {command} — {e}")
            return False

    def _get_ipc(self, property_name: str):
        """Get a property value from mpv via IPC. Returns None on failure."""
        try:
            with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as s:
                s.settimeout(1.0)
                s.connect(self._ipc_path)
                msg = json.dumps({"command": ["get_property", property_name]}) + "\n"
                s.sendall(msg.encode())
                data = s.recv(4096).decode()
                resp = json.loads(data.strip().split("\n")[0])
                if resp.get("error") == "success":
                    return resp.get("data")
        except Exception:
            pass
        return None

    def _wait_for_ipc(self, timeout: float = 3.0) -> bool:
        """Block until the IPC socket is available or timeout expires."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as s:
                    s.settimeout(0.5)
                    s.connect(self._ipc_path)
                return True
            except Exception:
                time.sleep(0.1)
        return False

    # ── Playback control ──────────────────────────────────────────────────────

    def play(self, url: str, track_id: str):
        """Start a new stream. Kills any existing mpv process first."""
        self._hard_stop()
        # Remove stale socket if present
        try:
            os.unlink(self._ipc_path)
        except FileNotFoundError:
            pass

        with self._lock:
            self._proc = subprocess.Popen(
                [
                    "mpv",
                    "--no-video",
                    "--really-quiet",
                    f"--input-ipc-server={self._ipc_path}",
                    url,
                ],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            self.current_id = track_id
            self._paused    = False

        # Wait for IPC socket in background so play() returns immediately
        threading.Thread(target=self._wait_for_ipc, daemon=True).start()

    def pause(self):
        """Pause playback without stopping the stream."""
        if self._send_ipc(["set_property", "pause", True]):
            self._paused = True

    def resume(self):
        """Resume playback from the current position."""
        if self._send_ipc(["set_property", "pause", False]):
            self._paused = False

    def seek_to_progress(self, progress: float, duration_ms: float):
        """
        Seek to a position given as 0.0–1.0 progress and total duration in ms.
        mpv expects seconds for absolute seeks.
        """
        if duration_ms <= 0:
            return
        position_sec = (progress * duration_ms) / 1000.0
        self._send_ipc(["seek", round(position_sec, 2), "absolute"])

    def seek_to_ms(self, position_ms: float):
        """Seek to an absolute position in milliseconds."""
        self._send_ipc(["seek", round(position_ms / 1000.0, 2), "absolute"])

    def get_position_sec(self) -> float | None:
        """Ask mpv for its current playback position in seconds via IPC."""
        return self._get_ipc("time-pos")

    def is_running(self) -> bool:
        with self._lock:
            return self._proc is not None and self._proc.poll() is None

    def is_paused(self) -> bool:
        return self._paused

    def finished_naturally(self) -> bool:
        """True if mpv exited on its own (track ended) rather than being stopped by us."""
        with self._lock:
            return (
                self._proc is not None
                and self._proc.poll() is not None  # process exited
                and self.current_id is not None    # we had a track playing
                and not self._paused               # we didn't pause it
            )

    def stop(self):
        self._hard_stop()

    def _hard_stop(self):
        with self._lock:
            if self._proc and self._proc.poll() is None:
                self._send_ipc(["quit"])
                try:
                    self._proc.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    self._proc.kill()
            self._proc      = None
            self.current_id = None
            self._paused    = False


# ─── Poller ───────────────────────────────────────────────────────────────────

class PlaybackPoller:
    def __init__(self, client, player, on_update, on_lost):
        self._client    = client
        self._player    = player
        self._on_update = on_update
        self._on_lost   = on_lost
        self._stop_evt  = threading.Event()

    def start(self):
        threading.Thread(target=self._loop, daemon=True).start()

    def stop(self):
        self._stop_evt.set()

    def _loop(self):
        consecutive_errors = 0
        while not self._stop_evt.is_set():

            # ── Check if mpv finished the track naturally ──────────────────
            if self._player.finished_naturally():
                print("[ENDED] mpv finished track naturally, notifying phone")
                # Clear state before calling ended so finished_naturally()
                # doesn't fire again on the next loop iteration
                with self._player._lock:
                    finished_id = self._player.current_id
                    self._player.current_id = None
                    self._player._proc = None
                print(f"[ENDED] track was {finished_id!r}")
                self._client.track_ended()
                # Give the phone a moment to advance the queue before polling
                self._stop_evt.wait(POLL_INTERVAL)
                continue

            info = self._client.now_playing()

            if info.get("_error"):
                consecutive_errors += 1
                if consecutive_errors >= 3:
                    self._on_lost()
                    return
            else:
                consecutive_errors = 0
                self._on_update(info)

                track_id    = info.get("id")
                is_playing  = info.get("isPlaying", False)
                position_ms = float(info.get("position", 0))   # ms from Android
                duration_ms = float(info.get("duration", 0))   # ms from Android

                if track_id and is_playing:
                    if self._player.current_id != track_id:
                        print(f"[TRACK CHANGE] {self._player.current_id!r} → {track_id!r}, pos={position_ms}ms")
                        self._player.play(self._client.stream_url(track_id), track_id)
                        threading.Thread(
                            target=self._seek_after_ready,
                            args=(position_ms,),
                            daemon=True
                        ).start()
                    else:
                        if self._player.is_paused():
                            self._player.resume()

                        mpv_pos_sec = self._player.get_position_sec()
                        android_sec = position_ms / 1000.0
                        print(f"[SYNC] android={android_sec:.1f}s  mpv={mpv_pos_sec}s  duration={duration_ms/1000:.1f}s")
                        if mpv_pos_sec is not None and duration_ms > 0:
                            drift = abs(mpv_pos_sec - android_sec)
                            if drift > 5.0:
                                print(f"[SEEK] drift={drift:.1f}s → seeking to {android_sec:.1f}s")
                                self._player.seek_to_ms(position_ms)

                else:
                    if self._player.is_running() and not self._player.is_paused():
                        print("[PAUSE] Android paused, pausing mpv")
                        self._player.pause()

            self._stop_evt.wait(POLL_INTERVAL)

    def _seek_after_ready(self, position_ms: float):
        """Wait for IPC socket then seek to the Android playback position."""
        print(f"[SEEK_AFTER_READY] waiting for IPC, target={position_ms:.0f}ms")
        ready = self._player._wait_for_ipc(timeout=5.0)
        print(f"[SEEK_AFTER_READY] IPC ready={ready}")
        if ready and position_ms > 1000:
            self._player.seek_to_ms(position_ms)


# ─── GUI ──────────────────────────────────────────────────────────────────────

class App(tk.Tk):
    def __init__(self, host: str):
        super().__init__()
        self.title("MediaX")
        self.geometry("400x160")
        self.minsize(400, 160)
        self.configure(bg=BG)
        self.resizable(False, False)

        self._host    = host
        self._client  = None
        self._player  = Player()
        self._poller  = None
        self._art_ref = None
        self._last_id = None

        self._build_ui()
        self._do_connect(host)

    def _build_ui(self):
        # ── top bar ──────────────────────────────────────────────────────────
        top = tk.Frame(self, bg=BG)
        top.pack(fill="x", padx=20, pady=(16, 0))

        tk.Label(top, text="MediaX", font=("Helvetica", 15, "bold"),
                 fg=WHITE, bg=BG).pack(side="left")

        self._reload_btn = tk.Label(
            top, text="⟳", font=("Helvetica", 18), fg=MUTED, bg=BG, cursor="hand2"
        )
        self._reload_btn.pack(side="right")
        self._reload_btn.bind("<Button-1>", lambda e: self._on_reload())
        self._reload_btn.bind("<Enter>",    lambda e: self._reload_btn.config(fg=WHITE))
        self._reload_btn.bind("<Leave>",    lambda e: self._reload_btn.config(fg=MUTED))

        # ── status row ────────────────────────────────────────────────────────
        status_row = tk.Frame(self, bg=BG)
        status_row.pack(fill="x", padx=20, pady=(6, 0))

        self._dot = tk.Label(status_row, text="●", font=("Helvetica", 8),
                             fg=MUTED, bg=BG)
        self._dot.pack(side="left")

        self._status_lbl = tk.Label(status_row, text="Connecting…",
                                    font=("Helvetica", 10), fg=MUTED, bg=BG)
        self._status_lbl.pack(side="left", padx=(5, 0))

        # ── now-playing card ──────────────────────────────────────────────────
        self._card = tk.Frame(self, bg=BG2, padx=12, pady=10)

        self._art_lbl = tk.Label(self._card, bg=BG3, width=ART_SIZE, height=ART_SIZE)

        info = tk.Frame(self._card, bg=BG2)
        self._title_lbl = tk.Label(info, text="", font=("Helvetica", 12, "bold"),
                                    fg=WHITE, bg=BG2, anchor="w",
                                    wraplength=260, justify="left")
        self._title_lbl.pack(anchor="w")

        self._artist_lbl = tk.Label(info, text="", font=("Helvetica", 10),
                                     fg=MUTED, bg=BG2, anchor="w",
                                     wraplength=260, justify="left")
        self._artist_lbl.pack(anchor="w", pady=(2, 0))

        self._album_lbl = tk.Label(info, text="", font=("Helvetica", 9),
                                    fg=MUTED, bg=BG2, anchor="w",
                                    wraplength=260, justify="left")
        self._album_lbl.pack(anchor="w", pady=(1, 0))

        self._art_lbl.pack(side="left", padx=(0, 12))
        info.pack(side="left", fill="both", expand=True)

    # ── Connection ────────────────────────────────────────────────────────────

    def _do_connect(self, host: str):
        self._set_status("Connecting…", MUTED, MUTED)
        self._client = MediaXClient(host)
        threading.Thread(target=lambda: self.after(
            0, self._on_connected if self._client.ping() else self._on_failed
        ), daemon=True).start()

    def _on_connected(self):
        self._set_status(f"Connected · {self._host}:{DEFAULT_PORT}", GREEN, GREEN)
        self._poller = PlaybackPoller(
            client    = self._client,
            player    = self._player,
            on_update = lambda info: self.after(0, lambda: self._apply(info)),
            on_lost   = lambda: self.after(0, self._on_lost),
        )
        self._poller.start()

    def _on_failed(self):
        self._set_status("Cannot reach device — check IP and that the app is open", RED, RED)
        self._hide_card()

    def _on_lost(self):
        if self._poller:
            self._poller.stop()
            self._poller = None
        self._player.stop()
        # Resume phone playback since PC is no longer streaming
        if self._client:
            self._client.remote_play()
        self._set_status("Connection lost — press ⟳ to reconnect", RED, RED)
        self._hide_card()

    def _on_reload(self):
        if self._poller:
            self._poller.stop()
            self._poller = None
        self._player.stop()
        self._hide_card()
        self._last_id = None

        current_fg = self._status_lbl.cget("fg")
        if current_fg == RED:
            new_host = tkinter.simpledialog.askstring(
                "Reconnect", "Enter device IP:",
                initialvalue=self._host, parent=self
            )
            if new_host:
                self._host = new_host.strip()

        self._do_connect(self._host)

    # ── Now-playing ───────────────────────────────────────────────────────────

    def _apply(self, info: dict):
        track_id   = info.get("id")
        is_playing = info.get("isPlaying", False)

        if track_id and is_playing:
            self._set_status(f"Streaming · {self._host}", GREEN, GREEN)
            self._title_lbl.config(text=info.get("title", ""))
            self._artist_lbl.config(text=info.get("artist", ""))
            self._album_lbl.config(text=info.get("album", ""))

            if track_id != self._last_id:
                self._last_id = track_id
                self._set_art(None)
                threading.Thread(target=self._fetch_art, args=(track_id,), daemon=True).start()

            self._show_card()
        else:
            # Show paused state rather than hiding the card
            if track_id:
                self._set_status(f"Paused · {self._host}", MUTED, MUTED)
                self._show_card()
            else:
                self._set_status(f"Connected · {self._host}:{DEFAULT_PORT}", GREEN, GREEN)
                self._hide_card()
                self._last_id = None

    def _fetch_art(self, track_id: str):
        photo = self._client.album_art(track_id)
        self.after(0, lambda: self._set_art(photo))

    def _set_art(self, photo):
        self._art_ref = photo
        if photo:
            self._art_lbl.config(image=photo, width=ART_SIZE, height=ART_SIZE, bg=BG2)
        else:
            self._art_lbl.config(image="", width=ART_SIZE, height=ART_SIZE, bg=BG3)

    def _show_card(self):
        if not self._card.winfo_ismapped():
            self._card.pack(fill="x", padx=12, pady=(10, 12))
            self.geometry("400x220")

    def _hide_card(self):
        if self._card.winfo_ismapped():
            self._card.pack_forget()
            self.geometry("400x160")

    def _set_status(self, text: str, fg: str, dot: str):
        self._status_lbl.config(text=text, fg=fg)
        self._dot.config(fg=dot)

    def destroy(self):
        if self._poller:
            self._poller.stop()
        self._player.stop()
        if self._client:
            self._client.remote_play()
        super().destroy()


# ─── Entry point ──────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="MediaX Linux Client")
    parser.add_argument("--host", default=None)
    args = parser.parse_args()

    host = args.host
    if not host:
        root = tk.Tk()
        root.withdraw()
        host = tkinter.simpledialog.askstring(
            "Connect to MediaX",
            "Enter your Android device's IP address\n"
            "(find it in Settings → Stream Server):",
        )
        root.destroy()
        if not host:
            sys.exit(0)

    App(host.strip()).mainloop()


if __name__ == "__main__":
    main()