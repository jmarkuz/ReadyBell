#!/usr/bin/env python3
"""Prints a fresh VAPID key pair (P-256) in the URL-safe base64 form Web Push expects.

Usage:  python3 scripts/generate-vapid-keys.py
Then export VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY for the backend. Keep the private key secret and
keep the same pair across deploys: changing it invalidates every existing browser subscription.
"""
import base64

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


key = ec.generate_private_key(ec.SECP256R1())
public = key.public_key().public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)
private = key.private_numbers().private_value.to_bytes(32, "big")
print(f"VAPID_PUBLIC_KEY={b64url(public)}")
print(f"VAPID_PRIVATE_KEY={b64url(private)}")
