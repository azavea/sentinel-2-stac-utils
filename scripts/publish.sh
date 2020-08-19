#!/bin/bash

set -e

if [[ -n "${S2_STAC_UTILS_DEBUG}" ]]; then
    set -x
fi

function usage() {
    echo -n \
        "Usage: $(basename "$0") TAG
Publish docker image to tag
"
}

if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    if [ "${1:-}" = "--help" ]; then
        usage
    else
        TERRAFORM_DIR="$(dirname "$0")/../deployment/terraform"
        echo
        echo "Attempting to publish image [${S2_IMAGE_TAG}]..."
        echo "-----------------------------------------------------"
        echo
    fi

    if [[ -n "${S2_IMAGE_TAG}" ]]; then
       docker build . -t "${S2_IMAGE_TAG}"
       docker push "${S2_IMAGE_TAG}"
    else
        echo "ERROR: No S2_IMAGE_TAG variable defined."
        exit 1
    fi
fi
