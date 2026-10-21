#!/usr/bin/env python3
"""
Convert a resource pack or datapack from the old Realm Arms / Realm Raids namespaces to Gunmetal.

    python convert_pack_to_gunmetal.py <pack folder or .zip> [output] [--dry-run]

What it does
  * assets/realmarms/**  and assets/realmraids/**  ->  assets/gunmetal/**
    data/realmarms/**    and data/realmraids/**    ->  data/gunmetal/**
  * In every text file (.json .jem .jpm .properties .mcmeta .txt .mcfunction .lang .jsonc .yml .toml .cfg)
    anywhere in the pack, the words `realmarms` and `realmraids` become `gunmetal`
    (realmarms:loaded -> gunmetal:loaded, item.realmraids.x -> item.gunmetal.x, #realmarms:guns, ...).
    Only whole words are changed: something like `realmraids_icon.png` is left alone and reported.
  * Two files landing on the same path:
      - sounds.json and lang/*.json are merged key by key;
      - any other file: the realmarms copy wins and the other is written next to it as
        <name>.from_<namespace><ext> so nothing is lost. All of these are listed in the report.
  * Binary files (png, ogg, ...) are copied unchanged.
  * The original pack is never modified. Output defaults to <name>-gunmetal (folder) or
    <name>-gunmetal.zip (zip), next to the input.
  * --dry-run prints the report without writing anything.
"""
import json, os, re, sys, zipfile
from collections import OrderedDict

OLD = ("realmarms", "realmraids")
NEW = "gunmetal"
TEXT_EXT = {".json", ".jem", ".jpm", ".properties", ".mcmeta", ".txt", ".mcfunction", ".lang",
            ".jsonc", ".json5", ".yml", ".yaml", ".toml", ".cfg"}
WORD = re.compile(r"(?<![A-Za-z0-9_\-])(realmarms|realmraids)(?![A-Za-z0-9_\-])")
ANY = re.compile(r"realmarms|realmraids", re.I)


def read_pack(src):
    files = OrderedDict()
    if os.path.isdir(src):
        for root, _, names in os.walk(src):
            for n in sorted(names):
                full = os.path.join(root, n)
                rel = os.path.relpath(full, src).replace(os.sep, "/")
                with open(full, "rb") as f:
                    files[rel] = f.read()
    else:
        with zipfile.ZipFile(src) as z:
            for info in z.infolist():
                if not info.is_dir():
                    files[info.filename] = z.read(info.filename)
    return files


def new_path(rel):
    parts = rel.split("/")
    # assets/<ns>/... or data/<ns>/..., possibly nested inside a top folder of a zip
    for i in range(len(parts) - 1):
        if parts[i] in ("assets", "data") and parts[i + 1] in OLD:
            old_ns = parts[i + 1]
            parts[i + 1] = NEW
            return "/".join(parts), old_ns
    return rel, None


def is_text(rel):
    return os.path.splitext(rel)[1].lower() in TEXT_EXT


def load_json(data):
    return json.loads(data.decode("utf-8-sig"), object_pairs_hook=OrderedDict)


def merge_json_keys(a, b, label, report):
    out = OrderedDict(a)
    for k, v in b.items():
        if k in out and out[k] != v:
            report["key_conflicts"].append(f"{label}: \"{k}\" defined in both (kept the realmarms one)")
            continue
        out[k] = v
    return out


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry = "--dry-run" in sys.argv
    if not args:
        print(__doc__)
        sys.exit(1)
    src = args[0].rstrip("/\\")
    is_zip = not os.path.isdir(src)
    if len(args) > 1:
        dst = args[1]
    else:
        base = src[:-4] if src.lower().endswith(".zip") else src
        dst = base + "-gunmetal" + (".zip" if is_zip else "")

    files = read_pack(src)
    report = {"moved": 0, "rewritten": [], "merged": [], "collisions": [], "key_conflicts": [],
              "left_alone": [], "bad_json": []}
    out = OrderedDict()
    origin = {}

    # realmarms first so it wins plain collisions
    order = sorted(files, key=lambda r: (0 if "/realmarms/" in "/" + r else 1, r))
    for rel in order:
        data = files[rel]
        target, old_ns = new_path(rel)
        if old_ns:
            report["moved"] += 1
        if is_text(rel):
            try:
                text = data.decode("utf-8-sig")
            except UnicodeDecodeError:
                text = None
            if text is not None:
                new_text, n = WORD.subn(NEW, text)
                if n:
                    report["rewritten"].append(f"{target}  ({n} id{'s' if n != 1 else ''})")
                for m in ANY.finditer(new_text):
                    line = new_text.count("\n", 0, m.start()) + 1
                    report["left_alone"].append(f"{target}:{line}  ...{new_text[max(0, m.start()-25):m.end()+25].strip()}...")
                data = new_text.encode("utf-8")
        if target in out:
            name = target.rsplit("/", 1)[-1]
            mergeable = name == "sounds.json" or ("/lang/" in target and name.endswith(".json"))
            if mergeable:
                try:
                    merged = merge_json_keys(load_json(out[target]), load_json(data), target, report)
                    out[target] = (json.dumps(merged, indent=2, ensure_ascii=False) + "\n").encode("utf-8")
                    report["merged"].append(f"{target}  (from {origin[target]} + {rel})")
                    continue
                except Exception as e:
                    report["bad_json"].append(f"{rel}: {e}")
            if out[target] == data:
                report["collisions"].append(f"{target}: identical copies in {origin[target]} and {rel}, kept one")
                continue
            stem, ext = os.path.splitext(target)
            side = f"{stem}.from_{old_ns or 'pack'}{ext}"
            out[side] = data
            report["collisions"].append(f"{target}: kept {origin[target]}; {rel} saved as {side}")
            continue
        out[target] = data
        origin[target] = rel

    # basic JSON sanity on everything we produced
    for rel, data in out.items():
        if os.path.splitext(rel)[1].lower() in (".json", ".jem", ".jpm", ".mcmeta"):
            try:
                load_json(data)
            except Exception as e:
                report["bad_json"].append(f"{rel}: {e} (was it valid before? EMF tolerates some sloppiness)")

    print(f"Input : {src}")
    print(f"Output: {dst}{'  (dry run, nothing written)' if dry else ''}")
    print(f"Files moved into the gunmetal namespace: {report['moved']}")
    for title, key in (("Text files with ids rewritten", "rewritten"),
                       ("Merged (sounds.json / lang)", "merged"),
                       ("Path collisions", "collisions"),
                       ("Key conflicts while merging", "key_conflicts"),
                       ("Old names NOT changed (part of a longer word; check by hand)", "left_alone"),
                       ("JSON that does not parse", "bad_json")):
        items = report[key]
        print(f"\n{title}: {len(items)}")
        for it in items:
            print("  " + it)

    if dry:
        return
    if is_zip or dst.lower().endswith(".zip"):
        with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as z:
            for rel, data in out.items():
                z.writestr(rel, data)
    else:
        if os.path.exists(dst):
            print(f"\n{dst} already exists; pick another output path.")
            sys.exit(1)
        for rel, data in out.items():
            p = os.path.join(dst, *rel.split("/"))
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, "wb") as f:
                f.write(data)
    print(f"\nWrote {len(out)} files to {dst}")


if __name__ == "__main__":
    main()
