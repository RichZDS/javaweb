"""Export an audit snapshot through one read-only transaction; exclude password hashes."""
import json, os, re, subprocess, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
db, destination = sys.argv[1:3]
assert db in ('quiz_mock', 'quiz_mock_v2', 'quiz_mock_check')
out = Path(destination).resolve(); out.mkdir(parents=True, exist_ok=True); os.chmod(out, 0o700)
tables = ['app_user','category','question','practice_session','answer_record','wrong_question','favorite','exam_paper','paper_question','paper_rule','exam_record','exam_answer','question_feedback']
cmd = ['/data/tools/mysql-8.0/bin/mysql','--host=127.0.0.1','--port=13306','--user=root','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4',db]
def query(s): return subprocess.run(cmd,input=s,text=True,capture_output=True,check=True).stdout
schema = {}
for table in tables:
    schema[table] = [line.split('\t')[0] for line in query('SHOW COLUMNS FROM '+table).splitlines() if line.split('\t')[0] != 'password_hash']
    assert all(re.fullmatch('[a-z_]+', c) for c in schema[table])
statements = ["SET SESSION time_zone='+00:00'",'SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ','START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY']
for table in tables:
    fields = ','.join("'%s',`%s`" % (c,c) for c in schema[table])
    statements.append("SELECT JSON_OBJECT('table','%s','row',JSON_OBJECT(%s)) FROM %s ORDER BY id" % (table,fields,table))
statements.append('ROLLBACK')
sql = ';\n'.join(statements)+';\n'
(out/'snapshot-query.sql').write_text(sql)
data = {t: [] for t in tables}
for line in query(sql).splitlines():
    row=json.loads(line); data[row['table']].append(row['row'])
(out/'snapshot.json').write_text(json.dumps(data,ensure_ascii=False,separators=(',',':')))
summary={'database':db,'tables':{t:len(rows) for t,rows in data.items()},'passwordHashesExported':False,'readOnlyTransaction':True}
(out/'snapshot-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
print(json.dumps(summary,ensure_ascii=False))
