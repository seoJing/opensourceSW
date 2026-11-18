import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import sys
import time
from datetime import datetime, timezone
from urllib.error import URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

AXES = ("sweetness", "sourness", "bitterness")
SCHEMA = {
    "type": "object",
    "properties": {
        "status": {"type": "string", "enum": ["ESTIMATED", "UNKNOWN", "NOT_MENU"]},
        "taste": {"anyOf": [
            {"type": "object", "properties": {axis: {"type": "number", "minimum": 0, "maximum": 1} for axis in AXES},
             "required": list(AXES), "additionalProperties": False},
            {"type": "null"}
        ]},
        "reason": {"type": "string"}
    },
    "required": ["status", "taste", "reason"],
    "additionalProperties": False
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def input_hash(menu):
    return digest((menu["name"] + "\0" + (menu.get("description") or "")).encode("utf-8"))


def validate_estimate(value):
    if not isinstance(value, dict) or set(value) != {"status", "taste", "reason"}:
        raise ValueError("estimate must contain exactly status, taste, reason")
    if value["status"] not in ("ESTIMATED", "UNKNOWN", "NOT_MENU"):
        raise ValueError("unsupported estimate status")
    reason = value["reason"]
    if not isinstance(reason, str) or not reason.strip() or len(reason) > 2000:
        raise ValueError("reason must be nonblank and at most 2000 characters")
    taste = value["taste"]
    if value["status"] != "ESTIMATED":
        if taste is not None:
            raise ValueError("unknown/non-menu must have null taste")
    elif not isinstance(taste, dict) or set(taste) != set(AXES):
        raise ValueError("estimated item needs exactly three taste axes")
    else:
        for score in taste.values():
            if isinstance(score, bool) or not isinstance(score, (float, int)) or not math.isfinite(score) or not 0 <= score <= 1:
                raise ValueError("taste must be a finite number in [0,1]")
    return value


def request_json(base, endpoint, payload=None, timeout=180):
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = Request(base + endpoint, data=body, headers={"Content-Type": "application/json"})
    with urlopen(request, timeout=timeout) as response:
        return json.load(response)


def atomic_json(path, value):
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8") as output:
        json.dump(value, output, ensure_ascii=False, indent=2, allow_nan=False)
        output.write("\n")
        output.flush()
        os.fsync(output.fileno())
    temporary.replace(path)


def load_checkpoint(path, menus):
    records = {}
    if not path.exists():
        return records
    with path.open(encoding="utf-8") as source:
        for number, line in enumerate(source, 1):
            try:
                record = json.loads(line)
                menu = menus[record["menuId"]]
                if record["menuId"] in records or record["inputSha256"] != input_hash(menu):
                    raise ValueError("duplicate menu or changed input")
                basis = "NAME_AND_DESCRIPTION" if (menu.get("description") or "").strip() else "NAME_ONLY"
                if record["inputBasis"] != basis:
                    raise ValueError("input basis mismatch")
                validate_estimate({key: record[key] for key in ("status", "taste", "reason")})
                records[record["menuId"]] = record
            except (ValueError, KeyError, TypeError) as exception:
                raise ValueError(f"invalid checkpoint line {number}: {exception}") from exception
    return records


def run(arguments):
    parsed = urlparse(arguments.host)
    if parsed.scheme != "http" or parsed.hostname not in ("127.0.0.1", "localhost", "::1") or parsed.path not in ("", "/") or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("host must be a local HTTP Ollama origin")
    if arguments.limit < 1 or arguments.timeout < 1:
        raise ValueError("limit and timeout must be positive")
    if not arguments.profile_version or len(arguments.profile_version) > 128:
        raise ValueError("profile version must contain 1..128 characters")
    raw = arguments.catalog.read_bytes()
    catalog = json.loads(raw)
    menus = {menu["id"]: menu for menu in catalog["menus"]}
    if catalog.get("schemaVersion") != 1 or not menus or len(menus) != len(catalog["menus"]):
        raise ValueError("requires schemaVersion 1 and unique menu IDs")
    arguments.output.mkdir(parents=True, exist_ok=True)
    lock = arguments.output / ".generation.lock"
    with lock.open("x"):
        try:
            generate(arguments, raw, catalog, menus)
        finally:
            lock.unlink()


def generate(arguments, raw, catalog, menus):
    host = arguments.host.rstrip("/")
    prompt = Path(__file__).with_name("prompt.txt").read_text(encoding="utf-8")
    server = request_json(host, "/api/version")
    installed = request_json(host, "/api/tags")["models"]
    match = next((model for model in installed if model["name"] == arguments.model), None)
    if match is None:
        raise ValueError("model must already be installed; use ollama pull first")
    config = {
        "schemaVersion": 1, "profileVersion": arguments.profile_version,
        "dataVersion": catalog["dataVersion"], "catalogSha256": digest(raw),
        "definitionVersion": "taste-v1", "model": arguments.model,
        "modelDigest": match["digest"], "promptSha256": digest(prompt.encode("utf-8")),
        "ollamaVersion": server["version"],
        "options": {"temperature": 0, "seed": 42, "num_ctx": 4096, "num_predict": 512},
        "think": False, "generatorVersion": "taste-batch-v1",
        "responseSchemaSha256": digest(json.dumps(SCHEMA, sort_keys=True).encode("utf-8"))
    }
    manifest = arguments.output / "run.json"
    if manifest.exists():
        if json.loads(manifest.read_text()) != config:
            raise ValueError("run configuration changed; use a new output directory/profile version")
    else:
        atomic_json(manifest, config)
    (arguments.output / "prompt.txt").write_text(prompt, encoding="utf-8")
    checkpoint = arguments.output / "estimates.jsonl"
    records = load_checkpoint(checkpoint, menus)
    chosen = list(menus.values())
    if arguments.ids:
        ids = arguments.ids.read_text().splitlines()
        if len(ids) != len(set(ids)) or any(item not in menus for item in ids):
            raise ValueError("selection must contain unique known menu IDs")
        chosen = [menus[item] for item in ids]
    chosen = chosen[:arguments.limit]
    for menu in chosen:
        if menu["id"] in records:
            continue
        start = time.monotonic()
        response = request_json(host, "/api/chat", {
            "model": arguments.model, "stream": False, "think": False,
            "keep_alive": "5m", "format": SCHEMA, "options": config["options"],
            "messages": [
                {"role": "system", "content": prompt},
                {"role": "user", "content": json.dumps({"name": menu["name"], "description": menu.get("description")}, ensure_ascii=False)}
            ]
        }, arguments.timeout)
        if response.get("done") is not True or response.get("done_reason") != "stop":
            raise ValueError("incomplete model response; checkpoint preserved")
        estimate = validate_estimate(json.loads(response["message"]["content"]))
        record = {
            "menuId": menu["id"], "inputSha256": input_hash(menu),
            "inputBasis": "NAME_AND_DESCRIPTION" if (menu.get("description") or "").strip() else "NAME_ONLY",
            "generatedAt": datetime.now(timezone.utc).isoformat(), **estimate
        }
        with checkpoint.open("a", encoding="utf-8") as output:
            output.write(json.dumps(record, ensure_ascii=False, allow_nan=False) + "\n")
            output.flush()
            os.fsync(output.fileno())
        records[menu["id"]] = record
        print(f"{len(records)} {menu['id']} {record['status']} {time.monotonic() - start:.2f}s", flush=True)
    final_match = next((item for item in request_json(host, "/api/tags")["models"] if item["name"] == arguments.model), None)
    if final_match is None or final_match["digest"] != config["modelDigest"]:
        raise ValueError("model digest changed during generation; do not import this run")
    bundle = {key: config[key] for key in (
        "schemaVersion", "profileVersion", "dataVersion", "catalogSha256", "definitionVersion",
        "model", "modelDigest", "promptSha256", "ollamaVersion"
    )}
    bundle["records"] = list(records.values())
    atomic_json(arguments.output / "bundle.json", bundle)
    print(f"Bundle: {arguments.output / 'bundle.json'}; {len(records)} records; review status: UNREVIEWED")


def main():
    parser = argparse.ArgumentParser(description="Generate versioned cafe menu taste estimates using local Ollama")
    parser.add_argument("--catalog", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--profile-version", required=True)
    parser.add_argument("--model", default="qwen3:8b")
    parser.add_argument("--host", default="http://127.0.0.1:11434")
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--ids", type=Path)
    try:
        run(parser.parse_args())
    except (ValueError, KeyError, TypeError, OSError, URLError) as exception:
        print(f"Generation failed: {exception}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
