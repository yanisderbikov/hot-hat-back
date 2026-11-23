"""Public network diagnostics for the Immers -> Railway GPU queue.

Never sends GPU token, opens paid jobs, or prints environment secret values.
Read-only checks: DNS, TCP/TLS, public health endpoint.
"""
from __future__ import annotations

import os
import socket
import ssl
import time
from urllib.parse import urlparse

import requests

BASE=os.getenv("HOT_HAT_GPU_SERVER_BASE_URL","https://hot-hat-back.up.railway.app").rstrip("/")
url=urlparse(BASE)
host=url.hostname or ""

def note(label, result, *, elapsed=None):
    suffix=f" ({elapsed:.1f}s)" if elapsed is not None else ""
    print(f"{label}: {result}{suffix}",flush=True)

def main():
    if url.scheme!="https" or url.username or url.password or not host:
        note("config","invalid HTTPS Railway URL")
        return
    note("target",host)
    note("proxy_env",str(any(os.getenv(k) for k in (
        "HTTPS_PROXY","https_proxy","HTTP_PROXY","http_proxy","ALL_PROXY","all_proxy"))).lower())
    t=time.monotonic()
    try:
        addresses=socket.getaddrinfo(host,443,socket.AF_INET,socket.SOCK_STREAM)
        ips=list(dict.fromkeys(item[4][0] for item in addresses))
        note("dns_ipv4",str(ips[:4]),elapsed=time.monotonic()-t)
    except Exception as e:
        note("dns_ipv4",f"FAILED {type(e).__name__} {str(e)[:150]}",elapsed=time.monotonic()-t)
        return
    if not ips:
        note("dns_ipv4","NO_IPV4_ADDRESS")
        return
    for ip in ips[:2]:
        t=time.monotonic()
        try:
            with socket.create_connection((ip,443),timeout=5) as raw:
                note("tcp_443",f"OK {ip}",elapsed=time.monotonic()-t)
                raw.settimeout(6)
                with ssl.create_default_context().wrap_socket(raw,server_hostname=host) as tls:
                    note("tls",f"OK {tls.version()}",elapsed=time.monotonic()-t)
            break
        except Exception as e:
            note("tcp_tls",f"FAILED {ip} {type(e).__name__} {str(e)[:120]}",elapsed=time.monotonic()-t)
    for trust_env in (False,True):
        session=requests.Session()
        session.trust_env=trust_env
        started=time.monotonic()
        try:
            r=session.get(BASE+"/api/v2/app/health",timeout=(6,12),
                          allow_redirects=False)
            note(f"health_HTTPS_proxy_env_{trust_env}",
                 f"HTTP {r.status_code} server returned {len(r.content)} bytes",
                 elapsed=time.monotonic()-started)
        except Exception as e:
            note(f"health_HTTPS_proxy_env_{trust_env}",
                 f"FAILED {type(e).__name__} {str(e)[:180]}",
                 elapsed=time.monotonic()-started)
        finally:
            session.close()
    note("interpretation","If raw TCP/TLS fails, investigate Immers outbound networking. "
         "If TLS works but public /health fails, check Railway edge/application. "
         "If proxy_env=False works but True fails, a proxy setting is interfering.")

if __name__=="__main__":
    main()
