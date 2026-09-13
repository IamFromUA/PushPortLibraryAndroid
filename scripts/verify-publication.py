#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Oleh Yurkov
# SPDX-License-Identifier: Apache-2.0

"""Verify the locally published SDK before an upload or consumer build."""

import argparse
import hashlib
import io
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
SCM_URL = "https://github.com/IamFromUA/PushPortLibraryAndroid"
NOTICE_PATH = "META-INF/dev.pushport/android-sdk/"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=ROOT / "build/maven-repository")
    parser.add_argument("--pom", type=Path, default=ROOT / "build/publications/release/pom-default.xml")
    args = parser.parse_args()
    pom_bytes = args.pom.read_bytes()
    pom = ET.fromstring(pom_bytes)

    def value(path):
        return pom.findtext("/".join("m:" + part for part in path.split("/")), namespaces=NS)

    expected = {
        "groupId": "dev.pushport",
        "artifactId": "android-sdk",
        "packaging": "aar",
        "name": "PushPort Android SDK",
        "url": "https://pushport.dev",
        "licenses/license/name": "The Apache License, Version 2.0",
        "licenses/license/url": "https://www.apache.org/licenses/LICENSE-2.0.txt",
        "developers/developer/name": "Oleh Yurkov",
        "developers/developer/email": "support@pushport.dev",
        "scm/url": SCM_URL,
        "scm/connection": "scm:git:" + SCM_URL + ".git",
        "scm/developerConnection": "scm:git:ssh://git@github.com/IamFromUA/PushPortLibraryAndroid.git",
    }
    for field, expected_value in expected.items():
        require(value(field) == expected_value, f"Unexpected POM field: {field}")
    require(bool(value("description")), "POM description is missing")
    version = value("version")
    require(bool(version) and "/" not in version and "\\" not in version, "Invalid version")
    directory = args.repository / "dev/pushport/android-sdk" / version
    stem = "android-sdk-" + version
    require((directory / (stem + ".pom")).read_bytes() == pom_bytes, "Published POM is stale")

    def verify_notices(archive):
        names = archive.namelist()
        for filename in ("LICENSE", "NOTICE"):
            entry = NOTICE_PATH + filename
            require(names.count(entry) == 1, f"Missing or duplicated notice: {entry}")
            require(archive.read(entry) == (ROOT / filename).read_bytes(), f"Stale notice: {entry}")

    for suffix in (".aar", "-sources.jar", "-javadoc.jar"):
        path = directory / (stem + suffix)
        with ZipFile(path) as archive:
            if suffix == ".aar":
                with ZipFile(io.BytesIO(archive.read("classes.jar"))) as classes:
                    verify_notices(classes)
            else:
                verify_notices(archive)
        print(f"Verified notices: {path.name}")

    module = json.loads((directory / (stem + ".module")).read_text(encoding="utf-8"))
    require(module["component"]["group"] == "dev.pushport", "Incorrect Gradle module group")
    require(module["component"]["module"] == "android-sdk", "Incorrect Gradle module name")
    require(module["component"]["version"] == version, "Incorrect Gradle module version")
    for suffix in (".pom", ".module", ".aar", "-sources.jar", "-javadoc.jar"):
        artifact = directory / (stem + suffix)
        for algorithm in ("md5", "sha1", "sha256", "sha512"):
            checksum = Path(str(artifact) + "." + algorithm)
            digest = hashlib.new(algorithm, artifact.read_bytes()).hexdigest()
            require(checksum.read_text().strip() == digest, f"Invalid checksum: {checksum.name}")
    print(f"Verified metadata and checksums: dev.pushport:android-sdk:{version}")
    print("This validates local artifacts only; it does not upload or publish a release.")


if __name__ == "__main__":
    main()
