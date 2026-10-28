#!/usr/bin/env python3
"""Run from distribution root; no GPIO output. SIGTERM/rebind acceptance."""
import json, os, pathlib, signal, socket, subprocess, time
results=pathlib.Path('results'); results.mkdir(exist_ok=True)
records=[]
for iteration in range(3):
    log=results / ('lifecycle-%d.log' % iteration)
    with log.open('w') as stream:
        p=subprocess.Popen(['bin/car-pi','serve','--config','config/recording.properties'],stdout=stream,stderr=subprocess.STDOUT)
        try:
            deadline=time.monotonic()+10
            while 'READY tcp=8010' not in log.read_text():
                if p.poll() is not None: raise RuntimeError('service exited before ready')
                if time.monotonic()>deadline: raise RuntimeError('startup timeout')
                time.sleep(.05)
            with socket.create_connection(('127.0.0.1',8010),timeout=2): pass
            p.send_signal(signal.SIGTERM); code=p.wait(timeout=10)
            if code not in (0,143): raise RuntimeError('unexpected exit %d' % code)
            with socket.socket() as s:
                s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1); s.bind(('127.0.0.1',8010))
            records.append({'iteration':iteration+1,'exit':code,'status':'PASS'})
        finally:
            if p.poll() is None: p.kill(); p.wait()
(results/'lifecycle.json').write_text(json.dumps({'status':'PASS','verification':'已验证','runs':records},ensure_ascii=False,indent=2)+'\n')
print('PASS: 3 start/SIGTERM/rebind cycles')
