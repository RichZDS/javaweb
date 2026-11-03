"""Distribution evidence and temporal-order checks, without mutating the database."""
import collections as C, datetime as D, hashlib, json, re, sys
from pathlib import Path
root=Path(__file__).resolve().parents[2];out=Path(sys.argv[1]);data=json.loads((out/'snapshot.json').read_text());base=json.loads((out/'sql-audit.json').read_text());profiles=json.loads((root/'backend/src/main/resources/mock/users.json').read_text())
u={r['id']:r for r in data['app_user']}; pm={p['username']:p for p in profiles['users']};cat={r['id']:r for r in data['category']};ps={r['id']:r for r in data['practice_session']}
def dt(x):return D.datetime.fromisoformat(x)
def bj(x):return dt(x)+D.timedelta(hours=8)
def percentiles(values):
    values=sorted(values);return {str(p):values[round((len(values)-1)*p)] if values else None for p in [.1,.5,.9]}
byu=C.defaultdict(list); daycounts=C.Counter(); weekdays=C.Counter();hours=C.Counter();dist=C.Counter();profile_days=C.defaultdict(set); regmonth=C.Counter(); answered=[a for a in data['answer_record'] if a['answered']]
for a in answered:
    byu[a['user_id']].append(a);daycounts[(a['user_id'],str(bj(a['graded_at']).date()))]+=1
    if a['source']!='PRACTICE':continue
    p=pm[u[a['user_id']]['username']];t=bj(a['graded_at']);hours[t.hour]+=1;profile_days[p['persona']].add((a['user_id'],str(t.date()),t.weekday()))
    dist[min(min((t.hour-h)%24,(h-t.hour)%24) for h in p['hours'])]+=1
for x in data['app_user']:
    if x['role']=='USER':regmonth[str(bj(x['created_at']).date())[:7]]+=1
hist=C.Counter(); gains=[]; samples=C.defaultdict(list)
for uid,user in u.items():
    if user['role']!='USER':continue
    rows=byu[uid];p=pm[user['username']]
    if len(samples[p['persona']])<5:samples[p['persona']].append({'id':uid,'username':user['username'],'nickname':user['nickname'],'registeredOn':p['registeredOn'],'hours':p['hours'],'weekdayWeights':p.get('weekdayWeights'),'questionsPerDay':p['questionsPerDay'],'answers':len(rows),'accuracy':round(sum(a['is_correct'] for a in rows)/len(rows),4) if rows else None})
    if not rows:hist['no_answers']+=1;continue
    r=sum(a['is_correct'] for a in rows)/len(rows);hist[min(4,int(r*5))]+=1
    start=D.date.fromisoformat(p['registeredOn']); end=D.date.fromisoformat(p['lastActiveOn']) if p.get('lastActiveOn') else bj(base['cutoffCheck'].replace('Z','')).date()
    first=[a for a in rows if start<=bj(a['graded_at']).date()<start+D.timedelta(days=14)]; last=[a for a in rows if end-D.timedelta(days=13)<=bj(a['graded_at']).date()<=end]
    if (end-start).days>=28 and len(first)>=50 and len(last)>=50:
        f=sum(a['is_correct'] for a in first)/len(first);l=sum(a['is_correct'] for a in last)/len(last);gains.append({'userId':uid,'persona':p['persona'],'firstN':len(first),'lastN':len(last),'firstRate':round(f,4),'lastRate':round(l,4),'gain':round(l-f,4)})
# Keep unbounded A-B-A for comparison; the first-gap < 10 minute count matches
# MockSimulatorTest.aStudentDoesOneThingAtATime and excludes cross-day resumption.
interleaving=[]
for uid,rows in byu.items():
    seq=sorted([a for a in rows if a['source']=='PRACTICE'],key=lambda a:(a['graded_at'],a['id']))
    for a,b,c in zip(seq,seq[1:],seq[2:]):
        if a['source_id']==c['source_id']!=b['source_id']:
            interleaving.append({
                'userId':uid, 'answerIds':[a['id'],b['id'],c['id']],
                'sessionIds':[a['source_id'],b['source_id'],c['source_id']],
                'answerTimes':[a['graded_at'],b['graded_at'],c['graded_at']],
                'firstGapSeconds':(dt(b['graded_at'])-dt(a['graded_at'])).total_seconds(),
            })
notes=[x for x in data['favorite'] if x['note']]; fbs=data['question_feedback']; placeholders=[]
for table,rows,fields in [('favorite',data['favorite'],['note']),('question_feedback',fbs,['content','reply'])]:
    for r in rows:
        if any(re.search(r'\{(?:option|chapter)\}',r.get(f) or '') for f in fields):placeholders.append([table,r['id']])
handling=C.Counter((bj(x['handled_at']).date()-bj(x['created_at']).date()).days for x in fbs if x['handled_at'])
new_questions=json.loads((root/'backend/src/main/resources/mock/questions.json').read_text())['questions']; bystem={q['stem']:q for q in data['question']}; new_question_bad=[]
for q in new_questions:
    db=bystem.get(q['stem'])
    if not db or str(bj(db['created_at']).date())!=q['createdOn'] or db['status']!=q['status']:new_question_bad.append(q['stem'][:60])
result={'registrationsByMonth':dict(sorted(regmonth.items())),'usernameNoTrailingDigit':sum(not p['username'][-1].isdigit() for p in pm.values()),'distinctHourSets':len({tuple(p['hours']) for p in pm.values()}),'distinctWeekdayWeights':len({tuple(p.get('weekdayWeights',[1]*7)) for p in pm.values()}),'switchTargets':sum(bool(p.get('switchTarget')) for p in pm.values()),'noAvatars':sum(not x['avatar_url'] for x in u.values() if x['role']=='USER'),'accuracyBins20Pct':dict(hist),'dailyAnswerCountPercentiles':percentiles(list(daycounts.values())), 'practiceHours':dict(sorted(hours.items())),'distanceFromProfileHour':dict(sorted(dist.items())), 'weekendLearningDayShare':{p:{'days':len(days),'weekendDays':sum(w>=5 for uid,d,w in days),'share':round(sum(w>=5 for uid,d,w in days)/len(days),4)} for p,days in profile_days.items()},'gains':gains,'gainPercentiles':percentiles([x['gain'] for x in gains]),'personaSamples':dict(samples),'practiceABAAlternations':len(interleaving),'practiceABAFirstGapUnder10Minutes':sum(x['firstGapSeconds']<600 for x in interleaving),'alternationExamples':interleaving[:20],'favorites':len(data['favorite']),'withNotes':len(notes),'feedbackStatuses':dict(C.Counter(x['status'] for x in fbs)),'handlingCalendarDays':dict(handling),'unreplacedPlaceholders':placeholders,'newQuestionDateOrStatusMismatch':new_question_bad,'jsonSha256':{f:hashlib.sha256((root/f'backend/src/main/resources/mock/{f}.json').read_bytes()).hexdigest() for f in ['users','texts','questions','papers']}}
(out/'realism.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps({k:v for k,v in result.items() if k not in ['gains','personaSamples','alternationExamples','jsonSha256']},ensure_ascii=False))
