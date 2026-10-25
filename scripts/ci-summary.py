#!/usr/bin/env python3
"""
Resumen en Markdown de las pruebas (Surefire) y la cobertura (JaCoCo) de la última ejecución
de `./mvnw verify`. En la CI se agrega al resumen del job de GitHub Actions; en local se puede
correr igual para ver los números:  python scripts/ci-summary.py
"""
import csv
import glob
import os
import sys
import xml.etree.ElementTree as ET

BASE_PACKAGE = "com.ucentral.desarrollos.backendparkspotter"


def tests_section():
    reports = glob.glob("target/surefire-reports/TEST-*.xml")
    if not reports:
        return ["**No se encontraron reportes de pruebas** (¿falló la compilación?)."], False

    total = failures = errors = skipped = 0
    time = 0.0
    failed_cases = []
    for path in reports:
        suite = ET.parse(path).getroot()
        total += int(suite.get("tests", 0))
        failures += int(suite.get("failures", 0))
        errors += int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
        time += float(suite.get("time", 0) or 0)
        for case in suite.findall("testcase"):
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is not None:
                message = (problem.get("message") or "").strip().splitlines()
                failed_cases.append((case.get("classname", "").split(".")[-1], case.get("name"),
                                     message[0][:160] if message else ""))

    ok = failures == 0 and errors == 0
    lines = [
        "## Pruebas",
        "",
        f"{'✅' if ok else '❌'} **{total - failures - errors - skipped} de {total} pruebas pasaron** "
        f"({failures} fallas, {errors} errores, {skipped} omitidas) en {time:.1f} s, {len(reports)} clases de prueba.",
        "",
    ]
    if failed_cases:
        lines += ["| Clase | Prueba | Mensaje |", "|---|---|---|"]
        lines += [f"| {cls} | {name} | {msg.replace('|', '/')} |" for cls, name, msg in failed_cases]
        lines.append("")
    return lines, ok


def coverage_section():
    path = "target/site/jacoco/jacoco.csv"
    if not os.path.exists(path):
        return ["## Cobertura", "", "No se generó el reporte de JaCoCo."]

    packages = {}
    totals = [0] * 6
    for row in csv.DictReader(open(path, encoding="utf-8")):
        name = row["PACKAGE"].replace(BASE_PACKAGE, "").lstrip(".") or "(raíz)"
        values = [int(row[k]) for k in ("LINE_MISSED", "LINE_COVERED", "BRANCH_MISSED",
                                        "BRANCH_COVERED", "METHOD_MISSED", "METHOD_COVERED")]
        accumulated = packages.setdefault(name, [0] * 6)
        for i, value in enumerate(values):
            accumulated[i] += value
            totals[i] += value

    def pct(missed, covered):
        return f"{covered / (missed + covered) * 100:.1f} %" if missed + covered else "—"

    lines = [
        "## Cobertura (JaCoCo)",
        "",
        f"**Líneas {pct(totals[0], totals[1])} · Ramas {pct(totals[2], totals[3])} · Métodos {pct(totals[4], totals[5])}** "
        "(mínimos: líneas 90 %, ramas 85 %, métodos 95 %)",
        "",
        "| Paquete | Líneas | Ramas | Métodos |",
        "|---|---|---|---|",
    ]
    for name, v in sorted(packages.items()):
        lines.append(f"| {name} | {pct(v[0], v[1])} | {pct(v[2], v[3])} | {pct(v[4], v[5])} |")
    lines += ["", "El reporte HTML completo está en el artefacto **reportes-pruebas** (`jacoco/index.html`)."]
    return lines


def main():
    # En la consola de Windows la salida no es UTF-8 por defecto (tildes y emojis)
    sys.stdout.reconfigure(encoding="utf-8")
    test_lines, ok = tests_section()
    print("\n".join(["# Resultado de la CI", ""] + test_lines + coverage_section()))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
