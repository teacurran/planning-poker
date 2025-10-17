#!/bin/bash

##############################################################################
# RSA Key Pair Generation Script for JWT Token Signing
##############################################################################
#
# This script generates an RSA key pair for JWT token signing and verification.
# The private key is used to sign access tokens, and the public key is used
# to verify token signatures.
#
# SECURITY NOTICE:
# - The private key (privateKey.pem) is EXTREMELY SENSITIVE and must NEVER
#   be committed to version control.
# - In production, keys MUST be loaded from Kubernetes Secrets or a secure
#   key management service, NOT from filesystem.
# - This script is for development and testing purposes only.
#
# Usage:
#   ./generate-keys.sh
#
# Output:
#   - src/main/resources/privateKey.pem (2048-bit RSA private key)
#   - src/main/resources/publicKey.pem (RSA public key extracted from private)
#
##############################################################################

set -e  # Exit on error

# Change to backend directory
cd "$(dirname "$0")"

echo "Generating RSA key pair for JWT signing..."

# Generate 2048-bit RSA private key
echo "Step 1/2: Generating private key (privateKey.pem)..."
openssl genpkey -algorithm RSA \
  -out src/main/resources/privateKey.pem \
  -pkeyopt rsa_keygen_bits:2048

# Extract public key from private key
echo "Step 2/2: Extracting public key (publicKey.pem)..."
openssl rsa -pubout \
  -in src/main/resources/privateKey.pem \
  -out src/main/resources/publicKey.pem

echo ""
echo "✓ Key pair generated successfully!"
echo ""
echo "Files created:"
echo "  - src/main/resources/privateKey.pem (KEEP SECRET - DO NOT COMMIT)"
echo "  - src/main/resources/publicKey.pem"
echo ""
echo "IMPORTANT SECURITY REMINDERS:"
echo "  1. The privateKey.pem file is already in .gitignore and will not be committed"
echo "  2. Never share or expose the private key"
echo "  3. In production, load keys from Kubernetes Secrets or environment variables"
echo "  4. Rotate keys periodically (recommended: every 90 days)"
echo ""
