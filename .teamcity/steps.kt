
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildSteps.*

object CommonSteps {

    fun BuildType.createParameters(
    ) {
        params {
            param("teamcity.pullRequest.number", "")
            param("teamcity.git.fetchAllHeads", "true")
        }
    }

    fun BuildType.buildAndTest(

    ) {
        steps {
//            nuGetInstaller {
//                toolPath = "%teamcity.tool.NuGet.CommandLine.DEFAULT%"
//                projects = "TCSonarCube.sln"
//            }
            dotnetBuild {
                enabled = false
                name = "Build Solution"
                workingDir = "project"
                projects = "TCSonarCube.sln"
                sdk = "6"
                param(
                    "dotNetCoverage.dotCover.home.path",
                    "%teamcity.tool.JetBrains.dotCover.CommandLineTools.DEFAULT%"
                )
            }
//            dotnetTest {
//                name = "Test Solution"
//                workingDir = "project"
//                projects = "TCSonarCube.sln"
//                sdk = "6"
//                param(
//                    "dotNetCoverage.dotCover.home.path",
//                    "%teamcity.tool.JetBrains.dotCover.CommandLineTools.DEFAULT%"
//                )
//            }
            script {
                enabled = false
                name = "Test Solution In A Container"
                workingDir = "project"
                scriptContent = "dotnet test TCSonarCube.sln -r /src/results --logger 'trx;logfilename=testresults.trx' --nologo"
                dockerImagePlatform = ScriptBuildStep.ImagePlatform.Linux
                dockerImage = "mcr.microsoft.com/dotnet/sdk:6.0"
                dockerRunParameters = """
                    --env ASPNETCORE_ENVIRONMENT=Build
                    -v %system.teamcity.build.checkoutDir%/test-results:/src/results
                    -v /var/run/docker.sock:/var/run/docker.sock
                """.trimIndent()
            }

            script {
                enabled = false
                name = "Batect"
                workingDir = "./"
                scriptContent = """
                #!/bin/bash
                chmod +x ./batect
                export BATECT_ENABLE_TELEMETRY=false
                pullRequestNumber="NOT_SET"
                if [ -n "%teamcity.pullRequest.number%" ]; then
                    pullRequestNumber="%teamcity.pullRequest.number%"
                fi
                ./batect \
                --config-var TC_SONAR_QUBE_USE="%env.sonar_use%" \
                --config-var TC_SONAR_QUBE_SERVER=""%env.sonar_server%"" \
                --config-var TC_SONAR_QUBE_USER=""%env.sonar_user%"" \
                --config-var TC_SONAR_QUBE_PASSWORD=""%env.sonar_password%"" \
                --config-var TC_SONAR_QUBE_VERSION=""%build.counter%"" \
                --config-var TC_SONAR_QUBE_NUMBER=""${"$"}pullRequestNumber"" \
                run-tests
                """.trimIndent()
            }
        }
    }

    fun BuildType.printPullRequestNumber(
    ) {
        steps {
            script {
                name = "Print Pull Request Number teamcity-sonar"
                scriptContent = """
                #!/bin/bash
                id=%teamcity.pullRequest.number%
                echo "Id is: ${'$'}id"
                branch="pull/${'$'}id"
                echo "Branch is: ${'$'}branch"
            """.trimIndent()
            }
        }
    }

    fun BuildType.printDeployNumber(
    ) {
        steps {
            script {
                name = "Print Deploy Number teamcity-sonar"
                scriptContent = """
                #!/bin/bash
                echo "Running deployment"
                counter=%build.counter%
                echo "Counter is: ${'$'}counter"
            """.trimIndent()
            }
        }
    }

    fun BuildType.runSonarScript(
    ) {
        //CHANGE THIS BEFORE USING FOR REALZ"
        val imageRepository = "jpspringall"
        //CHANGE THIS BEFORE USING FOR REALZ"
        steps {
            exec {
                enabled = true
                name = "Run Sonar Script"
                path = "ci/run-sonar.sh"
                arguments =
                    """-s ""%env.sonar_server%"" -u ""%env.sonar_user%"" -p ""%env.sonar_password%"" -n ""%teamcity.pullRequest.number%"" -v ""%build.counter%"""""
                formatStderrAsError = true
                dockerImagePlatform = ExecBuildStep.ImagePlatform.Linux
                dockerPull = false
                dockerImage = "${imageRepository}/dotnet-sonar-scanner:9.0.1" //CHECK IMAGE NAME FOR REALZ
                dockerRunParameters = """
                    --network=host
                    -v %system.teamcity.build.checkoutDir%/test-results:/test-results
                """.trimIndent()
            }
        }
    }

    fun BuildType.runMakeTest(
    ) {
        steps {
            exec {
                enabled = false
                name = "Run End 2 End Tests"
                workingDir = "./"
                path = "./ci/run-end-2-end-test.sh"
                arguments = "-s %env.sonar_server% -i \"1\" -u \"%env.sonar_user%\" -p \"%env.sonar_password%\" -c \"%build.counter%\" -r \"%teamcity.pullRequest.number%\" -n \"%build.number%\""
                formatStderrAsError = true
            }
            script {
                enabled = false
                name = "Execute Make As script"
                workingDir = "./"
                scriptContent = """
                #!/bin/bash
                set +e # Continue on error

                echo "Running make directly"
                make

                echo "Running make via script"
                ./ci/make-test.sh


                """.trimIndent()
            }
        }
    }

}
