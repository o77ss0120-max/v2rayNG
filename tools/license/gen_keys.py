#!/usr/bin/env python3
"""Generate a fresh ECDSA P-256 keypair for Penny VPN license & server list signing.
IMPORTANT: Never commit the private key to git! Keep PENNY_PRIVATE_KEY_B64 in environment variables or secrets.
"""
import base64
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization

priv = ec.generate_private_key(ec.SECP256R1())
pub = priv.public_key()

priv_der = priv.private_bytes(
    encoding=serialization.Encoding.DER,
    format=serialization.PrivateFormat.PKCS8,
    encryption_algorithm=serialization.NoEncryption(),
)
pub_der = pub.public_bytes(
    encoding=serialization.Encoding.DER,
    format=serialization.PublicFormat.SubjectPublicKeyInfo,
)

print("ROTATED_PUBLIC_KEY_B64 (put in PennyLicenseManager.kt):")
print(base64.b64encode(pub_der).decode())
print("\nPENNY_PRIVATE_KEY_B64 (KEEP SECRET OUTSIDE REPO - set in env var PENNY_PRIVATE_KEY_B64):")
print(base64.b64encode(priv_der).decode())
