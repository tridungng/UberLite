#!/usr/bin/env python3
"""Cross-file consistency guard for the UberLite stack.

`server.port` and `spring.application.name` are each duplicated in four places - the module's
application.yml, its Dockerfile (`EXPOSE` + `HEALTHCHECK`), docker-compose.yml, and the gateway's
route table. No Java test can see across all four, and drift between them is silent: a service with
the wrong name registers with Eureka as UNKNOWN and every `@FeignClient` calling it fails with
"No instances available" only at runtime.

Run from the repo root:

    python3 scripts/check-config-consistency.py

Exits non-zero on any mismatch, so it can be wired into CI.
"""
import glob
import os
import re
import sys

# Infrastructure, not application services: they are not called by service id and are not routed.
INFRA = {"discovery-server", "api-gateway"}


def load_module_config():
    ports, names = {}, {}
    for path in sorted(glob.glob("*/src/main/resources/application.yml")):
        module = path.split("/")[0]
        # Only the default document; the docker profile must not redefine port or name.
        default_doc = open(path).read().split("\n---")[0]
        port = re.search(r"^\s*port:\s*(\d+)", default_doc, re.M)
        name = re.search(r"^\s{2}application:\s*\n(?:\s*#.*\n)*\s{4}name:\s*(\S+)", default_doc, re.M)
        ports[module] = port.group(1) if port else None
        names[module] = name.group(1) if name else None
    return ports, names


def load_compose_ports():
    """Published ports, read only from the `services:` block so the depends_on anchors are ignored."""
    text = open("docker-compose.yml").read()
    services_block = text.split("\nservices:\n", 1)[1].split("\nnetworks:", 1)[0]
    published = {}
    for match in re.finditer(r"^  ([a-z0-9-]+):$(.*?)(?=^  [a-z0-9-]+:$|\Z)",
                             services_block, re.M | re.S):
        mapping = re.search(r'ports:\s*(?:\n\s*-\s*|\[)"?(\d+):(\d+)"?', match.group(2))
        if mapping:
            published[match.group(1)] = (mapping.group(1), mapping.group(2))
    return published


def dockerfile_ports(module):
    path = os.path.join(module, "Dockerfile")
    if not os.path.exists(path):
        return None, None
    text = open(path).read()
    exposed = re.search(r"^EXPOSE (\d+)", text, re.M)
    probed = re.search(r"http://localhost:(\d+)/actuator/health", text)
    return (exposed.group(1) if exposed else None), (probed.group(1) if probed else None)


# The registry is the one service everything may wait for: it depends on nothing itself, so it
# cannot create a chain. Everything else finds its peers through Eureka at call time.
ALLOWED_SERVICE_DEPENDENCY = {"discovery-server"}


def compose_service_dependencies():
    """Maps each service built from this repo to the other *repo-built* services it waits for.

    A service-to-service `depends_on` re-couples the deployment units: it serialises startup, it
    makes `docker compose up <one-service>` drag half the stack in, and when something fails it
    reports the blocked services rather than the broken one. It also cannot be honoured in
    production, where the scheduler starts pods in whatever order it likes - so a service that needs
    it is broken there too. See docker-compose.yml's header and the root README.
    """
    text = open("docker-compose.yml").read()
    services_block = text.split("\nservices:\n", 1)[1].split("\nnetworks:", 1)[0]
    blocks = {name: body for name, body in re.findall(
        r"^  ([a-z0-9-]+):$(.*?)(?=^  [a-z0-9-]+:$|\Z)", services_block, re.M | re.S)}

    built = {name for name, body in blocks.items() if re.search(r"^\s+build:$", body, re.M)}
    offenders = {}
    for name in built:
        depends = re.search(r"^    depends_on:$(.*?)(?=^    [a-z_]+:|\Z)",
                            blocks[name], re.M | re.S)
        if not depends:
            continue
        targets = set(re.findall(r"^      ([a-z0-9-]+):$", depends.group(1), re.M))
        bad = sorted((targets & built) - ALLOWED_SERVICE_DEPENDENCY - {name})
        if bad:
            offenders[name] = bad
    return offenders


def dockerfile_probe_is_liveness(module):
    """The container probe must be the liveness group, not the composite health endpoint.

    The composite aggregates Eureka and the datastores, so probing it lets an unrelated dependency
    mark a healthy container unhealthy - and a restart, the only thing a runtime can do about it,
    never fixes the dependency.
    """
    path = os.path.join(module, "Dockerfile")
    if not os.path.exists(path):
        return True
    return "/actuator/health/liveness" in open(path).read()


def main():
    ports, names = load_module_config()
    compose = load_compose_ports()
    routed = set(re.findall(r"uri:\s*lb://(\S+)",
                            open("api-gateway/src/main/resources/application.yml").read()))

    problems = []
    header = f"{'module':30} {'port':6} {'name':30} {'EXPOSE':7} {'probe':7} {'compose':12} status"
    print(header)
    print("-" * len(header))

    for module in sorted(ports):
        port, name = ports[module], names[module]
        exposed, probed = dockerfile_ports(module)
        pub = compose.get(module)
        issues = []

        if port is None:
            issues.append("no server.port")
        if name is None:
            issues.append("no spring.application.name")
        elif name != module and module not in INFRA:
            issues.append(f"name '{name}' != module directory")
        if exposed != port:
            issues.append(f"Dockerfile EXPOSE {exposed} != server.port {port}")
        if probed != port:
            issues.append(f"HEALTHCHECK port {probed} != server.port {port}")
        if pub is None:
            issues.append("not published in docker-compose.yml")
        elif pub[1] != port:
            issues.append(f"compose container port {pub[1]} != server.port {port}")
        if name and name not in routed and module not in INFRA:
            issues.append("no gateway route")
        if not dockerfile_probe_is_liveness(module):
            issues.append("HEALTHCHECK must probe /actuator/health/liveness, not the composite")

        problems.extend(f"{module}: {issue}" for issue in issues)
        print(f"{module:30} {str(port):6} {str(name):30} {str(exposed):7} {str(probed):7} "
              f"{(':'.join(pub) if pub else '-'):12} {'OK' if not issues else 'MISMATCH'}")

    for service, targets in sorted(compose_service_dependencies().items()):
        problems.append(
            f"{service}: depends_on {', '.join(targets)} - application services must not wait "
            f"for each other (see docker-compose.yml header)")

    if problems:
        print("\nInconsistencies:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1

    print("\nAll module ports, service ids, Dockerfiles, compose entries and gateway routes agree.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

