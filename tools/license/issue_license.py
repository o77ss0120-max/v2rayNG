#!/usr/bin/env python3
"""Issue an ECDSA P-256 signed license (PL1.<payload>.<sig>) locked to a Device ID or ANY.
Security: Reads PENNY_PRIVATE_KEY_B64 strictly from environment variable (never hardcoded in repo).
"""
import os, sys, time, secrets, base64
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode().rstrip("=")


def issue(device_id: str = "ANY", valid_days: int = 90) -> str:
    priv_b64 = os.environ.get("PENNY_PRIVATE_KEY_B64", "").strip()
    if not priv_b64:
        raise RuntimeError(
            "PENNY_PRIVATE_KEY_B64 environment variable is not set! "
            "Never hardcode private keys in the repository."
        )
    alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    serial = "".join(secrets.choice(alphabet) for _ in range(6))
    deadline_ms = int((time.time() + valid_days * 86400) * 1000)
    payload = f"{serial}|{device_id.strip().upper()}|{deadline_ms}".encode("utf-8")
    priv = serialization.load_der_private_key(base64.b64decode(priv_b64), password=None)
    sig = priv.sign(payload, ec.ECDSA(hashes.SHA256()))
    return f"PL1.{b64url(payload)}.{b64url(sig)}"


if __name__ == "__main__":
    dev = sys.argv[1] if len(sys.argv) > 1 else "ANY"
    days = int(sys.argv[2]) if len(sys.argv) > 2 else 90
    print(issue(dev, days))
