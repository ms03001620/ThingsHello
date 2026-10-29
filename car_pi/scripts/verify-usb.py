#!/usr/bin/env python3
"""Pi-only, real USB capture acceptance. Uses isolated recording service on 18010/18000."""
import argparse, hashlib, json, os, pathlib, socket, struct, subprocess, time
p=argparse.ArgumentParser();p.add_argument('--duration',type=int,default=600);p.add_argument('--cycles',type=int,default=10);args=p.parse_args()
root=pathlib.Path('results/usb-hardware');root.mkdir(parents=True,exist_ok=True)
config=root/'service.properties';config.write_text('backend=recording\nbind=127.0.0.1\ntcp.port=18010\nudp.port=18000\ncamera=v4l2\ncamera.device=/dev/v4l/by-id/usb-lihappe8_Corp._USB_2.0_Camera-video-index0\nactions.path=results/usb-hardware/actions.jsonl\n')
def exact(s,n):
    b=b''
    while len(b)<n:
        part=s.recv(n-len(b))
        if not part:raise RuntimeError('TCP EOF')
        b+=part
    return b
def send(s,t,data):
    b=json.dumps(data).encode();s.sendall(struct.pack('<IB',len(b),t)+b)
def response(s):
    n,t=struct.unpack('<Ib',exact(s,5));assert 0<n<=512000
    body=exact(s,n).decode();assert t==5,(t,body)
    return json.loads(body)
def open_camera(tcp):
    send(tcp,3,dict(action=1,width=320,height=240,transferVideo=True));response(tcp)
    send(tcp,5,{});return response(tcp)
def close_camera(tcp):
    send(tcp,3,dict(action=0));response(tcp);send(tcp,5,{});return response(tcp)
def receive(udp):
    packet,peer=udp.recvfrom(65507);assert peer[1]==18000
    assert packet[:3]==b'\x05\xff\xd8' and packet[-2:]==b'\xff\xd9'
    jpeg=packet[1:];index=2
    while index<len(jpeg):
        assert jpeg[index]==255
        marker=jpeg[index+1];length=int.from_bytes(jpeg[index+2:index+4],'big')
        if marker in (192,193,194):
            height=int.from_bytes(jpeg[index+5:index+7],'big');width=int.from_bytes(jpeg[index+7:index+9],'big')
            assert (width,height)==(320,240);return jpeg
        assert length>=2;index+=2+length
    raise RuntimeError('JPEG dimensions missing')
def metrics(pid):
    status=pathlib.Path('/proc/%d/status'%pid).read_text()
    rss=int(next(line.split()[1] for line in status.splitlines() if line.startswith('VmRSS:')))
    stat=pathlib.Path('/proc/%d/stat'%pid).read_text().split(') ')[1].split()
    cpu=(int(stat[11])+int(stat[12]))/os.sysconf('SC_CLK_TCK')
    children=sorted({child for task in pathlib.Path('/proc/%d/task'%pid).glob('*/children') for child in task.read_text().split()})
    return dict(rssKiB=rss,cpuSeconds=cpu,children=children)
log=root/'server.log';report=dict(status='RUNNING',cycles=[],samples=[])
with log.open('w') as output:
    process=subprocess.Popen(['bin/car-pi','serve','--config',str(config)],stdout=output,stderr=subprocess.STDOUT)
    try:
        deadline=time.monotonic()+10
        while 'READY tcp=18010' not in log.read_text():
            if process.poll() is not None or time.monotonic()>deadline:raise RuntimeError('startup failed')
            time.sleep(.1)
        with socket.create_connection(('127.0.0.1',18010),timeout=5) as tcp:
            for cycle in range(args.cycles):
                open_camera(tcp)
                with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as udp:
                    udp.settimeout(5);udp.bind(('127.0.0.1',0));udp.sendto(b'\x05hello',('127.0.0.1',18000));jpeg=receive(udp)
                    (root/'first.jpg').write_bytes(jpeg)
                close_camera(tcp)
                with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as probe:probe.bind(('127.0.0.1',18000))
                assert not metrics(process.pid)['children'],'capture child remains after close'
                report['cycles'].append(dict(cycle=cycle+1,status='PASS'))
            print('%d capture open/close cycles PASS'%args.cycles,flush=True)
            open_camera(tcp)
            with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as udp:
                udp.settimeout(5);udp.bind(('127.0.0.1',0));udp.sendto(b'\x05hello',('127.0.0.1',18000))
                frames=0;unique=set();sizes=[];start=time.monotonic();next_sample=start
                while time.monotonic()-start<args.duration:
                    jpeg=receive(udp);frames+=1;unique.add(hashlib.sha256(jpeg).hexdigest());sizes.append(len(jpeg))
                    now=time.monotonic()
                    if now>=next_sample:
                        sample=dict(elapsed=round(now-start,2),frames=frames,**metrics(process.pid));report['samples'].append(sample)
                        (root/'progress.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(sample),flush=True);next_sample=now+30
                (root/'last.jpg').write_bytes(jpeg)
                report.update(frames=frames,elapsed=round(time.monotonic()-start,2),uniqueFrames=len(unique),minJpegBytes=min(sizes),maxJpegBytes=max(sizes))
            info=close_camera(tcp);report['cameraInfo']=info;assert not info['error'],info['error']
            assert not metrics(process.pid)['children']
            report['finalMetrics']=metrics(process.pid)
        process.terminate();code=process.wait(timeout=10);assert code in (0,143)
        with socket.socket() as probe:probe.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);probe.bind(('127.0.0.1',18010))
        report.update(status='PASS',verification='已验证',exit=code,scope='USB capture + Java TCP/UDP, not phone UI or waveform precision')
    except Exception as e:
        report.update(status='FAIL',reason=repr(e));raise
    finally:
        if process.poll() is None:process.terminate()
        try:process.wait(timeout=10)
        except subprocess.TimeoutExpired:process.kill();process.wait()
        (root/'result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False),flush=True)
