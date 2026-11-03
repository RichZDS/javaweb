"""Remaining mock checklist read paths on the authorized disposable API copy."""
import datetime as D,json,math,sys,urllib.request,urllib.error
from pathlib import Path
root=Path(__file__).resolve().parents[2];out=Path(sys.argv[1]);data=json.loads((out/'snapshot.json').read_text());base=json.loads((out/'api-audit-initial.json').read_text());state=json.loads((out/'stack.json').read_text())
assert state['database']=='quiz_mock_check' and state['url'].startswith('http://127.0.0.1:')
f=Path('/data/crew/qzd-5b1e/board/mock-local-creds.txt');assert f.stat().st_mode&0o777==0o600
creds={}
for line in f.read_text().splitlines():
 if '=' in line:
  k,v=line.split('=',1);creds[k.strip()]=v.strip().strip('"\'')
users={x['id']:x for x in data['app_user']};cat={x['id']:x for x in data['category']};paper={x['id']:x for x in data['exam_paper']};questions={x['id']:x for x in data['question']}
def obj(x):return json.loads(x) if isinstance(x,str) else x
def dt(x):return D.datetime.fromisoformat(x.replace('Z','+00:00')).replace(tzinfo=None) if x else None
def rate(n,d):return math.floor(n*10000/d+.5)/10000 if d else None
def examroot(cid):
 while cat[cid]['parent_id'] is not None:cid=cat[cid]['parent_id']
 return cid
client=urllib.request.build_opener(urllib.request.ProxyHandler({}));tokens={};reqs=[];checks=[]
def api(path,uid=None,body=None):
 headers={'Content-Type':'application/json'}
 if uid is not None:headers['Authorization']='Bearer '+tokens[uid]
 request=urllib.request.Request(state['url']+path,data=json.dumps(body).encode() if body else None,headers=headers)
 with client.open(request,timeout=15) as r:payload=json.load(r);reqs.append({'method':request.get_method(),'path':path,'status':r.status})
 assert payload['code']=='OK','unexpected API envelope';return payload['data']
def check(label,actual,expected):checks.append({'label':label,'passed':actual==expected,**({} if actual==expected else {'actual':actual,'expected':expected})})
def pages(path,uid):
 rows=[];sep='&' if '?' in path else '?'
 for n in range(1,1000):
  page=api(f'{path}{sep}page={n}&size=100',uid);rows+=page['items']
  if len(rows)>=page['total']:break
 check(path+' total',len(rows),page['total']);return rows
fatal=None
try:
 for uid in base['detailedSampleIds']:
  auth=api('/api/auth/login',body={'username':users[uid]['username'],'password':creds['MOCK_USER_PASSWORD']});tokens[uid]=auth['token']
  for rid in [x['id'] for x in cat.values() if x['level']==1]:
   rows=[r for r in data['exam_record'] if r['user_id']==uid and r['status']=='FINISHED' and paper[r['paper_id']]['category_id']==rid]
   actual=api(f'/api/stats/exams?categoryId={rid}',uid)
   expected=[{'recordId':r['id'],'paperId':r['paper_id'],'title':obj(r['question_snapshot'])['title'],'score':r['score'],'totalScore':r['total_score'],'passScore':r['pass_score'],'rate':rate(r['score'],r['total_score'])} for r in sorted(rows,key=lambda r:(r['submitted_at'],r['id']))]
   check(f'{uid}/{rid} exam stats',[{k:x[k] for k in ['recordId','paperId','title','score','totalScore','passScore','rate']} for x in actual],expected)
   catalog=api(f'/api/exams?categoryId={rid}',uid)
   check(f'{uid}/{rid} online papers',sorted(x['paperId'] for x in catalog),sorted(p['id'] for p in paper.values() if p['category_id']==rid and p['status']=='ONLINE'))
   for p in catalog:
    attempts=[r for r in rows if r['paper_id']==p['paperId']]
    check(f'{uid}/{p["paperId"]} attempts/best',[p['myAttempts'],p['myBestScore']],[len(attempts),max((r['score'] for r in attempts),default=None)])
  modes=set()
  for s in data['practice_session']:
   if s['user_id']!=uid or s['mode']!='DAILY':continue
   key=(bool(s['completed_at']),bool(s['recite_mode']))
   if key in modes:continue
   modes.add(key);actual=api(f'/api/practice/sessions/{s["id"]}',uid);ar=[a for a in data['answer_record'] if a['source']=='PRACTICE' and a['source_id']==s['id']]
   check(f'daily {s["id"]} counts',[actual['questionCount'],actual['answeredCount'],actual['correctCount']],[len(obj(s['question_ids'])),len(ar),sum(a['is_correct'] for a in ar)])
   check(f'daily {s["id"]} completion',dt(actual['completedAt']),dt(s['completed_at']))
  favorites=pages('/api/favorites',uid); expected=[x for x in data['favorite'] if x['user_id']==uid and examroot(questions[x['question_id']]['category_id'])==users[uid]['target_category_id']]
  check(f'{uid} favorites with notes',{x['questionId']:x['note'] for x in favorites},{x['question_id']:x['note'] for x in expected})
 auth=api('/api/auth/login',body={'username':creds['ADMIN_USERNAME'],'password':creds['ADMIN_PASSWORD']});uid=auth['user']['id'];tokens[uid]=auth['token'];actual=pages('/api/admin/feedback',uid)
 check('feedback status/content/reply',{x['id']:[x['status'],x['content'],x['reply']] for x in actual},{x['id']:[x['status'],x['content'],x['reply']] for x in data['question_feedback']})
except Exception as e:fatal=type(e).__name__+': '+str(e)
result={'requests':len(reqs),'assertions':len(checks),'failedAssertions':sum(not c['passed'] for c in checks),'fatal':fatal,'checks':checks}
(out/'api-details.json').write_text(json.dumps(result,ensure_ascii=False,indent=2,default=str));(out/'api-details-requests.json').write_text(json.dumps(reqs,ensure_ascii=False,indent=2));print(json.dumps({k:v for k,v in result.items() if k!='checks'},ensure_ascii=False));sys.exit(bool(fatal or result['failedAssertions']))
