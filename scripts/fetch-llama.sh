#!/usr/bin/env bash
# Fetches llama.cpp at the pinned commit.
#
# The source is deliberately not committed: it is 225 MB of third-party code that would dwarf the
# rest of the repository and make every clone and diff slower. Pinning the commit instead keeps
# builds reproducible, which is the only property that actually mattered.
set -euo pipefail

COMMIT="67107ac34f62882d8b53ea41ad68010ea635b9be"
DEST="$(cd "$(dirname "$0")/.." && pwd)/inference/llama/src/main/cpp/llama.cpp"

if [ -d "$DEST/.git" ]; then
    if [ "$(git -C "$DEST" rev-parse HEAD)" = "$COMMIT" ]; then
        echo "llama.cpp already at $COMMIT"
        exit 0
    fi
    echo "Updating llama.cpp to $COMMIT"
    git -C "$DEST" fetch --depth 1 origin "$COMMIT"
    git -C "$DEST" checkout "$COMMIT"
else
    echo "Cloning llama.cpp at $COMMIT"
    git clone --filter=blob:none --no-checkout https://github.com/ggerganov/llama.cpp.git "$DEST"
    git -C "$DEST" fetch --depth 1 origin "$COMMIT"
    git -C "$DEST" checkout "$COMMIT"
fi

echo "Done."
