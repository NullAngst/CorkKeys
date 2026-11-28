#!/usr/bin/env python3
"""Reads a uiautomator dump for the device test.

  ui.py keys DUMP PKG          every CorkKeys node: desc<TAB>cx<TAB>cy<TAB>left<TAB>top<TAB>right<TAB>bottom
  ui.py field DUMP             the first EditText's centre: cx<TAB>cy
  ui.py text DUMP              the first EditText's text
"""
import re
import sys
import xml.etree.ElementTree as ET


def nodes(path):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return []
    return list(root.iter("node"))


def bounds(n):
    m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", n.get("bounds", ""))
    return tuple(int(v) for v in m.groups()) if m else None


def main():
    cmd, path = sys.argv[1], sys.argv[2]
    ns = nodes(path)
    if cmd == "keys":
        pkg = sys.argv[3]
        for n in ns:
            if n.get("package") != pkg:
                continue
            b = bounds(n)
            desc = n.get("content-desc") or n.get("text") or ""
            if not b or not desc:
                continue
            l, t, r, bt = b
            print(f"{desc}\t{(l + r) // 2}\t{(t + bt) // 2}\t{l}\t{t}\t{r}\t{bt}")
    elif cmd in ("field", "text"):
        for n in ns:
            if n.get("class") == "android.widget.EditText":
                if cmd == "text":
                    print(n.get("text", ""))
                else:
                    l, t, r, b = bounds(n)
                    print(f"{(l + r) // 2}\t{(t + b) // 2}")
                return


main()
