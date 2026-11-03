import {readFile,writeFile,open,mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import {createServer} from 'node:net';
import {processInfo,writeRegistry,stopOwned,checkOwned} from '../lib/process-owner.mjs';
const repo=fileURLToPath(new URL('../..',import.meta.url)).replace(/\/$/,'');
const dir=`${repo}/e2e/artifacts/mock-v1`, stateFile=`${dir}/stack.json`;
if(process.argv[2]==='down'){
 const s=JSON.parse(await readFile(stateFile,'utf8')); if(s.repo!==repo||s.database!=='quiz_mock_check')throw Error('wrong owner');
 await stopOwned([s.process],repo,`${dir}/stop-processes.json`);
 console.log(JSON.stringify({stoppedPid:s.process.pid,copyDatabaseRetained:s.database}));
}else if(process.argv[2]==='up'){
 await mkdir(dir,{recursive:true});
 try{const s=JSON.parse(await readFile(stateFile,'utf8'));if(await checkOwned(s.process,repo))throw Error('mock backend already running');}catch(e){if(e.code!=='ENOENT')throw e;}
 const server=createServer();await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve)); const port=server.address().port;await new Promise(resolve=>server.close(resolve));
 const log=await open(`${dir}/backend.log`,'a',0o600);
 const child=spawn('/data/tools/jdk-17/bin/java',['-Xmx384m','-jar','target/quiz-backend.jar','--spring.profiles.active=dev',`--server.port=${port}`,'--server.address=127.0.0.1',
 '--spring.datasource.url=jdbc:mysql://127.0.0.1:13306/quiz_mock_check?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC',
 '--spring.datasource.username=root','--spring.datasource.password=','--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver',
 '--app.mock.enabled=false','--app.seed.enabled=false','--app.exam.auto-settle=false',`--app.upload.dir=${dir}/uploads`],
 {cwd:`${repo}/backend`,env:{...process.env,APP_SEED_ENABLED:'false'},stdio:['ignore',log.fd,log.fd],detached:true});
 await new Promise((resolve,reject)=>{child.once('spawn',resolve);child.once('error',reject)});
 const record=await processInfo(child.pid);await checkOwned(record,repo);await writeRegistry(`${dir}/process.json`,repo,[record]);
 const s={repo,database:'quiz_mock_check',url:`http://127.0.0.1:${port}`,process:record};await writeFile(stateFile,JSON.stringify(s,null,2),{mode:0o600});child.unref();await log.close();
 let ready=false;for(let i=0;i<60;i++){
  try{const r=await fetch(`${s.url}/api/app/version?platform=android`,{signal:AbortSignal.timeout(1000)});if(r.ok){ready=true;break}}catch{}
  await new Promise(r=>setTimeout(r,500));
 }
 if(!ready){await stopOwned([record],repo,`${dir}/stop-processes.json`);throw Error('mock copy backend not ready')}
 console.log(JSON.stringify({url:s.url,pid:child.pid,database:s.database,mock:false,seed:false,autoSettle:false}));
}else throw Error('use up or down');
