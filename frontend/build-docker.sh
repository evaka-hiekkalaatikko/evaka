#!/bin/bash

# SPDX-FileCopyrightText: 2017-2022 City of Espoo
#
# SPDX-License-Identifier: LGPL-2.1-or-later

set -euo pipefail

cd "$( dirname "${BASH_SOURCE[0]}")"

EVAKA_CUSTOMIZATIONS="${2:-espoo}"

if [ "${1:-}" = "test" ] || [ "${1:-}" = "builder" ]; then
    docker build -t evaka/frontend-builder \
        --target=builder \
        --build-arg build=0 \
        --build-arg commit="$(git rev-parse HEAD)" \
        --build-context "customizations=src/lib-customizations/${EVAKA_CUSTOMIZATIONS}" \
        -f Dockerfile .
else
    docker build -t evaka/frontend \
        --build-arg build=0 \
        --build-arg commit="$(git rev-parse HEAD)" \
        --build-context "customizations=src/lib-customizations/${EVAKA_CUSTOMIZATIONS}" \
        -f Dockerfile .
fi

if [ "${1:-}" = "test" ]; then
    docker run --rm evaka/frontend-builder:latest yarn lint
    docker run --rm evaka/frontend-builder:latest yarn type-check
    docker run --rm evaka/frontend-builder:latest yarn test --maxWorkers=2
fi
