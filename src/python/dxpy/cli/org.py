# Copyright (C) 2013-2015 DNAnexus, Inc.
#
# This file is part of dx-toolkit (DNAnexus platform client libraries).
#
#   Licensed under the Apache License, Version 2.0 (the "License"); you may not
#   use this file except in compliance with the License. You may obtain a copy
#   of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
#   License for the specific language governing permissions and limitations
#   under the License.

'''
This submodule contains the callables (and their helpers) that are called by
the org-based commands of the dx command-line client.
'''
from __future__ import (print_function, unicode_literals)

from ..compat import input
import dxpy
from ..cli import try_call, prompt_for_yn, INTERACTIVE_CLI
from ..cli.parsers import process_find_by_property_args
from ..exceptions import (err_exit, DXCLIError)
from dxpy.utils.printing import (fill, DELIMITER, format_find_projects_results)
import json


def get_org_invite_args(args):
    """
    PRECONDITION:
        - If /org-x/invite is being called in conjunction with /user/new, then
          `_validate_new_user_input()` has been called on `args`; otherwise,
          the parser must perform all the basic input validation.
        - `args.username` is well-formed and valid (e.g. it does not start with
          "user-").
    """
    org_invite_args = {"invitee": "user-" + args.username}
    org_invite_args["level"] = args.level
    if "set_bill_to" in args and args.set_bill_to is True:
        # /org-x/invite is called in conjunction with /user/new.
        org_invite_args["allowBillableActivities"] = True
    else:
        org_invite_args["allowBillableActivities"] = args.allow_billable_activities
    org_invite_args["appAccess"] = args.app_access
    org_invite_args["projectAccess"] = args.project_access
    org_invite_args["suppressEmailNotification"] = args.no_email
    return org_invite_args


def add_membership(args):
    try:
        dxpy.api.org_get_member_access(args.org_id,
                                       {"user": "user-" + args.username})
    except:
        pass
    else:
        raise DXCLIError("Cannot add a user who is already a member of the org")

    dxpy.api.org_invite(args.org_id, get_org_invite_args(args))

    if args.brief:
        print("org-" + args.org_id)
    else:
        print(fill("Invited user-{u} to {o}".format(u=args.username,
                                                    o=args.org_id)))


def _get_org_remove_member_args(args):
    remove_member_args = {
        "user": "user-" + args.username,
        "revokeProjectPermissions": args.revoke_project_permissions,
        "revokeAppPermissions": args.revoke_app_permissions}
    return remove_member_args


def remove_membership(args):
    # Will throw ResourceNotFound of the specified user is not currently a
    # member of the org.
    dxpy.api.org_get_member_access(args.org_id,
                                   {"user": "user-" + args.username})

    confirmed = not args.confirm
    if not confirmed:
        # Request interactive confirmation.
        print(fill("WARNING: About to remove user-{u} from {o}; project permissions will{rpp} be removed and app permissions will{rap} be removed".format(
            u=args.username, o=args.org_id,
            rpp="" if args.revoke_project_permissions else " not",
            rap="" if args.revoke_app_permissions else " not")))

        if prompt_for_yn("Please confirm"):
            confirmed = True

    if confirmed:
        result = dxpy.api.org_remove_member(args.org_id,
                                            _get_org_remove_member_args(args))
        if args.brief:
            print(result["id"])
        else:
            print(fill("Removed user-{u} from {o}".format(u=args.username,
                                                          o=args.org_id)))
            print(fill("Removed user-{u} from the following projects:".format(
                u=args.username)))
            if len(result["projects"].keys()) != 0:
                for project_id in result["projects"].keys():
                    print("\t{p}".format(p=project_id))
            else:
                print("\tNone")
            print(fill("Removed user-{u} from the following apps:".format(
                u=args.username)))
            if len(result["apps"].keys()) != 0:
                for app_id in result["apps"].keys():
                    print("\t{a}".format(a=app_id))
            else:
                print("\tNone")
    else:
        print(fill("Aborting removal of user-{u} from {o}".format(
            u=args.username, o=args.org_id)))


def _get_org_set_member_access_args(args):
    user_id = "user-" + args.username
    org_set_member_access_input = {user_id: {"level": args.level}}
    if args.allow_billable_activities is not None:
        org_set_member_access_input[user_id]["allowBillableActivities"] = (True if args.allow_billable_activities == "true" else False)
    if args.app_access is not None:
        org_set_member_access_input[user_id]["appAccess"] = (True if args.app_access == "true" else False)
    if args.project_access is not None:
        org_set_member_access_input[user_id]["projectAccess"] = args.project_access
    return org_set_member_access_input


def update_membership(args):
    # Will throw ResourceNotFound of the specified user is not currently a
    # member of the org.
    dxpy.api.org_get_member_access(args.org_id,
                                   {"user": "user-" + args.username})
    result = dxpy.api.org_set_member_access(args.org_id,
                                            _get_org_set_member_access_args(args))
    if args.brief:
        print(result["id"])
    else:
        print(fill("Updated membership of user-{u} in {o}".format(
            u=args.username, o=args.org_id)))


def _get_find_orgs_args(args):
    find_orgs_input = {"level": args.level}

    if args.with_billable_activities is not None:
        find_orgs_input["allowBillableActivities"] = args.with_billable_activities

    if not args.brief:
        find_orgs_input["describe"] = True

    return {"query": find_orgs_input}


def find_orgs(args):
    res_iter = dxpy.find_orgs(_get_find_orgs_args(args)["query"])

    if args.json:
        print(json.dumps(list(res_iter)))
    elif args.brief:
        for res in res_iter:
            print(res["id"])
    else:
        for res in res_iter:
            print("{o}{d1}{n}".format(
                o=res["id"],
                d1=(DELIMITER(args.delimiter) if args.delimiter else " : "),
                n=res["describe"]["name"]
            ))


def new_org(args):
    if args.name is None and INTERACTIVE_CLI:
        args.name = input("Enter descriptive name for org: ")

    if args.name is None:
        err_exit("No org name supplied and input is not interactive.")

    inputs = {"handle": args.handle, "name": args.name, "policies": {"memberListVisibility":
              args.member_list_visibility, "restrictProjectTransfer": args.project_transfer_ability}}

    try:
        resp = dxpy.api.org_new(inputs)
        if args.brief:
            print(resp['id'])
        else:
            print("Org " + args.name + " created (org-" + args.handle + ")")
    except:
        err_exit()


def _get_update_org_args(args):
    if not args.name and not args.member_list_visibility and not args.project_transfer_ability:
        err_exit("At least 1 of --name, --member-list-visibility, or --project-transfer-ability required")
    else:
        inputs = {"policies": dxpy.api.org_describe(args.org_id)['policies']}
        if args.name:
            inputs["name"] = args.name
        if args.member_list_visibility:
            inputs["policies"]["memberListVisibility"] = args.member_list_visibility
        if args.project_transfer_ability:
            inputs["policies"]["restrictProjectTransfer"] = args.project_transfer_ability
        return inputs


def update_org(args):
    inputs = _get_update_org_args(args)
    try:
        dxpy.api.org_update(args.org_id, inputs)
    except:
        err_exit('Error while updating organization')
    if args.brief:
        print(args.org_id)
    else:
        print(fill("Updated {o}".format(o=args.org_id)))


def org_find_projects(args):
    try_call(process_find_by_property_args, args)
    try:
        results = dxpy.org_find_projects(org_id=args.org_id, name=args.name, name_mode='glob',
                                         ids=args.ids, properties=args.properties, tags=args.tag,
                                         describe=(not args.brief),
                                         public=args.public,
                                         created_after=args.created_after,
                                         created_before=args.created_before)
        format_find_projects_results(args, results)
    except:
        err_exit()
