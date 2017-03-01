#!/usr/bin/env python3

import http.server
import os
import socketserver
import sys

class Handler(http.server.SimpleHTTPRequestHandler):

    def do_GET(self):
        self.send_response(200)
        marathonId = os.getenv("MARATHON_APP_ID", "NO_MARATHON_APP_ID_SET")
        self.wfile.write("Pong {}".format(marathonId))
        return

if __name__ == "__main__":
    port = int(sys.argv[1])
    appId = sys.argv[2]
    version = sys.argv[3]
    url = "{}/{}".format(sys.argv[4], port)
    taskId = os.getenv("MESOS_TASK_ID", "<UNKNOWN>")

    with socketserver.TCPServer(("", port), Handler) as httpd:
       msg = "AppMock[{appId} {version}]: {taskId} has taken the stage at port {port}. Will query {url} for health status.".format(appId, version, taskId, port, url)
       print(msg)
       httpd.serve_forever()


