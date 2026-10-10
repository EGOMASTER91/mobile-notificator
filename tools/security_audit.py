"""Check resolved Maven runtime versions against OSV. Requires network; no credentials.

Run :app:writeSecurityDependencies first, then pass the generated JSON path.
Exit 0: completed/no known findings; 1: known findings; errors never count as clean.
"""
import argparse
import datetime
import json
import pathlib
import urllib.request


def request(queries):
    payload = json.dumps({'queries': queries}).encode()
    req = urllib.request.Request('https://api.osv.dev/v1/querybatch', data=payload,
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=45) as response:
        result = json.load(response)['results']
    if len(result) != len(queries):
        raise RuntimeError('OSV did not return a result for every dependency')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dependencies', type=pathlib.Path)
    parser.add_argument('--output', type=pathlib.Path, default=pathlib.Path('security/osv-audit.json'))
    args = parser.parse_args()
    dependencies = json.loads(args.dependencies.read_text(encoding='utf-8'))
    findings = []
    for start in range(0, len(dependencies), 100):
        batch = dependencies[start:start + 100]
        queries = [{'package': {'ecosystem': 'Maven', 'name': item['name']},
                    'version': item['version']} for item in batch]
        for item, query, response in zip(batch, queries, request(queries)):
            vulns = response.get('vulns', [])
            seen_tokens = set()
            while response.get('next_page_token'):
                token = response['next_page_token']
                if token in seen_tokens:
                    raise RuntimeError('OSV repeated a pagination token')
                seen_tokens.add(token)
                response = request([{**query, 'page_token': token}])[0]
                vulns.extend(response.get('vulns', []))
            if vulns:
                findings.append({**item, 'vulnerabilities': vulns})
    report = {'completedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'scope': 'app debug and release resolved Maven runtime dependencies',
              'dependencyCount': len(dependencies), 'dependencies': dependencies,
              'findings': findings, 'database': 'https://osv.dev',
              'limitation': 'No finding means no advisory returned by OSV for these versions; it is not a security guarantee.'}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({'dependencyCount': len(dependencies), 'findings': findings}, indent=2))
    return 1 if findings else 0


if __name__ == '__main__':
    raise SystemExit(main())
