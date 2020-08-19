#!/bin/bash

set -e

if [[ -n "${S2_STAC_UTILS_DEBUG}" ]]; then
    set -x
fi

function usage() {
    echo -n \
        "Usage: $(basename "$0")
Run batch jobs to process imagery
"
}

if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    if [ "${1:-}" = "--help" ]; then
        usage
    else
        TERRAFORM_DIR="$(dirname "$0")/../deployment/terraform"
        echo
        echo "Attempting to submit batch jobs..."
        echo "-----------------------------------------------------"
        echo
    fi

    while IFS= read -r INVENTORY_PATH; do
        INVENTORY_ABBREVIATION=$(echo "${INVENTORY_PATH}" | cut -c 21-30 | sed 's/\//-/g')
        echo "SUBMITTING JOB FOR DATE: $INVENTORY_ABBREVIATION"
        aws batch submit-job \
            --job-name "${COLLECTION}-${INVENTORY_ABBREVIATION}" \
            --job-queue queueCPU \
            --job-definition createSentinel2Catalogs \
            --parameters inventoryPath="s3://noaafloodmap-data-us-east-1/${INVENTORY_PATH}",outputCatalogRoot="s3://noaafloodmap-data-us-east-1/${COLLECTION}-stac",collection="${COLLECTION}"
    done <"${INVENTORY_CSV_PATH}"
fi
