#!/usr/bin/env python3
"""Converts Ionicons SVGs (MIT, https://github.com/ionic-team/ionicons) into
Android VectorDrawables named ion_<name>.xml.

usage: ion2vd.py <ionicons/dist/svg dir> <res/drawable dir> name [name...]
"""
import re, sys, os
import xml.etree.ElementTree as ET

def f(v): return float(v) if v is not None else 0.0

ARGS = {"m": 2, "l": 2, "h": 1, "v": 1, "c": 6, "s": 4, "q": 4, "t": 2, "a": 7, "z": 0}
NUM = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")

def normalize(d):
    """Rewrites SVG path data with explicit separators. SVG allows arc flags to be glued to the
    next number ("a10 10 0 0128 5"); Android's path parsers do not, so they mangle the glyph."""
    out = []
    i, n = 0, len(d)
    cmd = None
    argi = 0
    while i < n:
        ch = d[i]
        if ch.isspace() or ch == ",":
            i += 1; continue
        if ch.isalpha():
            cmd = ch; argi = 0; out.append(ch); i += 1
            continue
        k = ARGS[cmd.lower()]
        if cmd.lower() == "a" and argi % 7 in (3, 4):
            out.append(ch); i += 1  # a single-digit flag
        else:
            m = NUM.match(d, i)
            if not m: raise ValueError(d[i:i+20])
            out.append(m.group(0)); i = m.end()
        argi += 1
    # join: letters stick to the following number, numbers separated by spaces
    res = ""
    for t in out:
        if res and not res[-1].isalpha() and not t.isalpha(): res += " "
        res += t
    return res

def circle(cx, cy, rx, ry):
    return (f"M{cx-rx},{cy}a{rx},{ry} 0 1,0 {2*rx},0a{rx},{ry} 0 1,0 {-2*rx},0z")

def rect(x, y, w, h, rx, ry):
    if rx <= 0 and ry <= 0:
        return f"M{x},{y}h{w}v{h}h{-w}z"
    if rx <= 0: rx = ry
    if ry <= 0: ry = rx
    rx = min(rx, w/2); ry = min(ry, h/2)
    return (f"M{x+rx},{y}h{w-2*rx}a{rx},{ry} 0 0,1 {rx},{ry}v{h-2*ry}"
            f"a{rx},{ry} 0 0,1 {-rx},{ry}h{-(w-2*rx)}a{rx},{ry} 0 0,1 {-rx},{-ry}"
            f"v{-(h-2*ry)}a{rx},{ry} 0 0,1 {rx},{-ry}z")

def convert(src, name):
    root = ET.parse(src).getroot()
    vb = root.get("viewBox", "0 0 512 512").split()
    w, h = vb[2], vb[3]
    out = [f'<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           f'    android:width="24dp" android:height="24dp"',
           f'    android:viewportWidth="{w}" android:viewportHeight="{h}">']
    for el in root.iter():
        tag = el.tag.split('}')[-1]
        if tag == "path": d = el.get("d")
        elif tag == "circle":
            r = f(el.get("r")); d = circle(f(el.get("cx")), f(el.get("cy")), r, r)
        elif tag == "ellipse":
            d = circle(f(el.get("cx")), f(el.get("cy")), f(el.get("rx")), f(el.get("ry")))
        elif tag == "rect":
            d = rect(f(el.get("x")), f(el.get("y")), f(el.get("width")), f(el.get("height")),
                     f(el.get("rx")), f(el.get("ry")))
        elif tag in ("polygon", "polyline"):
            pts = el.get("points").split()
            d = "M" + " L".join(pts) + ("z" if tag == "polygon" else "")
        elif tag == "line":
            d = f"M{el.get('x1')},{el.get('y1')}L{el.get('x2')},{el.get('y2')}"
        else: continue
        attrs = [f'android:pathData="{normalize(d)}"']
        fill = el.get("fill")
        stroke = el.get("stroke")
        if fill != "none": attrs.append('android:fillColor="#FFFFFFFF"')
        if el.get("fill-rule") == "evenodd": attrs.append('android:fillType="evenOdd"')
        if stroke and stroke != "none":
            attrs.append('android:strokeColor="#FFFFFFFF"')
            attrs.append(f'android:strokeWidth="{el.get("stroke-width", "1")}"')
            cap = el.get("stroke-linecap")
            if cap in ("round", "square"): attrs.append(f'android:strokeLineCap="{cap}"')
            join = el.get("stroke-linejoin")
            if join in ("round", "bevel"): attrs.append(f'android:strokeLineJoin="{join}"')
        out.append("    <path " + " ".join(attrs) + "/>")
    out.append("</vector>")
    return "\n".join(out) + "\n"

if __name__ == "__main__":
    srcdir, dst = sys.argv[1], sys.argv[2]
    for n in sys.argv[3:]:
        xml = convert(os.path.join(srcdir, n + ".svg"), n)
        with open(os.path.join(dst, "ion_" + n.replace("-", "_") + ".xml"), "w") as fh:
            fh.write(xml)
