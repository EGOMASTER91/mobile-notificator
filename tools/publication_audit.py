"""Check tracked files/history for accidental local/private publication metadata.

Complementary to Gitleaks; this is a conservative metadata check, not a PII detector.
Optional --apk checks ZIP entries for local paths and rejects private key files.
"""
import argparse
import pathlib
import re
import subprocess
import zipfile

LOCAL_PATH = re.compile(rb"(?:[A-Za-z]:[\\/](?:Users|Documents and Settings)[\\/]|" + rb"/" + rb"home/[^/\s]+/)", re.I)
# A URL userinfo test fixture is not an email address. Actual secrets are checked by Gitleaks.
EMAIL = re.compile(r"(?<![:\w])[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
PRIVATE_NAME = re.compile(r"(?:^|/)(?:local\.properties|keystore\.properties|credentials[^/]*\.json|\.env(?:\.[^/]*)?)$|\.(?:jks|keystore|p12|pfx|pem|key|apk|aab|hprof)$", re.I)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=pathlib.Path)
    args = parser.parse_args()
    root = pathlib.Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip())
    paths = subprocess.check_output(['git', 'ls-files', '-z']).decode().split('\0')
    failures = []
    for name in filter(None, paths):
        if PRIVATE_NAME.search(name):
            failures.append(f'Private/generated filename: {name}')
        data = (root / name).read_bytes()
        if LOCAL_PATH.search(data):
            failures.append(f'Local personal path: {name}')
        if (b'private-' + b'history-') in data:
            failures.append(f'Private repository reference: {name}')
        if pathlib.Path(name).suffix in {'.md', '.json', '.yml', '.xml', '.kt', '.py', '.ps1', '.properties'}:
            for address in EMAIL.findall(data.decode('utf-8', errors='ignore')):
                if not address.endswith(('.invalid', '.example', '.noreply.github.com', '@example.com', '@example.org')):
                    failures.append(f'Unexpected email metadata: {name}')
                    break
    history = subprocess.check_output(['git', 'log', '--all', '--format=%an <%ae>%n%cn <%ce>'], text=True)
    for address in EMAIL.findall(history):
        if not address.endswith('.invalid'):
            failures.append('Non-neutral identity in public history')
            break
    if args.apk:
        with zipfile.ZipFile(args.apk) as archive:
            for name in archive.namelist():
                if re.search(r'\.(?:jks|keystore|p12|pfx|pem|key)$', name, re.I):
                    failures.append(f'Private key file in APK: {name}')
                if LOCAL_PATH.search(archive.read(name)):
                    failures.append(f'Local path in APK: {name}')
    print(f'Checked {len(list(filter(None, paths)))} tracked files and public Git identities.')
    for failure in failures:
        print(failure)
    print(f'Publication metadata findings: {len(failures)}')
    return int(bool(failures))


if __name__ == '__main__':
    raise SystemExit(main())
