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

    return index


def plot_test_timing(plot, stats, marathon_type, test_type, x):
    """ Plots a specific test graph.
        In addition, it sets the legend title, and flags the highest scale reached.
    """
    deploy_time = stats.get(get_key(marathon_type, test_type, 'deploy_time'))

    if deploy_time is None or len(deploy_time) == 0:
        return

    timings = np.array(deploy_time)
    title = '{} Scale Times'.format(test_type.title())
    timings_handle, = plot.plot(x, timings, label=title)
    fail_index = index_of_first_failure(stats, marathon_type, test_type)

    if fail_index > 0:
        scale_at_fail = stats.get(get_key(marathon_type, test_type, 'max'))[fail_index]
        time_at_fail = stats.get(get_key(marathon_type, test_type, 'human_deploy_time'))[fail_index]
        text = '{} at {}'.format(scale_at_fail, time_at_fail)
        plot.text(fail_index, timings[fail_index], text,  wrap=True)


def plot_test_errors(plot, stats, marathon_type, test_type, x):
    """ Plots the number of errors for a given test
    """
    test_errors = stats.get(get_key(marathon_type, test_type, 'errors'))
    if test_errors is None or len(test_errors) == 0:
        return

    plot.set_title("Errors During Test")
    errors = np.array(test_errors)
    title = '{} Errors'.format(test_type.title())
    errors_handle, = plot.plot(x, errors, label=title, marker='o', linestyle='None')


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
    test_errors = get_key(marathon_type, test_types[0], 'errors')
    if test_errors is None or stats.get(test_errors) is None:
        fig, time_plot = plt.subplots(nrows=1)
    else:
        fig, (time_plot, error_plot) = plt.subplots(nrows=2)

    # figure size, borders and padding
    fig.subplots_adjust(left=0.12, bottom=0.08, right=0.90, top=0.90, wspace=0.25, hspace=0.40)
    fig.set_size_inches(8.5, 6)

    # Titles and X&Y setup
    time_plot.title.set_text('Marathon Scale Test for v{}'.format(metadata['marathon-version']))
    targets = stats.get(get_key(marathon_type, test_types[0], 'target'))

    x = np.array(range(len(targets)))

    plt.xticks(x, targets)
    time_plot.set_xlabel('Scale Targets on {} nodes'.format(metadata['private-agents']))
    time_plot.set_ylabel('Time to Reach Scale (sec)')

    # graph of all the things
    for test_type in test_types:
        plot_test_timing(time_plot, stats, marathon_type, test_type, x)
    time_plot.legend(loc='upper left')

    # graph the errors if they exist
    if error_plot is not None:
        for test_type in test_types:
            plot_test_errors(error_plot, stats, marathon_type, test_type, x)
        error_plot.legend(loc='upper left')

    plt.savefig(file_name)
