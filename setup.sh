#!/bin/bash
# A remote build cache upload can take a pooled connection that went idle during a long test task
# and was silently dropped by the network. Linux retransmits on such a connection for about 15
# minutes (tcp_retries2=15) before giving up, once per upload attempt, which stalled push builds by
# up to an hour. With 5 retries a dead connection fails within seconds and the build carries on.
if [ "$GITHUB_ACTIONS" = "true" ] && [ "$RUNNER_OS" = "Linux" ]; then
    sudo -n sysctl -w net.ipv4.tcp_retries2=5 > /dev/null || true
fi
# The JVM, native and Python CI builds never read the local Maven repository: they resolve the
# modules of this repository as projects of the same build
case "$GITHUB_WORKFLOW" in
    "Java CI"|"Python CI"|"GraalVM Latest CI"|"GraalVM Dev CI") exit 0 ;;
esac
./gradlew pTML
