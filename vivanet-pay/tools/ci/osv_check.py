#!/usr/bin/env python3
"""Revisa las dependencias Maven de Vivanet Pay contra la base pública OSV (https://osv.dev).

Uso: python3 -I osv_check.py <salida de `mvn dependency:list -DoutputFile=...`>
Sale con código 1 si alguna dependencia tiene vulnerabilidades conocidas, 2 si falla la consulta.
Solo se envían coordenadas públicas de Maven (grupo:artefacto y versión), nunca datos del proyecto.
"""
import json
import re
import sys
import urllib.request

LINE = re.compile(r"^\s*([\w.\-]+):([\w.\-]+):[\w\-]+(?::[\w.\-]+)?:([\w.\-]+):(compile|runtime|provided|test|system)\b")


def parse(path):
    deps = set()
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            m = LINE.match(line)
            if m:
                deps.add((f"{m.group(1)}:{m.group(2)}", m.group(3)))
    return sorted(deps)


def post(url, payload):
    req = urllib.request.Request(url, data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    deps = parse(sys.argv[1])
    if not deps:
        print("No se encontraron dependencias en el archivo.")
        return 2
    try:
        queries = [{"package": {"name": n, "ecosystem": "Maven"}, "version": v} for n, v in deps]
        results = post("https://api.osv.dev/v1/querybatch", {"queries": queries})["results"]
    except Exception as exc:  # noqa: BLE001 - informar y fallar de forma explícita
        print(f"No se pudo consultar OSV: {exc}")
        return 2
    found = []
    for (name, version), res in zip(deps, results):
        for vuln in res.get("vulns", []):
            found.append((name, version, vuln["id"]))
    print(f"Dependencias revisadas: {len(deps)}")
    if not found:
        print("Sin vulnerabilidades conocidas en OSV.")
        return 0
    print(f"Vulnerabilidades encontradas: {len(found)}")
    for name, version, vid in found:
        summary = ""
        try:
            summary = post_get(vid)
        except Exception:  # noqa: BLE001
            pass
        print(f"- {name} {version}: {vid} {summary}")
    return 1


def post_get(vid):
    with urllib.request.urlopen(f"https://api.osv.dev/v1/vulns/{vid}", timeout=30) as resp:
        data = json.load(resp)
    return "- " + (data.get("summary") or data.get("details", "")[:120]).replace("\n", " ")


if __name__ == "__main__":
    sys.exit(main())
