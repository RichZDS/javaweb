"""Compare API responses on quiz_mock_check to the untouched source snapshot."""
import collections as C, datetime as D, json, math, sys, urllib.request, urllib.error
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; out=Path(sys.argv[1]); data=json.loads((out/'snapshot.json').read_text()); state=json.loads((out/'stack.json').read_text())
assert state['database']=='quiz_mock_check' and state['repo']==str(ROOT) and state['url'].startswith('http://127.0.0.1:')
file=Path('/data/crew/qzd-5b1e/board/mock-local-creds.txt'); assert file.stat().st_mode & 0o777==0o600
creds={}
for line in file.read_text().splitlines():
    if '=' in line:
        k,v=line.split('=',1);creds[k.strip()]=v.strip().strip('"\'')
assert all(creds.get(k) for k in ['MOCK_USER_PASSWORD','ADMIN_USERNAME','ADMIN_PASSWORD']), 'credentials incomplete'
admin_only='--admin-only' in sys.argv[2:]
profiles=json.loads((ROOT/'backend/src/main/resources/mock/users.json').read_text()); pmap={p['username']:p for p in profiles['users']}
u={r['id']:r for r in data['app_user']}; cat={r['id']:r for r in data['category']}; q={r['id']:r for r in data['question']}; ps={r['id']:r for r in data['practice_session']}; er={r['id']:r for r in data['exam_record']}; paper={r['id']:r for r in data['exam_paper']}
learners=[r for r in u.values() if r['role']=='USER']; roots=[r['id'] for r in cat.values() if r['level']==1]
def obj(x):return json.loads(x) if isinstance(x,str) else x
def dt(x):return D.datetime.fromisoformat(x.replace('Z','+00:00')).replace(tzinfo=None) if x else None
def day(x):return (dt(x)+D.timedelta(hours=8)).date()
today=(D.datetime.now(D.timezone.utc)+D.timedelta(hours=8)).date(); monday=today-D.timedelta(days=today.weekday())
def rate(n,d): return math.floor(n*10000/d+.5)/10000 if d else None
def root(c):
    while cat[c]['parent_id'] is not None:c=cat[c]['parent_id']
    return c
children=C.defaultdict(list)
for c in cat.values():children[c['parent_id']].append(c)
order={}
def visit(parent):
    for c in sorted(children[parent],key=lambda r:(r['sort'],r['id'])):order[c['id']]=len(order);visit(c['id'])
visit(None)
byu=C.defaultdict(list); sessions=C.defaultdict(list); exams=C.defaultdict(list); bysource=C.defaultdict(list)
for a in data['answer_record']:
    if a['answered']:byu[a['user_id']].append(a)
    bysource[(a['source'],a['source_id'])].append(a)
for s in ps.values():sessions[s['user_id']].append(s)
for e in er.values():exams[e['user_id']].append(e)
checks=[]; requests=[]; tokens={}; client=urllib.request.build_opener(urllib.request.ProxyHandler({}))
def check(code,label,actual,expected):
    ok=actual==expected; checks.append({'id':code,'label':label,'passed':ok,**({} if ok else {'actual':actual,'expected':expected})})
def api(path,uid=None,body=None,status=200):
    headers={'Content-Type':'application/json'}
    if uid is not None:headers['Authorization']='Bearer '+tokens[uid]
    req=urllib.request.Request(state['url']+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
    try:
        with client.open(req,timeout=15) as r: code=r.status; payload=json.load(r)
    except urllib.error.HTTPError as e:code=e.code;payload=json.load(e)
    requests.append({'method':req.get_method(),'path':path,'status':code})
    if code!=status:raise RuntimeError(f'HTTP mismatch {req.get_method()} {path}: {code}, expected {status}')
    if code<400 and payload.get('code')!='OK':raise RuntimeError('API envelope mismatch')
    return payload['data']
def pages(path,uid):
    allrows=[]; sep='&' if '?' in path else '?'
    for n in range(1,1000):
        result=api(f'{path}{sep}page={n}&size=100',uid);allrows.extend(result['items'])
        if len(allrows)>=result['total']:break
    check('PAGING',path+' total',len(allrows),result['total']);return allrows

def overview(uid,rid):
    rows=[a for a in byu[uid] if a['root_category_id']==rid]
    done={D.date.fromisoformat(s['daily_date']) for s in sessions[uid] if s['mode']=='DAILY' and s['completed_at'] and s['root_category_id']==rid}
    anchor=today if today in done else today-D.timedelta(days=1);streak=0
    while anchor in done:streak+=1;anchor-=D.timedelta(days=1)
    return {'answerCount':len(rows),'correctCount':sum(a['is_correct'] for a in rows),'correctRate':rate(sum(a['is_correct'] for a in rows),len(rows)), 'studyDays':len({day(a['graded_at']) for a in rows}),'streakDays':streak,'todayAnswerCount':sum(day(a['graded_at'])==today for a in rows),'examCount':sum(e['status']=='FINISHED' and paper[e['paper_id']]['category_id']==rid for e in exams[uid])}

try:
    sample=set();selected=set()
    if not admin_only:
        # All 300 learners: disabled rejection or authenticated profile+overview.
        for user in learners:
            uid=user['id']; disabled=user['status']=='DISABLED'
            auth=api('/api/auth/login',body={'username':user['username'],'password':creds['MOCK_USER_PASSWORD']},status=403 if disabled else 200)
            check('A01',f'user {uid} login expected status',True,True)
            if disabled:continue
            tokens[uid]=auth['token']; me=api('/api/me',uid)
            check('A01',f'user {uid} profile',{k:me[k] for k in ['id','username','targetCategoryId','wrongRemoveThreshold','avatarUrl']},{'id':uid,'username':user['username'],'targetCategoryId':user['target_category_id'],'wrongRemoveThreshold':user['wrong_remove_threshold'],'avatarUrl':user['avatar_url']})
            check('A02',f'user {uid} default overview',api('/api/stats/overview',uid),overview(uid,user['target_category_id']))
        # Cover all target switches and at least one active learner in each persona x initial target.
        sample=set(); seen=set()
        for user in learners:
            if user['id'] not in tokens:continue
            p=pmap[user['username']];key=(p['persona'],p['target'])
            if key not in seen or p.get('switchTarget'):sample.add(user['id']);seen.add(key)
        for uid in sorted(sample):
            for rid in roots:
                rows=[a for a in byu[uid] if a['root_category_id']==rid]
                check('A02',f'{uid}/{rid} explicit overview',api(f'/api/stats/overview?categoryId={rid}',uid),overview(uid,rid))
                daily=[]
                for delta in range(29,-1,-1):
                    d=today-D.timedelta(days=delta);ar=[a for a in rows if day(a['graded_at'])==d];good=sum(a['is_correct'] for a in ar)
                    daily.append({'date':str(d),'answerCount':len(ar),'correctCount':good,'correctRate':rate(good,len(ar))})
                check('A02',f'{uid}/{rid} 30 daily rows',api(f'/api/stats/daily?categoryId={rid}&days=30',uid),daily)
                expected={}
                for c in cat.values():
                    if c['level']!=3 or root(c['id'])!=rid:continue
                    ar=[a for a in rows if a['category_id_snapshot']==c['id']]
                    expected[c['id']]={'questionCount':sum(x['category_id']==c['id'] and x['status']=='PUBLISHED' for x in q.values()),'doneCount':len({a['question_id'] for a in ar}),'answerCount':len(ar),'correctRate':rate(sum(a['is_correct'] for a in ar),len(ar))}
                actual=api(f'/api/stats/categories?categoryId={rid}',uid)
                check('A02',f'{uid}/{rid} chapter totals',{x['categoryId']:{k:x[k] for k in ['questionCount','doneCount','answerCount','correctRate']} for x in actual},expected)
                latest={}
                for a in sorted(rows,key=lambda a:(a['graded_at'],a['id'])):
                    if day(a['graded_at'])>=today-D.timedelta(days=29):latest[a['question_id']]=a
                groups=C.defaultdict(list)
                for a in latest.values():groups[a['category_id_snapshot']].append(a)
                weak=[{'categoryId':cid,'sampleCount':len(ar),'correctRate':rate(sum(a['is_correct'] for a in ar),len(ar))} for cid,ar in groups.items() if len(ar)>=5]
                weak=sorted(weak,key=lambda x:(x['correctRate'],-x['sampleCount'],order[x['categoryId']]))[:5]
                actual=api(f'/api/stats/weak?categoryId={rid}&days=30&limit=5',uid)
                check('A02',f'{uid}/{rid} latest-per-question weak',[{k:x[k] for k in ['categoryId','sampleCount','correctRate']} for x in actual],weak)
            rid=u[uid]['target_category_id']; active=[w for w in data['wrong_question'] if w['user_id']==uid and not w['removed'] and root(q[w['question_id']]['category_id'])==rid]
            for sort in ['RECENT','MOST']:
                ordered=sorted(active,key=lambda w:((-w['wrong_count'],) if sort=='MOST' else ())+(-dt(w['last_wrong_at']).timestamp(),-w['id']))
                actual=pages(f'/api/wrong-questions?sort={sort}',uid)
                check('A03',f'{uid} wrong {sort} order',[x['questionId'] for x in actual],[w['question_id'] for w in ordered])
                expected={w['question_id']:w for w in active}
                bad=[]
                for x in actual:
                    w=expected[x['questionId']]
                    if (x['wrongCount'],x['correctStreak'],x['lastWrongAnswer'],dt(x['lastWrongAt']))!=(w['wrong_count'],w['correct_streak'],obj(w['last_wrong_answer']),dt(w['last_wrong_at'])):bad.append(x['questionId'])
                check('A03',f'{uid} wrong values {sort}',bad,[])
            groups=api('/api/wrong-questions/groups',uid)
            check('A03',f'{uid} wrong chapter groups',{x['categoryId']:x['count'] for x in groups},dict(C.Counter(q[w['question_id']]['category_id'] for w in active)))
            for path,table,idkey in [('/api/practice/sessions',sessions,'id'),('/api/exam-records',exams,'recordId')]:
                actual=pages(path+'?all=true',uid);check('A05',f'{uid} all history {path}',set(x[idkey] for x in actual),set(x['id'] for x in table[uid]))
                actual=pages(path,uid); expected=[x for x in table[uid] if (x['root_category_id'] if table is sessions else paper[x['paper_id']]['category_id'])==rid]
                check('A05',f'{uid} scoped history {path}',set(x[idkey] for x in actual),set(x['id'] for x in expected))
        # Leaderboard expected ranking includes every ACTIVE user, not just sampled people.
        for rid in roots:
            counts={uid:sum(a['root_category_id']==rid and day(a['graded_at'])>=monday for a in rows) for uid,rows in byu.items() if u[uid]['status']=='ACTIVE'}
            ordered=sorted([(uid,n) for uid,n in counts.items() if n],key=lambda x:(-x[1],x[0]));ranked={};last=None
            for i,(uid,n) in enumerate(ordered):
                if n!=last:rank=i+1;last=n
                ranked[uid]=(rank,n)
            top=[{'rank':ranked[uid][0],'nickname':u[uid]['nickname'],'avatarUrl':u[uid]['avatar_url'],'answerCount':n} for uid,n in ordered[:20]]
            probes=set(sorted(sample)[:1]+[uid for uid,n in ordered[:1]]+[uid for uid,n in ordered[20:21]]+[uid for uid in tokens if uid not in ranked][:1])
            for uid in sorted(probes):
                actual=api(f'/api/stats/leaderboard?period=WEEK&categoryId={rid}',uid);rank,n=ranked.get(uid,(None,0))
                check('A06',f'{uid}/{rid} leaderboard',actual,{'items':top,'me':{'rank':rank,'answerCount':n}})
        # Settled exams: one per paper x finish reason x shuffle plus zero/full/partial scores.
        selected=set(); seen=set()
        for e in er.values():
            if e['status']!='FINISHED' or e['user_id'] not in tokens:continue
            snap=obj(e['question_snapshot']);key=(e['paper_id'],e['finish_reason'],bool(snap.get('shuffleOptions')),'zero' if e['score']==0 else 'full' if e['score']==e['total_score'] else 'partial')
            if key not in seen:seen.add(key);selected.add(e['id'])
        for eid in sorted(selected):
            e=er[eid];actual=api(f'/api/exam-records/{eid}',e['user_id']);ar={a['question_id']:a for a in bysource[('EXAM',eid)]}
            check('A04',f'exam {eid} totals',[actual['score'],actual['totalScore'],actual['passed'],sum(x['score'] for x in actual['items']),sum(x['score'] for x in actual['byType']),sum(x['score'] for x in actual['byCategory'])],[e['score'],e['total_score'],bool(e['passed']),e['score'],e['score'],e['score']])
            check('A04',f'exam {eid} counts',[actual['correctCount'],actual['wrongCount'],actual['unansweredCount']],[sum(a['answered'] and a['is_correct'] for a in ar.values()),sum(a['answered'] and not a['is_correct'] for a in ar.values()),sum(not a['answered'] for a in ar.values())])
            bad=[]
            for item in actual['items']:
                original=[item['optionMapping'][k] for k in item['userAnswer']] if item.get('optionMapping') else item['userAnswer']
                if sorted(original)!=sorted(obj(ar[item['questionId']]['user_answer']) or []):bad.append(item['questionId'])
            check('A04',f'exam {eid} original answer mapping',bad,[])
    auth=api('/api/auth/login',body={'username':creds['ADMIN_USERNAME'],'password':creds['ADMIN_PASSWORD']});staff=auth['user']['id'];tokens[staff]=auth['token']
    dashboard=api('/api/admin/dashboard',staff);answered=[a for a in data['answer_record'] if a['answered']]
    check('A07','dashboard headline',{k:dashboard[k] for k in ['userCount','questionCount','paperCount','todayAnswerCount','pendingFeedbackCount']},{'userCount':300,'questionCount':sum(x['status']=='PUBLISHED' for x in q.values()),'paperCount':len(paper),'todayAnswerCount':sum(day(a['graded_at'])==today for a in answered),'pendingFeedbackCount':sum(x['status']=='PENDING' for x in data['question_feedback'])})
    daily=[]
    for delta in range(13,-1,-1):
        d=today-D.timedelta(days=delta);ar=[a for a in answered if day(a['graded_at'])==d];daily.append({'date':str(d),'answerCount':len(ar),'activeUsers':len({a['user_id'] for a in ar})})
    check('A07','dashboard 14 day activity',dashboard['daily'],daily)
    bad=[]
    for item in dashboard['papers']:
        rows=[e for e in er.values() if e['status']=='FINISHED' and e['paper_id']==item['paperId']];n=len(rows)
        expected=[n,math.floor(sum(e['score'] for e in rows)*100/n+.5)/100 if n else None,rate(sum(e['passed'] for e in rows),n)]
        if [item['attempts'],item['avgScore'],item['passRate']]!=expected:bad.append(item['paperId'])
    check('A07','dashboard per-paper stats',bad,[])
    for path,table in [('/api/admin/users','app_user'),('/api/admin/questions','question'),('/api/admin/papers','exam_paper'),('/api/admin/feedback','question_feedback')]:
        actual=pages(path,staff); expected=[x['id'] for x in data[table]]
        check('A07',path+' rows',set(x['id'] for x in actual),set(expected))
    result={'date':str(today),'baseUrl':state['url'],'database':state['database'],'requests':len(requests),'assertions':len(checks),'failedAssertions':sum(not c['passed'] for c in checks),'mode':'admin-only' if admin_only else 'all','learners':0 if admin_only else 300,'activeProfilesAndOverviews':len(tokens)-1,'detailedSampleIds':sorted(sample),'examSampleIds':sorted(selected),'checks':checks}
except Exception as error:
    result={'fatal':str(error),'requests':len(requests),'assertions':len(checks),'failedAssertions':sum(not c['passed'] for c in checks),'checks':checks}
# Sets only appear in failures. Never attach auth responses, tokens or credentials.
(out/'api-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2,default=lambda x:sorted(x) if isinstance(x,set) else str(x)))
(out/'api-requests.json').write_text(json.dumps(requests,ensure_ascii=False,indent=2))
print(json.dumps({k:v for k,v in result.items() if k!='checks'},ensure_ascii=False));sys.exit(bool(result.get('fatal') or result['failedAssertions']))
