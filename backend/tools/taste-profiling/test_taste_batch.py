import argparse
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("taste_batch", Path(__file__).with_name("taste_batch.py"))
batch = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(batch)


class EstimateTests(unittest.TestCase):
    def test_rejects_false_missing_out_of_range_and_nonfinite_scores(self):
        for score in (True, -0.1, 1.1, float("nan"), float("inf"), "0.5", None):
            with self.subTest(score=score), self.assertRaises(ValueError):
                batch.validate_estimate({"status": "ESTIMATED", "taste": dict(sweetness=score, sourness=0.2, bitterness=0.4), "reason": "일반 메뉴명에 근거"})
        with self.assertRaises(ValueError):
            batch.validate_estimate({"status": "ESTIMATED", "taste": {"sweetness": 0.2}, "reason": "test"})

    def test_unknown_is_null_not_zero(self):
        result = {"status": "UNKNOWN", "taste": None, "reason": "이름만으로 판단 불가"}
        self.assertEqual(batch.validate_estimate(result), result)
        with self.assertRaises(ValueError):
            batch.validate_estimate({**result, "taste": dict.fromkeys(batch.AXES, 0)})

    def test_extra_fields_and_blank_reason_are_rejected(self):
        for value in ({"status": "UNKNOWN", "taste": None, "reason": " "},
                      {"status": "UNKNOWN", "taste": None, "reason": "test", "confidence": 1}):
            with self.assertRaises(ValueError):
                batch.validate_estimate(value)


class CheckpointTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.menus = [{"id": "menu-1", "name": "아메리카노", "description": None},
                      {"id": "menu-2", "name": "레몬에이드", "description": "레몬 음료"}]
        self.catalog = self.root / "catalog.json"
        self.catalog.write_text(json.dumps({"schemaVersion": 1, "dataVersion": "test-v1", "menus": self.menus}))
        self.args = argparse.Namespace(catalog=self.catalog, output=self.root / "output", host="http://127.0.0.1:11434",
                                       model="qwen3:8b", profile_version="test-v1", limit=2, timeout=10, ids=None)
        self.chats = 0

    def respond(self, host, endpoint, payload=None, timeout=180):
        if endpoint == "/api/version":
            return {"version": "test"}
        if endpoint == "/api/tags":
            return {"models": [{"name": "qwen3:8b", "digest": "abc"}]}
        self.chats += 1
        return {"done": True, "done_reason": "stop", "message": {"content": json.dumps({
            "status": "ESTIMATED", "taste": dict(sweetness=0.1, sourness=0.2, bitterness=0.7),
            "reason": "일반 메뉴 특성 추정"})}}

    def test_resume_avoids_duplicate_calls_and_binds_provenance(self):
        with patch.object(batch, "request_json", side_effect=self.respond):
            batch.run(self.args)
            batch.run(self.args)
            self.assertEqual(self.chats, 2)
            result = json.loads((self.args.output / "bundle.json").read_text())
            self.assertEqual(len(result["records"]), 2)
            self.assertEqual(result["records"][0]["inputBasis"], "NAME_ONLY")
            self.assertEqual(result["records"][1]["inputBasis"], "NAME_AND_DESCRIPTION")
            self.assertEqual(result["catalogSha256"], batch.digest(self.catalog.read_bytes()))
            self.args.model = "changed"
            with self.assertRaises(ValueError):
                batch.run(self.args)

    def test_failure_preserves_completed_work_and_next_run_resumes(self):
        def fail_second(host, endpoint, payload=None, timeout=180):
            if endpoint == "/api/chat" and self.chats == 1:
                raise OSError("temporary failure")
            return self.respond(host, endpoint, payload, timeout)
        with patch.object(batch, "request_json", side_effect=fail_second), self.assertRaises(OSError):
            batch.run(self.args)
        self.assertEqual(len((self.args.output / "estimates.jsonl").read_text().splitlines()), 1)
        self.assertFalse((self.args.output / ".generation.lock").exists())
        with patch.object(batch, "request_json", side_effect=self.respond):
            batch.run(self.args)
        self.assertEqual(self.chats, 2)

    def test_changed_input_cannot_reuse_checkpoint(self):
        with patch.object(batch, "request_json", side_effect=self.respond):
            batch.run(self.args)
            self.catalog.write_text(self.catalog.read_text().replace("test-v1", "test-v2"))
            with self.assertRaisesRegex(ValueError, "configuration changed"):
                batch.run(self.args)

    def test_corrupt_checkpoint_fails_without_silent_truncation(self):
        with patch.object(batch, "request_json", side_effect=self.respond):
            batch.run(self.args)
            path = self.args.output / "estimates.jsonl"
            path.write_text(path.read_text() + '{"truncated":')
            with self.assertRaisesRegex(ValueError, "checkpoint line 3"):
                batch.run(self.args)

    def test_remote_host_is_rejected_before_network(self):
        self.args.host = "https://example.invalid"
        with patch.object(batch, "request_json") as request, self.assertRaises(ValueError):
            batch.run(self.args)
        request.assert_not_called()

    def test_incomplete_generation_is_not_checkpointed(self):
        def incomplete(host, endpoint, payload=None, timeout=180):
            value = self.respond(host, endpoint, payload, timeout)
            if endpoint == "/api/chat":
                value["done_reason"] = "length"
            return value
        with patch.object(batch, "request_json", side_effect=incomplete), self.assertRaises(ValueError):
            batch.run(self.args)
        self.assertFalse((self.args.output / "estimates.jsonl").exists())


if __name__ == "__main__":
    unittest.main()
