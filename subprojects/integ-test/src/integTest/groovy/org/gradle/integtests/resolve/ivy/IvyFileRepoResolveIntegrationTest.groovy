/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import spock.lang.Issue
import spock.lang.Ignore

class IvyFileRepoResolveIntegrationTest extends AbstractDependencyResolutionTest {

    @Ignore
    @Issue("GRADLE-2502")
    public void "can resolve dynamic version from different repositories"() {
        given:
        def repo1 = ivyRepo("ivyRepo1")
        def repo2 = ivyRepo("ivyRepo2")
        repo1.module('group', 'projectA', '1.1').withStatus("milestone").publish()
        repo2.module('group', 'projectA', '1.2').withStatus("integration").publish()

        and:
        buildFile << """
  repositories {
      ivy {
          url "${repo1.uri}"
      }
      ivy {
          url "${repo2.uri}"
      }

  }
  configurations { compile }
  dependencies {
      compile 'group:projectA:latest.milestone'
  }
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """

        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar')
    }

    public void "does not cache local artifacts or metadata"() {
        given:
        def repo = ivyRepo()
        def moduleA = repo.module('group', 'projectA', '1.2')
        moduleA.publish()
        def moduleB = repo.module('group', 'projectB', '9-beta')
        moduleB.publish()

        and:
        buildFile << """
repositories {
    ivy {
        artifactPattern "${repo.uri}/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(moduleA.jarFile)

        when:
        moduleA.dependsOn('group', 'projectB', '9-beta')
        moduleA.publishWithChangedContent()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-9-beta.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(moduleA.jarFile)
        file('libs/projectB-9-beta.jar').assertIsCopyOf(moduleB.jarFile)
    }

    public void "does not cache resolution of dynamic versions or changing modules"() {
        def repo = ivyRepo()

        given:
        buildFile << """
repositories {
    ivy {
        artifactPattern "${repo.uri}/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]"
    }
}

configurations {
    compile
}

dependencies {
    compile group: "group", name: "projectA", version: "1.+"
    compile group: "group", name: "projectB", version: "latest.integration"
    compile group: "group", name: "projectC", version: "1.0", changing: true
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        when:
        def projectA1 = repo.module("group", "projectA", "1.1")
        projectA1.publish()
        def projectB1 = repo.module("group", "projectB", "1.0")
        projectB1.publish()
        def projectC1 = repo.module("group", "projectC", "1.0")
        projectC1.publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar', 'projectB-1.0.jar', 'projectC-1.0.jar')
        file('libs/projectA-1.1.jar').assertIsCopyOf(projectA1.jarFile)
        file('libs/projectB-1.0.jar').assertIsCopyOf(projectB1.jarFile)
        def jarC = file('libs/projectC-1.0.jar')
        jarC.assertIsCopyOf(projectC1.jarFile)
        def jarCsnapshot = jarC.snapshot()

        when:
        def projectA2 = repo.module("group", "projectA", "1.2")
        projectA2.publish()
        def projectB2 = repo.module("group", "projectB", "2.0")
        projectB2.publish()
        projectC1.publishWithChangedContent()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-2.0.jar', 'projectC-1.0.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(projectA2.jarFile)
        file('libs/projectB-2.0.jar').assertIsCopyOf(projectB2.jarFile)

        def jarC1 = file('libs/projectC-1.0.jar')
        jarC1.assertIsCopyOf(projectC1.jarFile)
        jarC1.assertHasChangedSince(jarCsnapshot)
    }
}
