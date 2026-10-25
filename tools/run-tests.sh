#!/bin/bash
# Compiles the plugin and its tests against the hand-written Paper API stubs in ./stubs and runs them.
# Needs: a JDK 21 (javac, java) and python3 with PyYAML (only for the config.yml / plugin.yml checks).
# This is NOT a real Maven/Paper build: run `mvn clean package` for that. (src/test is skipped by Maven on purpose:
# those checks call sandbox-only helpers that do not exist in the real Paper API.)
set -e
cd "$(dirname "$0")/.."
B="$(mktemp -d)"
mkdir -p "$B/stubs" "$B/wapi" "$B/main" "$B/test"
javac -encoding UTF-8 -nowarn -d "$B/stubs" $(find stubs -name "*.java")
javac -encoding UTF-8 -nowarn -cp "$B/stubs" -d "$B/wapi" $(find tools/waiting-api -name "*.java")
javac -encoding UTF-8 -Xlint:all -Werror -cp "$B/stubs" -d "$B/main" $(find src/main/java -name "*.java")
javac -encoding UTF-8 -Xlint:all -Werror -cp "$B/stubs:$B/main:$B/wapi" -d "$B/test" $(find src/test/java -name "*.java")
python3 -I tools/flatten-config.py src/main/resources/config.yml "$B/shipped.flat" >/dev/null
status=0
python3 -I - <<'PY' || status=1
import yaml
yaml.safe_load(open('src/main/resources/plugin.yml', encoding='utf-8'))
print('plugin.yml parses')
cfg = yaml.safe_load(open('src/main/resources/config.yml', encoding='utf-8'))
defs = cfg['traits']['definitions']
assert isinstance(defs, list) and defs, 'traits.definitions must be a non-empty list'
ids = [d['id'] for d in defs]
assert len(ids) == len(set(ids)), 'duplicate trait ids'
assert all(d['kind'] in ('STAT', 'ABILITY', 'COSMETIC') for d in defs), 'bad trait kind'
print('config.yml parses;', len(defs), 'traits, ids unique')
PY
for t in $(cd src/test/java && find . -name "*Test.java" | sed 's#^\./##;s#\.java$##;s#.*/##' | sort); do
  # WaitingBridgeTest runs twice: with NexusWaiting's API on the classpath and without it
  for extra in "$B/wapi" ""; do
    [ "$t" != "WaitingBridgeTest" ] && [ -z "$extra" ] && continue
    label="$t"
    if [ "$t" = "WaitingBridgeTest" ]; then label="$t (NexusWaiting $([ -n "$extra" ] && echo present || echo absent))"; fi
    echo "== $label"
    rc=0
    out="$(java -Dshipped.config="$B/shipped.flat" -cp "$B/stubs:$B/main:$B/test${extra:+:$extra}" "com.nexuscraft.nexushatchlings.$t" 2>&1)" || rc=$?
    echo "$out" | grep -E "FAIL|checks" || true
    if [ "$rc" -ne 0 ]; then status=1; fi
  done
done
rm -rf "$B"
exit $status
