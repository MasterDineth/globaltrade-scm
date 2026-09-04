import base64
import hashlib
import os

salt = os.urandom(16)
password = b"password123"
iterations = 120000
dk = hashlib.pbkdf2_hmac('sha256', password, salt, iterations, 32)

print(f"{iterations}:{base64.b64encode(salt).decode('utf-8')}:{base64.b64encode(dk).decode('utf-8')}")
