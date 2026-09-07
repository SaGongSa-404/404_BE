const fs=require('fs');
for(const file of process.argv.slice(2)) {
 const rows=fs.readFileSync(file,'utf8').trim().split('\n').slice(1).map(l=>{
  const [scenario,block,lane,n,p50,p95]=l.split(',');return {scenario,block:+block,lane,p50:+p50,p95:+p95};
 });
 const median=a=>{a=[...a].sort((x,y)=>x-y);return (a[(a.length-1)>>1]+a[a.length>>1])/2;};
 const result={file};
 for(const scenario of [...new Set(rows.map(r=>r.scenario))]) {
  const a=rows.filter(r=>r.scenario===scenario&&r.lane==='baseline');
  const b=rows.filter(r=>r.scenario===scenario&&r.lane==='candidate');
  result[scenario]={};
  for(const metric of ['p50','p95']) {
   const deltas=a.map(x=>b.find(y=>y.block===x.block)[metric]-x[metric]);
   result[scenario][metric]={baselineMedian:median(a.map(x=>x[metric])),candidateMedian:median(b.map(x=>x[metric])),pairedDeltaMedian:median(deltas),deltaMin:Math.min(...deltas),deltaMax:Math.max(...deltas),positiveBlocks:deltas.filter(x=>x>0).length,blocks:deltas.length};
  }
 }
 console.log(JSON.stringify(result,null,2));
}
