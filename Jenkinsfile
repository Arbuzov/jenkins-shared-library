@Library('asatnet@develop')_
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
						publishArtifactory('FileExchange/debug/'),
						["\t<li>","</li>\n"],
						["<ul>\n","</ul>\n"]
					),
					"Unversioned list"
				)
				sayHello 'Maaan'
				publishSummary(
					arrayToString(
						publishArtifactory('FileExchange/debug/', 'artifactory', 'debug.txt', BUILD_NUMBER),
						["\t<li>","</li>\n"],
						["<ul>\n","</ul>\n"]
					)
				)
				
				
				copyFromArtifactory('FileExchange/debug/', 'artifactory', 'latest');
				
				copyFromArtifactory('FileExchange/debug/', 'artifactory', 'versioned', '20');
				
				timeout(time: 10, unit: 'SECONDS') {
					input (
						message: "Message"
					);
				}
				echo getLatestApprover();
				
				archiveArtifacts(
					artifacts: "**/**",
					onlyIfSuccessful: false
				)
			}
		}
	}
}