import boto3
import click
import pandas as pd

from datetime import date
from functools import reduce
from io import StringIO
import json
from multiprocessing import Pool
import os
from tempfile import TemporaryDirectory
import time
from typing import List, Optional, Tuple

BUCKET = "sentinel-inventory"
s3_client = boto3.client("s3")


def get_prefix(collection: str) -> str:
    today = date.today()
    return f"sentinel-s2-{collection}/sentinel-s2-{collection}-inventory/{today.year}-{today.month:02}-{today.day-1:02}T00-00Z"


def get_manifest(collection: str) -> List[dict]:
    key = f"{get_prefix(collection)}/manifest.json"
    resp = s3_client.get_object(Bucket=BUCKET, Key=key)
    js = json.loads(resp["Body"].read())
    return js["files"]


def write_group(bucket: str, prefix: str, group: Tuple[str, pd.DataFrame]) -> None:
    (date, df) = group
    y, m, d = date.split("-")
    out_buf = StringIO()
    df.to_csv(out_buf, index=False, header=False)
    out_buf.seek(0)
    s3_client.put_object(
        Bucket=bucket,
        Key=f"{prefix}/{y}/{m.zfill(2)}/{d.zfill(2)}/data.csv",
        Body=out_buf.read(),
    )


def get_group(groupby: pd.core.groupby.DataFrameGroupBy, key: str) -> pd.DataFrame:
    try:
        return groupby.get_group(key)
    except KeyError:
        # because DataFrames are kind of monoidal
        return pd.DataFrame()


def partition_inventory_csv(infile: dict,) -> pd.core.groupby.DataFrameGroupBy:
    print(f"""Processing {infile["key"]}""")
    with TemporaryDirectory() as tempdir:
        local_path = os.path.join(tempdir, infile["key"].split("/")[-1])
        resp = s3_client.get_object(Bucket=BUCKET, Key=infile["key"])
        with open(local_path, "wb") as outf:
            outf.write(resp["Body"].read())
        all_files = pd.read_csv(
            local_path,
            header=None,
            names=["bucket", "key", "file_size", "ingest_timestamp"],
        )

    infos = all_files[all_files["key"].str.endswith("productInfo.json")]
    date_split = infos["key"].str.extract(
        r"^tiles/(?:\d{2})/[A-Z]/(?:[A-Z]{2})/(\d{4})/(\d+)/(\d+).*"
    )
    date_split["date"] = (
        date_split[0]
        + "-"
        + date_split[1].str.zfill(2)
        + "-"
        + date_split[2].str.zfill(2)
    )
    joined = infos.join(date_split["date"])
    return joined.groupby("date")


@click.group()
def cli():
    pass


@click.command()
@click.argument("s2collection", type=str)
@click.option("--output-bucket", type=str, help="S3 bucket for where to store results")
@click.option("--output-prefix", type=str, help="S3 prefix for where to store results")
@click.option(
    "--threads", type=int, help="How many inventory files to process at a time"
)
@click.option("--take", type=int)
def split_days(
    s2collection: str,
    output_bucket: str,
    output_prefix: str,
    threads: int = 8,
    take: Optional[int] = None,
) -> None:
    manifest_files = get_manifest(s2collection)
    print(f"Rows to process: {take or len(manifest_files)}")
    start = time.time()
    with Pool(threads) as pool:
        groups = pool.map(partition_inventory_csv, manifest_files[:take])
    all_keys = reduce(lambda x, y: x | y, [set(g.groups.keys()) for g in groups])
    keyed = {k: pd.concat([get_group(g, k) for g in groups]) for k in all_keys}
    for k, df in keyed.items():
        write_group(output_bucket, output_prefix, (k, df))
    print(f"Took {time.time() - start} seconds")


cli.add_command(split_days)


if __name__ == "__main__":
    cli()
