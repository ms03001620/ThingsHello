#!/usr/bin/env python3
"""On a Pi with no camera: verify capture failure leaves TCP control alive."""
import json, pathlib, socket, struct, subprocess, time
root=pathlib.Path('results'); root.mkdir(exist_ok=True)
config=root/'missing-camera.properties'
config.write_text('backend=recording\nbind=127.0.0.1\ntcp.port=0\nudp.port=0\ncamera=rpicam\nactions.path=results/missing-camera-actions.jsonl\n')
def read_exact(s,n):
    result=b''
    while len(result)<n:
        chunk=s.recv(n-len(result))
        if not chunk: raise RuntimeError('TCP closed')
        result+=chunk
    return result
def send(s,t,obj):
    data=json.dumps(obj).encode();s.sendall(struct.pack('<IB',len(data),t)+data)
def receive(s):
    n,t=struct.unpack('<Ib',read_exact(s,5));assert 0<n<=512000
    return t,read_exact(s,n).decode()
log=root/'missing-camera.stdout'
with log.open('w') as output:
    process=subprocess.Popen(['bin/car-pi','serve','--config',str(config)],stdout=output,stderr=subprocess.STDOUT)
    try:
        deadline=time.monotonic()+10
        while 'READY tcp=' not in log.read_text():
            if process.poll() is not None or time.monotonic()>deadline: raise RuntimeError('startup failed')
            time.sleep(.05)
        port=int(log.read_text().split('READY tcp=')[1].split()[0])
        with socket.create_connection(('127.0.0.1',port),timeout=3) as tcp:
            for attempt in range(3):
                send(tcp,3,dict(action=1,width=320,height=240,transferVideo=True));assert receive(tcp)[0]==5
                deadline=time.monotonic()+8
                while True:
                    send(tcp,5,{}); kind,text=receive(tcp); assert kind==5
                    info=json.loads(text)
                    if info['error']: break
                    if time.monotonic()>deadline: raise RuntimeError('Expected missing-camera failure (camera may be connected)')
                    time.sleep(.1)
                assert 'camera' in info['error'].lower()
                send(tcp,0,dict(left=0,right=0))
                send(tcp,5,{}); assert receive(tcp)[0]==5
                send(tcp,3,dict(action=0));assert receive(tcp)[0]==5
            assert process.poll() is None
        process.terminate();code=process.wait(timeout=10);assert code in (0,143)
        result=dict(status='PASS',scope='no-camera failure handling, not physical capture',cycles=3,exit=code)
        (root/'missing-camera.json').write_text(json.dumps(result,indent=2)+'\n'); print(json.dumps(result))
    finally:
        if process.poll() is None: process.kill();process.wait()
