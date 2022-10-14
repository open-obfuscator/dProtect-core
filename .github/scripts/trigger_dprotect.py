#!/usr/bin/env python
"""
This script is used to trigger the main dProtect CI workflow
when there is a commit in dProtect-core
"""
import requests
import os
import sys
import logging

LOG_LEVEL         = logging.INFO
ORG               = "open-obfuscator"
REPO              = "dProtect"
URL_BASE          = "https://api.github.com/repos/{org}/{repo}".format(org=ORG, repo=REPO)
DPROTECT_WORKFLOW_TOKEN = os.getenv("DPROTECT_WORKFLOW_TOKEN", None)

logging.getLogger().addHandler(logging.StreamHandler(stream=sys.stdout))
logging.getLogger().setLevel(LOG_LEVEL)
logger = logging.getLogger(__name__)

if DPROTECT_WORKFLOW_TOKEN is None or len(DPROTECT_WORKFLOW_TOKEN) == 0:
    logger.error("DPROTECT_WORKFLOW_TOKEN is not set")
    sys.exit(1)

def list_workflow():
    url = "{base}/actions/workflows".format(base=URL_BASE)
    headers = {
        "Accept": "application/vnd.github.v3+json",
        "Authorization": "token {}".format(DPROTECT_WORKFLOW_TOKEN)
    }
    r = requests.get(url, headers=headers)
    if r.status_code != 200:
        logger.error("Error while trying to list workflow's actions: %s", r.text)
        return None
    return r.json()


def get_workflow(name: str):
    flows = list_workflow()
    if flows is None:
        logger.error("Can't list the workflows")
        sys.exit(1)

    return next(filter(lambda e: e["name"] == name, flows["workflows"]), None)

def trigger_workflow(name: str, branch: str = "main"):
    flow = get_workflow(name)
    if flow is None:
        logger.error("Can't find workflow '%s'", name)
        sys.exit(1)

    workflow_id = flow["id"]
    endpoint = "{base}/actions/workflows/{workflow_id}/dispatches".format(base=URL_BASE, workflow_id=workflow_id)

    headers = {
        "Accept": "application/vnd.github.v3+json",
        "Authorization": "token {}".format(DPROTECT_WORKFLOW_TOKEN)
    }
    data = {
        "ref": branch
    }
    r = requests.post(endpoint, headers=headers, json=data)

    if r.status_code != 204:
        logger.error("Error while trying to list workflow's actions: %s", r.text)
        return None

trigger_workflow("dProtect Main")


