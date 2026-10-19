#!/usr/bin/env python3
"""Writes All India Radio's live stations (from akashvani.gov.in) to the app's assets, as JSON.

The page lists every station in a script: '276': { name: '...', image: '...', live_url: '...', page: '...' }.
Run at build time, so the app gets the current list; on any failure the app ships without it.
"""
import json, re, ssl, sys, urllib.request

PAGE = "https://akashvani.gov.in/radio/live.php"
OUT = sys.argv[1]

# The site's certificate has been seen expired; the links found are checked below instead.
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
req = urllib.request.Request(PAGE, headers={"User-Agent": "Mozilla/5.0 (Linux; Android 14) Vaanalai/1.0"})
text = urllib.request.urlopen(req, timeout=30, context=ctx).read().decode("utf-8", "replace")
text = re.sub(r"(?<![:'\"])//[^\n]*", "", text)  # drop commented-out lines, not the // in links

LINK = re.compile(r"^https://[A-Za-z0-9.-]+\.(cdn\.bitgravity\.com|wavespb\.com)/[A-Za-z0-9/_.-]+\.m3u8$")
stations, seen = [], set()
for m in re.finditer(r"'(\d+)'\s*:\s*\{([^{}]*)\}", text):
    fields = dict(re.findall(r"(\w+)\s*:\s*'([^']*)'", m.group(2)))
    name, url = fields.get("name", "").strip(), fields.get("live_url", "").strip()
    if not name or not LINK.match(url) or url in seen:
        continue
    seen.add(url)
    image = fields.get("image", "")
    page = fields.get("page", "").strip("/").removeprefix("channel-").replace("-", " ")
    stations.append({"id": m.group(1), "name": name, "url": url,
                     "image": image if image.startswith("https://") else "", "group": page})

if len(stations) < 50:
    sys.exit(f"Only found {len(stations)} stations; the page has probably changed.")
with open(OUT, "w", encoding="utf-8") as f:
    json.dump(stations, f, ensure_ascii=False, separators=(",", ":"))
print(f"Saved {len(stations)} All India Radio stations")
