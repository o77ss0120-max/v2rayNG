#!/usr/bin/env python3
"""Issue and manage ECDSA P-256 signed Penny VPN licenses (PL1.<payload>.<sig>).
Supports both pipe format (serial|device|deadline|days|gb) and compact JSON format ({s,d,exp,max}),
appends local issuance audit records (.issued_licenses.jsonl), and supports local serial revocation.
Security: Reads PENNY_PRIVATE_KEY_B64 strictly from environment variable (never hardcoded in repo).
"""
import argparse
import base64
import json
import os
import secrets
import time
from pathlib import Path
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode().rstrip("=")


def load_private_key():
    priv_b64 = os.environ.get("PENNY_PRIVATE_KEY_B64", "").strip()
    if not priv_b64:
        raise RuntimeError(
            "PENNY_PRIVATE_KEY_B64 environment variable is not set! "
            "Never hardcode private keys in the repository."
        )
    return serialization.load_der_private_key(base64.b64decode(priv_b64), password=None)


def issue(
    device_id: str = "ANY",
    valid_days: int = 90,
    plan_days: int = 30,
    quota_gb: int = 50,
    fmt: str = "pipe",
    log_file: str = ".issued_licenses.jsonl",
) -> str:
    priv = load_private_key()
    alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    serial = "".join(secrets.choice(alphabet) for _ in range(6))
    clean_device = device_id.strip().upper() or "ANY"
    now = time.time()
    deadline_ms = int((now + valid_days * 86400) * 1000)

    if fmt == "json":
        exp_sec = int(now + plan_days * 86400)
        max_bytes = int(quota_gb * 1024 * 1024 * 1024)
        payload = json.dumps(
            {"s": serial, "d": clean_device, "exp": exp_sec, "max": max_bytes},
            separators=(",", ":"),
        ).encode("utf-8")
    else:
        payload = f"{serial}|{clean_device}|{deadline_ms}|{plan_days}|{quota_gb}".encode("utf-8")

    sig = priv.sign(payload, ec.ECDSA(hashes.SHA256()))
    token = f"PL1.{b64url(payload)}.{b64url(sig)}"

    if log_file:
        entry = {
            "serial": serial,
            "deviceId": clean_device,
            "format": fmt,
            "planDays": plan_days,
            "quotaGb": quota_gb,
            "activateBeforeMs": deadline_ms,
            "issuedAtMs": int(now * 1000),
            "revoked": False,
        }
        with open(log_file, "a", encoding="utf-8") as fh:
            fh.write(json.dumps(entry) + "\n")

    return token


def revoke_serial(serial: str, revoke_file: str = ".revoked_serials.json") -> dict:
    clean = serial.strip().upper()
    path = Path(revoke_file)
    data = {}
    if path.exists():
        data = json.loads(path.read_text(encoding="utf-8"))
    record = {"serial": clean, "revokedAtMs": int(time.time() * 1000)}
    data[clean] = record
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return record


def main():
    parser = argparse.ArgumentParser(description="Penny VPN ECDSA P-256 License Issuer & Audit Tool")
    parser.add_argument("legacy_device", nargs="?", default=None, help="Positional device ID (backward compatible)")
    parser.add_argument("legacy_valid_days", nargs="?", type=int, default=None, help="Positional activation validity days")
    parser.add_argument("--device", default="ANY", help="Target 64-bit Device ID or ANY")
    parser.add_argument("--valid-days", type=int, default=90, help="Days before activation deadline")
    parser.add_argument("--days", type=int, default=30, help="Subscription duration in days (1-365)")
    parser.add_argument("--gb", type=int, default=50, help="Subscription data quota in GB (1-1000)")
    parser.add_argument("--format", choices=["pipe", "json"], default="pipe", help="Token payload encoding format")
    parser.add_argument("--log-file", default=".issued_licenses.jsonl", help="Local audit log path (empty to disable)")
    parser.add_argument("--revoke", default="", help="Mark a license serial as revoked in .revoked_serials.json")
    args = parser.parse_args()

    if args.revoke:
        rec = revoke_serial(args.revoke)
        print(json.dumps({"ok": True, "revoked": rec}))
        return

    dev = args.legacy_device if args.legacy_device is not None else args.device
    vdays = args.legacy_valid_days if args.legacy_valid_days is not None else args.valid_days
    print(issue(dev, vdays, max(1, min(365, args.days)), max(1, min(1000, args.gb)), args.format, args.log_file))


if __name__ == "__main__":
    main()
