import math
import matplotlib.pyplot as plt
import numpy as np

from common import get_key
"""
    Graph functions for scale graphs.
    Prints 1up and 2up graphs of scale timings and errors.

    If you are running this on a Mac, you will likely need to create a ~/.matplotlib/matplotlibrc
    file and include `backend: TkAgg`.
    http://stackoverflow.com/questions/21784641/installation-issue-with-matplotlib-python
"""


def index_of_first_failure(stats, marathon_type, test_type):
    """ Finds the first occurance of an error during a deployment
    """
    index = -1
    deploy_status = stats.get(get_key(marathon_type, test_type, 'deployment_status'))
    for status in deploy_status:
        index += 1
        if "f" == status:
            return index

    return -1


def pad(array, size):
    current_size = len(array)
    if current_size < size:
        pad = np.zeros(size - current_size)
        padded = array.tolist() + pad.tolist()
        return np.array(padded)
    else:
        return array


def plot_test_timing(plot, stats, marathon_type, test_type, xticks):
    """ Plots a specific test graph.
        In addition, it sets the legend title, and flags the highest scale reached.
    """
    deploy_time = stats.get(get_key(marathon_type, test_type, 'deploy_time'))

    if deploy_time is None or len(deploy_time) == 0 or deploy_time[0] <= 0.0:
        return

    timings = np.array(deploy_time)
    title = '{} Scale Times'.format(test_type.title())
    timings = pad(timings, len(xticks))
    timings_handle, = plot.plot(xticks, timings, label=title)

    fail_index = index_of_first_failure(stats, marathon_type, test_type)
    if fail_index > 0:
        scale_at_fail = stats.get(get_key(marathon_type, test_type, 'max'))[fail_index]
        time_at_fail = stats.get(get_key(marathon_type, test_type, 'human_deploy_time'))[fail_index]
        text = '{} at {}'.format(scale_at_fail, time_at_fail)
        plot.text(fail_index, timings[fail_index], text,  wrap=True)


def plot_test_errors(plot, stats, marathon_type, test_type, xticks):
    """ Plots the number of errors for a given test
    """
    test_errors = stats.get(get_key(marathon_type, test_type, 'errors'))
    if test_errors is None or len(test_errors) == 0:
        return 0

    plot.set_title("Errors During Test")
    errors = np.array(test_errors)
    title = '{} Errors'.format(test_type.title())
    errors = pad(errors, len(errors))
    errors_handle, = plot.plot(xticks, errors, label=title, marker='o', linestyle='None')
    return max(test_errors)


def create_scale_graph(stats, metadata, test_types=[], file_name='scale.png'):
    """ Creates a 1up or 2up scale graph depending on if error information is provided.
        The first 1up graph "time_plot", is x = scale and y = time to reach scale
        The second graph "error_plot", is an error graph that plots the number of errors that occurred during the test.
    """
    marathon_type = metadata['marathon']
    error_plot = None
    fig = None
    time_plot = None

    # figure and plots setup
    if error_graph_enabled(stats, marathon_type, test_types):
        fig, (time_plot, error_plot) = plt.subplots(nrows=2)
    else:
        fig, time_plot = plt.subplots(nrows=1)

    # figure size, borders and padding
    fig.subplots_adjust(left=0.12, bottom=0.08, right=0.90, top=0.90, wspace=0.25, hspace=0.40)
    fig.set_size_inches(8.5, 6)

    # Titles and X&Y setup
    time_plot.title.set_text('Marathon Scale Test for v{}'.format(metadata['marathon-version']))
    targets = get_scale_targets(stats, marathon_type, test_types)
    if targets is None:
        print('Unable to create graph due without targets')
        return

    xticks = np.array(range(len(targets)))

    plt.xticks(xticks, targets)
    time_plot.set_xticks(xticks, targets)
    agents, cpus, mem = get_resources(metadata)
    time_plot.set_xlabel('Scale Targets on {} nodes with {} cpus and {} mem'.format(agents, cpus, mem))
    time_plot.set_ylabel('Time to Reach Scale (sec)')
    time_plot.grid(True)

    # graph of all the things
    for test_type in test_types:
        plot_test_timing(time_plot, stats, marathon_type, test_type, xticks)
    time_plot.legend(loc='upper left')

    # graph the errors if they exist
    if error_plot is not None:
        top = 1
        for test_type in test_types:
            largest = plot_test_errors(error_plot, stats, marathon_type, test_type, xticks)
            if largest > top:
                top = largest

        error_plot.legend(loc='upper left')
        error_plot.set_ylim(bottom=0, top=roundup_top(top))

    plt.savefig(file_name)


def roundup_top(x):
    return int(math.ceil(x / 10.0)) * 10


def get_scale_targets(stats, marathon_type, test_types):
    """ Returns the scale targets 1, 10, 100, 1000
        It is possible that some tests are ignored so we may have to
        loop to grab the right list.
    """
    targets = None
    for test_type in test_types:
        targets = stats.get(get_key(marathon_type, test_type, 'target'))
        if targets and len(targets) > 0:
            return targets

    return targets


def error_graph_enabled(stats, marathon_type, test_types):
    """ Returns true if there is any error data to graph
    """
    enabled = False
    for test_type in test_types:
        test_errors_key = get_key(marathon_type, test_type, 'errors')
        if test_errors_key is not None:
            test_errors = stats.get(test_errors_key)
            # if there are test errors... graph them
            if test_errors is not None and len(test_errors) > 0:
                return True

    return False


def get_resources(metadata):
    agents = 0
    cpus = 0
    mem = 0
    try:
        agents = metadata['private-agents']
        cpus = metadata['resources']['cpus']
        mem = metadata['resources']['memory']
    except Exception as e:
        print(e)

    return (agents, cpus, mem)
