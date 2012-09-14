'''
App Builder Library
+++++++++++++++++++

Contains utility methods useful for compiling and deploying applets and apps
onto the platform.

You can specify the destination project in the following ways (with the earlier
ones taking precedence):

* Supply the 'project' argument to :func:`upload_resources()` or
  :func:`upload_applet()`.
* Supply the 'project' attribute in your dxapp.json.
* Set the ``DX_WORKSPACE_ID`` environment variable (when running in a job context).

'''

import os, sys, json, subprocess, tempfile, logging, multiprocessing
import dxpy

NUM_CORES = multiprocessing.cpu_count()

class AppletBuilderException(Exception):
    """
    This exception is raised by the methods in this module when app or applet
    building fails.
    """
    pass

def _validate_applet_spec(applet_spec):
    if 'runSpec' not in applet_spec:
        raise AppletBuilderException("Required field 'runSpec' not found in dxapp.json")

def _validate_app_spec(app_spec):
    pass

def _get_applet_spec(src_dir):
    applet_spec_file = os.path.join(src_dir, "dxapp.json")
    with open(applet_spec_file) as fh:
        applet_spec = json.load(fh)

    _validate_applet_spec(applet_spec)
    if 'project' not in applet_spec:
        applet_spec['project'] = dxpy.WORKSPACE_ID
    return applet_spec

def _get_app_spec(src_dir):
    app_spec_file = os.path.join(src_dir, "dxapp.json")
    with open(app_spec_file) as fh:
        app_spec = json.load(fh)

    _validate_app_spec(app_spec)
    return app_spec

def build(src_dir):
    """
    Runs any build scripts that are found in the specified directory.

    In particular, runs ``./configure`` if it exists, followed by ``make -jN``
    if it exists (building with as many parallel tasks as there are CPUs on the
    system).
    """
    logging.debug("Building in " + src_dir)
    # TODO: use Gentoo or deb buildsystem
    config_script = os.path.join(src_dir, "configure")
    if os.path.isfile(config_script) and os.access(config_script, os.X_OK):
        logging.debug("Running ./configure")
        subprocess.check_call([config_script])
    if os.path.isfile(os.path.join(src_dir, "Makefile")) \
        or os.path.isfile(os.path.join(src_dir, "makefile")) \
        or os.path.isfile(os.path.join(src_dir, "GNUmakefile")):
        logging.debug("Building with make -j%d" % (NUM_CORES,))
        subprocess.check_call(["make", "-C", src_dir, "-j" + str(NUM_CORES)])

def upload_resources(src_dir, project=None):
    """
    :returns: A reference to the generated archive
    :rtype: list

    Archives and uploads the contents of the ``resources/`` subdirectory of
    *src_dir* to a new remote file object, and returns an list describing a
    single bundled dependency in the form expected by the ``bundledDepends``
    field of a run specification.
    """
    applet_spec = _get_applet_spec(src_dir)

    if project is None:
        dest_project = applet_spec['project']
    else:
        dest_project = project
        applet_spec['project'] = project

    resources_dir = os.path.join(src_dir, "resources")
    if os.path.exists(resources_dir) and len(os.listdir(resources_dir)) > 0:
        logging.debug("Uploading in " + src_dir)

        with tempfile.NamedTemporaryFile(suffix=".tar.gz") as tar_fh:
            subprocess.check_call(['tar', '-C', resources_dir, '-czf', tar_fh.name, '.'])
            if 'folder' in applet_spec:
                try:
                    dxpy.DXProject(dest_project).new_folder(applet_spec['folder'], parents=True)
                except dxpy.exceptions.DXAPIError:
                    pass # TODO: make this better
            target_folder = applet_spec['folder'] if 'folder' in applet_spec else '/'
            dx_resource_archive = dxpy.upload_local_file(tar_fh.name, wait_on_close=True,
                                                         project=dest_project, folder=target_folder, hidden=True)
            archive_link = dxpy.dxlink(dx_resource_archive.get_id())
            return [{'name': 'resources.tar.gz', 'id': archive_link}]
    else:
        return None

def upload_applet(src_dir, uploaded_resources, check_name_collisions=True, overwrite=False, project=None, dx_toolkit_autodep=True):
    """
    Creates a new applet object.
    """
    applet_spec = _get_applet_spec(src_dir)

    if project is None:
        dest_project = applet_spec['project']
    else:
        dest_project = project
        applet_spec['project'] = project

    if 'name' not in applet_spec:
        try:
            applet_spec['name'] = os.path.basename(os.path.abspath(src_dir))
        except:
            raise AppletBuilderException("Could not resolve applet name from specification or working directory")

    if 'dxapi' not in applet_spec:
        applet_spec['dxapi'] = dxpy.API_VERSION

    if check_name_collisions:
        logging.debug("Searching for applets with name " + applet_spec["name"])
        for result in dxpy.find_data_objects(classname="applet", properties={"name": applet_spec["name"]}, project=dest_project):
            if overwrite:
                logging.info("Deleting applet %s" % (result['id']))
                # TODO: test me
                dxpy.DXProject(dest_project).remove_objects([result['id']])
            else:
                raise AppletBuilderException("A applet with name %s already exists (id %s) and the overwrite option was not given" % (applet_spec["name"], result['id']))

    # -----
    # Override various fields from the pristine dxapp.json

    # Inline description from a readme file
    if 'description' not in applet_spec:
        readme_filename = None
        for filename in 'README.md', 'Readme.md', 'readme.md':
            if os.path.exists(os.path.join(src_dir, filename)):
                readme_filename = filename
                break
        if readme_filename is None:
            logging.warn("No description found; you should supply one in README.md")
        else:
            with open(os.path.join(src_dir, readme_filename)) as fh:
                applet_spec['description'] = fh.read()

    # Inline the code of the program
    if "runSpec" in applet_spec and "file" in applet_spec["runSpec"]:
        # Avoid using runSpec.file for now, it's not fully implemented
        #code_filename = os.path.join(src_dir, applet_spec["runSpec"]["file"])
        #f = dxpy.upload_local_file(code_filename, wait_on_close=True)
        #applet_spec["runSpec"]["file"] = f.get_id()
        # Put it into runSpec.code instead
        with open(os.path.join(src_dir, applet_spec["runSpec"]["file"])) as code_fh:
            applet_spec["runSpec"]["code"] = code_fh.read()
            del applet_spec["runSpec"]["file"]

    # Attach bundled resources to the app
    if uploaded_resources is not None:
        applet_spec["runSpec"].setdefault("bundledDepends", [])
        applet_spec["runSpec"]["bundledDepends"].extend(uploaded_resources)

    # Include the DNAnexus client libraries as an execution dependency, if they are not already
    # there
    dx_toolkit_dep = {"name": "dx-toolkit",
                      "package_manager": "git",
                      "url": "git@github.com:dnanexus/dx-toolkit.git",
                      "tag": "master",
                      "build_commands": "make install DESTDIR=/ PREFIX=/opt/dnanexus"}
    if dx_toolkit_autodep:
        applet_spec["runSpec"].setdefault("execDepends", [])
        dx_toolkit_dep_found = False
        for dep in applet_spec["runSpec"]["execDepends"]:
            if dep.get('name') == 'dx-toolkit' or dep.get('url') == "git@github.com:dnanexus/dx-toolkit.git":
                dx_toolkit_dep_found = True
        if not dx_toolkit_dep_found:
            applet_spec["runSpec"]["execDepends"].append(dx_toolkit_dep)

    # Supply a contactUrl if one is not provided
    if "details" not in applet_spec:
        applet_spec["details"] = {}
    if "contactUrl" not in applet_spec["details"]:
        new_contact_url = "http://wiki.dnanexus.com/Apps/%s" % (applet_spec["name"],)
        logging.info("Setting contactUrl to %s" % (new_contact_url,))
        logging.info('You can override this in your dxapp.json: {details: {contactUrl: "%s", ...}, ...' % (new_contact_url,))
        applet_spec["details"]["contactUrl"] = new_contact_url

    # -----
    # Now actually create the applet

    applet_id = dxpy.api.appletNew(applet_spec)["id"]

    properties = {"name": applet_spec["name"]}
    if "title" in applet_spec:
        properties["title"] = applet_spec["title"]
    if "summary" in applet_spec:
        properties["summary"] = applet_spec["summary"]
    if "description" in applet_spec:
        properties["description"] = applet_spec["description"]

    dxpy.api.appletSetProperties(applet_id, {"project": dest_project, "properties": properties})

    if "categories" in applet_spec:
        dxpy.DXApplet(applet_id, project=dest_project).add_tags(applet_spec["categories"])

    return applet_id

def _create_or_update_version(app_name, version, app_spec, try_update=True):
    """
    Creates a new version of the app. Returns an app_id, or None if the app has
    already been created and published.
    """
    # This has a race condition since the app could have been created or
    # published since we last looked.
    try:
        app_id = dxpy.api.appNew(app_spec)["id"]
        print >> sys.stderr, 'appNew => %r' % (app_id)
        return app_id
    except dxpy.exceptions.DXAPIError as e:
        # TODO: detect this error more reliably
        if e.name == 'InvalidInput' and e.msg == 'Specified name and version conflict with an existing alias':
            print >> sys.stderr, 'App %s/%s already exists' % (app_spec["name"], version)
            # The version number was already taken, so app/new doesn't work.
            # However, maybe it hasn't been published yet, so we might be able
            # to app-xxxx/update it.
            app_describe = dxpy.api.appDescribe("app-" + app_name, alias=version)
            if app_describe.get("published", 0) > 0:
                return None
            return _update_version(app_name, version, app_spec, try_update=try_update)
        raise e

def _update_version(app_name, version, app_spec, try_update=True):
    """
    Updates a version of the app in place. Returns an app_id, or None if the
    app has already been published.
    """
    if not try_update:
        return None
    try:
        app_id = dxpy.api.appUpdate("app-" + app_name, version, app_spec)["id"]
        print >> sys.stderr, 'appUpdate => %r' % (app_id)
        return app_id
    except dxpy.exceptions.DXAPIError as e:
        if e.name == 'InvalidState':
            print >> sys.stderr, 'App %s/%s has already been published' % (app_spec["name"], version)
            return None
        raise e

def create_app(applet_id, src_dir, publish=False, set_default=False, billTo=None, try_versions=None, try_update=True):
    """
    Creates a new app object from the specified applet.
    """
    app_spec = _get_app_spec(src_dir)
    print >> sys.stderr, "Will create app with spec: ", app_spec

    applet_desc = dxpy.DXApplet(applet_id).describe(incl_properties=True)
    app_spec["applet"] = applet_id
    app_spec["name"] = applet_desc["name"]

    if "title" in applet_desc["properties"]:
        app_spec["title"] = applet_desc["properties"]["title"]
    if "summary" in applet_desc["properties"]:
        app_spec["summary"] = applet_desc["properties"]["summary"]
    if "description" in applet_desc["properties"]:
        app_spec["description"] = applet_desc["properties"]["description"]

    if billTo:
        app_spec["billTo"] = billTo
    if not try_versions:
        try_versions = [app_spec["version"]]

    for version in try_versions:
        print >> sys.stderr, "Attempting to create version %s..." % (version,)
        app_spec['version'] = version
        app_describe = None
        try:
            app_describe = dxpy.api.appDescribe("app-" + app_spec["name"], alias=version)
        except dxpy.exceptions.DXAPIError as e:
            if e.name == 'ResourceNotFound':
                pass
            else:
                raise e
        # Now app_describe is None if the app didn't exist, OR it contains the
        # app describe content.

        # The describe check does not eliminate race conditions since an app
        # may always have been created, or published, since we last looked at
        # it. So the describe that happens here is just to save time and avoid
        # unnecessary API calls, but we always have to be prepared to recover
        # from API errors.
        if app_describe is None:
            print >> sys.stderr, 'App %s/%s does not yet exist' % (app_spec["name"], version)
            app_id = _create_or_update_version(app_spec['name'], app_spec['version'], app_spec, try_update=try_update)
            if app_id is None:
                continue
            print >> sys.stderr, "Created app " + app_id
            # Success!
            break
        elif app_describe.get("published", 0) == 0:
            print >> sys.stderr, 'App %s/%s already exists and has not been published' % (app_spec["name"], version)
            app_id = _update_version(app_spec['name'], app_spec['version'], app_spec, try_update=try_update)
            if app_id is None:
                continue
            print >> sys.stderr, "Updated existing app " + app_id
            # Success!
            break
        else:
            print >> sys.stderr, 'App %s/%s already exists and has been published' % (app_spec["name"], version)
            # App has already been published. Give up on this version.
            continue
    else:
        # All versions requested failed
        if len(try_versions) != 1:
            tried_versions = 'any of the requested versions: ' + ', '.join(try_versions)
        else:
            tried_versions = 'the requested version: ' + try_versions[0]
        raise EnvironmentError('Could not create %s' % (tried_versions,))

    if "categories" in app_spec:
        dxpy.api.appAddCategories(app_id, input_params={'categories': app_spec["categories"]})

    if publish:
        dxpy.api.appPublish(app_id, input_params={'makeDefault': set_default})

    return app_id
