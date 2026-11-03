"""Run the reviewed checklist SQL, under a read-only transaction on the source."""
import datetime as D, json, re, subprocess, sys
from pathlib import Path
root=Path(__file__).resolve().parents[2]
db,out,cutoff=sys.argv[1:4]; out=Path(out)
assert db in ('quiz_mock','quiz_mock_v2')
s=json.loads((out/'snapshot.json').read_text()); learner=next(x for x in s['app_user'] if x['role']=='USER')
blocks=re.findall(r'```sql\n(.*?)```',(root/'e2e/mock-check.md').read_text(),re.S)[1:]
assert not any(re.search(r'(?im)^\s*(INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|TRUNCATE|REPLACE)\b',b) for b in blocks)
assert re.fullmatch(r'[\dTZ:.+-]+',cutoff)
today=(D.datetime.now(D.timezone.utc)+D.timedelta(hours=8)).date().isoformat()
profiles=(root/'backend/src/main/resources/mock/users.json').read_bytes().hex()
sql=f"SET SESSION time_zone='+00:00';\nSTART TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;\nSET @cutoff_utc=CAST('{cutoff.replace('T',' ').replace('Z','')}' AS DATETIME(3)), @api_today=DATE('{today}'), @uid={learner['id']}, @root={learner['target_category_id']};\nSET @profiles_json=CONVERT(0x{profiles} USING utf8mb4);\n"
for i,b in enumerate(blocks,1): sql+=f"SELECT 'checklist_group_{i}' AS audit_section;\n{b}\n"
sql+='ROLLBACK;\n'; (out/'checklist-executed.sql').write_text(sql)
with (out/'checklist-sql.tsv').open('w') as dest:
    result=subprocess.run(['/data/tools/mysql-8.0/bin/mysql','-h127.0.0.1','-P13306','-uroot','--default-character-set=utf8mb4','--batch','--raw',db],input=sql,text=True,stdout=dest,stderr=subprocess.PIPE)
print(json.dumps({'database':db,'sqlGroups':len(blocks),'exitCode':result.returncode,'apiToday':today,'sampleUserId':learner['id'],'sampleRoot':learner['target_category_id']}))
if result.returncode: print(result.stderr)
sys.exit(result.returncode)
