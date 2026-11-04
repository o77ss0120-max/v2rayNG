#!/usr/bin/env python3
"""Sign a VIP server list file with #PENNY-SIG:<base64> using ECDSA P-256."""
import sys, base64
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

DEFAULT_PRIV_B64 = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg24qFGfXDebC7ZIpviyW+diNOnfKVpzwIDDtNNtzyeG2hRANCAAToooR7kGhZcyri6M+4DBdDRbrkyUAx3/SDS/6OT57nNrq3+wZQHht6CAGN7FZnFyfEnk4P7SuT1hB3dP6iJqah"

def sign_list(raw_text: str, priv_b64: str = DEFAULT_PRIV_B64) -> str:
    lines = [
        l.strip() for l in raw_text.replace("\r\n", "\n").split("\n")
        if l.strip() and not l.strip().startswith("#PENNY-SIG:")
    ]
    canonical = "\n".join(lines)
    priv = serialization.load_der_private_key(base64.b64decode(priv_b64), password=None)
    sig = priv.sign(canonical.encode("utf-8"), ec.ECDSA(hashes.SHA256()))
    sig_b64 = base64.b64encode(sig).decode()
    return f"#PENNY-SIG:{sig_b64}\n{canonical}\n"

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 sign_list.py <input_configs.txt> [output_signed.txt]")
        sys.exit(1)
    with open(sys.argv[1], "r", encoding="utf-8") as f:
        signed = sign_list(f.read())
    if len(sys.argv) > 2:
        with open(sys.argv[2], "w", encoding="utf-8") as out:
            out.write(signed)
    else:
        print(signed)
