#!/usr/bin/env python3
"""Issue an ECDSA P-256 signed license (PL1.<payload>.<sig>) locked to a Device ID or ANY."""
import sys, time, secrets, base64
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

DEFAULT_PRIV_B64 = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg24qFGfXDebC7ZIpviyW+diNOnfKVpzwIDDtNNtzyeG2hRANCAAToooR7kGhZcyri6M+4DBdDRbrkyUAx3/SDS/6OT57nNrq3+wZQHht6CAGN7FZnFyfEnk4P7SuT1hB3dP6iJqah"

def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode().rstrip("=")

def issue(device_id: str = "ANY", valid_days: int = 90, priv_b64: str = DEFAULT_PRIV_B64) -> str:
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
