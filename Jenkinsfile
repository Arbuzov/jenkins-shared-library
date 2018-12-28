@Library('asatnet@master')_
GIT_URL = ''
pipeline {
	agent {label 'master'}
	options {
		disableConcurrentBuilds()
	}
	stages {
		stage('Test steps')
		{
			steps {
				sh "install -d versioned && install -d latest && env>debug.txt"
				
				publishSummary(
					arrayToString(
						publishArtifactory('FileExchange/debug/', 'artifactory', 'debug.txt'),
						["\t<li>","</li>\n"],
						["<ul>\n","</ul>\n"]
					),
					"Unversioned list"
				)
				sayHello 'User'
				script {
					ARTIFACTORY_UPLOADS = publishArtifactory('FileExchange/debug/', 'artifactory', 'debug.txt', BUILD_NUMBER);
				}
				publishSummary(
					arrayToString(
						ARTIFACTORY_UPLOADS,
						["\t<li>","</li>\n"],
						["<ul>\n","</ul>\n"]
					)
				)
				sh "env"
				script {
					if (env.GIT_URL == null) {
						GIT_URL = env.GIT_URL_1;
					} else {
						GIT_URL = env.GIT_URL;
					}
				}
				withCredentials([string(credentialsId: 'gitlab-text-secret', variable: 'gitLabAPISecret')]) {
					tagToGitlab(GIT_URL, "1", env.GIT_COMMIT);
					tagToGitlab(
						GIT_URL,
						"${BUILD_NUMBER}",
						env.GIT_COMMIT,
						"This is a test tag from build ${BUILD_NUMBER}",
						"## Release ${BUILD_NUMBER}\n\n__Build reference:__ ${BUILD_URL}\n\n__Artifactory:__ ${ARTIFACTORY_UPLOADS[0]}"
					);
				}
				
				copyFromArtifactory('sidu-scpc-bbf')
				
				sh "ls -la1"
				
				copyFromArtifactory('FileExchange/debug/', 'artifactory', 'latest');

				archiveArtifacts(
					artifacts: "**/**",
					onlyIfSuccessful: false
				)
				script {
					try {
						timeout(time: 20, unit: 'SECONDS') {
							input (
								message: "Message"
							);
						}
					} catch (err) {
						def user = err.getCauses()[0].getUser()
						if('SYSTEM' == user.toString()) {
							currentBuild.result = "SUCCESS"
						}
					}
				}
				echo getLatestApprover();
			}
			
			post {
				always {
					cleanWs()
				}
			}
		}
	}
}