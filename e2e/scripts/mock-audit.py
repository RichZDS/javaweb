"""Independent consistency and realism audit of the read-only SQL snapshot."""
import collections as C, datetime as D, hashlib, json, re, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
out=Path(sys.argv[1]); cutoff=D.datetime.fromisoformat(sys.argv[2].replace('Z','+00:00')).replace(tzinfo=None)
data=json.loads((out/'snapshot.json').read_text()); profiles=json.loads((ROOT/'backend/src/main/resources/mock/users.json').read_text())
def obj(s): return json.loads(s) if isinstance(s,str) else s
def dt(s): return D.datetime.fromisoformat(s.replace('Z','+00:00')).replace(tzinfo=None) if s else None
def bj(s): return dt(s)+D.timedelta(hours=8)
def day(s): return bj(s).date()
def index(t): return {r['id']:r for r in data[t]}
u=index('app_user'); q=index('question'); cat=index('category'); ps=index('practice_session'); er=index('exam_record'); papers=index('exam_paper')
learners=[r for r in u.values() if r['role']=='USER']; pmap={p['username']:p for p in profiles['users']}; unames={r['username']:r for r in learners}
answers=data['answer_record']; byu=C.defaultdict(list); bysource=C.defaultdict(list); bypair=C.defaultdict(list)
for a in answers: byu[a['user_id']].append(a); bysource[(a['source'],a['source_id'])].append(a); bypair[(a['user_id'],a['question_id'])].append(a)
results=[]; failures=[]
def check(code,label,bad):
    if isinstance(bad,bool): bad=[] if bad else ['condition false']
    row={'id':code,'check':label,'failures':len(bad),'examples':bad[:12]}; results.append(row)
    if bad: failures.append(row)
def root(cid):
    while cid in cat and cat[cid]['parent_id'] is not None: cid=cat[cid]['parent_id']
    return cid
check('S01','300 learners, one admin, exact JSON username set',len(learners)==300 and len(u)==301 and set(unames)==set(pmap))
check('S01','username/nickname rules',[r['id'] for r in learners if not re.fullmatch(r'[A-Za-z0-9_]{3,20}',r['username']) or re.match(r'^(qa_|pm_|fe_|be_|perf_|deploy|admin)',r['username']) or not 1<=len(r['nickname'])<=30])
profile_bad=[]
for r in learners:
    p=pmap[r['username']]; target=p['switchTarget']['to'] if p.get('switchTarget') and p['switchTarget']['on']<=day(cutoff.isoformat()).isoformat() else p['target']
    if day(r['created_at']).isoformat()!=p['registeredOn'] or cat[r['target_category_id']]['name']!=target or r['wrong_remove_threshold']!=p['wrongRemoveThreshold'] or (r['status']=='DISABLED')!=p['disabled'] or bool(r['avatar_url'])!=p['avatar']: profile_bad.append(r['id'])
check('S02','profile registration/final target/settings/status/avatar',profile_bad)
future=[]; early=[]; times=C.defaultdict(list)
for table,rows in data.items():
    for row in rows:
        owner=row.get('user_id',row['id'] if table=='app_user' else er.get(row.get('record_id'),{}).get('user_id'))
        for field,v in row.items():
            if not field.endswith('_at') or field=='deadline_at' or not v: continue
            value=dt(v); times[table+'.'+field].append(value)
            if value>cutoff: future.append([table,row['id'],field,v])
            if owner in u and u[owner]['role']=='USER' and value<dt(u[owner]['created_at']): early.append([table,row['id'],field])
check('S03','no future occurred timestamps (deadline excluded)',future); check('S03','no event before learner registration',early)
order=[]; dropout=[]
for table,first,last in [('app_user','created_at','updated_at'),('practice_session','started_at','completed_at'),('practice_session','started_at','updated_at'),('favorite','created_at','updated_at'),('question_feedback','created_at','handled_at'),('exam_record','started_at','submitted_at')]:
    for r in data[table]:
        if r[last] and dt(r[last])<dt(r[first]): order.append([table,r['id'],first,last])
for r in learners:
    p=pmap[r['username']]
    if p['disabled'] and day(r['updated_at']).isoformat()<=p['lastActiveOn']: order.append(['disabled',r['id']])
    if p['lastActiveOn']:
        limit=D.date.fromisoformat(p['lastActiveOn'])
        for a in byu[r['id']]:
            if a['source']=='PRACTICE' and day(a['graded_at'])>limit: dropout.append(a['id'])
check('S04','event ordering and disable after last active day',order); check('S04','dropout no subsequent practice submission',dropout)
question_ids={s['id']:set(obj(s['question_ids'])) for s in ps.values()}; snaps={r['id']:obj(r['question_snapshot']) for r in er.values()}; examids={rid:{i['questionId'] for i in s['questions']} for rid,s in snaps.items()}
refs=[]; duplicates=C.Counter(); published_early=[]
for a in answers:
    duplicates[(a['source'],a['source_id'],a['question_id'])]+=1
    source=(ps if a['source']=='PRACTICE' else er).get(a['source_id'])
    ids=(question_ids if a['source']=='PRACTICE' else examids).get(a['source_id'],set())
    if a['user_id'] not in u or a['question_id'] not in q or not source or source['user_id']!=a['user_id'] or a['question_id'] not in ids or (a['source']=='PRACTICE' and source['recite_mode']): refs.append(a['id'])
    if a['question_id'] in q and (dt(a['graded_at'])<dt(q[a['question_id']]['created_at']) or q[a['question_id']]['status']=='DRAFT'): published_early.append(a['id'])
    if a['root_category_id']!=root(a['category_id_snapshot']): refs.append(['root',a['id']])
for r in er.values():
    if r['paper_id'] not in papers or dt(r['started_at'])<dt(papers[r['paper_id']]['created_at']): published_early.append(['paper',r['id']])
check('S05','no answer before question creation/draft or exam before paper creation',published_early)
check('S06','answer references/owner/membership/recite/root',refs); check('S06','unique answer tuple',[list(k) for k,v in duplicates.items() if v!=1])
other_refs=[]
for t in ['wrong_question','favorite','question_feedback']:
    for r in data[t]:
        if r['user_id'] not in u or r['question_id'] not in q: other_refs.append([t,r['id']])
check('S06','wrong/favorite/feedback references',other_refs)
check('S07','150k to 250k total answer rows',150000<=len(answers)<=250000)
structural=[]
for c in cat.values():
    if c['level'] not in (1,2,3) or (c['level']==1 and c['parent_id'] is not None) or (c['level']>1 and (c['parent_id'] not in cat or cat[c['parent_id']]['level']!=c['level']-1)): structural.append(['category',c['id']])
for x in q.values():
    if x['category_id'] not in cat or cat[x['category_id']]['level']!=3:structural.append(['question',x['id']])
for x in papers.values():
    if x['category_id'] not in cat or cat[x['category_id']]['level']!=1:structural.append(['paper',x['id']])
for x in data['paper_question']:
    if x['paper_id'] not in papers or x['question_id'] not in q:structural.append(['paper_question',x['id']])
for x in data['paper_rule']:
    if x['paper_id'] not in papers or x['category_id'] not in cat or root(x['category_id'])!=papers[x['paper_id']]['category_id']:structural.append(['paper_rule',x['id']])
check('S06','category/paper/question/rule references',structural)
session_bad=[]
for x in ps.values():
    ids=obj(x['question_ids'])
    if x['user_id'] not in u or len(ids)!=len(set(ids)) or not set(ids).issubset(q):session_bad.append(x['id'])
    if any(dt(a['graded_at'])<dt(x['started_at']) for a in bysource[('PRACTICE',x['id'])]):session_bad.append(['before_start',x['id']])
check('S06','practice owner/fixed list and answer start order',session_bad)

wrong={(w['user_id'],w['question_id']):w for w in data['wrong_question']}; replay_bad=[]; wrong_time_bad=[]; expected_wrong={}
for pair,rows in bypair.items():
    state=None; first_wrong=None; last_wrong=None; removed_event=None
    for a in sorted(rows,key=lambda a:a['id']):
        if not a['answered']: continue
        if not a['is_correct']:
            first_wrong=first_wrong or a;last_wrong=a;removed_event=None
            state={'wrong_count':(state or {}).get('wrong_count',0)+1,'correct_streak':0,'removed':False,'last_wrong_answer':obj(a['user_answer'])}
        elif state and not state['removed'] and a['source']=='PRACTICE' and ps[a['source_id']]['mode'] in ('WRONG','RETRY'):
            state['correct_streak']+=1
            if state['correct_streak']>=u[a['user_id']]['wrong_remove_threshold']: state['removed']=True;removed_event=a
    if state:
        expected_wrong[pair]=state; w=wrong.get(pair)
        if w:
            if dt(w['created_at'])<dt(first_wrong['graded_at']) or dt(w['last_wrong_at'])<dt(last_wrong['graded_at']):wrong_time_bad.append(list(pair))
            if last_wrong['source']=='PRACTICE' and dt(w['last_wrong_at'])!=dt(last_wrong['graded_at']):wrong_time_bad.append(['last_practice',*pair])
            if bool(w['removed_at'])!=state['removed'] or (removed_event and dt(w['removed_at'])!=dt(removed_event['graded_at'])):wrong_time_bad.append(['removal',*pair])

        if not w or any((obj(w[k]) if k=='last_wrong_answer' else w[k])!=v for k,v in state.items()): replay_bad.append({'pair':pair,'expected':state,'actual':{k:w[k] for k in state} if w else None})
check('S09','full wrong-book state replay including removed entries',replay_bad)
check('S09','wrong-book creation/last mistake/removal timestamps',wrong_time_bad)
check('S09','no wrong-book entry without a wrong answer',[list(k) for k in wrong if k not in expected_wrong])
# Independent grading, using original labels and snapshot fill options.
def grade(kind,given,key,fill=None):
    given=given or []; fill=fill or {}
    if kind=='FILL':
        def norm(x):
            x=x.strip()
            if fill.get('ignoreSpaces'): x=re.sub(r'\s+','',x)
            return x.lower() if fill.get('ignoreCase') else x
        return len(given)==len(key) and all(any(norm(g)==norm(v) for v in accepted) for g,accepted in zip(given,key))
    return sorted(set(given))==sorted(set(key or []))
practice_grade_bad=[]
for a in answers:
    if a['source']=='PRACTICE':
        x=q[a['question_id']]
        if grade(x['type'],obj(a['user_answer']),obj(x['answer_json']),obj(x['fill_options']))!=bool(a['is_correct']):practice_grade_bad.append(a['id'])
check('S08','independent grading of every practice submission',practice_grade_bad)
eas={(r['record_id'],r['question_id']):r for r in data['exam_answer']}; exam_bad=[]; time_bad=[]; independent_bad=[]
for e in er.values():
    if e['status']!='FINISHED': continue
    items=snaps[e['id']]['questions']; aa={a['question_id']:a for a in bysource[('EXAM',e['id'])]}; total=0; stored_sum=0
    if len(aa)!=len(items) or len(examids[e['id']])!=len(items): exam_bad.append([e['id'],'row count'])
    for item in items:
        a=aa.get(item['questionId']); saved=eas.get((e['id'],item['questionId']))
        if not a: continue
        expected=bool(a['answered']) and grade(item['type'],obj(a['user_answer']),item['acceptedAnswers'] if item['type']=='FILL' else item['correctAnswer'],item.get('fillOptions'))
        total+=item['score'] if expected else 0; stored_sum+=(saved['score'] or 0) if saved else 0
        if expected!=bool(a['is_correct']) or (saved and saved['score']!=(item['score'] if expected else 0)): independent_bad.append(a['id'])
        if dt(a['graded_at'])!=dt(e['submitted_at']): time_bad.append(['grade',a['id']])
    if total!=e['score'] or stored_sum!=e['score'] or sum(i['score'] for i in items)!=e['total_score'] or bool(e['passed'])!=(total>=e['pass_score']): exam_bad.append([e['id'],'score'])
    if e['finish_reason']=='TIMEOUT' and dt(e['submitted_at'])!=dt(e['deadline_at']): time_bad.append(['timeout',e['id']])
    if e['finish_reason']=='MANUAL' and not dt(e['started_at'])<=dt(e['submitted_at'])<dt(e['deadline_at']): time_bad.append(['manual',e['id']])
for a in data['exam_answer']:
    e=er.get(a['record_id'])
    if not e or a['question_id'] not in examids[e['id']] or not dt(e['started_at'])<=dt(a['received_at'])<=dt(e['deadline_at']): time_bad.append(['save',a['id']])
check('S10','all settled exam scores equal independently graded per-item sums',exam_bad)
check('S10','independent four-type grading of every exam item',independent_bad); check('S10','exam time/answer membership consistency',time_bad)
daily_bad=[]; duplicate_daily=C.Counter()
for s in ps.values():
    if s['mode']!='DAILY': continue
    duplicate_daily[(s['user_id'],s['root_category_id'],s['daily_date'])]+=1
    n=len(bysource[('PRACTICE',s['id'])]); count=len(question_ids[s['id']])
    if bool(s['completed_at'])!=(n==count and count>0) or s['daily_date']!=day(s['started_at']).isoformat(): daily_bad.append(s['id'])
check('S11','daily completion iff all fixed questions submitted, correct Beijing day',daily_bad)
check('S11','unique daily group',[list(k) for k,v in duplicate_daily.items() if v!=1])
# Distribution evidence, not hard pass/fail constraints for stochastic outcomes.
def rate(rows):
    rows=[a for a in rows if a['answered']]
    return round(sum(a['is_correct'] for a in rows)/len(rows),4) if rows else None
by_persona={}; by_target={}; hours=C.Counter(); weekdays=C.Counter(); registrations=C.Counter(); profile_counts=C.Counter(p['persona'] for p in pmap.values())
for persona in sorted(profile_counts):
    ids=[r['id'] for r in learners if pmap[r['username']]['persona']==persona]; rows=[a for uid in ids for a in byu[uid] if a['answered']]; user_rates=sorted(rate(byu[uid]) for uid in ids if rate(byu[uid]) is not None)
    by_persona[persona]={'users':len(ids),'answers':len(rows),'weightedAccuracy':rate(rows),'meanUserAccuracy':round(sum(user_rates)/len(user_rates),4) if user_rates else None,'p10':user_rates[int((len(user_rates)-1)*.1)] if user_rates else None,'p50':user_rates[int((len(user_rates)-1)*.5)] if user_rates else None,'p90':user_rates[int((len(user_rates)-1)*.9)] if user_rates else None}
for rid in [c['id'] for c in cat.values() if c['level']==1]:
    rows=[a for a in answers if a['root_category_id']==rid and a['answered']]
    by_target[cat[rid]['name']]={'currentUsers':sum(r['target_category_id']==rid for r in learners),'answers':len(rows),'accuracy':rate(rows)}
for a in answers:
    if a['source']=='PRACTICE' and a['answered']:
        persona=pmap[u[a['user_id']]['username']]['persona']; timestamp=bj(a['graded_at']); hours[(persona,timestamp.hour)]+=1; weekdays[(persona,timestamp.weekday())]+=1
for r in learners: registrations[day(r['created_at']).isoformat()]+=1
modes=C.Counter((s['mode'],bool(s['recite_mode']),bool(s['option_orders'])) for s in ps.values())
summary={'database':json.loads((out/'snapshot-summary.json').read_text())['database'],'snapshotDirectory':out.name,'cutoffCheck':cutoff.isoformat()+'Z','checks':results,'failedChecks':len(failures),'tables':{t:len(rows) for t,rows in data.items()},'answered':sum(a['answered'] for a in answers),'answerSources':dict(C.Counter(a['source'] for a in answers)), 'personas':by_persona,'targets':by_target,'hours':[{'persona':k[0],'hour':k[1],'count':v} for k,v in sorted(hours.items())],'weekdays':[{'persona':k[0],'weekdayMonday0':k[1],'count':v} for k,v in sorted(weekdays.items())], 'registrations':dict(sorted(registrations.items())),'modes':[{'mode':k[0],'recite':k[1],'shuffle':k[2],'count':v} for k,v in sorted(modes.items())], 'timestampRanges':{k:{'min':min(v).isoformat(),'max':max(v).isoformat()} for k,v in times.items()},'fingerprints':{t:hashlib.sha256(json.dumps(rows,sort_keys=True,ensure_ascii=False).encode()).hexdigest() for t,rows in data.items()}}
(out/'sql-audit.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)); print(json.dumps({'checks':len(results),'failedChecks':len(failures),'tables':summary['tables'],'failures':failures},ensure_ascii=False))
sys.exit(bool(failures))
