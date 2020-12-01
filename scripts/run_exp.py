#!/usr/bin/env python3

import argparse
import enum
import os
import re
import shlex
import subprocess
import time


class StorageMedia(enum.Enum):
    MEMORY = "memory"
    SSD = "ssd"
    HDD = "hdd"

    def __str__(self):
        return self.value


WD = os.path.join("/", "usr", "local", "src", "TaoStore")
CONFIGS_DIR = os.path.join(WD, "configs")
DEFAULT_CONFIG = os.path.join(CONFIGS_DIR, "default.config")

BASE_CMD = "java --class-path ./out/production/TaoStore:./libs/guava-19.0.jar:./libs/commons-math3-3.6.1.jar:./libs/junit-4.11.jar"
CLIENT_CLASS = "TaoClient.TaoClient"
PROXY_CLASS = "TaoProxy.TaoProxy"
SERVER_CLASS = "TaoServer.TaoServer"


def parse_args():
    parser = argparse.ArgumentParser(description="Run experiments")

    parser.add_argument("--patterns", "-p", type=str, required=False,
                        help="patterns to filter experiments")

    parser.add_argument("--logdir", "-l", type=str, required=False,
                        default=os.path.join(".", "exp_data"),
                        help="patterns to filter experiments")

    return parser.parse_args()


def run_sync(cmd):
    subprocess.run(shlex.split(cmd),
                   cwd=WD,
                   check=True)


def run_sync_unchecked(cmd):
    subprocess.run(shlex.split(cmd),
                   cwd=WD)


def run(cmd):
    return subprocess.Popen(shlex.split(cmd),
                            cwd=WD,
                            stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT)


def client_cmd(config):
    cmd = "{} {} --config_file {}".format(BASE_CMD, CLIENT_CLASS, config["config_file"])
    cmd += " --runType load_test --load_test_type asynchronous"
    cmd += " --load_size {}".format(config["num_operations"])
    cmd += " --data_set_size {}".format(config["num_blocks"])
    return cmd


def proxy_cmd(config):
    cmd = "{} {} --config_file {}".format(BASE_CMD, PROXY_CLASS, config["config_file"])
    return cmd


def server_cmd(config):
    cmd = "{} {} --config_file {}".format(BASE_CMD, SERVER_CLASS, config["config_file"])
    return cmd


def generate_log_dirname(root, config):
    dirname = ""

    fields = ["tag", "num_blocks", "num_clients", "num_operations", "storage"]
    for field in fields:
        if field in config:
            dirname += field + "@" + str(config[field]) + "__"

    return os.path.join(root, dirname[:-2])


def generate_configs():
    configs = []
    default = {
        "config_file": DEFAULT_CONFIG,
        "num_blocks": 1000,
        "num_operations": 1000,
        "storage": str(StorageMedia.HDD),
    }

    # STORAGE
    for sm in list(StorageMedia):
        for num_clients in [2**i for i in range(0, 5)]:
            storage = default.copy()

            storage["tag"] = "storage"
            storage["storage"] = str(sm)
            storage["num_clients"] = num_clients

            configs.append(storage)

    # SCALABILITY
    for num_clients in [2**i for i in range(0, 5)]:
        scalability = default.copy()

        scalability["tag"] = "scalability"
        scalability["num_clients"] = num_clients

        configs.append(scalability)


    return configs


def filter_configs(configs, patterns):
    filtered = []
    if patterns:
        patterns = patterns.split("__")

        for config in configs:
            match = True
            for pattern in patterns:
                key, _, value = pattern.partition("@")
                if key not in config or str(config[key]) != str(value):
                    match = False
                    break

            if match:
                filtered.append(config)
    else:
        filtered = configs

    return filtered


def replace_config_line(config_string, key, value):
    return re.sub("{}=.*$".format(key),
                  "{}={}\n".format(key, value),
                  config_string,
                  flags=re.MULTILINE)


def write_config_file(config):
    with open(config["config_file"], "r") as config_file:
        config_string = config_file.read()

    field_mappings = [
        ("log_directory", "log_directory"),
        ("num_clients", "proxy_thread_count"),
    ]

    for fm in field_mappings:
        config_string = replace_config_line(config_string, fm[1], config[fm[0]])
    
    if "storage" in config:
        sm = config["storage"]
        path = ""
        if sm == str(StorageMedia.HDD):
            path = os.path.join("/", "usr", "local", "src", "TaoStore", "oram.txt")
        elif sm == str(StorageMedia.SSD):
            path = os.path.join("/", "mnt", "ssd", "oram.txt")
        elif sm == str(StorageMedia.MEMORY):
            path = os.path.join("/", "tmp", "oram.txt")
        else:
            raise ValueError("Unexpected StorageMedia: " + sm)

        config_string = replace_config_line(config_string, "oram_file", path)

    log_directory = config["log_directory"]
    if not os.path.exists(log_directory):
        os.makedirs(log_directory)

    config_filename = os.path.join(log_directory, "experiment.config")
    with open(config_filename, "w") as config_file:
        config_file.write(config_string)

    config["config_file"] = config_filename


def reset_state():
    run_sync_unchecked("killall -q java")
    run_sync("rm -f oram.txt")
    run_sync("ant clean all")


def run_exp(config):
    reset_state()
    write_config_file(config)

    server = run(server_cmd(config))
    proxy = run(proxy_cmd(config))
    client = run(client_cmd(config))

    try:
        stdout, stderr = client.communicate(timeout=240)

        if stdout is not None:
            stdout = stdout.decode("utf-8")
            with open(os.path.join(config["log_directory"], "client.log"), "w+") as f:
                f.write(stdout)

    except subprocess.TimeoutExpired:
        print("Experiment failed!")

    server.kill()
    proxy.kill()

    if server.stdout is not None:
        stdout = server.stdout.read().decode("utf-8")
        with open(os.path.join(config["log_directory"], "server.log"), "w+") as f:
            f.write(stdout)

    if proxy.stdout is not None:
        stdout = proxy.stdout.read().decode("utf-8")
        with open(os.path.join(config["log_directory"], "proxy.log"), "w+") as f:
            f.write(stdout)


def run_all(args):
    # Generate configs
    configs = generate_configs()
    configs = filter_configs(configs, args.patterns)

    # Generate log dir names
    for config in configs:
        config["log_directory"] = generate_log_dirname(args.logdir, config)

    # Run experiments
    for config in configs:
        run_exp(config)


def main():
    args = parse_args()

    run_all(args)


if __name__ == "__main__":
    main()


