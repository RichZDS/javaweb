"""Copy the authorized local mock database. Never writes to the source database."""
import json, os, re, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'e2e/artifacts/mock-v1'
OUT.mkdir(parents=True, exist_ok=True)
os.chmod(OUT, 0o700)
MYSQL = '/data/tools/mysql-8.0/bin/mysql'
ARGS = ['--host=127.0.0.1', '--port=13306', '--user=root', '--batch', '--raw', '--skip-column-names', '--default-character-set=utf8mb4']
def sql(statement, db=None):
    return subprocess.run([MYSQL, *ARGS, *([db] if db else [])], input=statement, text=True, capture_output=True, check=True).stdout.strip()

# Refuse to silently overwrite a previous audit copy.
assert sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='quiz_mock_check'") == '0', 'quiz_mock_check already exists; preserve it'
dump = OUT / 'snapshot.private.sql'
with dump.open('w') as output:
    os.chmod(dump, 0o600)
    subprocess.run(['/data/tools/mysql-8.0/bin/mysqldump', *ARGS[:3], '--single-transaction', '--skip-lock-tables', '--skip-add-locks', '--no-tablespaces', '--set-gtid-purged=OFF', '--hex-blob', 'quiz_mock'], stdout=output, stderr=subprocess.PIPE, check=True)
with dump.open() as source:
    for line in source:
        assert not re.match(r'^\s*(?:USE\s|CREATE\s+DATABASE)', line, re.I), 'dump contains database selection'
sql('CREATE DATABASE quiz_mock_check CHARACTER SET utf8mb4')
with dump.open() as source:
    subprocess.run([MYSQL, *ARGS, 'quiz_mock_check'], stdin=source, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, check=True)

# Explicit authorization from lead message 7cc3c3a4: only the copy's USER password_hash.
file = Path('/data/crew/qzd-5b1e/board/mock-local-creds.txt')
assert file.stat().st_mode & 0o777 == 0o600
creds = {}
for line in file.read_text().splitlines():
    if '=' in line:
        key, value = line.split('=', 1)
        creds[key.strip()] = value.strip().strip('"\'')
hash_value = creds.get('MOCK_USER_PASSWORD_BCRYPT', '')
assert re.fullmatch(r'\$2[aby]\$\d\d\$[./A-Za-z0-9]{53}', hash_value), 'bcrypt credential not yet ready; copy remains intact'
matched = sql("UPDATE app_user SET password_hash=CONVERT(0x" + hash_value.encode().hex() + " USING utf8mb4) WHERE role='USER'; SELECT ROW_COUNT();", 'quiz_mock_check')
assert matched == '300', 'expected 300 copy users'
summary = {'source': 'quiz_mock', 'target': 'quiz_mock_check', 'sourceWrite': False,
           'authorizedPasswordHashUpdate': {'column': 'password_hash', 'where': "role='USER'", 'changedRows': int(matched)},
           'answerRows': int(sql('SELECT COUNT(*) FROM answer_record', 'quiz_mock_check'))}
(OUT / 'copy.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2))
dump.unlink()
print(json.dumps(summary, ensure_ascii=False))
