#!/usr/bin/env python3
"""Materialize GitHub signing secrets without printing their contents."""
import base64
import os
from pathlib import Path


def property_value(value):
    # Escape UTF-16 code units for java.util.Properties, including supplementary characters.
    encoded = value.encode('utf-16-be').hex()
    return ''.join('\\u' + encoded[i:i + 4] for i in range(0, len(encoded), 4))


def main():
    names = ('KEYSTORE_BASE64', 'KEYSTORE_PASSWORD', 'KEY_ALIAS', 'KEY_PASSWORD')
    if any(not os.environ.get(name) for name in names):
        raise SystemExit('Configure all four release signing secrets before publishing.')
    try:
        key = base64.b64decode(''.join(os.environ['KEYSTORE_BASE64'].split()), validate=True)
    except ValueError:
        raise SystemExit('KEYSTORE_BASE64 is not valid base64.')
    os.umask(0o077)
    Path('key.jks').write_bytes(key)
    properties = {'storeFile': 'key.jks', 'storePassword': os.environ['KEYSTORE_PASSWORD'],
                  'keyAlias': os.environ['KEY_ALIAS'], 'keyPassword': os.environ['KEY_PASSWORD']}
    Path('keystore.properties').write_text(''.join(f'{k}={property_value(v)}\n' for k, v in properties.items()), encoding='ascii')


if __name__ == '__main__':
    main()
