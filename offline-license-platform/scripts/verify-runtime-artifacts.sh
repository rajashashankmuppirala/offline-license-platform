#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
VERSION=1.0.0-SNAPSHOT

cd "$ROOT"

# Packaging is deliberately completed before either archive or dependency scan.
mvn -q -DskipTests package
mvn -q -DskipTests -DincludeScope=runtime -DoutputFile=target/runtime-dependencies.txt dependency:tree

artifacts="
license-server/target/license-server-$VERSION.jar
license-client-java/target/license-client-java-$VERSION.jar
license-engine/target/license-engine-$VERSION.jar
license-storage/target/license-storage-$VERSION.jar
examples/sample-product/target/sample-product-$VERSION.jar
examples/tomcat-example/target/tomcat-example-$VERSION.jar
examples/kubernetes-example/target/kubernetes-example-$VERSION.jar
"

fail() {
  printf 'runtime artifact verification failed: %s\n' "$1" >&2
  exit 1
}

for archive in $artifacts; do
  [ -f "$archive" ] || fail "missing packaged archive $archive"

  entries=$(jar tf "$archive")
  if printf '%s\n' "$entries" | grep -Eiq '(^|/)([^/]*(private|vendor)[^/]*\.(key|pem|der|p8|pk8)|[^/]*seed[^/]*|id_ed25519)$'; then
    fail "$archive contains a private-key or seed-like entry"
  fi
  if printf '%s\n' "$entries" | grep -Eq '(^|/)(com/enterprise/licensing/crypto/vendor/|com/enterprise/licensing/issuer/)'; then
    fail "$archive contains vendor signing or issuer classes"
  fi
  if unzip -p "$archive" | LC_ALL=C grep -aEiq -- '-----BEGIN (ED25519 |EC |RSA )?PRIVATE KEY-----|vendor-private\.(key|pem)|default[-_ ]?secret|change[-_ ]?me'; then
    fail "$archive contains private-key material or a default secret"
  fi
done

dependency_files="
license-server/target/runtime-dependencies.txt
license-client-java/target/runtime-dependencies.txt
license-engine/target/runtime-dependencies.txt
license-storage/target/runtime-dependencies.txt
examples/sample-product/target/runtime-dependencies.txt
examples/tomcat-example/target/runtime-dependencies.txt
examples/kubernetes-example/target/runtime-dependencies.txt
"
for graph in $dependency_files; do
  [ -f "$graph" ] || fail "missing resolved dependency graph $graph"
done
if grep -E 'com\.enterprise\.licensing:(license-crypto-vendor|license-issuer-cli).*:(compile|runtime)$' $dependency_files >/dev/null 2>&1; then
  fail "a runtime dependency graph reaches vendor signing or issuer code"
fi

printf 'Verified %s packaged runtime archives and their resolved runtime dependency graphs.\n' "$(printf '%s\n' $artifacts | wc -l | tr -d ' ')"
