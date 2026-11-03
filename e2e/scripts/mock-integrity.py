"""Verify the source was untouched and copy changes stayed inside authorization."""
import json,subprocess,sys
from pathlib import Path
root=Path(__file__).resolve().parents[2];out=Path(sys.argv[1]);before=json.loads((out/'snapshot.json').read_text())
source=json.loads((out.parent/'mock-source-after/snapshot.json').read_text());copy=json.loads((out.parent/'mock-copy-after/snapshot.json').read_text())
source_changed=[t for t in before if before[t]!=source[t]];copy_changes=[]
for t,rows in before.items():
 newer={r['id']:r for r in copy[t]}
 if len(newer)!=len(rows):copy_changes.append({'table':t,'countChanged':True})
 for row in rows:
  b=newer.get(row['id'],{});fields=[f for f,v in row.items() if b.get(f)!=v]
  if fields:copy_changes.append({'table':t,'id':row['id'],'fields':fields})
unexpected=[r for r in copy_changes if r.get('table')!='app_user' or r.get('fields')!=['last_active_at']]
f=Path('/data/crew/qzd-5b1e/board/mock-local-creds.txt');assert f.stat().st_mode&0o777==0o600
creds={}
for line in f.read_text().splitlines():
 if '=' in line:
  k,v=line.split('=',1);creds[k.strip()]=v.strip().strip('"\'')
bcrypt_hex=creds['MOCK_USER_PASSWORD_BCRYPT'].encode().hex()
statement="START TRANSACTION READ ONLY; SELECT COUNT(*) FROM quiz_mock.app_user WHERE password_hash=CONVERT(0x"+bcrypt_hex+" USING utf8mb4); SELECT COUNT(*) FROM quiz_mock_check.app_user WHERE role='USER' AND password_hash=CONVERT(0x"+bcrypt_hex+" USING utf8mb4); ROLLBACK;"
r=subprocess.run(['/data/tools/mysql-8.0/bin/mysql','-h127.0.0.1','-P13306','-uroot','--batch','--skip-column-names'],input=statement,text=True,capture_output=True,check=True)
counts=[int(x) for x in r.stdout.splitlines()]
result={'sourceChangedTables':source_changed,'copyUnexpectedChanges':unexpected,'copyLastActiveUpdates':sum(c.get('fields')==['last_active_at'] for c in copy_changes),'sourceMatchesNewPasswordHash':counts[0],'copyUserMatchesNewPasswordHash':counts[1],'note':'password_hash excluded from snapshots; only match counts queried, no hashes exported'}
(out/'integrity.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False));sys.exit(bool(source_changed or unexpected or counts!=[0,300]))
