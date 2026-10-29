#!/usr/bin/env python3
"""Pi-only physical motor test. Wheels must be raised. Uses separate TCP port 18012."""
import argparse, datetime, json, pathlib, socket, struct, subprocess, time
parser=argparse.ArgumentParser();parser.add_argument('--allow-motion',action='store_true');args=parser.parse_args()
if not args.allow_motion:parser.error('Physical movement requires --allow-motion and raised wheels')
root=pathlib.Path('results');root.mkdir(exist_ok=True)
config=(pathlib.Path('config/pi3-lgpio.properties').read_text().replace('tcp.port=8010','tcp.port=18012').replace('camera=v4l2','camera=disabled').replace('actions.path=results/hardware-actions.jsonl','actions.path=results/control-test.jsonl'))
(root/'control-test.properties').write_text(config);(root/'control-test.jsonl').write_text('')
def send(s,t,data):
    b=json.dumps(data).encode();s.sendall(struct.pack('<IB',len(b),t)+b)
def exact(s,n):
    b=b''
    while len(b)<n:
        part=s.recv(n-len(b))
        if not part:raise RuntimeError('Unexpected EOF')
        b+=part
    return b
def barrier(s):
    send(s,5,{});n,t=struct.unpack('<Ib',exact(s,5));assert t==5;exact(s,n)
def stop_levels():
    levels={str(pin):subprocess.check_output(['pinctrl','get',str(pin)],text=True).strip() for pin in (16,13)}
    assert all('| lo' in text for text in levels.values()),levels
    return levels
def events():return [json.loads(s) for s in (root/'control-test.jsonl').read_text().splitlines()]
report=dict(status='RUNNING',time=datetime.datetime.now(datetime.timezone.utc).isoformat(),checks=[])
log=root/'control-test.stdout'
with log.open('w') as output:
    process=subprocess.Popen(['bin/car-pi','serve','--config','results/control-test.properties'],stdout=output,stderr=subprocess.STDOUT)
    try:
        deadline=time.monotonic()+8
        while 'READY' not in log.read_text():
            if process.poll() is not None or time.monotonic()>deadline:raise RuntimeError('startup failed')
            time.sleep(.05)
        with socket.create_connection(('127.0.0.1',18012),timeout=3) as tcp:
            for label,left,right in [('left',40,0),('right',0,40)]:
                send(tcp,0,dict(left=left,right=right));barrier(tcp);time.sleep(.35)
                send(tcp,0,dict(left=0,right=0));barrier(tcp)
                report['checks'].append(dict(name=label+' zero command',levels=stop_levels()))
                time.sleep(.4)
            send(tcp,0,dict(left=-30,right=-30));barrier(tcp);time.sleep(.3)
        deadline=time.monotonic()+3
        while not any(e['action']=='wheel.release' and e['command']==0 for e in events()):
            if time.monotonic()>deadline:raise RuntimeError('disconnect release missing')
            time.sleep(.05)
        report['checks'].append(dict(name='disconnect stops motors',levels=stop_levels()));time.sleep(.1)
        with socket.create_connection(('127.0.0.1',18012),timeout=3) as tcp:
            send(tcp,0,dict(left=25,right=25));barrier(tcp);time.sleep(.2)
            process.terminate();code=process.wait(timeout=10);assert code in (0,143)
        report['checks'].append(dict(name='SIGTERM stops motors',levels=stop_levels()))
        all_events=events();assert not any(e['action']=='rejected' for e in all_events),all_events
        report.update(status='PASS',exit=code,configuredPwmHz=300,waveformPrecision='unverified')
    finally:
        if process.poll() is None:
            process.terminate()
            try:process.wait(timeout=10)
            except subprocess.TimeoutExpired:process.kill();process.wait()
        (root/'control-test.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report),flush=True)
