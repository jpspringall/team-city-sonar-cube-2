
import CommonSteps.buildAndTest
import CommonSteps.createParameters
import CommonSteps.printDeployNumber
import CommonSteps.printPullRequestNumber
import CommonSteps.runMakeTest
import CommonSteps.runSonarScript
import jetbrains.buildServer.configs.kotlin.AbsoluteId
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.RelativeId
import jetbrains.buildServer.configs.kotlin.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.project
import jetbrains.buildServer.configs.kotlin.toId
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.ui.add
import jetbrains.buildServer.configs.kotlin.version

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2023.11"

val mainCheckoutDirectory = "./sonar-qube-test"

object MasterBuild : BuildType({
    name = "Master Build"

    vcs {
        root(DslContext.settingsRoot.id!!)
        cleanCheckout = true
        excludeDefaultBranchChanges = true
    }

    params {
        param("git.branch.specification", "")
    }

    createParameters()

    printPullRequestNumber()

    runMakeTest()

    buildAndTest()

    runSonarScript()

    triggers {
        vcs {
        }
    }

    features {}
})

object PullRequestBuild : BuildType({
    name = "Pull Request Build"

    vcs {
        root(DslContext.settingsRoot)
        cleanCheckout = true
        excludeDefaultBranchChanges = true
    }

    params {
        param("git.branch.specification", "+:refs/pull/*/merge")
    }
    createParameters()

    printPullRequestNumber()

    runMakeTest()

    buildAndTest()

    runSonarScript()

    triggers {
        vcs {
        }
    }

    features {
        commitStatusPublisher {
            vcsRootExtId = "${DslContext.settingsRoot.id}"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = vcsRoot()
            }
        }
        pullRequests {
            vcsRootExtId = "${DslContext.settingsRoot.id}"
            provider = github {
                authType = vcsRoot()
                filterSourceBranch = "refs/pull/*/merge"
                filterAuthorRole = PullRequests.GitHubRoleFilter.MEMBER
            }
        }
    }
})

// Doing this, fixes this error: Kotlin:
// Object DeployBuild captures the script class instance. Try to use class or anonymous object instead
// https://stackoverflow.com/questions/17516930/how-to-create-an-instance-of-anonymous-class-of-abstract-class-in-kotlin
val deployBuild = BuildType{

    val buildTypeName = "Deploy Build"

    name = buildTypeName
    id = RelativeId(buildTypeName.toId())


    vcs {
        root(DslContext.settingsRoot)
        cleanCheckout = true
        excludeDefaultBranchChanges = true
    }

    buildNumberPattern = MasterBuild.depParamRefs.buildNumber.toString()

    dependencies {
        snapshot(MasterBuild) {
            onDependencyFailure = FailureAction.FAIL_TO_START
            onDependencyCancel = FailureAction.CANCEL
        }
    }

    params {
        param("git.branch.specification", "")
        param("https.private.root.build.step", "%https.private.root%")
    }

    createParameters()

    printDeployNumber(mainCheckoutDirectory)

    triggers {
    }

    features {}
}

val builds: ArrayList<BuildType> = arrayListOf()

builds.add(MasterBuild)
builds.add(PullRequestBuild)
builds.add(deployBuild)

val project = Project {
    builds.forEach{
        buildType(it)
    }

    buildTypesOrder = builds
}

for (bt : BuildType in project.buildTypes ) {
    val gitSpec = bt.params.findRawParam("git.branch.specification")
    if (gitSpec != null && gitSpec.value.isNotBlank()) {
        bt.vcs.branchFilter = """
            +:*
            -:<default>
        """.trimIndent()
    }
    if (bt.name == "Pull Request Build" || bt.name == "Master Build") {
        bt.features.add {
            feature {
                type = "xml-report-plugin"
                param("verbose", "true")
                param("xmlReportParsing.reportType", "trx")
                param("xmlReportParsing.reportDirs","%system.teamcity.build.checkoutDir%/test-results/**/*.trx")
            }
        }
    }

    if (bt.name == "Deploy Build") {


        bt.vcs.root(DslContext.settingsRoot.id!!, "+:. => $mainCheckoutDirectory")
        val vcsRootName = "RootTeamCitySonarCubeProject_TeamCitySonarPrivateHttps"
        bt.vcs.root(AbsoluteId(vcsRootName), "+:. => ./private-https-test")

    }

//    if (bt.name == "Pull Request Build" || bt.name == "Master Build")
//    {
//        bt.features.add {  xmlReport {
//            reportType = XmlReport.XmlReportType.TRX
//            rules = "%system.teamcity.build.checkoutDir%/test-results/**/*.trx" //Remember to match this in test output
//            verbose = true
//        } }
//    }
}

project(project)