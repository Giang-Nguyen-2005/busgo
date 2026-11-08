// Fail closed before sending any mutating HTTP request to the live application.
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const { execFileSync } = require('node:child_process');

const origin = 'http://127.0.0.1:5196';
const financeOrigin = 'http://127.0.0.1:5198';
const container = 'busgo-m306-regression-mysql-1';
function mysql(schema, sql) {
  return execFileSync('docker', ['exec', '-i', container, 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --skip-column-names --user="$MYSQL_USER" "$1"', 'mysql', schema],
    {input: sql, encoding:'utf8'}).trim();
}
async function verifyM306(target) {
  assert([origin,financeOrigin].includes(target), 'M30.6 requires a dedicated frontend port');
  const finance=target===financeOrigin;
  const schema=finance?'busgo_m306_finance':'busgo_m306_live';
  const sql=statement=>mysql(schema,statement);
  const instance = JSON.parse(execFileSync('docker',['inspect',container],{encoding:'utf8'}))[0];
  assert.equal(instance.Config.Labels['com.docker.compose.project'],'busgo-m306-regression');
  assert.equal(instance.Config.Labels['com.docker.compose.service'],'mysql');
  assert.equal(instance.Mounts.find(m=>m.Destination==='/var/lib/mysql')?.Name,'busgo-m306-regression_mysql_data');
  assert.deepEqual(instance.NetworkSettings.Ports['3306/tcp'],[{HostIp:'127.0.0.1',HostPort:'3346'}]);
  assert.equal(sql('SELECT DATABASE(), @@hostname'),schema+'\t'+instance.Config.Hostname);
  const marker='m306-'+randomUUID();
  // A fresh challenge proves both the direct BE and the FE proxy read this exact schema.
  const id=sql(`INSERT INTO transport_operators(name,code,status,created_at,updated_at) VALUES('${marker}','${marker}','ACTIVE',UTC_TIMESTAMP(),UTC_TIMESTAMP()); SELECT LAST_INSERT_ID();`);
  assert.match(id,/^\d+$/);
  try {
    for(const base of [finance?'http://127.0.0.1:8138':'http://127.0.0.1:8136',target]) {
      const r=await fetch(base+'/api/v1/public/operators/'+id+'/profile',{redirect:'error',signal:AbortSignal.timeout(15000)});
      assert.equal(r.status,200);assert.equal((await r.json()).data.operator.name,marker);
    }
  } finally {
    sql(`DELETE FROM transport_operators WHERE id=${id} AND code='${marker}'`);
  }
  return {sql};
}
module.exports={origin,financeOrigin,verifyM306};
