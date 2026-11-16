"""Fetch the pinned native/offline navigation dependencies (run from any directory)."""
from concurrent.futures import ThreadPoolExecutor
from hashlib import sha256
from pathlib import Path
from urllib.request import urlretrieve

DEST = Path(__file__).resolve().parents[1] / 'app/libs/navigation'
RUNTIMES = [
    ('osmand-java.jar', 'obf-runtime/osmand-java.jar', '6583dc3df3739337c1b20a8dfd69816aa7ec60414d004feddcae3865524ea698'),
    ('osmand-shared-jvm.jar', 'obf-runtime/osmand-shared-jvm.jar', '2154b9d6372e0a1f9454dc9e3604bf1124e44fb1a87fc033135ec76b5cd0de36'),
    ('gnu-trove-osmand.jar', 'obf-runtime/gnu-trove-osmand.jar', 'f485614b9407f27a4777910604fe494f49a4541438cb7f7c24df54eb2a988cd7'),
    ('kxml2-android.jar', 'obf-runtime/kxml2-vela.jar', '5143e831f3b887a17aed12d8ec948e297081c79c93ce46e04fff1fbf9aa31dab'),
    ('sherpa-onnx-1.13.3.aar', 'tts-runtime/sherpa-onnx-1.13.3.aar', '243ad797a3b6e75ebbeaf7a2ab4aec0777e7d71b730685abb762a120940b07b6'),
    ('cronet-155.0.8059.16.aar', 'cronet-runtime/cronet-155.0.8059.16.aar', '6879172bc9bb86265605b3a9876b0958961fba934941ec98ebb9fb7da52deee9'),
]

def fetch(item):
    name, remote, expected = item
    path = DEST / name
    if path.exists() and sha256(path.read_bytes()).hexdigest() == expected:
        return f'{name}: verified'
    temporary = path.with_suffix(path.suffix + '.download')
    urlretrieve('https://github.com/PimpinPumpkin/Vela/releases/download/' + remote, temporary)
    if sha256(temporary.read_bytes()).hexdigest() != expected:
        raise RuntimeError(f'Checksum mismatch: {name}')
    temporary.replace(path)
    return f'{name}: downloaded and verified'

if __name__ == '__main__':
    DEST.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=4) as pool:
        for result in pool.map(fetch, RUNTIMES):
            print(result, flush=True)
