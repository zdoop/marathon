from common import app, available_resources, cluster_info, ensure_mom_version
from datetime import timedelta
from dcos import marathon
import itertools
import shakedown
from utils import marathon_on_marathon

def linear_step_function(step_size=1000):
    """
    Curried linear step function that gives next instances size based on a step.
    """
    def inner(step):
        return step * step_size
    return inner


def incremental_steps(step_func):
    """
    Generator that yields new instances size in steps until eternity.

    :param step_func The current step number is passed to this function. It
        should return the next size. See 'linear_step_function' for an example.
    :yield Next size
    """
    for current_step in itertools.count(start=1):
        yield step_func(current_step)


def test_incremental_scale():
    """
    Scale instances of app in steps until the first error, e.g. a timeout, is
    reached.
    """

    cluster_info()
    print(available_resources())

    app_def = {
      "id": "cap-app",
      "instances":  1,
      "cmd": "for (( ; ; )); do sleep 100000000; done",
      "cpus": 0.001,
      "mem": 8,
      "disk": 0,
      "backoffFactor": 1.0,
      "backoffSeconds": 0,
    }

    client = marathon.create_client()
    client.add_app(app_def)

    for new_size in incremental_steps(linear_step_function(step_size=1000)):
        shakedown.echo("Scaling to {}".format(new_size))
        shakedown.deployment_wait(
            app_id='cap-app', timeout=timedelta(minutes=10).total_seconds())

        client.scale_app('/cap-app', new_size)
        shakedown.deployment_wait(
            app_id='cap-app', timeout=timedelta(minutes=10).total_seconds())
        shakedown.echo("done.")


def test_incremental_app_scale():
    """
    Scale number of app in steps until the first error, e.g. a timeout, is
    reached.
    """

    batch_size = 50

    cluster_info()
    print(available_resources())

    def app_def(app_id):
        return {
            "id": app_id,
            "instances":  1,
            "cmd": "for (( ; ; )); do sleep 100000000; done",
            "cpus": 0.001,
            "mem": 8,
            "disk": 0,
            "backoffFactor": 1.0,
            "backoffSeconds": 0,
        }

    client = marathon.create_client()
    # client.remove_group('/', True)

    for step in itertools.count(start=1):
        shakedown.echo("Add {} apps".format(batch_size))

        app_id = "app-{0:0>4}".format(step)
        client.add_app(app_def(app_id))
        # group_id = "/batch-{0:0>3}".format(step)
        # app_ids = ("app-{}".format(i) for i in range(batch_size))
        # app_definitions = [app_def(app_id) for app_id in app_ids]
        # next_batch = {
        #     "apps": app_definitions,
        #     "dependencies": [],
        #     "id": group_id
        # }

        # client.create_group(next_batch)
        shakedown.deployment_wait(timeout=timedelta(minutes=15).total_seconds())

        shakedown.echo("done.")

