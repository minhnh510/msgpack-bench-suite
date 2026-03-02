import json
import subprocess
import sys
import time
import urllib.request
from pathlib import Path
from datetime import datetime

ROOT = Path(__file__).resolve().parent
STATUS = ROOT / 'status.json'
RESULT = ROOT / 'result.json'
LOG = ROOT / 'run.log'
LIBS = ROOT / 'libs'
LIBS.mkdir(parents=True, exist_ok=True)


def write_status(**kwargs):
    obj = {'updated_at': datetime.now().strftime('%d-%m-%Y %H:%M:%S GMT+7')}
    obj.update(kwargs)
    STATUS.write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding='utf-8')


def log(msg):
    with LOG.open('a', encoding='utf-8') as f:
        f.write(f"[{datetime.now().isoformat()}] {msg}\n")


def download(url: str, dst: Path):
    if dst.exists() and dst.stat().st_size > 0:
        return
    urllib.request.urlretrieve(url, dst)


def wait_health(url: str, timeout_sec: int = 30):
    t0 = time.time()
    while time.time() - t0 < timeout_sec:
        try:
            with urllib.request.urlopen(url, timeout=3) as r:
                if r.status == 200:
                    return True
        except Exception:
            pass
        time.sleep(0.4)
    return False


try:
    write_status(stage='starting')
    log('START')

    deps = {
        'jackson-core-2.17.2.jar': 'https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.17.2/jackson-core-2.17.2.jar',
        'jackson-databind-2.17.2.jar': 'https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.17.2/jackson-databind-2.17.2.jar',
        'jackson-annotations-2.17.2.jar': 'https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.17.2/jackson-annotations-2.17.2.jar',
        'jackson-dataformat-msgpack-0.9.8.jar': 'https://repo1.maven.org/maven2/org/msgpack/jackson-dataformat-msgpack/0.9.8/jackson-dataformat-msgpack-0.9.8.jar',
        'msgpack-core-0.9.8.jar': 'https://repo1.maven.org/maven2/org/msgpack/msgpack-core/0.9.8/msgpack-core-0.9.8.jar',
    }

    write_status(stage='downloading_deps', total_deps=len(deps))
    for i, (name, url) in enumerate(deps.items(), 1):
        dst = LIBS / name
        download(url, dst)
        write_status(stage='downloading_deps', done=i, total_deps=len(deps), current=name)

    write_status(stage='starting_python_server')
    server_proc = subprocess.Popen([sys.executable, str(ROOT / 'server.py')], cwd=str(ROOT), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    log(f'server pid={server_proc.pid}')

    if not wait_health('http://127.0.0.1:18991/health', timeout_sec=30):
        raise RuntimeError('python server health check failed')

    write_status(stage='compiling_kotlin')
    kotlinc = Path(r'C:\Users\NHM\openclaw-workspace\runtime\research\toolchains\kotlin-compiler-2.1.10\kotlinc\bin\kotlinc.bat')
    if not kotlinc.exists():
        raise RuntimeError(f'kotlinc not found: {kotlinc}')

    cp_compile = ';'.join(p.as_posix() for p in sorted(LIBS.glob('*.jar')))
    outjar = ROOT / 'benchmark-client.jar'
    argfile = ROOT / 'kotlinc_args.txt'
    argfile.write_text('\n'.join([
        '-classpath',
        cp_compile,
        '-include-runtime',
        '-d',
        outjar.as_posix(),
        (ROOT / 'BenchmarkClient.kt').as_posix(),
    ]), encoding='utf-8')

    c = subprocess.run([str(kotlinc), '@' + str(argfile)], cwd=str(ROOT), capture_output=True, text=True, timeout=300)
    if c.returncode != 0:
        raise RuntimeError(f'kotlin compile failed: {c.stderr[:1200]}')

    write_status(stage='running_kotlin_client')
    cp_run = str(outjar) + ';' + ';'.join(str(p) for p in sorted(LIBS.glob('*.jar')))
    r = subprocess.run(['java', '-cp', cp_run, 'BenchmarkClientKt', 'http://127.0.0.1:18991', '1500'], cwd=str(ROOT), capture_output=True, text=True, timeout=900)
    if r.returncode != 0:
        raise RuntimeError(f'kotlin run failed: {r.stderr[:1200]}')

    parsed = json.loads(r.stdout.strip())
    RESULT.write_text(json.dumps({'ok': True, 'benchmark': parsed, 'finished_at': datetime.now().strftime('%d-%m-%Y %H:%M:%S GMT+7')}, ensure_ascii=False, indent=2), encoding='utf-8')
    write_status(stage='done', ok=True)
    log('DONE')

except Exception as e:
    RESULT.write_text(json.dumps({'ok': False, 'error': str(e)}, ensure_ascii=False, indent=2), encoding='utf-8')
    write_status(stage='error', ok=False, error=str(e))
    log(f'ERROR {e}')

finally:
    try:
        if 'server_proc' in locals() and server_proc and server_proc.poll() is None:
            server_proc.terminate()
            time.sleep(1)
            if server_proc.poll() is None:
                server_proc.kill()
    except Exception:
        pass
