#!/usr/bin/env python3
import sys
import re
import xml.etree.ElementTree as ET

def find_coords(xml_path, query):
    try:
        tree = ET.parse(xml_path)
    except Exception as e:
        return None

    for node in tree.iter('node'):
        text = node.attrib.get('text', '')
        desc = node.attrib.get('content-desc', '')
        if query in text or query in desc:
            bounds = node.attrib.get('bounds', '')
            m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                return (x1 + x2) // 2, (y1 + y2) // 2
    return None

if __name__ == '__main__':
    if len(sys.argv) < 3:
        sys.exit(1)
    xml_path = sys.argv[1]
    query = sys.argv[2]
    coords = find_coords(xml_path, query)
    if coords:
        print(f"{coords[0]} {coords[1]}")
        sys.exit(0)
    sys.exit(1)
