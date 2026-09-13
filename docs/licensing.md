# SDK licensing

PushPort Android SDK is licensed under the [Apache License, Version 2.0](../LICENSE).

| Item | Value |
| --- | --- |
| Copyright holder and publisher | Oleh Yurkov |
| Copyright notice | Copyright 2026 Oleh Yurkov |
| Project name | PushPort Android SDK |
| SPDX identifier | Apache-2.0 |
| Public contact | support@pushport.dev |
| Project website | https://pushport.dev |
| Maven coordinates | dev.pushport:android-sdk |

## Scope

The license applies to the original source code, tests, build scripts and documentation in `PushPortLibrary`. PushPort is the project name, not a separate corporate copyright holder. The license does not extend to sibling projects, the backend, customer or operator sites, or access to the hosted PushPort service. Dependencies remain subject to their respective licenses.

## Using and redistributing the SDK

The SDK may be used in commercial and closed-source applications. Applications that merely use the SDK do not have to publish their source code under this license. Modifications and redistribution of the SDK are permitted subject to the full license terms.

When redistributing the SDK or a derivative, include the license and applicable notices. Mark modified files and retain the notices required by section 4 of the license. [NOTICE](../NOTICE) identifies the original project and copyright holder. The license does not grant rights to the PushPort brand beyond the uses permitted in section 6.

This guide summarizes the distribution setup; [LICENSE](../LICENSE) contains the governing terms.

## Published archives

The build copies the canonical `LICENSE` and `NOTICE` files into `META-INF/dev.pushport/android-sdk/` in the AAR's `classes.jar`, the sources JAR and the Dokka `javadoc` JAR. The SDK-specific path avoids collisions with notices shipped by other dependencies. Generated copies are build outputs; edit the canonical files only.

The POM declares Apache License 2.0 and the publisher's public contact. Before release, verify the notices in the actual Maven artifacts and preserve relevant attribution when assembling the consuming application's third-party notices.

## References

- [Apache License 2.0 and instructions for applying it](https://www.apache.org/licenses/LICENSE-2.0)
- [Maven Central metadata requirements](https://central.sonatype.org/publish/requirements/)
