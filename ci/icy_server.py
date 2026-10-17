"""A tiny internet-radio server for the smoke test: plays an MP3 on a loop and, like real
stations, tells the player the song's name ("ICY" metadata) when asked to.
Usage: icy_server.py <mp3 file> <port> <song title>   (logs requests to stderr)"""
import http.server
import socketserver
import sys

MP3, PORT, TITLE = sys.argv[1], int(sys.argv[2]), sys.argv[3]
META_EVERY = 8192  # bytes of audio between song-name blocks


def meta_block(title):
    text = ("StreamTitle='%s';" % title.replace("'", "")).encode("utf-8")
    text += b"\0" * ((-len(text)) % 16)
    return bytes([len(text) // 16]) + text


class Station(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"

    def do_GET(self):
        icy = self.headers.get("Icy-MetaData") == "1"
        self.send_response(200)
        self.send_header("Content-Type", "audio/mpeg")
        self.send_header("icy-name", "Smoke Test FM")
        if icy:
            self.send_header("icy-metaint", str(META_EVERY))
        self.end_headers()
        audio = open(MP3, "rb").read()
        block = meta_block(TITLE)
        looped = audio * 2  # so a chunk can run on past the end into the start
        pos = 0
        try:
            while True:  # the stream never ends, like a radio station
                chunk = looped[pos:pos + META_EVERY]
                pos = (pos + META_EVERY) % len(audio)
                self.wfile.write(chunk)
                if icy:
                    self.wfile.write(block)
        except (BrokenPipeError, ConnectionResetError):
            pass


class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


Server(("", PORT), Station).serve_forever()
