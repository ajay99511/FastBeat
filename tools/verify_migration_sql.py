#!/usr/bin/env python3
"""Verify docs/migration_v1_to_v5.sql against the exported Room schemas.

Every CREATE statement in the document must be byte-identical to the corresponding
`createSql` in 5.json (with Room's ${TABLE_NAME} placeholder substituted). A transcription
error in that file becomes a production data-loss bug, so it is checked mechanically and
never by eye.

Exit 0 = document matches the schema. Exit 1 = it does not.
Run from the repository root:  python tools/verify_migration_sql.py
"""
import io
import json
import re
import sqlite3
import sys

SCHEMA_DIR = "app/schemas/com.local.offlinemediaplayer.data.db.AppDatabase/"
DOC = "docs/migration_v1_to_v5.sql"
KOTLIN = "app/src/main/java/com/local/offlinemediaplayer/data/db/Migrations.kt"

# Seed values for the three columns added to playback_history. These are NOT in the exported
# schema -- they exist only as Kotlin property defaults in Entities.kt -- so they are asserted
# here explicitly. If Entities.kt changes, this must change with it.
EXPECTED_SEEDS = {"duration": "0", "audioTrackIndex": "-1", "subtitleTrackIndex": "-1"}


def load(version):
    with io.open(f"{SCHEMA_DIR}{version}.json", encoding="utf-8") as fh:
        return json.load(fh)["database"]


def statements(text):
    """Strip comments and blank lines, return the SQL statements."""
    sql = "\n".join(l for l in text.splitlines() if not l.lstrip().startswith("--"))
    return [s.strip() + ";" for s in sql.split(";") if s.strip()]


def main():
    v1, v5 = load(1), load(5)
    e1 = {e["tableName"]: e for e in v1["entities"]}
    e5 = {e["tableName"]: e for e in v5["entities"]}
    doc = io.open(DOC, encoding="utf-8").read()
    stmts = statements(doc)
    failures, checks = [], 0

    # --- every CREATE TABLE must match 5.json byte-for-byte ---
    for tbl in (t for t in e5 if t not in e1):
        want = e5[tbl]["createSql"].replace("${TABLE_NAME}", tbl) + ";"
        checks += 1
        if want not in stmts:
            failures.append(f"CREATE TABLE for `{tbl}` missing or not byte-identical to 5.json")

    # --- every CREATE INDEX must match 5.json byte-for-byte ---
    for tbl in (t for t in e5 if t not in e1):
        for ix in e5[tbl].get("indices", []):
            want = ix["createSql"].replace("${TABLE_NAME}", tbl) + ";"
            checks += 1
            if want not in stmts:
                failures.append(f"CREATE INDEX `{ix['name']}` on `{tbl}` missing or not byte-identical")

    # --- ALTER TABLE ADD COLUMN: one per column added to playback_history, correct type/seed ---
    old_cols = {f["columnName"] for f in e1["playback_history"]["fields"]}
    added = [f for f in e5["playback_history"]["fields"] if f["columnName"] not in old_cols]
    if {f["columnName"] for f in added} != set(EXPECTED_SEEDS):
        failures.append(f"columns added to playback_history changed: {[f['columnName'] for f in added]}")
    for f in added:
        col = f["columnName"]
        checks += 1
        want = (f"ALTER TABLE `playback_history` ADD COLUMN `{col}` "
                f"{f['affinity']} NOT NULL DEFAULT {EXPECTED_SEEDS.get(col)};")
        if want not in stmts:
            failures.append(f"ALTER TABLE for `{col}` missing or wrong:\n    expected: {want}")
        if not f["notNull"]:
            failures.append(f"`{col}` is no longer NOT NULL in 5.json -- revisit the DEFAULT clause")
        if f.get("defaultValue") is not None:
            failures.append(f"`{col}` now declares defaultValue={f['defaultValue']!r} in 5.json -- "
                            "DR-3's premise has changed, re-read it")

    # --- tables that must NOT be touched ---
    checks += 1
    if e1["media_analytics"]["createSql"] != e5["media_analytics"]["createSql"]:
        failures.append("media_analytics differs between v1 and v5 -- the document says it is unchanged")
    for stmt in stmts:
        if re.search(r"\bmedia_analytics\b", stmt):
            failures.append(f"document contains a statement touching media_analytics: {stmt[:60]}")

    # --- no stray statements ---
    expected_count = len(added) + sum(
        1 + len(e5[t].get("indices", [])) for t in e5 if t not in e1
    )
    checks += 1
    if len(stmts) != expected_count:
        failures.append(f"document has {len(stmts)} statements, expected exactly {expected_count}")

    # --- FK ordering: playlists must be created before its referrer ---
    order = [i for i, s in enumerate(stmts) if "CREATE TABLE" in s]
    pos = {t: i for i, s in enumerate(stmts) for t in e5 if f"`{t}`" in s and "CREATE TABLE" in s}
    for tbl in (t for t in e5 if t not in e1):
        for fk in e5[tbl].get("foreignKeys", []):
            checks += 1
            if fk["table"] in pos and pos.get(tbl, -1) < pos[fk["table"]]:
                failures.append(f"`{tbl}` is created before its FK target `{fk['table']}`")

    # --- Migrations.kt must contain exactly these statements, verbatim ---
    kt = io.open(KOTLIN, encoding="utf-8").read()
    # The trailing `,?` tolerates ktlint's trailing-comma-on-call-site rule (P4-D.2).
    kt_stmts = re.findall(r'execSQL\(\s*"([^"]*)"\s*,?\s*\)', kt)
    checks += 1
    if len(kt_stmts) != len(stmts):
        failures.append(f"{KOTLIN} has {len(kt_stmts)} execSQL calls, the .sql document has {len(stmts)}")
    doc_bare = [st.rstrip(";") for st in stmts]
    for want in doc_bare:
        checks += 1
        if want not in kt_stmts:
            failures.append(f"{KOTLIN} is missing or has altered: {want[:100]}")
    for got in kt_stmts:
        if got not in doc_bare:
            failures.append(f"{KOTLIN} has a statement absent from the .sql document: {got[:100]}")
    checks += 1
    if "Migration(1, 5)" not in kt:
        failures.append(f"{KOTLIN} does not declare Migration(1, 5)")

    # --- the statements must actually run, and must preserve existing rows ---
    try:
        con = sqlite3.connect(":memory:")
        con.execute("PRAGMA foreign_keys=ON")
        for t, e in e1.items():
            con.execute(e["createSql"].replace("${TABLE_NAME}", t))
        con.execute("INSERT INTO playback_history VALUES (42, 1234, 99999, 'video')")
        for st in doc_bare:
            con.execute(st)
        row = con.execute(
            "SELECT mediaId, position, duration, timestamp, mediaType, "
            "audioTrackIndex, subtitleTrackIndex FROM playback_history"
        ).fetchone()
        checks += 1
        if row != (42, 1234, 0, 99999, "video", -1, -1):
            failures.append(f"migration altered existing data: got {row}")
        for t, e in e5.items():
            got = {r[1]: (r[2].upper(), r[3]) for r in con.execute(f"PRAGMA table_info(`{t}`)")}
            want = {f["columnName"]: (f["affinity"].upper(), 1 if f["notNull"] else 0)
                    for f in e["fields"]}
            checks += 1
            if got != want:
                failures.append(f"post-migration structure of `{t}` differs from v5: "
                                f"{set(want.items()) ^ set(got.items())}")
    except sqlite3.Error as exc:
        failures.append(f"migration failed to execute on SQLite {sqlite3.sqlite_version}: {exc}")

    print(f"checked {checks} invariants against {SCHEMA_DIR}5.json "
          f"(incl. live execution on SQLite {sqlite3.sqlite_version})")
    if failures:
        print(f"\nFAIL ({len(failures)}):")
        for f in failures:
            print("  -", f)
        return 1
    print(f"OK - {DOC} matches the exported schema exactly "
          f"({len(stmts)} statements: {len(added)} ALTER, "
          f"{sum(1 for t in e5 if t not in e1)} CREATE TABLE, "
          f"{sum(len(e5[t].get('indices', [])) for t in e5 if t not in e1)} CREATE INDEX)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
