#!/usr/bin/env python3
"""Generate ECDSA P-256 keypair for Penny VPN license & VIP server list signing."""
import base64
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization

priv = ec.generate_private_key(ec.SECP256R1())
pub = priv.public_key()

priv_der = priv.private_bytes(
    encoding=serialization.Encoding.DER,
    format=serialization.PrivateFormat.PKCS8,
    encryption_algorithm=serialization.NoEncryption()
)
pub_der = pub.public_bytes(
    encoding=serialization.Encoding.DER,
    format=serialization.PublicFormat.SubjectPublicKeyInfo
)

print("LICENSE_PUBLIC_KEY (put in MainActivity.kt / gradle.properties):")
print(base64.b64encode(pub_der).decode())
print("\nLICENSE_PRIVATE_KEY (keep secret, use with issue_license.py & sign_list.py):")
print(base64.b64encode(priv_der).decode())
