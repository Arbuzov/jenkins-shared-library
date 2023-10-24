def call (Map args) {
    DIST_REPO = [
        'stretch': [
            'repo': 'scpc',
            'image': 'debian:stretch-slim',
        ], 
        'buster': [
            'repo': 'buster',
            'image': 'debian:buster-slim',
        ],
        'bullseye': [
            'repo': 'bullseye',
            'image': 'debian:bullseye-slim',
        ],
        'bookworm': [
            'repo': 'bookworm-local',
            'image': 'debian:bookworm-slim',
        ]
    ]
    PACKAGE_NAME = args.PACKAGE_NAME
    DISTS = args.DISTS ?: ['stretch', 'buster', 'bullseye', 'bookworm']
    VERSION = args.VERSION ?: ''
    pipeline {
        agent any
        stages {
            stage ('Build spacebridge packages') {
                matrix {
                    axes {
                        axis {
                            name 'DIST_NAME'
                            values 'stretch', 'buster' , 'bullseye', 'bookworm'
                        }
                    }
                    stages {
                        stage('Env wrapper') {
                            when {
                                expression {
                                    return DIST_NAME in DISTS
                                }
                            }
                            stages {
                                stage('Build') {
                                    agent {
                                        dockerfile {
                                            additionalBuildArgs  "--build-arg DIST_NAME=${DIST_NAME} --build-arg DIST_IMAGE=${DIST_REPO[DIST_NAME]['image']}"
                                        }
                                    }
                                    environment {
                                    IS_DEVELOPMENT = "${((CHANGE_ID)||(GIT_BRANCH!='master'))}"
                                    DEB_VERSION_SUFFIX="${DIST_NAME}${IS_DEVELOPMENT=='true'?'.dev':''}.${env.BUILD_NUMBER}"
                                    }
                                    steps {
                                        sh """#!/bin/bash
                                            # set -e
                                            # set -x
                                            rm -rf deb_dist
                                            pip3 download --no-binary=${PACKAGE_NAME} ${PACKAGE_NAME}${VERSION==''?'':'=='+VERSION}
                                            py2dsc-deb --debian-version=${DEB_VERSION_SUFFIX} \
                                                        --dist-dir ./deb_dist ${PACKAGE_NAME}-[0-9]*.tar.gz
                                            ls -la deb_dist
                                        """
                                        stash includes: 'deb_dist/*.deb', name: "${DIST_NAME}-deb"
                                    }
                                }
                                stage('Push deb package to the repository') {
                                    when {
                                        allOf {
                                            branch 'master'
                                            not { changeRequest() }
                                        }
                                    }
                                    agent { label 'local-deb-repo' }
                                    options { skipDefaultCheckout() }
                                    steps {
                                        unstash "${DIST_NAME}-deb"
                                        dir('deb_dist') {
                                            script {
                                                findFiles(glob: "*.deb").each {
                                                    sh """
                                                        sudo reprepro -C main -b /data/debian/ remove ${DIST_REPO[DIST_NAME]['repo']} `dpkg -f ${it.getName()} Package`
                                                        sudo reprepro -C main -b /data/debian/ includedeb ${DIST_REPO[DIST_NAME]['repo']} ${it.getName()}
                                                        rm ${it.getName()}
                                                    """
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}