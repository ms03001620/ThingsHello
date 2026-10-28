#!/usr/bin/env python3
"""Validate a running fixture service over LAN; no physical outputs are requested."""
import argparse, hashlib, json, pathlib, socket, struct, time
p=argparse.ArgumentParser(); p.add_argument('host'); p.add_argument('--output',default='results/network.json'); args=p.parse_args()
def read_exact(s,n):
    b=b''
    while len(b)<n:
        chunk=s.recv(n-len(b))
        if not chunk: raise RuntimeError('unexpected TCP EOF')
        b+=chunk
    return b
def receive(s):
    n,t=struct.unpack('<Ib',read_exact(s,5))
    if not 0<n<=512000: raise RuntimeError('invalid response size')
    return t,read_exact(s,n).decode()
def send(s,t,data):
    b=json.dumps(data).encode(); s.sendall(struct.pack('<IB',len(b),t)+b)
with socket.create_connection((args.host,8010),timeout=5) as tcp:
    send(tcp,3,dict(action=1,width=320,height=240,transferVideo=True))
    assert receive(tcp)[0]==5
    send(tcp,5,{})
    t,body=receive(tcp); assert t==5 and json.loads(body)['sizes']==[dict(mWidth=320,mHeight=240)]
    try:
        with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as udp:
            udp.settimeout(5); udp.bind(('',0)); udp.sendto(b'\x05hello',(args.host,8000))
            packet,peer=udp.recvfrom(65507)
            assert peer[1]==8000 and packet[:3]==b'\x05\xff\xd8' and packet[-2:]==b'\xff\xd9'
            expected=pathlib.Path(__file__).resolve().parents[1]/'src/main/resources/fixture-320x240.jpg'
            if expected.exists(): assert packet[1:]==expected.read_bytes()
            result=dict(status='PASS',verification='已验证',bytes=len(packet),jpegSha256=hashlib.sha256(packet[1:]).hexdigest(),sourcePort=peer[1],clientPort=udp.getsockname()[1])
    finally:
        send(tcp,3,dict(action=0)); assert receive(tcp)[0]==5
        send(tcp,5,{}); assert receive(tcp)[0]==5
out=pathlib.Path(args.output); out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n'); print(out.read_text())
