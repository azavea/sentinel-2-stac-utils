# sentinel-2-stac-utils
stac4s based tools for creating and updating STAC datasets of Sentinel 2

# publishing containers

To publish a container run `S2_IMAGE_TAG=<TAG> ./scripts/publish.sh`

# creating sentinel 2 catalogs with AWS Batch

Submitting jobs via AWS Batch to create a catalog assumes the following:
 - There is a job definition with the name `createSentinel2Catalogs`
 - There is a job queue with the name `queueCPU`

Step One: Download a list of inventory CSVs and save to a file

```
aws s3 ls --recursive s3://noaafloodmap-data-us-east-1/l2a-split-inventory/ | cut -c 32- > l2a-inventory.txt
```

Step Two: Submit batch jobs

```
INVENTORY_PATH=./l2a-inventory.txt COLLECTION=l2a ./scripts/submit-batch-jobs.sh
```