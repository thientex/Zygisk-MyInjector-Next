#!/usr/bin/env python3
"""Run production RootProbe against the actual libsu dependency and a host sh.
Example: python3 tests/host/run.py --libsu-jar /path/to/classes.jar \
    --android-jar /path/to/android.jar --jdk /path/to/jdk17
"""
from pathlib import Path
import argparse
import os
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--libsu-jar', required=True, type=Path)
parser.add_argument('--android-jar', required=True, type=Path)
parser.add_argument('--jdk', required=True, type=Path)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[2]
classpath = os.pathsep.join(map(str, [args.libsu_jar.resolve(), args.android_jar.resolve()]))
sources = [repo / 'tests/host/android/text/TextUtils.java',
           repo / 'tests/host/RootProbeIntegration.java',
           repo / 'configapp/src/main/java/com/jiqiu/configapp/RootProbe.java']
with tempfile.TemporaryDirectory(prefix='root-probe-') as output:
    subprocess.run([str(args.jdk / 'bin/javac'), '-cp', classpath, '-d', output,
                    *map(str, sources)], check=True)
    subprocess.run([str(args.jdk / 'bin/java'), '-cp', output + os.pathsep + classpath,
                    'com.jiqiu.configapp.RootProbeIntegration'], check=True, timeout=30)
